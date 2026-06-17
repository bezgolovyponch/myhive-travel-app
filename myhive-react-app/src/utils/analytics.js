import { generateUuid } from './uuid';

// Stateless. trip_id / user_role are passed explicitly by the caller from the
// source of truth (TripContext, the route shareToken, or resolveUserRole) — no
// module state that could leak across role switches in one tab.
export function pushEvent(event, params = {}) {
  window.dataLayer = window.dataLayer || [];
  const payload = { event, event_id: generateUuid() };
  for (const [key, value] of Object.entries(params)) {
    // Strict !== — no coercion, so false and 0 survive; only the three
    // "no value" sentinels are dropped.
    if (value !== undefined && value !== null && value !== '') {
      payload[key] = value;
    }
  }
  window.dataLayer.push(payload);
}
