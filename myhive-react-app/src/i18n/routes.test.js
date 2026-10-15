import {
  DESTINATION_HUBS,
  LOCALIZED_PATHNAMES,
  activityPath,
  blogIndexPath,
  blogPostPath,
  delocalizeSegments,
  destinationPath,
  localizeHref,
  localizePath,
  localizeSegments,
  matchCatalogPath,
  packagePath,
  routePath,
} from './routes';

describe('path builders', () => {
  it('maps a hub destination to its keyword path and others to /destination', () => {
    expect(DESTINATION_HUBS.prague).toBe('/prague-stag-do');
    expect(destinationPath('prague')).toBe('/prague-stag-do');
    expect(destinationPath('krakow')).toBe('/destination/krakow');
    expect(activityPath('prague', 'beer-spa')).toBe('/prague-stag-do/activities/beer-spa');
    expect(activityPath('krakow', 'beer-spa')).toBe('/destination/krakow/activity/beer-spa');
    expect(packagePath('prague', 'weekend')).toBe('/prague-stag-do/packages/weekend');
    expect(packagePath('krakow', 'weekend')).toBe('/destination/krakow/package/weekend');
    expect(blogIndexPath()).toBe('/ideas');
    expect(blogPostPath('how-to')).toBe('/ideas/how-to');
  });

  it('declares every hub path in the localized table', () => {
    Object.values(DESTINATION_HUBS).forEach((hub) => {
      expect(LOCALIZED_PATHNAMES[hub]).toBeDefined();
      expect(LOCALIZED_PATHNAMES[`${hub}/activities/[slug]`]).toBeDefined();
      expect(LOCALIZED_PATHNAMES[`${hub}/packages/[slug]`]).toBeDefined();
    });
  });
});

describe('localizeSegments / delocalizeSegments', () => {
  it('is the identity for English', () => {
    expect(localizeSegments('/prague-stag-do/activities/beer-spa', 'en')).toBe('/prague-stag-do/activities/beer-spa');
    expect(localizeSegments('/about', 'en')).toBe('/about');
  });

  it('translates the hub, activity, package and ideas segments for German', () => {
    expect(localizeSegments('/prague-stag-do', 'de')).toBe('/jga-prag');
    expect(localizeSegments('/prague-stag-do/activities/beer-spa', 'de')).toBe('/jga-prag/aktivitaeten/beer-spa');
    expect(localizeSegments('/prague-stag-do/packages/weekend', 'de')).toBe('/jga-prag/pakete/weekend');
    expect(localizeSegments('/ideas', 'de')).toBe('/ratgeber');
    expect(localizeSegments('/ideas/how-to', 'de')).toBe('/ratgeber/how-to');
  });

  it('leaves unlisted and already-German paths alone (idempotent)', () => {
    expect(localizeSegments('/about', 'de')).toBe('/about');
    expect(localizeSegments('/destination/krakow/activity/x', 'de')).toBe('/destination/krakow/activity/x');
    expect(localizeSegments('/jga-prag/aktivitaeten/beer-spa', 'de')).toBe('/jga-prag/aktivitaeten/beer-spa');
    expect(localizeSegments('/prague-stag-do/packages', 'de')).toBe('/prague-stag-do/packages');
  });

  it('keeps query and hash and react-router params', () => {
    expect(localizeSegments('/prague-stag-do?tab=trip-builder&restore=T#x', 'de')).toBe('/jga-prag?tab=trip-builder&restore=T#x');
    expect(localizeSegments('/prague-stag-do/activities/:slug', 'de')).toBe('/jga-prag/aktivitaeten/:slug');
  });

  it('delocalizes back to the internal form', () => {
    expect(delocalizeSegments('/jga-prag', 'de')).toBe('/prague-stag-do');
    expect(delocalizeSegments('/jga-prag/aktivitaeten/beer-spa?x=1', 'de')).toBe('/prague-stag-do/activities/beer-spa?x=1');
    expect(delocalizeSegments('/ratgeber/how-to', 'de')).toBe('/ideas/how-to');
    expect(delocalizeSegments('/about', 'de')).toBe('/about');
    expect(delocalizeSegments('/prague-stag-do', 'en')).toBe('/prague-stag-do');
  });

  it('routePath defaults to the current locale (English in tests)', () => {
    expect(routePath('/prague-stag-do')).toBe('/prague-stag-do');
    expect(routePath('/prague-stag-do', 'de')).toBe('/jga-prag');
  });
});

describe('localizePath / localizeHref', () => {
  it('prefixes and translates for German, keeps English bare', () => {
    expect(localizePath('/prague-stag-do/activities/beer-spa', 'de')).toBe('/de/jga-prag/aktivitaeten/beer-spa');
    expect(localizePath('/prague-stag-do/activities/beer-spa', 'en')).toBe('/prague-stag-do/activities/beer-spa');
    expect(localizePath('/', 'de')).toBe('/de');
    expect(localizePath('/about', 'de')).toBe('/de/about');
    expect(localizePath('/prague-stag-do?tab=activities', 'de')).toBe('/de/jga-prag?tab=activities');
  });

  it('localizeHref keeps external and already-prefixed hrefs and translates the rest', () => {
    expect(localizeHref('https://x.test/prague-stag-do', 'de')).toBe('https://x.test/prague-stag-do');
    expect(localizeHref('/de/jga-prag', 'de')).toBe('/de/jga-prag');
    expect(localizeHref('/prague-stag-do?tab=trip-builder#top', 'de')).toBe('/de/jga-prag?tab=trip-builder#top');
    expect(localizeHref('/jga-prag', 'de')).toBe('/de/jga-prag');
  });
});

describe('matchCatalogPath', () => {
  it('recognizes hub, activity and package pages in both forms', () => {
    expect(matchCatalogPath('/prague-stag-do', 'en')).toEqual({ destinationSlug: 'prague', kind: 'hub' });
    expect(matchCatalogPath('/jga-prag?tab=activities', 'de')).toEqual({ destinationSlug: 'prague', kind: 'hub' });
    expect(matchCatalogPath('/jga-prag/aktivitaeten/beer-spa', 'de')).toEqual({ destinationSlug: 'prague', kind: 'activity' });
    expect(matchCatalogPath('/prague-stag-do/packages/weekend', 'en')).toEqual({ destinationSlug: 'prague', kind: 'package' });
    expect(matchCatalogPath('/destination/krakow', 'en')).toEqual({ destinationSlug: 'krakow', kind: 'hub' });
    expect(matchCatalogPath('/destination/krakow/activity/x', 'de')).toEqual({ destinationSlug: 'krakow', kind: 'activity' });
  });

  it('returns null elsewhere', () => {
    expect(matchCatalogPath('/about', 'en')).toBeNull();
    expect(matchCatalogPath('/ideas/how-to', 'en')).toBeNull();
    expect(matchCatalogPath('/', 'de')).toBeNull();
  });
});
