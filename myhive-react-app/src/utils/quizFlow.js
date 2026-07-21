// Per-tab handoff between the organizer quiz flow (CuratePage) and the Trip
// Builder: {setup, responses} survives a refresh but not a new tab.
const QUIZ_FLOW_KEY = 'myhive-quiz-flow';

export function readQuizFlow() {
  try {
    const raw = sessionStorage.getItem(QUIZ_FLOW_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch (e) {
    // Malformed storage — treat as absent rather than crash the Trip Builder.
    return null;
  }
}

export function writeQuizFlow(context) {
  try {
    sessionStorage.setItem(QUIZ_FLOW_KEY, JSON.stringify(context));
  } catch (e) {
    // A blocked sessionStorage (quota exceeded, private-mode restrictions,
    // storage disabled, etc.) must not crash the handoff effect — there is no
    // app error boundary here, so the organizer would otherwise see a blank
    // screen. Swallow it: they just land in a plain non-quiz Trip Builder.
  }
}

export function clearQuizFlow() {
  try {
    sessionStorage.removeItem(QUIZ_FLOW_KEY);
  } catch (e) {
    // Same rationale as writeQuizFlow above — never let a storage failure
    // propagate out of this handoff util.
  }
}
