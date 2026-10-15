import { describe, expect, it } from 'vitest';
import { GONE_BLOG_SLUGS, isGone } from '../gone';

describe('isGone', () => {
  it('marks the five retired seed posts in both locales, trailing slash tolerated', () => {
    expect(GONE_BLOG_SLUGS).toHaveLength(5);
    expect(isGone('/blog/bali-on-a-budget-a-group-travel-guide')).toBe(true);
    expect(isGone('/de/blog/bali-on-a-budget-a-group-travel-guide/')).toBe(true);
  });
  it('leaves everything else alone', () => {
    expect(isGone('/blog')).toBe(false);
    expect(isGone('/blog/how-to-plan-a-stress-free-group-trip')).toBe(false);
    expect(isGone('/ideas/bali-on-a-budget-a-group-travel-guide')).toBe(false);
  });
});
