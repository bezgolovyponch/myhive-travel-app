// Acceptance for the 2026-10 URL move (spec: docs/superpowers/specs/2026-10-15-seo-prague-urls-phase1-design.md).
// Usage: node scripts/check-redirects.mjs <base-url> [--legacy-sitemap <file>] [--delay-ms <n>]
//   --legacy-sitemap  every <loc> of an old sitemap must land somewhere sensible (see checkLegacySitemap)
//   --delay-ms        pause before each request (default 0; 150 when the host is www.trivlu.com)
// Driven by seo/redirect-map.csv: every 301 row is ONE 308 to a page that answers 200,
// the 410 rows answer 410 with no hop, landings never redirect. Then the catalog from
// the API is held against the map, and the pattern fallbacks / renamed-slug guards are exercised.
// Needs `npm run sync-legacy` first (legacy-src is generated and gitignored).
// Exit 1 on any error or when nothing was checked. Warnings never fail the run.
import fs from 'node:fs';
import { pathToFileURL } from 'node:url';
import { DESTINATION_HUBS, SUPPORTED_LOCALES, activityPath, destinationPath, localizePath, packagePath } from '../legacy-src/i18n/routes.js';

// Duplicate of GONE_BLOG_SLUGS in lib/gone.ts (the owner): node cannot import .ts directly. Keep in sync.
const GONE_BLOG_SLUGS = [
  'top-5-group-travel-destinations-for-2026',
  'why-ai-is-changing-the-way-we-travel-together',
  'bali-on-a-budget-a-group-travel-guide',
  'the-rise-of-multi-traveler-experiences',
  'weekend-getaway-ideas-for-large-groups',
];

// Per primaryCategory.slug, the Prague activities of the redirect map (SEO brief numbers).
const EXPECTED_CATEGORY_COUNTS = {
  shooting: 5,
  driving: 7,
  action: 11,
  beer: 10,
  food: 9,
  boats: 3,
  nightlife: 8,
  strippers: 5,
  pranks: 11,
  transfers: 3,
};
const EXPECTED_UNMAPPED_ON_PROD = 10;
const PROD_HOST = 'www.trivlu.com';
const PROD_DELAY_MS = 150;

let base = '';
let delayMs = 0;
let errors = 0;
let checks = 0;
let warnings = 0;

function fail(message) {
  errors += 1;
  console.log(`FAIL ${message}`);
}

function warn(message) {
  warnings += 1;
  console.log(`WARN ${message}`);
}

function check(ok, message) {
  checks += 1;
  if (!ok) {
    fail(message);
  }
}

// ---- redirect map (same rules as lib/redirect-map.ts: 8 fields, no quotes, CRLF tolerated) ----

function parseRedirectMap(csv) {
  const rows = [];
  const lines = csv.split('\n').map((line) => line.replace(/\r$/, ''));
  lines.slice(1).forEach((line, index) => {
    if (line.trim() === '') {
      return;
    }
    const where = `redirect-map.csv line ${index + 2}`;
    if (line.includes('"')) {
      throw new Error(`${where}: quoted cells are not supported`);
    }
    const fields = line.split(',');
    if (fields.length !== 8) {
      throw new Error(`${where}: expected 8 fields, got ${fields.length}`);
    }
    const [type, oldEn, newEn, oldDe, newDe, categoryEn, categoryDe, note] = fields;
    rows.push({ type, oldEn, newEn, oldDe, newDe, categoryEn, categoryDe, note });
  });
  return rows;
}

function loadMap() {
  return parseRedirectMap(fs.readFileSync(new URL('../seo/redirect-map.csv', import.meta.url), 'utf8'));
}

function slugOf(url) {
  return url.slice(url.lastIndexOf('/') + 1);
}

// ---- http helpers ----

async function sleep(ms) {
  if (ms > 0) {
    await new Promise((resolve) => setTimeout(resolve, ms));
  }
}

// fetch that names the URL in its failure, so "script aborted" says what was unreachable.
async function fetchUrl(url, options) {
  await sleep(delayMs);
  try {
    return await fetch(url, options);
  } catch (e) {
    throw new Error(`fetch ${url} failed: ${e.cause?.code ?? e.cause?.errors?.[0]?.code ?? e.message}`);
  }
}

