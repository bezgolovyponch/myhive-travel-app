// Bridges the real catalogue (lib/api types) to the landing mockups' card and
// calculator models. The curated lists below come straight from the approved
// mockups (fixes/trivlu-landing-*.html); every entry carries the mockup's
// price/image as a fallback so the sections render even when the API record
// moved or the backend is unreachable, but live API data wins when it matches.
import type { Activity, Category } from '../../lib/api';
import type { Pool, PoolItem, Kind } from './engine';

export const IMG_BASE = 'https://img.trivlu.com/';

// The real trip builder is the destination page's SPA-owned tab — the same
// place the main header's cart continues to. The homepage ignores ?tab=, so
// landing CTAs must never point there.
export function builderUrl(destinationSlug: string): string {
  return `/destination/${destinationSlug}?tab=trip-builder`;
}

// Landing selections ride along as query params: TripBuilder.js consumes
// `picks` (comma-separated slugs); useTripDeepLink consumes `add` (one slug)
// with the same semantics as a legacy Add-to-trip click.
export function builderUrlWithPicks(destinationSlug: string, picked: string[]): string {
  const base = builderUrl(destinationSlug);
  return picked.length ? `${base}&picks=${picked.join(',')}` : base;
}

export function builderUrlWithAdd(destinationSlug: string, activitySlug: string): string {
  return `${builderUrl(destinationSlug)}&add=${activitySlug}`;
}

export function activityLink(destinationSlug: string, activitySlug: string): string {
  return `/destination/${destinationSlug}/activity/${activitySlug}`;
}
export const PHONE_DISPLAY = '+420 795 518 597';
export const PHONE_HREF = 'tel:+420795518597';
export const WHATSAPP_HREF =
  "https://wa.me/420795518597?text=Hi%20Trivlu%2C%20I%27d%20like%20some%20help%20planning%20our%20bachelor%20party.";

export interface LandingActivity {
  id: string; // API id — the cart dedupes on it, so adds must carry it
  slug: string;
  name: string;
  category: string | null; // primary category name, localized by the backend (photo chip)
  categories: string[]; // localized names
  categorySlugs: string[]; // locale-stable slugs where the API provides them
  price: number;
  hasGroupMin: boolean;
  minPrice: number | null;
  durationLabel: string | null;
  imageUrl: string;
}

export function formatDurationLabel(minutes: number | null | undefined): string | null {
  if (minutes == null || !Number.isFinite(minutes) || minutes <= 0) return null;
  if (minutes < 60) return `${minutes} min`;
  const hours = Math.round((minutes / 60) * 10) / 10;
  return `${hours} h`;
}

function categoryNames(a: Activity): string[] {
  return (a.categories ?? []).map((c) => (typeof c === 'string' ? c : c.name)).filter(Boolean);
}

function categorySlugs(a: Activity): string[] {
  return (a.categories ?? [])
    .map((c) => (typeof c === 'string' ? undefined : c.slug))
    .filter((s): s is string => Boolean(s));
}

export function toLandingActivity(a: Activity): LandingActivity {
  const cats = categoryNames(a);
  const hasGroupMin = a.minPrice != null && a.minPrice > 0;
  return {
    id: a.id,
    slug: a.slug,
    name: a.name,
    category: cats[0] ?? null,
    categories: cats,
    categorySlugs: categorySlugs(a),
    price: a.price,
    hasGroupMin,
    minPrice: hasGroupMin ? a.minPrice! : null,
    durationLabel: formatDurationLabel(a.duration),
    imageUrl: a.imageUrl || '',
  };
}

// A landing add IS a cart add — same reducer, same localStorage — so the
// payload has to look like the ones ActivityCard dispatches on the main site.
// destinationSlug comes from the page rather than the record: the dropdown's
// Continue button navigates by it, and the API omits it on nested activities.
export interface CartActivity {
  id: string;
  slug: string;
  name: string;
  price: number;
  minPrice: number | null;
  imageUrl: string;
  destinationSlug: string;
}

export function toCartItem(a: LandingActivity, destinationSlug: string): CartActivity {
  return {
    id: a.id,
    slug: a.slug,
    name: a.name,
    price: a.price,
    minPrice: a.minPrice,
    imageUrl: a.imageUrl,
    destinationSlug,
  };
}

// One row per category, in the mockup's order, keyed by the locale-stable
// category slug. Display labels live in the landing dictionary
// (landing.rows.<slug>) so the approved copy renders in every locale; an
// unknown slug falls back to the live category name.
export const ROW_ORDER: string[] = [
  'extreme',
  'guns-and-bullets',
  'food-and-drink',
  'czech-beer',
  'nightlife',
  'wellness',
  'stag-hot-babies-and-pranks',
  'transfer',
];

export const PER_ROW = 6;

export function categoryLink(destinationSlug: string, categorySlug: string): string {
  return `/destination/${destinationSlug}?tab=activities&category=${categorySlug}`;
}

export interface ActivityRow {
  slug: string;
  liveName: string; // localized category name from the API; label fallback
  total: number; // "N in the catalogue"
  items: LandingActivity[]; // photographed first, PER_ROW deep
}

