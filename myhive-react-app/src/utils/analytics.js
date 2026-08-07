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

// Leaving the document in the same tick as a pushEvent can destroy the queued
// event before GTM's asynchronous container dispatches it. In the SPA these CTAs
// opened a modal or navigated client-side, so the document survived; a
// server-rendered page has to do a real navigation. Yield briefly first,
// bounded so navigation can never hang on analytics.
// Mitigation, not a guarantee: the airtight version is GTM's eventCallback on
// the event itself, which needs the container's tag configuration to confirm.
const NAVIGATION_FLUSH_MS = 250;

export function navigateAfterEvents(href) {
  setTimeout(() => {
    window.location.assign(href);
  }, NAVIGATION_FLUSH_MS);
}