// `target` is a path on the base host, or an absolute URL (the www/http checks).
async function follow(target) {
  const hops = [];
  let url = target.startsWith('http') ? target : base + target;
  for (let i = 0; i < 5; i += 1) {
    const res = await fetchUrl(url, { redirect: 'manual' });
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
  check(ok, `${label}: hops=${JSON.stringify(r.hops)} status=${r.status} url=${r.url}${r.noLocation ? ' (redirect without Location header)' : ''}`);
}

async function expectSingle308(oldPath, newPath) {
  const r = await follow(oldPath);
  report(!r.noLocation && r.hops.length === 1 && r.hops[0] === 308 && r.status === 200 && r.url === base + newPath, `${oldPath} -> ${newPath}`, r);
}

// Edge-level moves (apex -> www, http -> https) may add a hop in front of the app's own 308.
async function expectAtMostTwoHops(oldUrl, newPath) {
  const r = await follow(oldUrl);
  report(!r.noLocation && r.hops.length >= 1 && r.hops.length <= 2 && r.status === 200 && r.url === base + newPath, `${oldUrl} -> ${newPath} (<= 2 hops)`, r);
}

async function expectDirect(path, status) {
  const r = await follow(path);
  report(!r.noLocation && r.hops.length === 0 && r.status === status, `${path} is ${status} with no hop`, r);
}

async function getJson(path) {
  const token = process.env.INTERNAL_API_TOKEN; // lets the catalog reads skip the 100 req/min/IP limiter
  const res = await fetchUrl(`${base}${path}`, token ? { headers: { 'X-Internal-Token': token } } : {});
  if (!res.ok) {
    throw new Error(`${path} answered ${res.status}`);
  }
  try {
    return await res.json();
  } catch (e) {
    throw new Error(`${path} returned a non-JSON body (${e.message})`);
  }
}

// ---- check classes ----

// 301 rows: EN old, DE old and the explicit /en/ variant all land on the new address in one hop.
async function checkMovedRows(rows) {
  for (const row of rows.filter((r) => r.note === '301')) {
    await expectSingle308(row.oldEn, row.newEn);
    await expectSingle308(row.oldDe, row.newDe);
    await expectSingle308(`/en${row.oldEn}`, row.newEn);
  }
}

async function checkGoneRows(rows) {
  for (const row of rows.filter((r) => r.note === '410')) {
    await expectDirect(row.oldEn, 410);
    await expectDirect(row.oldDe, 410);
  }
}

async function checkLandingRows(rows) {
  for (const row of rows.filter((r) => r.note === 'landing')) {
    await expectDirect(row.oldEn, 200);
    await expectDirect(row.oldDe, 200);
  }
}

// The hub keeps its query string through the redirect (utm tags, Trip Builder deep link).
async function checkHubQuery(rows) {
  const query = '?utm_source=test&tab=trip-builder';
  for (const row of rows.filter((r) => r.type === 'hub' && r.note === '301')) {
    await expectSingle308(row.oldEn + query, row.newEn + query);
    await expectSingle308(row.oldDe + query, row.newDe + query);
  }
}

// Activities that are not in the map and every package: the pattern fallbacks, EN / DE / explicit /en.
// An activity is mapped when its current slug is the new slug of a 301 row (their old URLs are
// covered by the explicit row checks) or, before the slug import, when its old URL is a row.
async function checkPatternFallbacks(destSlug, activities, packages, mapByOld, mapByNew) {
  for (const a of activities) {
    const old = `/destination/${destSlug}/activity/${a.slug}`;
    if (mapByNew.has(a.slug) || mapByOld.has(old)) {
      continue;
    }
    await expectSingle308(old, activityPath(destSlug, a.slug));
    await expectSingle308(localizePath(old, 'de'), localizePath(activityPath(destSlug, a.slug), 'de'));
    await expectSingle308(`/en${old}`, activityPath(destSlug, a.slug));
  }
  for (const p of packages) {
    const old = `/destination/${destSlug}/package/${p.slug}`;
    await expectSingle308(old, packagePath(destSlug, p.slug));
    await expectSingle308(localizePath(old, 'de'), localizePath(packagePath(destSlug, p.slug), 'de'));
    await expectSingle308(`/en${old}`, packagePath(destSlug, p.slug));
  }
}

// Renamed activities: the old slug under the NEW hub path also lands on the new page.
async function checkRenameGuards(rows) {
  for (const row of rows.filter((r) => r.type === 'activity' && r.note === '301')) {
    const match = /^\/destination\/([^/]+)\/activity\/[^/]+$/.exec(row.oldEn);
    if (!match || !DESTINATION_HUBS[match[1]] || slugOf(row.oldEn) === slugOf(row.newEn)) {
      continue;
    }
    const oldSlug = slugOf(row.oldEn);
    await expectSingle308(activityPath(match[1], oldSlug), row.newEn);
    await expectSingle308(localizePath(activityPath(match[1], oldSlug), 'de'), row.newDe);
  }
}

async function checkNotFounds() {
  for (const locale of SUPPORTED_LOCALES) {
    await expectDirect(localizePath('/blog/not-a-post', locale), 404);
    await expectDirect(localizePath('/destination/not-a-city', locale), 404);
  }
}

// Data-driven: "mapped" Prague activities are those whose slug is a new EN slug of the map.
// Each must carry the DE slug and primary category of its row; per-category counts must match
// the brief; the rest ("unmapped") must have no DE slug, and are counted exactly only on prod.
function checkCatalog(activities, mapByNewSlug) {
  const counts = {};
  let mapped = 0;
  let unmapped = 0;
  for (const a of activities) {
    const row = mapByNewSlug.get(a.slug);
    if (!row) {
      unmapped += 1;
      check(!a.slugDe, `catalog: unmapped activity ${a.slug} has slugDe '${a.slugDe}'`);
      continue;
    }
    mapped += 1;
    check(a.slugDe === slugOf(row.newDe), `catalog: ${a.slug} slugDe '${a.slugDe}' != map '${slugOf(row.newDe)}'`);
    const category = a.primaryCategory?.slug;
    check(category === row.categoryEn, `catalog: ${a.slug} primaryCategory '${category}' != map '${row.categoryEn}'`);
    counts[category] = (counts[category] ?? 0) + 1;
  }
  for (const [category, expected] of Object.entries(EXPECTED_CATEGORY_COUNTS)) {
    check((counts[category] ?? 0) === expected, `catalog: primaryCategory ${category} has ${counts[category] ?? 0} activities, expected ${expected}`);
  }
  console.log(`catalog: ${mapped} mapped, ${unmapped} unmapped activities`);
  if (new URL(base).hostname === PROD_HOST) {
    check(unmapped === EXPECTED_UNMAPPED_ON_PROD, `catalog: ${unmapped} unmapped activities, expected ${EXPECTED_UNMAPPED_ON_PROD}`);
  }
}

// The three edge hops that only exist on the real host.
async function checkProdHost(mapRows) {
  const hub = mapRows.find((r) => r.type === 'hub');
  const blogIndex = mapRows.find((r) => r.type === 'blog-index');
  await expectAtMostTwoHops('https://trivlu.com/destination/prague', hub.newEn);
  await expectAtMostTwoHops('http://www.trivlu.com/destination/prague', hub.newEn);
  await expectAtMostTwoHops('https://trivlu.com/blog', blogIndex.newEn);
}

// Old sitemap: Prague catalog paths are one 308 to the final address, /blog and its posts follow
// the map (410 for the retired ones), everything else must simply answer 200. A 404 there only
// means the record has since been deleted: a warning, not an error.
async function checkLegacySitemap(file, mapRows) {
  const mapByOld = new Map(mapRows.filter((r) => r.note === '301').map((r) => [r.oldEn, r]));
  const xml = fs.readFileSync(file, 'utf8');
  const paths = [...xml.matchAll(/<loc>([^<]+)<\/loc>/g)].map((m) => {
    const url = new URL(m[1].trim().replace(/&amp;/g, '&'));
    return url.pathname + url.search;
  });
  check(paths.length > 0, `legacy sitemap ${file}: no <loc> found`);
  for (const path of paths) {
    await checkLegacyPath(path, mapByOld);
  }
  console.log(`legacy sitemap: ${paths.length} urls`);
}

async function checkLegacyPath(path, mapByOld) {
  const blog = /^\/blog\/([^/]+)$/.exec(path);
  if (blog && GONE_BLOG_SLUGS.includes(blog[1])) {
    return expectDirect(path, 410);
  }
  const row = mapByOld.get(path);
  if (row) {
    return expectSingle308(path, row.newEn);
  }
  const catalog = /^\/destination\/([^/]+)(?:\/(activity|package)\/([^/]+))?$/.exec(path);
  if (catalog && DESTINATION_HUBS[catalog[1]]) {
    const [, dest, kind, slug] = catalog;
    if (!kind) {
      return expectSingle308(path, destinationPath(dest));
    }
    const target = kind === 'activity' ? activityPath(dest, slug) : packagePath(dest, slug);
    const fallback = await follow(path);
    if (fallback.hops.length === 1 && fallback.hops[0] === 308 && fallback.status === 404 && fallback.url === base + target) {
      // The pattern fallback is right; the catalog this server runs on just lacks the record (dev seed vs prod).
      warn(`legacy sitemap ${path} -> ${target} answers 404 (record not in this catalog)`);
      checks += 1;
      return undefined;
    }
    return expectSingle308(path, target);
  }
  const r = await follow(path);
  if (r.hops.length === 0 && r.status === 404) {
    warn(`legacy sitemap ${path} answers 404 (record no longer exists)`);
    checks += 1;
    return undefined;
  }
  return report(!r.noLocation && r.hops.length === 0 && r.status === 200, `legacy sitemap ${path} is 200 with no hop`, r);
}

// ---- driver ----

function parseArgs(argv) {
  const args = { base: null, legacySitemap: null, delayMs: null, help: false };
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === '--help' || arg === '-h') {
      args.help = true;
    } else if (arg === '--legacy-sitemap') {
      args.legacySitemap = argv[++i];
    } else if (arg === '--delay-ms') {
      args.delayMs = Number(argv[++i]);
    } else if (!arg.startsWith('--') && args.base === null) {
      args.base = arg.replace(/\/+$/, '');
    } else {
      throw new Error(`unknown argument ${arg}`);
    }
  }
  return args;
}