export function buildRows(
  activities: LandingActivity[],
  categories: Category[] = [],
): ActivityRow[] {
  // Some payloads carry category names only; resolve those through the live
  // categories list so slug matching still works.
  const nameToSlug = new Map(categories.map((c) => [c.name.toLowerCase(), c.slug]));
  const slugToName = new Map(categories.map((c) => [c.slug, c.name]));
  const inCategory = (a: LandingActivity, slug: string) =>
    a.categorySlugs.includes(slug) ||
    a.categories.some((n) => nameToSlug.get(n.toLowerCase()) === slug);
  return ROW_ORDER.map((slug) => {
    const all = activities.filter((a) => inCategory(a, slug));
    const items = [...all.filter((a) => a.imageUrl), ...all.filter((a) => !a.imageUrl)].slice(
      0,
      PER_ROW,
    );
    return {
      slug,
      liveName: slugToName.get(slug) ?? '',
      total: all.length,
      items,
    };
  }).filter((row) => row.total > 0);
}

// The hero swipe deck: eight signature activities in the mockup's order.
const DECK_SLUGS = [
  'ak-47-glock-17-shooting',
  'army-tank-experience',
  'beer-spa',
  'go-karting-experience',
  'rafting-extreme',
  'river-boat-cruise',
  'live-music-irish-dinner',
  'car-demolition-in-prague',
];
const DECK_NAMES = [
  'AK-47 and Glock 17 Shooting',
  'Army Tank Experience',
  'Beer Spa',
  'Go-Karting Experience',
  'Rafting Extreme',
  'River Boat Cruise',
  'Live Music Irish Dinner',
  'Car Demolition in Prague',
];
export const DECK_SIZE = 8;

export function buildDeck(activities: LandingActivity[]): LandingActivity[] {
  const found: LandingActivity[] = [];
  DECK_SLUGS.forEach((slug, i) => {
    const a =
      activities.find((x) => x.slug === slug) ??
      activities.find((x) => x.name.toLowerCase() === DECK_NAMES[i].toLowerCase());
    if (a && a.imageUrl) found.push(a);
  });
  if (found.length >= DECK_SIZE) return found.slice(0, DECK_SIZE);
  // Catalogue drifted: top up with photographed activities not already dealt in.
  const extra = activities.filter((a) => a.imageUrl && !found.includes(a));
  return [...found, ...extra].slice(0, DECK_SIZE);
}

