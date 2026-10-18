// Ф1 SSR smoke checks (spec §11 + SEO-v3 метатеги table).
// Usage: node scripts/smoke.mjs [base-url]   (default http://localhost:3000)
// Verifies per page: HTTP 200, exactly one <h1>, content present in RAW HTML,
// expected <title> (where SEO-v3 fixes it), title ≤60 chars / description ≤155,
// canonical present. Plus: true 404s, robots.txt, sitemap.xml, legacy fallback.
const BASE = process.argv[2] ?? 'http://localhost:3000';

try {
  await fetch(BASE + '/robots.txt');
} catch {
  console.error(`Cannot reach ${BASE} — is the server running? (npm run start, or pass a URL: node scripts/smoke.mjs <url>)`);
  process.exit(2);
}
const fails = [];

async function fetchPage(path) {
  const res = await fetch(BASE + path, { redirect: 'manual' });
  return { code: res.status, html: await res.text() };
}

// Every <link ...> tag as { rel, hreflang, href }, whatever the attribute order or casing.
function linkTags(html) {
  return [...html.matchAll(/<link\b[^>]*>/gi)].map(([tag]) => {
    const attr = (name) => tag.match(new RegExp(`\\s${name}="([^"]*)"`, 'i'))?.[1];
    return { rel: attr('rel'), hreflang: attr('hreflang'), href: attr('href') };
  });
}

function meta(html) {
  const title = html.match(/<title>([^<]*)<\/title>/)?.[1]?.replace(/&amp;/g, '&') ?? '';
  const desc = html.match(/<meta name="description" content="([^"]*)"/)?.[1] ?? '';
  const h1 = (html.match(/<h1[\s>]/g) ?? []).length;
  const canonical = /<link rel="canonical"/.test(html);
  return { title, desc, h1, canonical };
}

async function check(path, { needles = [], title: expectedTitle } = {}) {
  const { code, html } = await fetchPage(path);
  const { title, desc, h1, canonical } = meta(html);
  console.log(`== ${path} [${code}] h1x${h1} | ${title.slice(0, 70)}`);
  if (code !== 200) fails.push(`${path}: HTTP ${code}`);
  if (h1 !== 1) fails.push(`${path}: ${h1} h1 tags`);
  if (!canonical) fails.push(`${path}: no canonical`);
  if (title.length > 60) fails.push(`${path}: title ${title.length} chars (>60)`);
  if (desc.length > 155) fails.push(`${path}: description ${desc.length} chars (>155)`);
  if (!desc) fails.push(`${path}: no description`);
  if (expectedTitle && title !== expectedTitle)
    fails.push(`${path}: title "${title}" != SEO-v3 "${expectedTitle}"`);
  for (const n of needles) {
    if (!html.includes(n)) fails.push(`${path}: raw HTML missing ${JSON.stringify(n)}`);
  }
}

async function expect404(path) {
  const { code } = await fetchPage(path);
  console.log(`== ${path} -> ${code}${code === 404 ? '' : '  EXPECTED 404'}`);
  if (code !== 404) fails.push(`${path}: expected 404, got ${code}`);
}

// SEO-v3 «метатеги» table — titles are pinned where the doc fixes them.
// The homepage now renders the canonical CRA component, so its title and H1 are
// CRA's — deliberately overriding the SEO-v3 заготовка ("Trivlu — Stag Do Trips,
// Sorted in Minutes" / "The Easiest Stag Do Decision"), which had drifted from
// what production actually serves. Still within the ≤60 char budget checked below.
await check('/', {
  title: 'Trivlu — Prague Stag Do. Planned in 10 Minutes.',
  needles: ['Prague Stag Do. Planned in 10 minutes.', 'application/ld+json', 'activity-card'],
});
await check('/prague-stag-do', {
  title: 'Prague Stag Do — Activities & Packages | Trivlu',
  needles: ['Prague Stag Do', 'application/ld+json', 'activity-card'],
});
await check('/ideas', { title: 'Stag Do Planning Guides & Ideas | Trivlu Blog', needles: ['The Stag Do Playbook'] });
await check('/about', { title: 'About Trivlu — Group Travel Made Easy', needles: ['About Trivlu'] });
await check('/contact', { title: 'Contact Trivlu — Talk to the Team', needles: ['info@trivlu.com'] });
await check('/terms', { needles: ['Terms'] });
await check('/privacy-policy', { needles: ['Privacy'] });
await check('/cookie-policy', { needles: ['Cookie'] });
await check('/refund-policy', { needles: ['Refund'] });

// detail pages (slugs discovered from live data)
const { html: destHtml } = await fetchPage('/prague-stag-do');
const aslug = destHtml.match(/href="\/prague-stag-do\/activities\/([a-z0-9-]+)"/)?.[1];
if (aslug) await check(`/prague-stag-do/activities/${aslug}`, { needles: ['BreadcrumbList'] });
else fails.push('no activity links found on /prague-stag-do');
const { html: blogHtml } = await fetchPage('/ideas');
const pslug = blogHtml.match(/href="\/ideas\/([a-z0-9-]+)"/)?.[1];
if (pslug) await check(`/ideas/${pslug}`, { needles: ['Article'] });
else fails.push('no post links found on /ideas');

