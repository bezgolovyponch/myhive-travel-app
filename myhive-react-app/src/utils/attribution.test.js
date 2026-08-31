import { captureFromUrl, getAttribution, getRef, MAX_AGE_MS, captureFirstTouch, getFirstTouch } from './attribution';

beforeEach(() => {
  localStorage.clear();
});

describe('captureFromUrl — UTM attribution', () => {
  test('stores utm params and referrer on first capture', () => {
    captureFromUrl('?utm_source=fb&utm_medium=paid_social&utm_campaign=test', 'https://google.com', 1000);

    expect(getAttribution(1000)).toEqual({
      utm_source: 'fb',
      utm_medium: 'paid_social',
      utm_campaign: 'test',
      referrer: 'https://google.com',
    });
  });

  test('a second capture with a new utm overwrites the first', () => {
    captureFromUrl('?utm_source=fb&utm_medium=paid_social&utm_campaign=summer', '', 1000);
    captureFromUrl('?utm_source=ig&utm_campaign=winter', '', 2000);

    const stored = getAttribution(2000);
    expect(stored.utm_source).toBe('ig');
    expect(stored.utm_campaign).toBe('winter');
    expect(stored.utm_medium).toBeUndefined();
    expect(stored.ts).toBeUndefined();
  });

  test('param-less call does NOT clear existing attribution (direct visit)', () => {
    captureFromUrl('?utm_source=fb&utm_campaign=test', 'https://fb.com', 1000);
    captureFromUrl('', '', 2000);

    const stored = getAttribution(2000);
    expect(stored.utm_source).toBe('fb');
    expect(stored.utm_campaign).toBe('test');
    expect(stored.ts).toBeUndefined();
  });

  test('only includes params that are actually present (no undefined keys)', () => {
    captureFromUrl('?utm_source=email', '', 1000);

    const stored = getAttribution(1000);
    expect(stored).toHaveProperty('utm_source', 'email');
    expect(stored).not.toHaveProperty('utm_medium');
    expect(stored).not.toHaveProperty('utm_campaign');
    expect(stored).not.toHaveProperty('utm_term');
    expect(stored).not.toHaveProperty('utm_content');
    expect(stored).not.toHaveProperty('gclid');
    expect(stored).not.toHaveProperty('fbclid');
  });

  test('ignores empty-string param values (e.g. "?utm_source=")', () => {
    captureFromUrl('?utm_source=', '', 1000);

    expect(getAttribution(1000)).toEqual({});
    expect(localStorage.getItem('myhive-attribution')).toBeNull();
  });

  test('gclid and fbclid are treated as attribution params', () => {
    captureFromUrl('?gclid=abc123', '', 1000);
    expect(getAttribution(1000)).toMatchObject({ gclid: 'abc123' });
    expect(getAttribution(1000)).not.toHaveProperty('ts');

    localStorage.clear();

    captureFromUrl('?fbclid=xyz789', '', 2000);
    expect(getAttribution(2000)).toMatchObject({ fbclid: 'xyz789' });
    expect(getAttribution(2000)).not.toHaveProperty('ts');
  });
});

describe('captureFromUrl — ref storage', () => {
  test('ref param is stored independently in myhive-ref', () => {
    captureFromUrl('?ref=invite', '', 1000);

    expect(getRef()).toBe('invite');
  });

  test('ref param alone does NOT create or alter attribution', () => {
    captureFromUrl('?ref=invite', '', 1000);

    expect(getAttribution(1000)).toEqual({});
    expect(localStorage.getItem('myhive-attribution')).toBeNull();
  });

  test('utm capture does NOT clear a previously stored ref', () => {
    captureFromUrl('?ref=invite', '', 500);
    captureFromUrl('?utm_source=ig', '', 1000);

    expect(getRef()).toBe('invite');
    expect(getAttribution(1000).utm_source).toBe('ig');
  });

  test('ref and utm can coexist in a single call', () => {
    captureFromUrl('?utm_source=fb&ref=partner', 'https://fb.com', 1000);

    expect(getRef()).toBe('partner');
    expect(getAttribution(1000)).toMatchObject({ utm_source: 'fb' });
    expect(getAttribution(1000)).not.toHaveProperty('ref');
    expect(getAttribution(1000)).not.toHaveProperty('ts');
  });

  test('getRef returns null when no ref was stored', () => {
    expect(getRef()).toBeNull();
  });
});

describe('getAttribution — TTL', () => {
  test('returns stored object when within 90-day TTL', () => {
    const t0 = 0;
    captureFromUrl('?utm_source=fb', '', t0);

    const justUnder = MAX_AGE_MS - 1;
    expect(getAttribution(t0 + justUnder)).toMatchObject({ utm_source: 'fb' });
    expect(localStorage.getItem('myhive-attribution')).not.toBeNull();
  });

  test('returns {} and removes the key when >= 90 days old', () => {
    const t0 = 1000;
    captureFromUrl('?utm_source=fb', '', t0);

    const expired = t0 + MAX_AGE_MS;
    expect(getAttribution(expired)).toEqual({});
    expect(localStorage.getItem('myhive-attribution')).toBeNull();
  });

  test('returns {} and removes the key when stored ts is missing/NaN', () => {
    localStorage.setItem('myhive-attribution', JSON.stringify({ utm_source: 'x' }));

    expect(getAttribution(1000)).toEqual({});
    expect(localStorage.getItem('myhive-attribution')).toBeNull();
  });

  test('returns {} when localStorage has no attribution', () => {
    expect(getAttribution(1000)).toEqual({});
  });
});

describe('idempotency', () => {
  test('calling captureFromUrl twice with the same URL produces the same state', () => {
    captureFromUrl('?utm_source=fb&utm_campaign=test', 'https://fb.com', 1000);
    captureFromUrl('?utm_source=fb&utm_campaign=test', 'https://fb.com', 1000);

    expect(getAttribution(1000)).toEqual({
      utm_source: 'fb',
      utm_campaign: 'test',
      referrer: 'https://fb.com',
    });
  });

  test('calling captureFromUrl with ?ref=invite twice leaves ref unchanged and attribution empty', () => {
    captureFromUrl('?ref=invite', '', 1000);
    captureFromUrl('?ref=invite', '', 1000);

    expect(getRef()).toBe('invite');
    expect(getAttribution(1000)).toEqual({});
  });
});

describe('first touch', () => {
  beforeEach(() => localStorage.clear());

  test('records the very first visit with its params', () => {
    captureFirstTouch('?utm_source=fb&utm_campaign=summer', 'https://facebook.com', 1000);
    expect(getFirstTouch()).toEqual({ ts: 1000, utm_source: 'fb', utm_campaign: 'summer', referrer: 'https://facebook.com' });
  });

  test('is never overwritten by later visits', () => {
    captureFirstTouch('', '', 1000);
    captureFirstTouch('?utm_source=ig&utm_campaign=late', 'https://instagram.com', 2000);
    expect(getFirstTouch().ts).toBe(1000);
    expect(getFirstTouch().utm_source).toBeUndefined();
  });
});
