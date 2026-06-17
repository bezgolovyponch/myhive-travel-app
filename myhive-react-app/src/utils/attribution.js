const ATTRIBUTION_KEY = 'myhive-attribution';
const REF_KEY = 'myhive-ref';
const ATTRIBUTION_PARAMS = ['utm_source', 'utm_medium', 'utm_campaign', 'utm_term', 'utm_content', 'gclid', 'fbclid'];

export const MAX_AGE_MS = 90 * 24 * 60 * 60 * 1000;

// Last-non-direct-click: overwrites stored attribution whenever at least one
// known attribution param is present in the URL. Param-less (direct) visits
// leave any existing attribution untouched. Empty-string values (e.g.
// "?utm_source=") are ignored — a malformed/empty tag is not a real signal.
// ref is stored independently and is never affected by utm logic.
export function captureFromUrl(search, referrer, nowMs = Date.now()) {
  const params = new URLSearchParams(search);

  try {
    // Handle ref independently — never clears attribution, never touched by utm.
    const ref = params.get('ref');
    if (ref) {
      localStorage.setItem(REF_KEY, ref);
    }

    // Collect only the attribution params that carry a non-empty value.
    const attribution = {};
    for (const key of ATTRIBUTION_PARAMS) {
      const value = params.get(key);
      if (value) {
        attribution[key] = value;
      }
    }

    // Only overwrite stored attribution when at least one param is present.
    if (Object.keys(attribution).length > 0) {
      localStorage.setItem(ATTRIBUTION_KEY, JSON.stringify({ ...attribution, referrer, ts: nowMs }));
    }
  } catch (_e) {
    // localStorage unavailable / quota exceeded — attribution is best-effort.
  }
}

// Returns the stored attribution object, or {} if absent, malformed, or expired
// (>= 90 days). Removes the key on malformed/expired data.
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

  if (!Number.isFinite(stored.ts) || nowMs - stored.ts >= MAX_AGE_MS) {
    localStorage.removeItem(ATTRIBUTION_KEY);
    return {};
  }

  return stored;
}

// Returns the stored ref value, or null if unset.
export function getRef() {
  return localStorage.getItem(REF_KEY);
}