// German spelling of the hub: same page, localized segments, canonical + hreflang
// pointing at the right scheme on both sides.
const { code: deCode, html: deHub } = await fetchPage('/de/jga-prag');
console.log(`== /de/jga-prag [${deCode}]`);
if (deCode !== 200) fails.push(`/de/jga-prag: HTTP ${deCode}`);
if (!/<link rel="canonical" href="[^"]*\/de\/jga-prag"/.test(deHub)) {
  fails.push('/de/jga-prag: canonical is not /de/jga-prag');
}
if (aslug) {
  const dePath = `/de/jga-prag/aktivitaeten/${aslug}`;
  const { code: deActCode, html: deAct } = await fetchPage(dePath);
  console.log(`== ${dePath} [${deActCode}]`);
  if (deActCode !== 200) fails.push(`${dePath}: HTTP ${deActCode}`);
  // The EN counterpart can carry a different slug now: trust the page's own alternate,
  // require that it answers 200 and that the page's de alternate is its canonical.
  const deLinks = linkTags(deAct);
  const enAlt = deLinks.find((l) => l.rel === 'alternate' && l.hreflang === 'en');
  const deAlt = deLinks.find((l) => l.rel === 'alternate' && l.hreflang === 'de');
  const deCanonical = deLinks.find((l) => l.rel === 'canonical');
  if (!enAlt?.href) {
    fails.push(`${dePath}: no hreflang="en" alternate`);
  } else {
    const enPath = new URL(enAlt.href, BASE).pathname;
    const { code: enCode } = await fetchPage(enPath);
    console.log(`== ${enPath} [${enCode}] (en alternate of ${dePath})`);
    if (enCode !== 200) fails.push(`${dePath}: hreflang="en" ${enAlt.href} answers ${enCode}`);
  }
  if (!deAlt?.href || deAlt.href !== deCanonical?.href) {
    fails.push(`${dePath}: hreflang="de" (${deAlt?.href}) is not the canonical (${deCanonical?.href})`);
  }
}

await expect404('/destination/atlantis');
await expect404('/ideas/not-a-real-post');
await expect404('/prague-stag-do/activities/not-a-real-activity');

const { code: rc, html: robots } = await fetchPage('/robots.txt');
console.log(`== /robots.txt [${rc}]: ${robots.trim().split('\n').slice(0, 2).join(' | ')}`);
if (rc !== 200) fails.push(`/robots.txt: HTTP ${rc}`);
if (!robots.includes('Bytespider')) fails.push('/robots.txt: Bytespider is not blocked');
if (robots.includes('GPTBot')) fails.push('/robots.txt: GPTBot must not be listed');

// /sitemap.xml is an index of per-locale sitemaps (non-empty maps only).
const { code: sc, html: sm } = await fetchPage('/sitemap.xml');
const sitemaps = (sm.match(/<sitemap>/g) ?? []).length;
console.log(`== /sitemap.xml [${sc}]: ${sitemaps} sitemaps`);
if (sc !== 200 || !sm.includes('<sitemapindex') || sitemaps < 2) fails.push(`sitemap index: HTTP ${sc}, ${sitemaps} <sitemap> entries`);

// Each entry names its EN, DE and x-default alternates.
function urlEntries(xml) {
  return [...xml.matchAll(/<url>[\s\S]*?<\/url>/g)].map(([entry]) => entry);
}

for (const p of ['/sitemaps/pages.xml', '/de/sitemaps/pages.xml']) {
  const { code, html } = await fetchPage(p);
  const entries = urlEntries(html);
  console.log(`== ${p} [${code}]: ${entries.length} urls`);
  if (code !== 200 || entries.length < 5) fails.push(`${p}: HTTP ${code}, ${entries.length} urls (< 5)`);
  const lacking = entries.filter((e) => (e.match(/hreflang=/g) ?? []).length !== 3);
  if (lacking.length > 0) fails.push(`${p}: ${lacking.length} <url> entries without exactly three hreflang links`);
}

// The old API sitemap's 150 URLs, EN only; the retired API endpoint is gone for good.
const { code: lc, html: legacy } = await fetchPage('/sitemaps/legacy-prague.xml');
const legacyUrls = urlEntries(legacy).length;
console.log(`== /sitemaps/legacy-prague.xml [${lc}]: ${legacyUrls} urls`);
if (lc !== 200 || legacyUrls !== 150) fails.push(`legacy-prague sitemap: HTTP ${lc}, ${legacyUrls} urls (expected 150)`);
await expect404('/de/sitemaps/legacy-prague.xml');
const { code: apiCode } = await fetchPage('/api/sitemap.xml');
console.log(`== /api/sitemap.xml -> ${apiCode}${apiCode === 410 ? '' : '  EXPECTED 410'}`);
if (apiCode !== 410) fails.push(`/api/sitemap.xml: expected 410, got ${apiCode}`);

for (const p of ['/admin', '/vote/new']) {
  const { code, html } = await fetchPage(p);
  const shim = html.includes('Loading');
  console.log(`== ${p} [${code}] legacy-shim=${shim ? 'yes' : 'NO'}`);
  if (code !== 200 || !shim) fails.push(`${p}: legacy fallback broken`);
}

console.log(`\nRESULT: ${fails.length === 0 ? 'PASS' : `${fails.length} FAILURES`}`);
for (const f of fails) console.log('  -', f);
process.exit(fails.length === 0 ? 0 : 1);
