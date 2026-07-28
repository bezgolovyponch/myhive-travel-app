// Unsubmitted trip-setup values (travelers/dates). Survives close/reopen so a
// user who dismisses the modal never re-types what they already entered.
const SETUP_DRAFT_KEY = 'myhive-setup-draft';

export function readSetupDraft() {
  try {
    const raw = localStorage.getItem(SETUP_DRAFT_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch (e) {
    return null;
  }
}

export function writeSetupDraft(draft) {
  try {
    localStorage.setItem(SETUP_DRAFT_KEY, JSON.stringify(draft));
  } catch (e) {
    // Blocked storage must never break the modal.
  }
}

export function clearSetupDraft() {
  try {
    localStorage.removeItem(SETUP_DRAFT_KEY);
  } catch (e) {
    // Same rationale as writeSetupDraft.
  }
}
