// Event-layer verification for the SSR pages, with GTM switched OFF.
//
// Why this can exist at all: utils/analytics.js pushes into window.dataLayer
// unconditionally — the container only *consumes* that queue. So event coverage
// is provable without GTM, without CookieYes, and without sending a single hit
// to Google or Meta. The script asserts that too, so running it can never
// pollute GA4 or an ad audience.
//
// What it actually proves, and the reason it is not a unit test: the CRA was a
// SPA where a CTA never destroyed the document, so a queued event always
// survived. Under SSR these CTAs perform real navigations, and an event pushed
// in the same tick can die with the page. Mirroring dataLayer into
// sessionStorage from an init script means the assertion still sees events that
// were pushed immediately before the document went away — which is the only way
// to tell "the event fired" apart from "the event fired and survived".
//
// Usage: node scripts/verify-analytics.mjs [baseUrl]      (default :3000)

import { createRequire } from 'node:module';
import { readdirSync } from 'node:fs';
import path from 'node:path';

const BASE = (process.argv[2] || process.env.BASE_URL || 'http://localhost:3000').replace(/\/$/, '');

// Dev-only tool: playwright is deliberately NOT a dependency of this app (it
// would pull a browser download into every install and every CI image that only
// ever runs `next build`). Use a local copy if the repo grows one, else the npx
// cache a developer already has.
function loadPlaywright() {
  const require = createRequire(import.meta.url);
  try {
    return require('playwright');
  } catch {}
  const cache = path.join(process.env.HOME || '', '.npm', '_npx');
  let entries = [];
  try {
    entries = readdirSync(cache);
  } catch {}
  for (const entry of entries) {
    const candidate = path.join(cache, entry, 'node_modules', 'playwright');
    try {
      return require(candidate);
    } catch {}
  }
  console.error(
    'playwright not found. Run it once so npx caches it:\n  npx playwright@latest --version'
  );
  process.exit(2);
}

// One case per CTA that pushes an event on the home page. `mobile` marks the CTA
// that only exists in the mobile layout.
const CASES = [
  {
    name: 'hero — Start Group Vote',
    selector: '.hero-cta-group .hp-btn-primary',
    expect: { event: 'cta_click', cta_label: 'Start Group Vote', block: 'hero' },
  },
  {
    name: 'hero — Explore activities',
    selector: '.hero-cta-group .hp-btn-secondary',
    expect: { event: 'cta_click', cta_label: 'Explore activities', block: 'hero' },
  },
  {
    name: 'how-it-works — Start Group Vote',
    selector: '.how-it-works > button',
    expect: { event: 'cta_click', cta_label: 'Start Group Vote', block: 'trip_builder' },
  },
  {
    name: 'reviews — Start Group Vote',
    selector: '.reviews-section button.btn--primary',
    expect: { event: 'cta_click', cta_label: 'Start Group Vote', block: 'reviews' },
  },
  {
    name: 'activities — View All Activities',
    selector: '.featured-activities-cta',
    expect: { event: 'cta_click', cta_label: 'View All Activities', block: 'activities' },
  },
  {
    name: 'contact — WhatsApp',
    selector: '.contact-cta-wa',
    expect: { event: 'contact_click', channel: 'whatsapp' },
  },
  {
    name: 'sticky mobile bar — Start Group Vote',
    selector: '.sticky-vote-cta button',
    mobile: true,
    // The bar mounts only once an IntersectionObserver reports the hero gone, so
    // it does not exist in the initial DOM at any viewport.
    scrollPast: '.hero-cta-group',
    // Reported rather than dropped: a skipped case must not read as coverage.
    // Flip the flag in legacy-src/services/config.js to include it.
    disabledBy: 'STICKY_VOTE_CTA_ENABLED=false in services/config.js',
    expect: { event: 'cta_click', cta_label: 'Start Group Vote', block: 'sticky_mobile' },
  },
];

// Anything matching these means the run reached a real analytics endpoint, i.e.
// the guard broke or GTM was forced. Then the numbers below are contaminated and
// so is production data — hard fail rather than a warning.
const ANALYTICS_HOSTS =
  /googletagmanager\.com|google-analytics\.com|\/g\/collect|connect\.facebook\.net|facebook\.com\/tr|cookieyes/;

