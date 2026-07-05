import { generateUuid } from './uuid';

const STORAGE_KEY = 'myhive.voterToken';

export function getOrCreateVoterToken() {
  let token = localStorage.getItem(STORAGE_KEY);
  if (!token) {
    token = generateUuid();
    localStorage.setItem(STORAGE_KEY, token);
  }
  return token;
}

// localStorage key recording that this browser already voted in a given
// session, used to short-circuit/redirect repeat visits to the same link.
export function votedKey(shareToken) {
  return `myhive-voted-${shareToken}`;
}
