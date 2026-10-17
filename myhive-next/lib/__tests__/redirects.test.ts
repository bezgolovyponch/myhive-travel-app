import { describe, expect, it } from 'vitest';
import { legacyUrlRedirects } from '../redirects';

const OLD_POST = 'how-to-plan-a-stress-free-group-trip';
const NEW_POST = 'how-to-plan-a-stag-do';

function permanent(source: string, destination: string) {
  return { source, destination, permanent: true };
}

describe('legacyUrlRedirects', () => {
  it('lists exactly the 15 single-hop permanent rules', () => {
    expect(legacyUrlRedirects()).toEqual([
      // English (bare)
      permanent('/destination/prague', '/prague-stag-do'),
      permanent('/destination/prague/activity/:slug', '/prague-stag-do/activities/:slug'),
      permanent('/destination/prague/package/:slug', '/prague-stag-do/packages/:slug'),
      // German
      permanent('/de/destination/prague', '/de/jga-prag'),
      permanent('/de/destination/prague/activity/:slug', '/de/jga-prag/aktivitaeten/:slug'),
      permanent('/de/destination/prague/package/:slug', '/de/jga-prag/pakete/:slug'),
      // Blog, English then German
      permanent('/blog', '/ideas'),
      permanent(`/blog/${OLD_POST}`, `/ideas/${NEW_POST}`),
      permanent('/de/blog', '/de/ratgeber'),
      permanent(`/de/blog/${OLD_POST}`, `/de/ratgeber/${NEW_POST}`),
      // Explicit /en/ prefix goes straight to the bare English URL
      permanent('/en/destination/prague', '/prague-stag-do'),
      permanent('/en/destination/prague/activity/:slug', '/prague-stag-do/activities/:slug'),
      permanent('/en/destination/prague/package/:slug', '/prague-stag-do/packages/:slug'),
      permanent('/en/blog', '/ideas'),
      permanent(`/en/blog/${OLD_POST}`, `/ideas/${NEW_POST}`),
    ]);
  });

  it('has no generic blog slug rule (it would swallow the 410s)', () => {
    const sources = legacyUrlRedirects().map((r) => r.source);
    expect(sources.some((s) => s.includes('/blog/:'))).toBe(false);
  });
});
