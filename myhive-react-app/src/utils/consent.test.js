import { hasConsent } from './consent';

describe('hasConsent', () => {
  afterEach(() => {
    delete window.getCkyConsent;
  });

  test('returns false for both signals when getCkyConsent is not defined', () => {
    expect(hasConsent('ad_storage')).toBe(false);
    expect(hasConsent('analytics_storage')).toBe(false);
  });

  test('returns true for ad_storage when advertisement category is granted', () => {
    window.getCkyConsent = () => ({ categories: { advertisement: true } });
    expect(hasConsent('ad_storage')).toBe(true);
    expect(hasConsent('analytics_storage')).toBe(false);
  });

  test('returns true for analytics_storage when analytics category is granted', () => {
    window.getCkyConsent = () => ({ categories: { analytics: true } });
    expect(hasConsent('analytics_storage')).toBe(true);
    expect(hasConsent('ad_storage')).toBe(false);
  });

  test('returns false when getCkyConsent throws', () => {
    window.getCkyConsent = () => {
      throw new Error('CookieYes not ready');
    };
    expect(hasConsent('ad_storage')).toBe(false);
    expect(hasConsent('analytics_storage')).toBe(false);
  });
});