const USAGE = 'Usage: node scripts/check-redirects.mjs <base-url> [--legacy-sitemap <file>] [--delay-ms <n>]';

async function run(args, mapRows) {
  const mapByOld = new Map(mapRows.filter((r) => r.note === '301').map((r) => [r.oldEn, r]));

  const mapByNew = new Map(mapRows.filter((r) => r.type === 'activity' && r.note === '301').map((r) => [slugOf(r.newEn), r]));

  await checkMovedRows(mapRows);
  await checkGoneRows(mapRows);
  await checkLandingRows(mapRows);
  await checkHubQuery(mapRows);
  await checkRenameGuards(mapRows);
  await checkNotFounds();

  const destinations = await getJson('/api/destinations');
  let hubCount = 0;
  for (const dest of destinations) {
    if (!DESTINATION_HUBS[dest.slug]) {
      for (const locale of SUPPORTED_LOCALES) {
        await expectDirect(localizePath(`/destination/${dest.slug}`, locale), 200);
      }
      continue;
    }
    hubCount += 1;
    const activities = await getJson(`/api/activities?destinationId=${encodeURIComponent(dest.id)}`);
    const packages = await getJson(`/api/packages?destinationId=${encodeURIComponent(dest.id)}`);
    console.log(`hub ${dest.slug}: ${activities.length} activities, ${packages.length} packages`);
    check(activities.length > 0, `hub ${dest.slug}: /api/activities returned no activities, nothing to exercise`);
    await checkPatternFallbacks(dest.slug, activities, packages, mapByOld, mapByNew);
    if (dest.slug === 'prague') {
      checkCatalog(activities, mapByNew);
    }
  }
  check(hubCount > 0, 'no hub destination in /api/destinations, nothing to exercise');

  if (new URL(base).hostname === PROD_HOST) {
    await checkProdHost(mapRows);
  }
  if (args.legacySitemap) {
    await checkLegacySitemap(args.legacySitemap, mapRows);
  }
}

async function main() {
  let args;
  try {
    args = parseArgs(process.argv.slice(2));
  } catch (e) {
    console.error(`${e.message}\n${USAGE}`);
    process.exit(2);
  }
  if (args.help || !args.base) {
    console.log(USAGE);
    process.exit(args.help ? 0 : 2);
  }
  base = args.base;
  delayMs = args.delayMs ?? (new URL(base).hostname === PROD_HOST ? PROD_DELAY_MS : 0);
  console.log(`check-redirects base=${base} delay=${delayMs}ms at ${new Date().toISOString()}`);
  try {
    await run(args, loadMap());
  } catch (e) {
    fail(`script aborted: ${e.message}`);
  }
  if (checks === 0) {
    fail('no assertion was executed');
  }
  console.log(`checks: ${checks} errors: ${errors} warnings: ${warnings}`);
  process.exit(errors === 0 ? 0 : 1);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  await main();
}
