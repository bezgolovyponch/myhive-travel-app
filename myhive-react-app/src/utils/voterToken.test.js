import { getOrCreateVoterToken } from './voterToken';

describe('getOrCreateVoterToken', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  test('creates and persists a UUID on first call', () => {
    const token = getOrCreateVoterToken();
    expect(token).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i);
    expect(localStorage.getItem('myhive.voterToken')).toBe(token);
  });

  test('returns the same token on subsequent calls', () => {
    const first = getOrCreateVoterToken();
    const second = getOrCreateVoterToken();
    expect(second).toBe(first);
  });

  test('reuses an existing localStorage value', () => {
    const existing = '11111111-1111-4111-8111-111111111111';
    localStorage.setItem('myhive.voterToken', existing);
    expect(getOrCreateVoterToken()).toBe(existing);
  });
});
