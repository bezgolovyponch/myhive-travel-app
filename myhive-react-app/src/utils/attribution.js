const ATTRIBUTION_KEY = 'myhive-attribution';
const REF_KEY = 'myhive-ref';
const ATTRIBUTION_PARAMS = ['utm_source', 'utm_medium', 'utm_campaign', 'utm_term', 'utm_content', 'gclid', 'fbclid'];

export const MAX_AGE_MS = 90 * 24 * 60 * 60 * 1000;

// Last-non-direct-click: overwrites stored attribution whenever at least one
// known attribution param is present in the URL. Param-less (direct) visits
// leave any existing attribution untouched.
// ref is stored independently and is never affected by utm logic.
export function captureFromUrl(search, referrer, nowMs = Date.now()) {
  const params = new URLSearchParams(search);

  // Handle ref independently — never clears attribution, never touched by utm.
  const ref = params.get('ref');
  if (ref !== null) {
    localStorage.setItem(REF_KEY, ref);
  }

  // Collect only the attribution params that are actually present.
  const attribution = {};
  for (const key of ATTRIBUTION_PARAMS) {
    const value = params.get(key);
    if (value !== null) {
      attribution[key] = value;
    }
  }

  // Only overwrite stored attribution when at least one param is present.
  if (Object.keys(attribution).length > 0) {
    localStorage.setItem(ATTRIBUTION_KEY, JSON.stringify({ ...attribution, referrer, ts: nowMs }));
  }
}

// Returns the stored attribution object, or {} if absent or expired (>= 90 days).
// Removes the key on expiry.
export function getAttribution(nowMs = Date.now()) {
  const raw = localStorage.getItem(ATTRIBUTION_KEY);
  if (raw === null) {
    return {};
  }

  let stored;
  try {
    stored = JSON.parse(raw);
  } catch (_e) {
    localStorage.removeItem(ATTRIBUTION_KEY);
    return {};
  }

  if (nowMs - stored.ts >= MAX_AGE_MS) {
    localStorage.removeItem(ATTRIBUTION_KEY);
    return {};
  }

  return stored;
}

// Returns the stored ref value, or null/undefined if unset.
export function getRef() {
  return localStorage.getItem(REF_KEY);
}
