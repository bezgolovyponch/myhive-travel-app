// Retired seed posts (ТЗ 2026-10: "410, not 404 and not a redirect to the
// homepage"). next.config redirects() cannot emit 410, so middleware does.
export const GONE_BLOG_SLUGS = [
  'top-5-group-travel-destinations-for-2026',
  'why-ai-is-changing-the-way-we-travel-together',
  'bali-on-a-budget-a-group-travel-guide',
  'the-rise-of-multi-traveler-experiences',
  'weekend-getaway-ideas-for-large-groups',
] as const;

const GONE = new Set<string>(GONE_BLOG_SLUGS.flatMap((s) => [`/blog/${s}`, `/de/blog/${s}`]));

export function isGone(pathname: string): boolean {
  return GONE.has(pathname.replace(/\/+$/, '') || '/');
}