// The calculator's curated pools, split by the part of the day they suit.
// Prices/images are the mockup export; live catalogue values override by slug.
type CuratedEntry = PoolItem & { slug?: string };
const CURATED_POOL: Record<Kind, CuratedEntry[]> = {
  day: [
    { slug: 'axe-throwing', n: 'Axe Throwing', p: 33, i: '07b184b8-27e7-463e-8053-fee0c7153896.jpg' },
    { slug: 'unlimited-paintball-experience', n: 'Unlimited Rubber Paintball', p: 35, i: 'db1b954e-cb38-4ea7-a507-1892cd2c9e3b.png' },
    { slug: 'bubble-football', n: 'Bubble Football', p: 37, i: '24a0aeb0-b3d5-43b2-848a-0f70bb68f4a1.jpg' },
    { slug: 'river-boat-cruise', n: 'River Boat Cruise', p: 37, i: '613cdcc2-18d6-4d98-8e46-0cf64bd26253.webp' },
    { slug: 'go-karting-experience', n: 'Go-Karting Experience', p: 54, i: '2825b389-7ff5-47cf-a7f9-4a7885ff8932.png' },
    { slug: 'ak-47-glock-17-shooting', n: 'AK-47 and Glock 17 Shooting', p: 55, i: 'ba3c0544-bb2f-4649-9025-47e0865bf133.jpg' },
    { slug: 'army-tank-experience', n: 'Army Tank Experience', p: 55, i: '7444f6b8-0ddb-4d0e-9021-0652effc529e.webp' },
    { slug: 'quad-bikes', n: 'Quad Bikes', p: 78, i: '63d9882b-22ca-446f-bb26-3069cdfa1e27.jpg' },
    { slug: 'rafting-extreme', n: 'Rafting Extreme', p: 109, i: '15d22f9b-646a-46d0-b62a-3f6d271276d8.jpg' },
    { slug: 'car-demolition-in-prague', n: 'Car Demolition in Prague', p: 151, i: '27fbd072-bba7-4823-ad38-d1aece1065d2.jpg' },
  ],
  dinner: [
    { slug: 'live-music-irish-dinner', n: 'Live Music Irish Dinner', p: 41, i: '2fb4449f-7f8f-40ae-b9e2-b9fc19ebf699.webp' },
    { slug: 'succulent-bbq-ribs', n: 'Succulent BBQ Ribs', p: 43, i: '14cf0df2-de9b-41c7-aeee-85532b54bbdd.jpg' },
    { slug: 'grilled-suckling-pig', n: 'Grilled Suckling Pig', p: 51, i: 'ade3509b-8617-419d-91b4-a05f10ca94fa.jpg' },
    { slug: 'whiskey-tasting', n: 'Whiskey Tasting', p: 56, i: '886ed0da-6914-42a2-8ee8-d178fa731517.jpg' },
    { slug: 'czech-dinner-with-national-show', n: 'Czech Dinner with a Show', p: 78, i: 'eb5aedfd-04d7-42ff-b5c3-96ed4e80cffd.png' },
    { slug: 'medieval-dinner', n: 'Medieval Dinner', p: 89, i: '7be4a643-e1cc-4965-b048-ed97223ded25.jpg' },
    { slug: 'wine-tasting', n: 'Wine Tasting', p: 95, i: '6734a461-db35-42d6-81f3-004374c5a940.avif' },
  ],
  night: [
    { slug: 'casino-night', n: 'Casino Night', p: 26, i: '198dd86b-0ed5-47c2-a17b-2591a9883e35.jpg' },
    { slug: 'cocktails-karaoke-night', n: 'Cocktails and Karaoke', p: 35, i: 'd09b4df1-9b1f-4333-b9c3-5b9309ff05a0.webp' },
    { slug: 'vip-club-entrance', n: 'VIP Club Entrance', p: 60, i: '0f8081cf-cd96-4eab-972c-79772ddf7792.jpeg' },
  ],
  // The two categories groups book most, so every day gets one.
  signature: [
    { slug: 'stripper', n: 'Stripper', p: 20, i: '036ff6c6-3040-4ca9-b12c-fdbd01e2000d.jpg' },
    { slug: 'bowling-and-beers', n: 'Bowling and Beers', p: 27, i: '45f2c4a2-b865-42a9-a20d-c4633ed42343.jpg' },
    { slug: 'beer-tasting-experience', n: 'Beer Tasting Experience', p: 30, i: '4fa58b74-08cc-46ff-b3b7-51e7e2f75a4b.jpg' },
    { slug: 'xxl-roly-poly-show', n: 'XXL Roly-Poly Show', p: 39, i: '2cf99c79-d108-47d6-8153-fad53db5fb5a.webp' },
    { slug: 'burger-and-strip', n: 'Burger and Strip', p: 40, i: '417d31e3-412d-42a9-8a08-6c4828ac2b84.jpg' },
    { slug: 'stag-challenge', n: 'Self Tapping Pub', p: 40, i: 'ddce1cbf-44ec-4267-9dbf-313bf82f184c.png' },
    { slug: 'jelly-wrestling', n: 'Jelly Wrestling', p: 40, i: 'b4e17a6d-895c-4d11-b006-d537ece86a38.jpg' },
    { slug: 'steak-and-strip', n: 'Steak and Strip', p: 45, i: 'e58ff74a-ee07-419c-99e6-9a2b15f33dc7.png' },
    { slug: 'strip-limo', n: 'Strip Limo', p: 47, i: '5bcd6622-50f3-4cb0-9ee9-53a10311e174.jpg' },
    { slug: 'swimming-beer-bike', n: 'Swimming Beer Bike', p: 52, i: 'bac6c3d7-8be1-4cc6-95ad-1798c3e18d68.avif' },
    { slug: 'bar-guide-bar-tap-in-a-strip-club', n: 'Bar Guide + Bar Tap In A Strip Club', p: 60, i: '2d7cfb02-d0bc-4198-80cc-fd7b9de81a8a.jpg' },
    { slug: 'gold-show-package', n: 'Gold Show Package', p: 93, i: 'f74e82f6-4e13-4c42-b51d-59c3f2989da3.jpg' },
    { slug: 'beer-olympics', n: 'Beer Olympics', p: 109, i: '8da7c84a-677c-4628-a10c-5dd0a8ae8243.jpg' },
    { slug: 'beer-spa', n: 'Beer Spa', p: 113, i: 'ba9ba7d2-61e2-42f2-a9a5-73336d833341.jpg' },
  ],
};

export function poolImageUrl(item: PoolItem): string {
  if (!item.i) return '';
  return item.i.startsWith('http') ? item.i : IMG_BASE + item.i;
}

// Live catalogue values win over the mockup export where the slug still exists;
// each pool keeps its cheapest-first order, which bounds/assemble rely on.
export function hydratePool(activities: LandingActivity[]): Pool {
  const bySlug = new Map(activities.map((a) => [a.slug, a]));
  const hydrate = (entries: CuratedEntry[]): PoolItem[] =>
    entries
      .map((e) => {
        const live = e.slug ? bySlug.get(e.slug) : undefined;
        return live
          ? { n: live.name, p: live.price, i: live.imageUrl || e.i }
          : { n: e.n, p: e.p, i: e.i ? IMG_BASE + e.i : undefined };
      })
      .sort((a, b) => a.p - b.p);
  return {
    day: hydrate(CURATED_POOL.day),
    dinner: hydrate(CURATED_POOL.dinner),
    night: hydrate(CURATED_POOL.night),
    signature: hydrate(CURATED_POOL.signature),
  };
}
