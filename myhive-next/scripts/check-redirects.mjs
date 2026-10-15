// Acceptance for the 2026-10 URL move (spec: docs/superpowers/specs/2026-10-15-seo-prague-urls-phase1-design.md).
// Usage: node scripts/check-redirects.mjs [base-url]   (default http://localhost:3000)
// Every old address must be ONE 308 to the new one, which answers 200; the
// retired posts answer 410 with no hop; landings and non-hub cities never redirect.
// Needs `npm run sync-legacy` first (legacy-src is generated and gitignored).
import { DESTINATION_HUBS, SUPPORTED_LOCALES, activityPath, destinationPath, localizePath, packagePath } from '../legacy-src/i18n/routes.js';

// Duplicate of GONE_BLOG_SLUGS in lib/gone.ts (the owner): node cannot import .ts directly.
const GONE_BLOG_SLUGS = [
  'top-5-group-travel-destinations-for-2026',
  'why-ai-is-changing-the-way-we-travel-together',
  'bali-on-a-budget-a-group-travel-guide',
  'the-rise-of-multi-traveler-experiences',
  'weekend-getaway-ideas-for-large-groups',
];

const base = (process.argv[2] ?? 'http://localhost:3000').replace(/\/+$/, '');
let errors = 0;
let checks = 0;

function fail(message) {
  errors += 1;
  console.log(`FAIL ${message}`);
}

async function follow(path) {
  const hops = [];
  let url = base + path;
  for (let i = 0; i < 5; i += 1) {
    const res = await fetch(url, { redirect: 'manual' });
    if (res.status >= 300 && res.status < 400) {
      hops.push(res.status);
      const location = res.headers.get('location');
      if (!location) {
        return { hops, status: res.status, url, noLocation: true };
      }
      url = new URL(location, url).toString();
      continue;
    }
    return { hops, status: res.status, url };
  }
  return { hops, status: -1, url };
}

function report(ok, label, r) {
  checks += 1;
  if (!ok) {
    fail(`${label}: hops=${JSON.stringify(r.hops)} status=${r.status} url=${r.url}${r.noLocation ? ' (redirect without Location header)' : ''}`);
  }
}

async function expectSingle308(oldPath, newPath) {
  const r = await follow(oldPath);
  report(!r.noLocation && r.hops.length === 1 && r.hops[0] === 308 && r.status === 200 && r.url === base + newPath, `${oldPath} -> ${newPath}`, r);
}

async function expectDirect(path, status) {
  const r = await follow(path);
  report(!r.noLocation && r.hops.length === 0 && r.status === status, `${path} is ${status} with no hop`, r);
}

async function getJson(path) {
  const token = process.env.INTERNAL_API_TOKEN; // lets the catalog reads skip the 100 req/min/IP limiter
  const res = await fetch(`${base}${path}`, token ? { headers: { 'X-Internal-Token': token } } : {});
  if (!res.ok) {
    throw new Error(`${path} answered ${res.status}`);
  }
  try {
    return await res.json();
  } catch (e) {
    throw new Error(`${path} returned a non-JSON body (${e.message})`);
  }
}

async function run() {
  const destinations = await getJson('/api/destinations');
  let hubCount = 0;
  for (const dest of destinations) {
    if (!DESTINATION_HUBS[dest.slug]) {
      continue;
    }
    hubCount += 1;
    const activities = await getJson(`/api/activities?destinationId=${encodeURIComponent(dest.id)}`);
    const packages = await getJson(`/api/packages?destinationId=${encodeURIComponent(dest.id)}`);
    if (activities.length === 0) {
      fail(`hub ${dest.slug}: /api/activities returned no activities, nothing to exercise`);
    }
    console.log(`hub ${dest.slug}: ${activities.length} activities, ${packages.length} packages`);
  }
  if (hubCount === 0) {
    fail('no hub destination in /api/destinations, nothing to exercise');
  }

  for (const dest of destinations) {
    for (const locale of SUPPORTED_LOCALES) {
      const old = (p) => localizePath(p, locale);
      if (DESTINATION_HUBS[dest.slug]) {
        await expectSingle308(old(`/destination/${dest.slug}`), localizePath(destinationPath(dest.slug), locale));
        await expectSingle308(
          old(`/destination/${dest.slug}?utm_source=test&tab=trip-builder`),
          localizePath(`${destinationPath(dest.slug)}?utm_source=test&tab=trip-builder`, locale)
        );
        const activities = await getJson(`/api/activities?destinationId=${encodeURIComponent(dest.id)}`);
        for (const a of activities) {
          await expectSingle308(old(`/destination/${dest.slug}/activity/${a.slug}`), localizePath(activityPath(dest.slug, a.slug), locale));
        }
        const packages = await getJson(`/api/packages?destinationId=${encodeURIComponent(dest.id)}`);
        for (const p of packages) {
          await expectSingle308(old(`/destination/${dest.slug}/package/${p.slug}`), localizePath(packagePath(dest.slug, p.slug), locale));
        }
      } else {
        await expectDirect(old(`/destination/${dest.slug}`), 200);
      }
    }
  }
  for (const locale of SUPPORTED_LOCALES) {
    await expectSingle308(localizePath('/blog', locale), localizePath('/ideas', locale));
    for (const slug of GONE_BLOG_SLUGS) {
      await expectDirect(localizePath(`/blog/${slug}`, locale), 410);
    }
    await expectDirect(localizePath('/blog/not-a-post', locale), 404);
    await expectDirect(localizePath('/landing/prague', locale), 200);
    await expectDirect(localizePath('/destination/not-a-city', locale), 404);
  }
  if (checks === 0) {
    fail('no assertion was executed');
  }
}

console.log(`check-redirects base=${base} at ${new Date().toISOString()}`);
try {
  await run();
} catch (e) {
  fail(`script aborted: ${e.message}`);
}
console.log(`checks: ${checks}`);
console.log(`errors: ${errors}`);
process.exit(errors === 0 ? 0 : 1);