const MIRROR = () => {
  const KEY = '__verify_events';
  const read = () => {
    try {
      return JSON.parse(sessionStorage.getItem(KEY) || '[]');
    } catch {
      return [];
    }
  };
  // Patch push before the app's own `dataLayer = dataLayer || []` runs, so the
  // app keeps this array and the mirror captures every push, including ones made
  // milliseconds before a navigation tears the page down.
  window.dataLayer = window.dataLayer || [];
  const original = window.dataLayer.push.bind(window.dataLayer);
  window.dataLayer.push = function (...items) {
    const seen = read();
    for (const item of items) seen.push(item);
    try {
      sessionStorage.setItem(KEY, JSON.stringify(seen));
    } catch {}
    return original(...items);
  };
};

function matches(event, expected) {
  return Object.entries(expected).every(([key, value]) => event[key] === value);
}

const { webkit, devices } = loadPlaywright();
const browser = await webkit.launch();
const leaks = new Set();
const results = [];

for (const testCase of CASES) {
  if (testCase.disabledBy) {
    results.push({ name: testCase.name, skipped: testCase.disabledBy });
    continue;
  }
  const context = await browser.newContext(
    testCase.mobile ? { ...devices['iPhone 13'] } : { viewport: { width: 1280, height: 900 } }
  );
  await context.addInitScript(MIRROR);
  // The WhatsApp CTA is a real outbound link; keep the click local.
  await context.route(/wa\.me|whatsapp\.com/, (route) => route.abort());
  const page = await context.newPage();
  page.on('request', (request) => {
    if (ANALYTICS_HOSTS.test(request.url())) leaks.add(new URL(request.url()).host);
  });

  let error = null;
  let events = [];
  try {
    await page.goto(`${BASE}/`, { waitUntil: 'networkidle', timeout: 45000 });
    if (testCase.scrollPast) {
      const anchor = page.locator(testCase.scrollPast).first();
      await anchor.waitFor({ state: 'visible', timeout: 15000 });
      const box = await anchor.boundingBox();
      await page.evaluate((y) => window.scrollTo(0, y), (box?.y ?? 0) + (box?.height ?? 0) + 600);
      // IntersectionObserver callbacks are asynchronous; the mount follows one.
      await page.waitForTimeout(1200);
    }
    const target = page.locator(testCase.selector).first();
    await target.waitFor({ state: 'visible', timeout: 15000 });
    await target.click({ timeout: 15000 });
    // Longer than analytics.js' 250ms navigation flush, so the assertion covers
    // the case where the click does navigate away.
    await page.waitForTimeout(1500);
    events = await page.evaluate(() => {
      try {
        return JSON.parse(sessionStorage.getItem('__verify_events') || '[]');
      } catch {
        return [];
      }
    });
  } catch (e) {
    error = e.message.split('\n')[0];
  }

  const hit = events.find((event) => event && matches(event, testCase.expect));
  results.push({
    name: testCase.name,
    ok: Boolean(hit) && !error,
    error,
    hit,
    pushed: events.map((event) => event && event.event).filter(Boolean),
  });
  await context.close();
}

await browser.close();

console.log(`base: ${BASE}\n`);
for (const result of results) {
  if (result.skipped) {
    console.log(`SKIP  ${result.name}`);
    console.log(`      feature is off: ${result.skipped}`);
    continue;
  }
  console.log(`${result.ok ? 'PASS' : 'FAIL'}  ${result.name}`);
  if (result.ok) {
    console.log(`      event_id present: ${Boolean(result.hit.event_id)}  ${JSON.stringify(result.hit)}`);
  } else {
    console.log(`      expected not found${result.error ? ` (${result.error})` : ''}`);
    console.log(`      dataLayer saw: ${JSON.stringify(result.pushed)}`);
  }
}

const checked = results.filter((result) => !result.skipped);
const failed = checked.filter((result) => !result.ok);
const skipped = results.length - checked.length;
console.log(
  `\n${checked.length - failed.length}/${checked.length} CTAs pushed their event` +
    (skipped ? `, ${skipped} skipped behind a disabled feature flag` : '')
);
console.log(
  leaks.size
    ? `LEAK: analytics endpoints were contacted (${[...leaks].join(', ')}) — GTM is not off, results are contaminated`
    : 'no analytics endpoint was contacted — nothing was sent to GA4 or Meta'
);

process.exit(failed.length || leaks.size ? 1 : 0);
