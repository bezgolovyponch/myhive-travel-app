// Server-side data access for Server Components and sitemap.ts. Talks straight
// to the Spring backend via BACKEND_URL (never the /api rewrite — that exists
// for the browser). Read lazily at request time so `next start` picks up env.
const BACKEND = (process.env.BACKEND_URL ?? 'http://localhost:8080').replace(/\/+$/, '');

// §14.2 default: ISR by timer, 1 hour.
export const REVALIDATE_SECONDS = 3600;

async function get<T>(path: string): Promise<T | null> {
  // Rate-limit exemption for server-to-server traffic (cold ISR fills render
  // the whole catalog from one egress IP). Read per-request like BACKEND.
  const token = process.env.INTERNAL_API_TOKEN;
  const res = await fetch(`${BACKEND}${path}`, {
    next: { revalidate: REVALIDATE_SECONDS },
    ...(token ? { headers: { 'X-Internal-Token': token } } : {}),
  });
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(`Backend ${res.status} on ${path}`);
  return res.json();
}

// Vote sessions carry live counts (participantCount changes as people vote),
// so they skip the hour-long catalog cache in favor of a short revalidate.
async function getLive<T>(path: string): Promise<T | null> {
  const token = process.env.INTERNAL_API_TOKEN;
  const res = await fetch(`${BACKEND}${path}`, {
    next: { revalidate: 60 },
    ...(token ? { headers: { 'X-Internal-Token': token } } : {}),
  });
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(`Backend ${res.status} on ${path}`);
  return res.json();
}

export interface Destination {
  id: string;
  slug: string;
  name: string;
  description: string;
  country: string;
  city: string;
  imageUrl: string;
  rating: number;
  activityCount: number;
  seoIndexable?: boolean | null;
}

export interface Category {
  id: string;
  name: string;
  slug: string;
}

export interface Activity {
  id: string;
  slug: string;
  name: string;
  description: string;
  includes?: string | null;  // semicolon/newline-separated list
  price: number;
  minPrice?: number | null;
  duration?: number | null;
  imageUrl: string;
  destinationSlug?: string | null;
  destinationId?: string | null;
  /** Localized like every other field when the request carries a locale. */
  destinationName?: string | null;
  categories?: ({ name: string; slug?: string } | string)[] | null;
  seoIndexable?: boolean | null;
}

export interface TripPackage {
  id: string;
  slug: string;
  name: string;
  description: string;
  discountPct?: number | null;
  originalPrice: number;
  discountedPrice: number;
  savings: number;
  imageUrl: string;
  destinationSlug?: string | null;
  /** Localized like every other field when the request carries a locale. */
  destinationName?: string | null;
  activities?: Activity[] | null;
  seoIndexable?: boolean | null;
}

export interface BlogPost {
  id: string;
  slug: string;
  title: string;
  excerpt?: string | null;
  content: string;
  imageUrl?: string | null;
  category?: string | null;
  // The backend's publication field is `date` (a plain YYYY-MM-DD), which is
  // also what the CRA blog pages render. There is no `publishedAt` — a field of
  // that name here silently made every consumer fall through to createdAt, i.e.
  // the row's insert timestamp, misreporting publication dates.
  date?: string | null;
  createdAt?: string | null;
  seoIndexable?: boolean | null;
}

// Shape of the backend's paged activity response, as CRA's DestinationPage
// consumes it: the catalog renders page 0 server-side and pages further on the
// client, so seeded state must carry totalElements/last too.
export interface ActivityPage {
  content: Activity[];
  totalElements: number;
  last: boolean;
}

export interface VoteSessionMeta {
  shareToken: string;
  destinationName: string;
  destinationSlug: string;
  status: string;
  participantCount: number;
  numberOfTravelers: number;
  groomName?: string | null;
}

export interface VoteActivityMeta {
  id: string;
  name: string;
  imageUrl?: string | null;
}

// Every read carries the page's locale: the backend resolves the translatable
// fields in place (same response shape) and omits the raw translations map it
// would otherwise return for the admin view. Distinct URLs per locale also
// keep the ISR fetch cache separate. Defaults to English so locale-agnostic
// callers (sitemap) get the public English view.
function withLocale(path: string, locale: string) {
  return `${path}${path.includes('?') ? '&' : '?'}locale=${encodeURIComponent(locale)}`;
}

export const api = {
  getDestinations: (locale = 'en') => get<Destination[]>(withLocale('/destinations', locale)),
  getDestinationBySlug: (slug: string, locale = 'en') =>
    get<Destination>(withLocale(`/destinations/slug/${encodeURIComponent(slug)}`, locale)),
  getDestinationCategories: (destinationId: string, locale = 'en') =>
    get<Category[]>(withLocale(`/destinations/${destinationId}/categories`, locale)),
  getActivities: (destinationId: string, locale = 'en') =>
    get<Activity[]>(
      withLocale(`/activities?destinationId=${encodeURIComponent(destinationId)}`, locale)
    ),
  getFeaturedActivities: (locale = 'en') =>
    get<Activity[]>(withLocale('/activities?featured=true', locale)),
  getActivitiesPaged: (destinationId: string, page = 0, size = 12, locale = 'en') =>
    get<ActivityPage>(
      withLocale(
        `/activities/paged?destinationId=${encodeURIComponent(destinationId)}&page=${page}&size=${size}`,
        locale
      )
    ),
  getActivityBySlug: (slug: string, locale = 'en') =>
    get<Activity>(withLocale(`/activities/slug/${encodeURIComponent(slug)}`, locale)),
  getPackages: (destinationId: string, locale = 'en') =>
    get<TripPackage[]>(
      withLocale(`/packages?destinationId=${encodeURIComponent(destinationId)}`, locale)
    ),
  getPackageBySlug: (slug: string, locale = 'en') =>
    get<TripPackage>(withLocale(`/packages/slug/${encodeURIComponent(slug)}`, locale)),
  getBlogPosts: (locale = 'en') => get<BlogPost[]>(withLocale('/blog', locale)),
  getBlogPostBySlug: (slug: string, locale = 'en') =>
    get<BlogPost>(withLocale(`/blog/slug/${encodeURIComponent(slug)}`, locale)),
  // Vote-session reads are live-ish (60s revalidate) and locale-free: the
  // session shell renders participant/state data, not translated catalog copy.
  getVoteSession: (shareToken: string) =>
    getLive<VoteSessionMeta>(`/vote/sessions/${encodeURIComponent(shareToken)}`),
  getVoteActivities: (shareToken: string) =>
    getLive<VoteActivityMeta[]>(`/vote/sessions/${encodeURIComponent(shareToken)}/activities`),
};
