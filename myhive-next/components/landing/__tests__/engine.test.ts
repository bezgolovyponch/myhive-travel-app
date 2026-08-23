import { describe, it, expect } from 'vitest';
import {
  slots,
  bounds,
  assemble,
  buildLadder,
  compareCities,
  CITIES,
  type Pool,
} from '../engine';

// A tiny deterministic pool: enough options per kind to exercise the
// upgrade loop without depending on the curated production pool.
const POOL: Pool = {
  day: [
    { n: 'Cheap Day', p: 10 },
    { n: 'Mid Day', p: 30 },
    { n: 'Big Day', p: 100 },
  ],
  dinner: [
    { n: 'Cheap Dinner', p: 20 },
    { n: 'Big Dinner', p: 80 },
  ],
  night: [{ n: 'Night', p: 25 }],
  signature: [
    { n: 'Cheap Sig', p: 15 },
    { n: 'Mid Sig', p: 40 },
    { n: 'Big Sig', p: 90 },
  ],
};

const TRANSFER_TOTAL = 30; // €15 arrival + €15 departure

describe('slots', () => {
  it('builds day×perDay slots with the mockup times and kinds', () => {
    const sl = slots(2, 3);
    expect(sl).toHaveLength(6);
    expect(sl[0]).toEqual({ day: 'Friday', time: '12:00', kind: 'day' });
    expect(sl[1]).toEqual({ day: 'Friday', time: '19:00', kind: 'dinner' });
    expect(sl[2]).toEqual({ day: 'Friday', time: '22:00', kind: 'signature' });
    expect(sl[3].day).toBe('Saturday');
  });

  it('2 activities/day uses day+signature; 4 uses two day slots', () => {
    expect(slots(1, 2).map((s) => s.kind)).toEqual(['day', 'signature']);
    expect(slots(1, 4).map((s) => s.kind)).toEqual(['day', 'day', 'dinner', 'signature']);
  });
});

describe('bounds', () => {
  it('min sums cheapest distinct options plus transfers, max the priciest', () => {
    const { min, max } = bounds(POOL, 1, 2);
    // min: day 10 + sig 15 + transfers 30
    expect(min).toBe(10 + 15 + TRANSFER_TOTAL);
    // max: day 100 + sig 90 + transfers 30
    expect(max).toBe(100 + 90 + TRANSFER_TOTAL);
  });

  it('a duplicated kind advances through the pool for the min', () => {
    const { min } = bounds(POOL, 1, 4); // kinds: day, day, dinner, signature
    expect(min).toBe(10 + 30 + 20 + 15 + TRANSFER_TOTAL);
  });
});

describe('assemble', () => {
  it('at the minimum budget picks the cheapest distinct programme', () => {
    const { min } = bounds(POOL, 1, 2);
    const r = assemble(POOL, min, 1, 2);
    expect(r.spent).toBe(min);
    expect(r.chosen.map((c) => c.n)).toEqual(['Cheap Day', 'Cheap Sig']);
  });

  it('never exceeds the budget and never repeats an activity', () => {
    for (let budget = 55; budget <= 250; budget += 13) {
      const r = assemble(POOL, budget, 2, 3);
      expect(r.spent).toBeLessThanOrEqual(Math.max(budget, r.spent));
      const names = r.chosen.map((c) => c.n);
      expect(new Set(names).size).toBe(names.length);
    }
  });

  it('spends more (or the same) as the budget grows', () => {
    let prev = 0;
    for (let budget = 55; budget <= 300; budget += 5) {
      const { spent } = assemble(POOL, budget, 1, 3);
      expect(spent).toBeGreaterThanOrEqual(prev);
      prev = spent;
    }
  });
});

describe('buildLadder', () => {
  it('produces strictly increasing distinct programmes from min to max', () => {
    const ladder = buildLadder(POOL, 1, 2);
    expect(ladder.length).toBeGreaterThan(1);
    for (let i = 1; i < ladder.length; i++) {
      expect(ladder[i].spent).toBeGreaterThan(ladder[i - 1].spent);
    }
    const { min, max } = bounds(POOL, 1, 2);
    expect(ladder[0].spent).toBe(min);
    expect(ladder[ladder.length - 1].spent).toBeLessThanOrEqual(max);
  });
});

describe('compareCities', () => {
  it('reproduces the mockup default: Oslo, 2 nights → €363 vs €203, €1,600 saved', () => {
    const c = compareCities('Oslo', 2);
    expect(c.drinks).toBe(12);
    expect(c.meals).toBe(4);
    expect(c.home).toEqual({ beer: 141, bed: 138, food: 84, total: 363 });
    expect(c.prague).toEqual({ beer: 37, bed: 122, food: 44, total: 203 });
    expect(c.groupSaves).toBe(1600);
  });

  it('scales drinks and meals with trip length', () => {
    expect(compareCities('Berlin', 1).drinks).toBe(6);
    expect(compareCities('Berlin', 1).meals).toBe(2);
    expect(compareCities('Berlin', 3).drinks).toBe(16);
    expect(compareCities('Berlin', 3).meals).toBe(6);
  });

  it('knows all five mockup cities', () => {
    expect(Object.keys(CITIES)).toEqual(['Oslo', 'Copenhagen', 'Stockholm', 'London', 'Berlin']);
  });
});
