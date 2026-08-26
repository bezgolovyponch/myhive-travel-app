import { hasConsent } from './consent';

// CookieScript's currentState() lists ONLY the categories the visitor agreed
// to: 'performance' maps to analytics_storage, 'targeting' to ad_storage.
function stubConsent(categories) {
  window.CookieScript = { instance: { currentState: () => ({ action: 'accept', categories }) } };
}

describe('hasConsent', () => {
  afterEach(() => {
    delete window.CookieScript;
  });

  test('returns false for both signals when CookieScript is not loaded', () => {
    expect(hasConsent('ad_storage')).toBe(false);
    expect(hasConsent('analytics_storage')).toBe(false);
  });

  test('returns true for ad_storage when the targeting category is granted', () => {
    stubConsent(['strict', 'targeting']);
    expect(hasConsent('ad_storage')).toBe(true);
    expect(hasConsent('analytics_storage')).toBe(false);
  });

  test('returns true for analytics_storage when the performance category is granted', () => {
    stubConsent(['strict', 'performance']);
    expect(hasConsent('analytics_storage')).toBe(true);
    expect(hasConsent('ad_storage')).toBe(false);
  });

  test('returns false for both when the visitor rejected all', () => {
    stubConsent(['strict']);
    expect(hasConsent('ad_storage')).toBe(false);
    expect(hasConsent('analytics_storage')).toBe(false);
  });

  test('returns false when currentState throws', () => {
    window.CookieScript = {
      instance: {
        currentState: () => {
          throw new Error('CookieScript not ready');
        },
      },
    };
    expect(hasConsent('ad_storage')).toBe(false);
    expect(hasConsent('analytics_storage')).toBe(false);
  });
});
