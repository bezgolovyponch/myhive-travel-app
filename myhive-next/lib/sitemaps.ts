import {
  api,
  type Activity,
  type BlogPost,
  type Destination,
  type TripPackage,
} from './api';
import { loadRedirectMap, type MapRow } from './redirect-map';
import { SITE_URL, canonical } from './seo';
import {
  activityPath,
  blogIndexPath,
  blogPostPath,
  destinationPath,
  packagePath,
} from '../legacy-src/i18n/routes';

// Release date of the static pages; bump by hand when their content changes
// (deploy checklist: set it to the release date before merging).
export const STATIC_PAGES_LASTMOD = '2026-10-19';

export type SitemapName = 'pages' | 'activities' | 'guides' | 'articles';
export const SITEMAP_NAMES: readonly SitemapName[] = ['pages', 'activities', 'guides', 'articles'];

const LOCALES: readonly string[] = ['en', 'de'];
const LEGACY_PRAGUE = 'legacy-prague';

export interface SitemapEntry {
  loc: string;
  lastmod: string;
  alternates?: Record<string, string>;
}

export interface Catalog {
  destinations: Destination[];
  activitiesByDestination: Record<string, Activity[]>;
  packagesByDestination: Record<string, TripPackage[]>;
  posts: BlogPost[];
}

interface IndexItem {
  loc: string;
  lastmod: string;
}

// Same data and seoIndexable gates the old app/sitemap.ts used, incl. the
// parent rule: a non-indexable destination excludes all of its children.
// A failed backend fetch throws on purpose: Next keeps serving the last good
// response (ISR) and a cold first render 500s so crawlers retry, instead of
// caching an empty urlset for an hour.
export async function loadCatalog(): Promise<Catalog> {
  const catalog: Catalog = {
    destinations: [],
    activitiesByDestination: {},
    packagesByDestination: {},
    posts: [],
  };
  const destinations = ((await api.getDestinations()) ?? []).filter((d) => d.seoIndexable);
  for (const dest of destinations) {
    const [activities, packages] = await Promise.all([api.getActivities(dest.id), api.getPackages(dest.id)]);
    catalog.destinations.push(dest);
    catalog.activitiesByDestination[dest.id] = (activities ?? []).filter((a) => a.seoIndexable);
    catalog.packagesByDestination[dest.id] = (packages ?? []).filter((p) => p.seoIndexable);
  }
  catalog.posts = ((await api.getBlogPosts()) ?? []).filter((p) => p.seoIndexable);
  return catalog;
}

function dateOnly(value: string | null | undefined): string | null {
  return value ? value.slice(0, 10) : null;
}

function newest(dates: (string | null)[]): string | null {
  const present = dates.filter((d): d is string => d !== null);
  return present.length === 0 ? null : present.reduce((a, b) => (a > b ? a : b));
}

// One entry for `locale`; `pathFor` yields the EN-internal path using the
// locale-specific slug (activities and posts carry a German slug).
function entry(pathFor: (locale: string) => string, locale: string, lastmod: string): SitemapEntry {
  return {
    loc: canonical(pathFor(locale), locale),
    lastmod,
    alternates: Object.fromEntries(LOCALES.map((l) => [l, canonical(pathFor(l), l)])),
  };
}

function pagesEntries(catalog: Catalog, locale: string): SitemapEntry[] {
  const newestPost = newest(catalog.posts.filter((p) => p.seoIndexable).map((p) => dateOnly(p.updatedAt)));
  const entries: SitemapEntry[] = [
    entry(() => '/', locale, STATIC_PAGES_LASTMOD),
    entry(() => '/about', locale, STATIC_PAGES_LASTMOD),
    entry(() => '/contact', locale, STATIC_PAGES_LASTMOD),
    entry(() => blogIndexPath(), locale, newestPost ?? STATIC_PAGES_LASTMOD),
  ];
  for (const dest of catalog.destinations.filter((d) => d.seoIndexable)) {
    entries.push(
      entry(() => destinationPath(dest.slug), locale, dateOnly(dest.updatedAt) ?? STATIC_PAGES_LASTMOD),
    );
    for (const pkg of (catalog.packagesByDestination[dest.id] ?? []).filter((p) => p.seoIndexable)) {
      entries.push(
        entry(() => packagePath(dest.slug, pkg.slug), locale, dateOnly(pkg.updatedAt) ?? STATIC_PAGES_LASTMOD),
      );
    }
  }
  return entries;
}

