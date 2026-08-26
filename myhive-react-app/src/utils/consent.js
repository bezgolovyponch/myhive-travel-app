// signal: 'ad_storage' -> CookieScript 'targeting'; 'analytics_storage' -> 'performance'.
// Deny-by-default: returns false unless CookieScript explicitly reports the
// category granted. currentState().categories lists ONLY the agreed categories
// (see help.cookie-script.com, Custom Functions), so a simple includes() is the
// whole check; any missing API (blocked script, localhost, old cache) fails
// closed.
export function hasConsent(signal) {
  const category = signal === 'ad_storage' ? 'targeting' : 'performance';
  try {
    const instance = window.CookieScript && window.CookieScript.instance;
    const state = instance && typeof instance.currentState === 'function'
      ? instance.currentState()
      : null;
    return !!(state && Array.isArray(state.categories) && state.categories.includes(category));
  } catch (_e) {
    return false;
  }
}
