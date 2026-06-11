import { getDefaultDestination } from './defaultDestination';

test('prefers the configured default slug regardless of API order', () => {
  const budapest = { id: 'd2', slug: 'budapest', name: 'Budapest' };
  const prague = { id: 'd1', slug: 'prague', name: 'Prague' };

  expect(getDefaultDestination([budapest, prague])).toBe(prague);
});

test('falls back to the first destination when the default slug is missing', () => {
  const budapest = { id: 'd2', slug: 'budapest', name: 'Budapest' };
  const vienna = { id: 'd3', slug: 'vienna', name: 'Vienna' };

  expect(getDefaultDestination([budapest, vienna])).toBe(budapest);
});

test('returns null when no destinations are loaded', () => {
  expect(getDefaultDestination([])).toBeNull();
});
