// Pure logic for the "See what your budget actually buys" trip calculator and
// the home-city price comparison, ported 1:1 from the approved landing mockups
// (fixes/trivlu-landing-*.html). No DOM here — the components render its output.

export interface PoolItem {
  n: string; // display name
  p: number; // € per person
  i?: string; // image filename on img.trivlu.com (optional)
}

export type Kind = 'day' | 'dinner' | 'night' | 'signature';

export type Pool = Record<Kind, PoolItem[]>;

export interface Slot {
  day: string;
  time: string;
  kind: Kind;
}

export interface Programme {
  sl: Slot[];
  chosen: PoolItem[];
  spent: number;
}

export const TRANSFER: PoolItem = { n: 'Airport transfer', p: 15 };

const DAYNAMES = ['Friday', 'Saturday', 'Sunday', 'Monday'];
const TIMES: Record<number, string[]> = {
  2: ['15:00', '21:00'],
  3: ['12:00', '19:00', '22:00'],
  4: ['11:00', '15:00', '19:30', '22:30'],
};
const KINDS: Record<number, Kind[]> = {
  2: ['day', 'signature'],
  3: ['day', 'dinner', 'signature'],
  4: ['day', 'day', 'dinner', 'signature'],
};

export function slots(days: number, perDay: number): Slot[] {
  const out: Slot[] = [];
  for (let d = 0; d < days; d++) {
    KINDS[perDay].forEach((kind, i) => {
      out.push({ day: DAYNAMES[d], time: TIMES[perDay][i], kind });
    });
  }
  return out;
}

export function bounds(pool: Pool, days: number, perDay: number): { min: number; max: number } {
  const sl = slots(days, perDay);
  const lo: Partial<Record<Kind, number>> = {};
  const hi: Partial<Record<Kind, number>> = {};
  let min = TRANSFER.p * 2;
  let max = TRANSFER.p * 2;
  sl.forEach((s) => {
    lo[s.kind] = lo[s.kind] ?? 0;
    hi[s.kind] = hi[s.kind] ?? 0;
    min += pool[s.kind][Math.min(lo[s.kind]!++, pool[s.kind].length - 1)].p;
    max += pool[s.kind][Math.max(pool[s.kind].length - 1 - hi[s.kind]!++, 0)].p;
  });
  return { min, max };
}

// Fill every slot with the cheapest unused option of its kind, then trade up
// wherever the next step costs least, so the programme grows evenly with budget.
export function assemble(pool: Pool, budget: number, days: number, perDay: number): Programme {
  const sl = slots(days, perDay);
  const used = new Set<string>();
  const chosen = sl.map((s) => {
    const t = pool[s.kind].find((a) => !used.has(a.n)) || pool[s.kind][0];
    used.add(t.n);
    return t;
  });
  let spent = TRANSFER.p * 2 + chosen.reduce((a, b) => a + b.p, 0);
  for (let guard = 0; guard < 80; guard++) {
    let best = -1;
    let cand: PoolItem | null = null;
    let delta = Infinity;
    sl.forEach((s, i) => {
      const up = pool[s.kind].find((a) => a.p > chosen[i].p && !used.has(a.n));
      if (!up) return;
      const d = up.p - chosen[i].p;
      if (spent + d <= budget && d < delta) {
        delta = d;
        best = i;
        cand = up;
      }
    });
    if (best < 0 || !cand) break;
    used.delete(chosen[best].n);
    used.add((cand as PoolItem).n);
    spent += delta;
    chosen[best] = cand;
  }
  return { sl, chosen, spent };
}

// Every distinct programme between min and max, cheapest first. The budget
// slider indexes this ladder so no drag is ever a no-op.
export function buildLadder(pool: Pool, days: number, perDay: number): Programme[] {
  const { min, max } = bounds(pool, days, perDay);
  const ladder: Programme[] = [];
  let last = '';
  for (let b = min; b <= max; b += 1) {
    const r = assemble(pool, b, days, perDay);
    const key = r.chosen.map((c) => c.n).join('|');
    if (key !== last) {
      ladder.push(r);
      last = key;
    }
  }
  return ladder;
}

// Beer is Numbeo (Aug 2026). Beds are a central three-star double, per person
// per night. Meals are one sit-down dinner in an inexpensive restaurant.
export const CITIES: Record<string, { beer: number; bed: number; food: number }> = {
  Oslo: { beer: 11.74, bed: 69, food: 20.95 },
  Copenhagen: { beer: 9.25, bed: 67, food: 23.14 },
  Stockholm: { beer: 8.1, bed: 79, food: 18.9 },
  London: { beer: 9.42, bed: 82, food: 26.9 },
  Berlin: { beer: 5.65, bed: 82, food: 17.3 },
};
const PRAGUE = { beer: 3.09, bed: 61, food: 10.96 };

export const COMPARE_GROUP_SIZE = 10;

export interface CityComparison {
  drinks: number;
  meals: number;
  nights: number;
  home: { beer: number; bed: number; food: number; total: number };
  prague: { beer: number; bed: number; food: number; total: number };
  groupSaves: number;
}

export function compareCities(homeCity: string, nights: number): CityComparison {
  const drinks = nights === 1 ? 6 : nights === 3 ? 16 : 12;
  const meals = nights === 1 ? 2 : nights === 3 ? 6 : 4;
  const h = CITIES[homeCity];
  const home = {
    beer: Math.round(h.beer * drinks),
    bed: Math.round(h.bed * nights),
    food: Math.round(h.food * meals),
  };
  const prague = {
    beer: Math.round(PRAGUE.beer * drinks),
    bed: Math.round(PRAGUE.bed * nights),
    food: Math.round(PRAGUE.food * meals),
  };
  const homeTotal = home.beer + home.bed + home.food;
  const pragueTotal = prague.beer + prague.bed + prague.food;
  return {
    drinks,
    meals,
    nights,
    home: { ...home, total: homeTotal },
    prague: { ...prague, total: pragueTotal },
    groupSaves: (homeTotal - pragueTotal) * COMPARE_GROUP_SIZE,
  };
}
