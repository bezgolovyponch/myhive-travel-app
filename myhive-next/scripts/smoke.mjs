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
await check('/', {
  title: 'Trivlu — Stag Do Trips, Sorted in Minutes',
  needles: ['The Easiest Stag Do Decision', 'application/ld+json'],
});
await check('/destination/prague', {
  title: 'Prague Stag Do — Activities & Packages | Trivlu',
  needles: ['Prague Stag Do', 'application/ld+json', 'activity-card'],
});
await check('/blog', { title: 'Stag Do Planning Guides & Ideas | Trivlu Blog', needles: ['The Stag Do Playbook'] });
await check('/about', { title: 'About Trivlu — Group Travel Made Easy', needles: ['About Trivlu'] });
await check('/contact', { title: 'Contact Trivlu — Talk to the Team', needles: ['info@trivlu.com'] });
await check('/terms', { needles: ['Terms'] });
await check('/privacy-policy', { needles: ['Privacy'] });
await check('/cookie-policy', { needles: ['Cookie'] });
await check('/refund-policy', { needles: ['Refund'] });

// detail pages (slugs discovered from live data)
const { html: destHtml } = await fetchPage('/destination/prague');
const aslug = destHtml.match(/href="\/destination\/prague\/activity\/([a-z0-9-]+)"/)?.[1];
if (aslug) await check(`/destination/prague/activity/${aslug}`, { needles: ['BreadcrumbList'] });
else fails.push('no activity links found on /destination/prague');
const { html: blogHtml } = await fetchPage('/blog');
const pslug = blogHtml.match(/href="\/blog\/([a-z0-9-]+)"/)?.[1];
if (pslug) await check(`/blog/${pslug}`, { needles: ['Article'] });
else fails.push('no post links found on /blog');

await expect404('/destination/atlantis');
await expect404('/blog/not-a-real-post');
await expect404('/destination/prague/activity/not-a-real-activity');

const { code: rc, html: robots } = await fetchPage('/robots.txt');
console.log(`== /robots.txt [${rc}]: ${robots.trim().split('\n').slice(0, 2).join(' | ')}`);
const { code: sc, html: sm } = await fetchPage('/sitemap.xml');
const urls = (sm.match(/<url>/g) ?? []).length;
console.log(`== /sitemap.xml [${sc}]: ${urls} urls`);
// 8 = the static pages, always present. Catalog/blog URLs join only for
// records with seoIndexable=true (per-record gate) — against a backend
// without the flag columns (or before editorial flagging) 8 is correct.
if (sc !== 200 || urls < 8) fails.push(`sitemap: HTTP ${sc}, ${urls} urls`);

for (const p of ['/admin', '/vote/new']) {
  const { code, html } = await fetchPage(p);
  const shim = html.includes('Loading');
  console.log(`== ${p} [${code}] legacy-shim=${shim ? 'yes' : 'NO'}`);
  if (code !== 200 || !shim) fails.push(`${p}: legacy fallback broken`);
}

console.log(`\nRESULT: ${fails.length === 0 ? 'PASS' : `${fails.length} FAILURES`}`);
for (const f of fails) console.log('  -', f);
process.exit(fails.length === 0 ? 0 : 1);
