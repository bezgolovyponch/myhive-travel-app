// Server-side data access for Server Components and sitemap.ts. Talks straight
// to the Spring backend via BACKEND_URL (never the /api rewrite — that exists
// for the browser). Read lazily at request time so `next start` picks up env.
const BACKEND = (process.env.BACKEND_URL ?? 'http://localhost:8080').replace(/\/+$/, '');

// §14.2 default: ISR by timer, 1 hour.
export const REVALIDATE_SECONDS = 3600;

async function get<T>(path: string): Promise<T | null> {
  const res = await fetch(`${BACKEND}${path}`, {
    next: { revalidate: REVALIDATE_SECONDS },
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
  includes?: string | null;  // comma/semicolon-separated list
  price: number;
  minPrice?: number | null;
  duration?: number | null;
  imageUrl: string;
  destinationSlug?: string | null;
  destinationId?: string | null;
  categories?: ({ name: string; slug?: string } | string)[] | null;
}

export interface TripPackage {
  id: string;
  slug: string;
  name: string;
  description: string;
  price: number;
  imageUrl: string;
  destinationSlug?: string | null;
  activities?: Activity[] | null;
}

export interface BlogPost {
  id: string;
  slug: string;
  title: string;
  excerpt?: string | null;
  content: string;
  imageUrl?: string | null;
  category?: string | null;
  publishedAt?: string | null;
  createdAt?: string | null;
}

export const api = {
  getDestinations: () => get<Destination[]>('/destinations'),
  getDestinationBySlug: (slug: string) =>
    get<Destination>(`/destinations/slug/${encodeURIComponent(slug)}`),
  getDestinationCategories: (destinationId: string) =>
    get<Category[]>(`/destinations/${destinationId}/categories`),
  getActivities: (destinationId: string) =>
    get<Activity[]>(`/activities?destinationId=${encodeURIComponent(destinationId)}`),
  getFeaturedActivities: () => get<Activity[]>('/activities?featured=true'),
  getActivityBySlug: (slug: string) =>
    get<Activity>(`/activities/slug/${encodeURIComponent(slug)}`),
  getPackages: (destinationId: string) =>
    get<TripPackage[]>(`/packages?destinationId=${encodeURIComponent(destinationId)}`),
  getPackageBySlug: (slug: string) =>
    get<TripPackage>(`/packages/slug/${encodeURIComponent(slug)}`),
  getBlogPosts: () => get<BlogPost[]>('/blog'),
  getBlogPostBySlug: (slug: string) =>
    get<BlogPost>(`/blog/slug/${encodeURIComponent(slug)}`),
};
