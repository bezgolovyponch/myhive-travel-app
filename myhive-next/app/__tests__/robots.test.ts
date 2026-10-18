import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import robots from '../robots';

const EXPECTED_SERVICE_PATHS = ['/admin', '/de/admin', '/vote', '/de/vote', '/payment', '/de/payment'];

describe('robots', () => {
  let originalAllowIndexing: string | undefined;

  beforeEach(() => {
    originalAllowIndexing = process.env.ALLOW_INDEXING;
  });

  afterEach(() => {
    if (originalAllowIndexing === undefined) {
      delete process.env.ALLOW_INDEXING;
    } else {
      process.env.ALLOW_INDEXING = originalAllowIndexing;
    }
  });

  it('robots_blocksOnlyBytespiderWhenIndexingAllowed', () => {
    const expectedBlockedRule = { userAgent: ['Bytespider'], disallow: '/' };
    process.env.ALLOW_INDEXING = 'true';

    const result = robots();
    const rules = Array.isArray(result.rules) ? result.rules : [result.rules];
    const wildcardRule = rules.find((rule) => rule.userAgent === '*');

    expect(rules).toContainEqual(expectedBlockedRule);
    expect(JSON.stringify(result)).not.toContain('GPTBot');
    expect(result.sitemap).toMatch(/\/sitemap\.xml$/);
    expect(wildcardRule?.disallow).toEqual(EXPECTED_SERVICE_PATHS);
  });

  it('robots_disallowsAllWithoutFlag', () => {
    const expectedResult = { rules: { userAgent: '*', disallow: '/' } };
    delete process.env.ALLOW_INDEXING;

    expect(robots()).toEqual(expectedResult);
  });
});
