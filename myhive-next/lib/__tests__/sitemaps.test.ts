import { describe, expect, it } from 'vitest';
import type { Activity, BlogPost, Destination } from '../api';
import { loadRedirectMap } from '../redirect-map';
import {
  STATIC_PAGES_LASTMOD,
  buildSitemapFrom,
  indexItems,
  legacyPragueEntries,
  resolveSitemapName,
  urlsetXml,
} from '../sitemaps';
import type { Catalog } from '../sitemaps';

function destination(): Destination {
  return {
    id: 'd1',
    slug: 'prague',
    name: 'Prague',
    description: '',
    country: 'CZ',
    city: 'Prague',
    imageUrl: '',
    rating: 5,
    activityCount: 3,
    seoIndexable: true,
    updatedAt: '2026-10-20T10:00:00',
  };
}

function activity(slug: string, extra: Partial<Activity> = {}): Activity {
  return {
    id: slug,
    slug,
    name: slug,
    description: '',
    price: 10,
    imageUrl: '',
    seoIndexable: true,
    updatedAt: '2026-10-19T08:30:00.123',
    ...extra,
  };
}

function post(extra: Partial<BlogPost> = {}): BlogPost {
  return { id: 'p1', slug: 'tips', title: 'Tips', content: '', seoIndexable: false, ...extra };
}

function catalog(overrides: Partial<Catalog> = {}): Catalog {
  return {
    destinations: [destination()],
    activitiesByDestination: {
      d1: [
        activity('beer-bike', { slugDe: 'bierbike' }),
        activity('go-karts'),
        activity('hidden', { seoIndexable: false }),
      ],
    },
    packagesByDestination: { d1: [] },
    posts: [post()],
    ...overrides,
  };
}

describe('sitemaps', () => {
  it('buildSitemapFrom_pagesHasHomeAboutContactIdeasAndHub', () => {
    const expectedHubLastmod = '2026-10-20';

    const entries = buildSitemapFrom(catalog(), 'pages', 'en');

    expect(entries).toHaveLength(5);
    const hub = entries.find((e) => e.loc.endsWith('/prague-stag-do'));
    expect(hub?.lastmod).toBe(expectedHubLastmod);
    expect(hub?.alternates?.de).toMatch(/\/de\/jga-prag$/);
    expect(entries.map((e) => e.loc)).toEqual(
      expect.arrayContaining([expect.stringMatching(/\/about$/), expect.stringMatching(/\/contact$/), expect.stringMatching(/\/ideas$/)]),
    );
  });

  it('buildSitemapFrom_activitiesUsesGermanSlugAndSkipsNoindex', () => {
    const expectedDeSlug = 'bierbike';

    const de = buildSitemapFrom(catalog(), 'activities', 'de');
    const en = buildSitemapFrom(catalog(), 'activities', 'en');

    expect(de).toHaveLength(2);
    expect(en).toHaveLength(2);
    expect(de[0].loc.endsWith(`/de/jga-prag/aktivitaeten/${expectedDeSlug}`)).toBe(true);
    expect(de[1].loc.endsWith('/de/jga-prag/aktivitaeten/go-karts')).toBe(true);
    expect(en[0].loc.endsWith('/prague-stag-do/activities/beer-bike')).toBe(true);
    expect(en[0].lastmod).toBe('2026-10-19');
  });

  it('buildSitemapFrom_nullUpdatedAtUsesStaticLastmod', () => {
    const c = catalog({
      activitiesByDestination: { d1: [activity('go-karts', { updatedAt: null })] },
    });

    const entries = buildSitemapFrom(c, 'activities', 'en');

    expect(entries[0].lastmod).toBe(STATIC_PAGES_LASTMOD);
  });

  it('buildSitemapFrom_articlesEmptyWhenAllPostsNoindex', () => {
    expect(buildSitemapFrom(catalog(), 'articles', 'en')).toEqual([]);
    expect(buildSitemapFrom(catalog(), 'guides', 'en')).toEqual([]);
  });

  it('indexItems_excludesEmptyMaps', () => {
    const items = indexItems(catalog());

    expect(items.map((i) => i.loc).sort()).toEqual(
      [
        'https://www.trivlu.com/sitemaps/pages.xml',
        'https://www.trivlu.com/sitemaps/activities.xml',
        'https://www.trivlu.com/de/sitemaps/pages.xml',
        'https://www.trivlu.com/de/sitemaps/activities.xml',
      ].sort(),
    );
    const pages = items.find((i) => i.loc.endsWith('/sitemaps/pages.xml'));
    expect(pages?.lastmod).toBe('2026-10-21');
  });

  it('legacyPragueEntries_excludes410Rows', () => {
    const expectedCount = 150;

    const entries = legacyPragueEntries(loadRedirectMap());

    expect(entries).toHaveLength(expectedCount);
    expect(entries.every((e) => e.loc.startsWith('https://www.trivlu.com/'))).toBe(true);
    expect(entries.some((e) => e.loc.includes('top-5-'))).toBe(false);
    expect(entries.every((e) => e.lastmod === STATIC_PAGES_LASTMOD)).toBe(true);
  });

  it('urlsetXml_rendersXDefaultFromEnglish', () => {
    const xml = urlsetXml([
      {
        loc: 'https://www.trivlu.com/de/jga-prag?a=1&b=2',
        lastmod: '2026-10-20',
        alternates: { en: 'https://www.trivlu.com/prague-stag-do', de: 'https://www.trivlu.com/de/jga-prag' },
      },
    ]);

    expect(xml).toContain('xmlns:xhtml="http://www.w3.org/1999/xhtml"');
    expect(xml).toContain('<loc>https://www.trivlu.com/de/jga-prag?a=1&amp;b=2</loc>');
    expect(xml).toContain('<lastmod>2026-10-20</lastmod>');
    expect(xml).toContain('<xhtml:link rel="alternate" hreflang="x-default" href="https://www.trivlu.com/prague-stag-do"/>');
    expect(xml).toContain('hreflang="de" href="https://www.trivlu.com/de/jga-prag"');
  });

  it('resolveSitemapName_legacyOnlyEnglish', () => {
    expect(resolveSitemapName('legacy-prague.xml', 'de')).toBeNull();
    expect(resolveSitemapName('legacy-prague.xml', 'en')).toBe('legacy-prague');
    expect(resolveSitemapName('pages.xml', 'de')).toBe('pages');
    expect(resolveSitemapName('evil.xml', 'en')).toBeNull();
    expect(resolveSitemapName('pages', 'en')).toBeNull();
  });
});