function activityEntries(catalog: Catalog, locale: string): SitemapEntry[] {
  return catalog.destinations.filter((d) => d.seoIndexable).flatMap((dest) =>
    (catalog.activitiesByDestination[dest.id] ?? []).filter((a) => a.seoIndexable).map((a) =>
      entry(
        (l) => activityPath(dest.slug, l === 'de' ? (a.slugDe ?? a.slug) : a.slug),
        locale,
        dateOnly(a.updatedAt) ?? STATIC_PAGES_LASTMOD,
      ),
    ),
  );
}

function articleEntries(catalog: Catalog, locale: string): SitemapEntry[] {
  return catalog.posts.filter((p) => p.seoIndexable).map((p) =>
    entry(
      (l) => blogPostPath(l === 'de' ? (p.slugDe ?? p.slug) : p.slug),
      locale,
      dateOnly(p.updatedAt) ?? dateOnly(p.date) ?? STATIC_PAGES_LASTMOD,
    ),
  );
}

export function buildSitemapFrom(catalog: Catalog, name: SitemapName, locale: string): SitemapEntry[] {
  switch (name) {
    case 'pages':
      return pagesEntries(catalog, locale);
    case 'activities':
      return activityEntries(catalog, locale);
    case 'articles':
      return articleEntries(catalog, locale);
    case 'guides':
      // No data source yet (the tips section lands later).
      return [];
  }
}

export function legacyPragueEntries(rows: MapRow[]): SitemapEntry[] {
  return rows
    .filter((row) => row.note === '301')
    .flatMap((row) => [row.oldEn, row.oldDe])
    .map((path) => ({ loc: `${SITE_URL}${path}`, lastmod: STATIC_PAGES_LASTMOD }));
}

export function indexItems(catalog: Catalog): IndexItem[] {
  const items: IndexItem[] = [];
  for (const locale of LOCALES) {
    for (const name of SITEMAP_NAMES) {
      const lastmod = newest(buildSitemapFrom(catalog, name, locale).map((e) => e.lastmod));
      if (lastmod === null) {
        continue;
      }
      const prefix = locale === 'en' ? '' : `/${locale}`;
      items.push({ loc: `${SITE_URL}${prefix}/sitemaps/${name}.xml`, lastmod });
    }
  }
  return items;
}

function escapeXml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function alternateLinks(alternates: Record<string, string>): string[] {
  const links = Object.entries(alternates).map(
    ([lang, href]) => `<xhtml:link rel="alternate" hreflang="${escapeXml(lang)}" href="${escapeXml(href)}"/>`,
  );
  if (alternates.en) {
    links.push(`<xhtml:link rel="alternate" hreflang="x-default" href="${escapeXml(alternates.en)}"/>`);
  }
  return links;
}

export function urlsetXml(entries: SitemapEntry[]): string {
  const urls = entries.map((e) =>
    [
      '<url>',
      `<loc>${escapeXml(e.loc)}</loc>`,
      `<lastmod>${escapeXml(e.lastmod)}</lastmod>`,
      ...(e.alternates ? alternateLinks(e.alternates) : []),
      '</url>',
    ].join(''),
  );
  return [
    '<?xml version="1.0" encoding="UTF-8"?>',
    '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">',
    ...urls,
    '</urlset>',
  ].join('\n');
}

export function sitemapIndexXml(items: IndexItem[]): string {
  const maps = items.map(
    (i) => `<sitemap><loc>${escapeXml(i.loc)}</loc><lastmod>${escapeXml(i.lastmod)}</lastmod></sitemap>`,
  );
  return [
    '<?xml version="1.0" encoding="UTF-8"?>',
    '<sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">',
    ...maps,
    '</sitemapindex>',
  ].join('\n');
}

export function resolveSitemapName(file: string, locale: string): SitemapName | 'legacy-prague' | null {
  if (!file.endsWith('.xml')) {
    return null;
  }
  const base = file.slice(0, -'.xml'.length);
  if (base === LEGACY_PRAGUE) {
    return locale === 'en' ? LEGACY_PRAGUE : null;
  }
  return SITEMAP_NAMES.find((n) => n === base) ?? null;
}

export function xmlResponse(body: string): Response {
  return new Response(body, {
    status: 200,
    headers: { 'Content-Type': 'application/xml; charset=utf-8' },
  });
}

export async function sitemapResponse(file: string, locale: string): Promise<Response> {
  const name = resolveSitemapName(file, locale);
  if (name === null) {
    return new Response('Not found', { status: 404 });
  }
  if (name === LEGACY_PRAGUE) {
    return xmlResponse(urlsetXml(legacyPragueEntries(loadRedirectMap())));
  }
  return xmlResponse(urlsetXml(buildSitemapFrom(await loadCatalog(), name, locale)));
}
