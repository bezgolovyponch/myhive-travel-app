import { describe, it, expect } from 'vitest';
import {
  formatDurationLabel,
  toLandingActivity,
  buildRows,
  buildDeck,
  hydratePool,
  PER_ROW,
} from '../data';
import type { Activity } from '../../../lib/api';

function act(overrides: Partial<Activity>): Activity {
  return {
    id: 'id',
    slug: 'slug',
    name: 'Name',
    description: '',
    price: 10,
    imageUrl: 'https://img.trivlu.com/x.jpg',
    ...overrides,
  } as Activity;
}

describe('formatDurationLabel', () => {
  it('renders minutes under an hour, hours above', () => {
    expect(formatDurationLabel(45)).toBe('45 min');
    expect(formatDurationLabel(60)).toBe('1 h');
    expect(formatDurationLabel(90)).toBe('1.5 h');
    expect(formatDurationLabel(120)).toBe('2 h');
    expect(formatDurationLabel(null)).toBeNull();
  });
});

describe('toLandingActivity', () => {
  it('normalizes categories given as objects or strings', () => {
    const a = toLandingActivity(
      act({ categories: [{ name: 'Extreme' }, 'Chillout'] as Activity['categories'] }),
    );
    expect(a.category).toBe('Extreme');
    expect(a.categories).toEqual(['Extreme', 'Chillout']);
  });

  it('flags a group minimum only when minPrice is a positive number', () => {
    expect(toLandingActivity(act({ minPrice: 290 })).hasGroupMin).toBe(true);
    expect(toLandingActivity(act({ minPrice: 0 })).hasGroupMin).toBe(false);
    expect(toLandingActivity(act({ minPrice: null })).hasGroupMin).toBe(false);
  });
});

describe('buildRows', () => {
  const activities = [
    act({ slug: 'a1', categories: [{ name: 'Extreme' }] }),
    act({ slug: 'a2', categories: [{ name: 'Extreme' }], imageUrl: '' }),
    act({ slug: 'a3', categories: [{ name: 'Extreme' }, { name: 'Chillout' }] }),
    act({ slug: 'a4', categories: [{ name: 'Nightlife' }] }),
  ].map(toLandingActivity);

  it('groups by category in the mockup order, photographed activities first', () => {
    const rows = buildRows(activities);
    expect(rows.map((r) => r.name)).toEqual(['Extreme', 'Nightlife', 'Chillout']);
    const extreme = rows[0];
    expect(extreme.total).toBe(3);
    expect(extreme.items.map((i) => i.slug)).toEqual(['a1', 'a3', 'a2']);
  });

  it('caps a row at PER_ROW items but keeps the full count', () => {
    const many = Array.from({ length: 10 }, (_, i) =>
      toLandingActivity(act({ slug: `x${i}`, categories: [{ name: 'Extreme' }] })),
    );
    const rows = buildRows(many);
    expect(rows[0].items).toHaveLength(PER_ROW);
    expect(rows[0].total).toBe(10);
  });

  it('prefers the live category slug for the row link', () => {
    const rows = buildRows(activities, [
      { id: '1', name: 'Chillout', slug: 'wellness-live' },
    ]);
    expect(rows.find((r) => r.name === 'Chillout')?.slug).toBe('wellness-live');
    expect(rows.find((r) => r.name === 'Extreme')?.slug).toBe('extreme');
  });
});

describe('buildDeck', () => {
  it('deals the curated eight in mockup order when present', () => {
    const catalogue = [
      act({ slug: 'beer-spa', name: 'Beer Spa' }),
      act({ slug: 'army-tank-experience', name: 'Army Tank Experience' }),
      act({ slug: 'ak-47-glock-17-shooting', name: 'AK-47 and Glock 17 Shooting' }),
    ].map(toLandingActivity);
    const deck = buildDeck(catalogue);
    expect(deck.slice(0, 3).map((d) => d.slug)).toEqual([
      'ak-47-glock-17-shooting',
      'army-tank-experience',
      'beer-spa',
    ]);
  });

  it('tops up with photographed activities when curated slugs are missing', () => {
    const catalogue = Array.from({ length: 12 }, (_, i) =>
      toLandingActivity(act({ slug: `other-${i}` })),
    );
    const deck = buildDeck(catalogue);
    expect(deck).toHaveLength(8);
  });

  it('never deals a card without a photo', () => {
    const catalogue = [
      toLandingActivity(act({ slug: 'beer-spa', name: 'Beer Spa', imageUrl: '' })),
    ];
    expect(buildDeck(catalogue)).toHaveLength(0);
  });
});

describe('hydratePool', () => {
  it('overrides curated prices with live catalogue prices by slug', () => {
    const pool = hydratePool([
      toLandingActivity(act({ slug: 'axe-throwing', name: 'Axe Throwing NEW', price: 99 })),
    ]);
    const axe = pool.day.find((d) => d.n === 'Axe Throwing NEW');
    expect(axe?.p).toBe(99);
  });

  it('keeps every pool sorted cheapest-first after hydration', () => {
    const pool = hydratePool([
      toLandingActivity(act({ slug: 'axe-throwing', price: 999 })),
    ]);
    for (const kind of ['day', 'dinner', 'night', 'signature'] as const) {
      const prices = pool[kind].map((x) => x.p);
      expect(prices).toEqual([...prices].sort((a, b) => a - b));
    }
  });

  it('falls back to mockup values with img.trivlu.com images', () => {
    const pool = hydratePool([]);
    expect(pool.day[0].p).toBeGreaterThan(0);
    expect(pool.day[0].i).toMatch(/^https:\/\/img\.trivlu\.com\//);
  });
});

describe('buildRows category aliases', () => {
  it("matches the live catalogue's own category names but keeps the approved labels", () => {
    const activities = [
      act({ slug: 'p1', categories: [{ name: 'Hot babies and pranks' }] }),
      act({ slug: 't1', categories: [{ name: 'Transfer' }] }),
      act({ slug: 'b1', categories: [{ name: 'Czech beer' }] }),
    ].map(toLandingActivity);
    const rows = buildRows(activities, [
      { id: '1', name: 'Hot babies and pranks', slug: 'stag-live' },
    ]);
    expect(rows.map((r) => r.name)).toEqual(['Czech Beer', 'Pranks & Adults', 'Transfers']);
    expect(rows.find((r) => r.name === 'Pranks & Adults')?.slug).toBe('stag-live');
    expect(rows.find((r) => r.name === 'Transfers')?.slug).toBe('transfer');
  });
});
