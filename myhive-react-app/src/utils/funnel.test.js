import { nightsBetween, funnelParams } from './funnel';

beforeEach(() => localStorage.clear());

test('nightsBetween counts nights, not days', () => {
  expect(nightsBetween('2026-09-04', '2026-09-06')).toBe(2);
  expect(nightsBetween('2026-09-04', '2026-09-04')).toBe(0);
});

test('nightsBetween is undefined on missing/invalid dates', () => {
  expect(nightsBetween('', '2026-09-06')).toBeUndefined();
  expect(nightsBetween('nope', '2026-09-06')).toBeUndefined();
});

test('funnelParams maps fields and pulls source_campaign from attribution', () => {
  localStorage.setItem('myhive-attribution', JSON.stringify({ utm_campaign: 'summer', ts: Date.now() }));
  const p = funnelParams({ startDate: '2026-09-04', endDate: '2026-09-06', groupSize: 8, activitiesCount: 3, voteId: 'tok-1' });
  expect(p).toEqual({ nights: 2, group_size: 8, activities_count: 3, vote_id: 'tok-1', source_campaign: 'summer' });
});

test('funnelParams leaves unknown fields undefined', () => {
  const p = funnelParams({});
  expect(p.nights).toBeUndefined();
  expect(p.vote_id).toBeUndefined();
  expect(p.source_campaign).toBeUndefined();
});
