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
    act({ slug: 'a1', categories: [{ name: 'Extreme', slug: 'extreme' }] }),
    act({ slug: 'a2', categories: [{ name: 'Extreme', slug: 'extreme' }], imageUrl: '' }),
    act({
      slug: 'a3',
      categories: [
        { name: 'Extreme', slug: 'extreme' },
        { name: 'Chillout', slug: 'wellness' },
      ],
    }),
    act({ slug: 'a4', categories: [{ name: 'Nightlife', slug: 'nightlife' }] }),
  ].map(toLandingActivity);

  it('groups by category slug in the mockup order, photographed activities first', () => {
    const rows = buildRows(activities);
    expect(rows.map((r) => r.slug)).toEqual(['extreme', 'nightlife', 'wellness']);
    const extreme = rows[0];
    expect(extreme.total).toBe(3);
    expect(extreme.items.map((i) => i.slug)).toEqual(['a1', 'a3', 'a2']);
  });

  it('caps a row at PER_ROW items but keeps the full count', () => {
    const many = Array.from({ length: 10 }, (_, i) =>
      toLandingActivity(act({ slug: `x${i}`, categories: [{ name: 'Extreme', slug: 'extreme' }] })),
    );
    const rows = buildRows(many);
    expect(rows[0].items).toHaveLength(PER_ROW);
    expect(rows[0].total).toBe(10);
  });

  it('resolves name-only category payloads through the live categories list', () => {
    const nameOnly = [
      toLandingActivity(act({ slug: 'n1', categories: ['Extrem'] as Activity['categories'] })),
    ];
    const rows = buildRows(nameOnly, [{ id: '1', name: 'Extrem', slug: 'extreme' }]);
    expect(rows.map((r) => r.slug)).toEqual(['extreme']);
    expect(rows[0].liveName).toBe('Extrem');
  });

  it('carries the localized live category name for label fallback', () => {
    const rows = buildRows(activities, [{ id: '1', name: 'Nachtleben', slug: 'nightlife' }]);
    expect(rows.find((r) => r.slug === 'nightlife')?.liveName).toBe('Nachtleben');
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

describe('landing dictionary', () => {
  it('en and de carry the identical landing key structure', async () => {
    const en = (await import('../../../legacy-src/i18n/messages/en.json')).default as Record<
      string,
      unknown
    >;
    const de = (await import('../../../legacy-src/i18n/messages/de.json')).default as Record<
      string,
      unknown
    >;
    const keys = (node: unknown, prefix = ''): string[] =>
      node && typeof node === 'object'
        ? Object.entries(node as Record<string, unknown>).flatMap(([k, v]) =>
            keys(v, prefix ? `${prefix}.${k}` : k),
          )
        : [prefix];
    expect(keys(de.landing).sort()).toEqual(keys(en.landing).sort());
  });

  it('every landing row slug has a dictionary label in both locales', async () => {
    const { ROW_ORDER } = await import('../data');
    const en = (await import('../../../legacy-src/i18n/messages/en.json')).default as {
      landing: { rows: Record<string, string> };
    };
    const de = (await import('../../../legacy-src/i18n/messages/de.json')).default as {
      landing: { rows: Record<string, string> };
    };
    for (const slug of ROW_ORDER) {
      expect(en.landing.rows[slug], `en label for ${slug}`).toBeTruthy();
      expect(de.landing.rows[slug], `de label for ${slug}`).toBeTruthy();
    }
  });
});
