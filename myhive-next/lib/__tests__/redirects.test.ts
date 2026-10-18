import { describe, expect, it } from 'vitest';
import { loadRedirectMap } from '../redirect-map';
import { legacyUrlRedirects } from '../redirects';

function permanent(source: string, destination: string) {
  return { source, destination, permanent: true };
}

describe('legacyUrlRedirects', () => {
  it('legacyUrlRedirects_has325RulesInGroupOrder', () => {
    const rules = legacyUrlRedirects();

    expect(rules).toHaveLength(325);
    expect(rules[0]).toEqual(permanent('/destination/prague', '/prague-stag-do'));
    // 225 exact rules, then the activity pattern, then 5 more patterns, then 94 guards.
    expect(rules[225]).toEqual(permanent('/destination/prague/activity/:slug', '/prague-stag-do/activities/:slug'));
    expect(rules.slice(225, 231).every((r) => r.source.includes(':slug'))).toBe(true);
    expect(rules.slice(0, 225).some((r) => r.source.includes(':slug'))).toBe(false);
    const guards = rules.slice(231);
    expect(guards).toHaveLength(94);
    expect(guards.every((r) => r.source.includes('/activities/') || r.source.includes('/aktivitaeten/'))).toBe(true);
    expect(rules.every((r) => r.permanent)).toBe(true);
  });

  it('legacyUrlRedirects_noDuplicateSources', () => {
    const sources = legacyUrlRedirects().map((r) => r.source);

    expect(new Set(sources).size).toBe(sources.length);
  });

  it('legacyUrlRedirects_spotRows', () => {
    const rules = legacyUrlRedirects();

    expect(rules).toContainEqual(
      permanent('/de/destination/prague/activity/ak-47-glock-17-shooting', '/de/jga-prag/aktivitaeten/kalaschnikow-schiessen'),
    );
    expect(rules).toContainEqual(permanent('/en/blog', '/ideas'));
    expect(rules).toContainEqual(
      permanent('/de/blog/how-to-plan-a-stress-free-group-trip', '/de/ratgeber/jga-planen'),
    );
  });

  it('legacyUrlRedirects_skips410Rows', () => {
    const sources = legacyUrlRedirects().map((r) => r.source);

    expect(sources.some((s) => s.includes('top-5-group-travel-destinations-for-2026'))).toBe(false);
  });

  it('legacyUrlRedirects_guardSourcesAreNeverLivePages', () => {
    const live = new Set(loadRedirectMap().flatMap((row) => [row.newEn, row.newDe]));
    const guards = legacyUrlRedirects().slice(231);

    expect(guards.filter((g) => live.has(g.source))).toEqual([]);
  });

  it('legacyUrlRedirects_landingRowProducesNoRule', () => {
    const sources = legacyUrlRedirects().map((r) => r.source);

    expect(sources.some((s) => s.includes('/landing/'))).toBe(false);
  });

  it('has no generic blog slug rule (it would swallow the 410s)', () => {
    const sources = legacyUrlRedirects().map((r) => r.source);

    expect(sources.some((s) => s.includes('/blog/:'))).toBe(false);
  });
});
