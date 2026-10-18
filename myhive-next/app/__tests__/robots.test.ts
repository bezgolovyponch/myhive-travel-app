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
    process.env.ALLOW_INDEXING = 'true';

    const result = robots();

    expect(result.rules).toEqual([
      { userAgent: '*', disallow: EXPECTED_SERVICE_PATHS },
      { userAgent: ['Bytespider'], disallow: '/' },
    ]);
    expect(result.sitemap).toMatch(/\/sitemap\.xml$/);
  });

  it('robots_disallowsAllWithoutFlag', () => {
    const expectedResult = { rules: { userAgent: '*', disallow: '/' } };
    delete process.env.ALLOW_INDEXING;

    expect(robots()).toEqual(expectedResult);
  });
});
