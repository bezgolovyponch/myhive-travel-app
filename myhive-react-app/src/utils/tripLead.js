// The active reminder lead this browser is syncing to: {id, restoreToken}.
// localStorage (not sessionStorage) so the sync continues in later Trip Builder visits.
const TRIP_LEAD_KEY = 'myhive-trip-lead';

export function readTripLead() {
  try {
    const raw = localStorage.getItem(TRIP_LEAD_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch (e) {
    // Malformed/blocked storage — behave as if no lead is being tracked.
    return null;
  }
}

export function writeTripLead(lead) {
  try {
    localStorage.setItem(TRIP_LEAD_KEY, JSON.stringify(lead));
  } catch (e) {
    // Blocked storage must never break the flow that captured the lead.
  }
}

export function clearTripLead() {
  try {
    localStorage.removeItem(TRIP_LEAD_KEY);
  } catch (e) {
    // Same rationale as writeTripLead.
  }
}
