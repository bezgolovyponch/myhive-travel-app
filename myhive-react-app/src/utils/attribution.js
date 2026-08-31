const ATTRIBUTION_KEY = 'myhive-attribution';
const REF_KEY = 'myhive-ref';
const FIRST_TOUCH_KEY = 'myhive-first-touch';
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

// Returns the stored attribution object (without ts), or {} if absent, malformed,
// or expired (>= 90 days). Removes the key on malformed/expired data.
export function getAttribution(nowMs = Date.now()) {
  try {
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
    const { ts, ...attribution } = stored;
    return attribution;
  } catch (_e) {
    return {};
  }
}

// Returns the stored ref value, or null if unset.
export function getRef() {
  try {
    return localStorage.getItem(REF_KEY);
  } catch (_e) {
    return null;
  }
}

// First-touch: written exactly once — the first visit this browser ever makes —
// param-less direct visits included (the date itself is the point).
export function captureFirstTouch(search, referrer, nowMs = Date.now()) {
  try {
    if (localStorage.getItem(FIRST_TOUCH_KEY) !== null) {
      return;
    }
    const params = new URLSearchParams(search);
    const record = { ts: nowMs };
    const source = params.get('utm_source');
    const campaign = params.get('utm_campaign');
    if (source) record.utm_source = source;
    if (campaign) record.utm_campaign = campaign;
    if (referrer) record.referrer = referrer;
    localStorage.setItem(FIRST_TOUCH_KEY, JSON.stringify(record));
  } catch (_e) {
    // localStorage unavailable / quota exceeded — first touch is best-effort.
  }
}

// Returns the stored first-touch record, or null if unset/malformed.
export function getFirstTouch() {
  try {
    const raw = localStorage.getItem(FIRST_TOUCH_KEY);
    if (raw === null) return null;
    const parsed = JSON.parse(raw);
    return Number.isFinite(parsed.ts) ? parsed : null;
  } catch (_e) {
    return null;
  }
}
