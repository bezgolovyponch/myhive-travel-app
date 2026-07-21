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
  sessionStorage.setItem(QUIZ_FLOW_KEY, JSON.stringify(context));
}

export function clearQuizFlow() {
  sessionStorage.removeItem(QUIZ_FLOW_KEY);
}
