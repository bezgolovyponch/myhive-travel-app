import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';

// middleware.ts cannot be imported under vitest (next/server resolution) and
// Next requires `config.matcher` to be a static literal, so read the literal
// out of the source and evaluate it as a JS string, exactly as the compiler does.
const source = readFileSync(join(__dirname, '..', '..', 'middleware.ts'), 'utf8');
const literal = /matcher:\s*('(?:[^'\\]|\\.)*')/.exec(source)?.[1] ?? '';
const matcher: string = literal ? (0, eval)(literal) : '';
// Next wraps the matcher as an anchored regex; mirror that here.
const re = new RegExp('^' + matcher + '$');

describe('middleware matcher', () => {
  it('was found in middleware.ts', () => {
    expect(matcher).not.toBe('');
  });
  it('matches public page URLs', () => {
    for (const p of ['/', '/blog/x', '/de/jga-prag', '/prague-stag-do/activities/x']) {
      expect(re.test(p), p).toBe(true);
    }
  });
  it('skips api, Next internals and files with an extension', () => {
    for (const p of ['/favicon.ico', '/api/x', '/_next/static/a.js']) {
      expect(re.test(p), p).toBe(false);
    }
  });
});
