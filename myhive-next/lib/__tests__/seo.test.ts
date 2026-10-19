import { describe, expect, it } from 'vitest';
import { pageMetadata } from '../seo';

describe('pageMetadata', () => {
  it('pageMetadata_perLocalePathsBuildCanonicalAndHreflang', () => {
    const expectedEn = '/prague-stag-do/activities/axe-throwing';
    const expectedDe = '/prague-stag-do/activities/axtwerfen';

    const meta = pageMetadata({
      title: 'Axe',
      description: 'Throw axes',
      path: { en: expectedEn, de: expectedDe },
      locale: 'de',
    });

    expect(meta.alternates?.canonical).toBe('https://www.trivlu.com/de/jga-prag/aktivitaeten/axtwerfen');
    const languages = meta.alternates?.languages as Record<string, string>;
    expect(languages.en.endsWith(expectedEn)).toBe(true);
    expect(languages.de).toBe('https://www.trivlu.com/de/jga-prag/aktivitaeten/axtwerfen');
    expect(languages['x-default']).toBe(languages.en);
  });

  it('pageMetadata_stringPathUnchanged', () => {
    const meta = pageMetadata({ title: 'About', description: 'd', path: '/about', locale: 'de' });

    expect(meta.alternates?.canonical).toBe('https://www.trivlu.com/de/about');
    const languages = meta.alternates?.languages as Record<string, string>;
    expect(languages.en).toBe('https://www.trivlu.com/about');
    expect(languages['x-default']).toBe('https://www.trivlu.com/about');
  });
});
