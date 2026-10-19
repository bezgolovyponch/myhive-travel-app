import { describe, expect, it } from 'vitest';
import { activityPath } from '../../legacy-src/i18n/routes';
import { wrongSlugTarget } from '../slug-redirect';

const build = (slug: string) => activityPath('prague', slug);

describe('wrongSlugTarget', () => {
  it('activityPage_sameSlugBothLocales_noRedirect', () => {
    const entity = { slug: 'paintball', slugDe: 'paintball' };

    expect(wrongSlugTarget('paintball', entity, build, 'de')).toBeNull();
  });

  it('wrongSlugTarget_deRequestOnEnglishSlugRedirects', () => {
    const expectedSlugDe = 'axtwerfen';
    const entity = { slug: 'axe-throwing', slugDe: expectedSlugDe };

    expect(wrongSlugTarget('axe-throwing', entity, build, 'de')).toBe(
      `/de/jga-prag/aktivitaeten/${expectedSlugDe}`
    );
  });

  it('wrongSlugTarget_noGermanSlugServesEnglishSlug', () => {
    const entity = { slug: 'axe-throwing', slugDe: null };

    expect(wrongSlugTarget('axe-throwing', entity, build, 'de')).toBeNull();
  });

  it('wrongSlugTarget_enRequestMatchesEnglishSlug', () => {
    const entity = { slug: 'axe-throwing', slugDe: 'axtwerfen' };

    expect(wrongSlugTarget('axe-throwing', entity, build, 'en')).toBeNull();
  });
});
