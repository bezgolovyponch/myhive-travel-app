// Wrong-slug detection shared by the entity pages: an entity has one canonical
// slug per locale, and a request under the other locale's slug is 308'd to the
// right one instead of serving duplicate content.
import { entitySlug, localizePath } from '../legacy-src/i18n/routes';

/** The localized URL to redirect to, or null when `requested` is already right. */
export function wrongSlugTarget(
  requested: string,
  entity: { slug: string; slugDe?: string | null },
  buildPath: (slug: string) => string,
  locale: string
): string | null {
  const expected: string = entitySlug(entity, locale);
  if (expected === requested) {
    return null;
  }
  return localizePath(buildPath(expected), locale);
}
