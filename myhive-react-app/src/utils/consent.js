// signal: 'ad_storage' -> CookieYes 'advertisement'; 'analytics_storage' -> 'analytics'.
// Deny-by-default: returns false unless CookieYes explicitly reports the category granted.
//
// IMPORTANT: The exact CookieYes accessor (getCkyConsent) must be validated against the
// live CookieYes integration during GTM/dashboard setup. Until then this is deny-by-default,
// which is the safe failure mode.
export function hasConsent(signal) {
  const category = signal === 'ad_storage' ? 'advertisement' : 'analytics';
  try {
    const consent = typeof window.getCkyConsent === 'function' ? window.getCkyConsent() : null;
    return !!(consent && consent.categories && consent.categories[category] === true);
  } catch (_e) {
    return false;
  }
}
