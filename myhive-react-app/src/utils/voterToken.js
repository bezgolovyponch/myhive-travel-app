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
