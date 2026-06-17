import { resolveUserRole } from './userRole';

describe('resolveUserRole', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  test('returns organizer when shareToken is falsy', () => {
    expect(resolveUserRole(null)).toBe('organizer');
    expect(resolveUserRole(undefined)).toBe('organizer');
    expect(resolveUserRole('')).toBe('organizer');
  });

  test('returns organizer when myhive-initiator-{token} is set in localStorage', () => {
    const token = 'abc123';
    localStorage.setItem(`myhive-initiator-${token}`, '1');
    expect(resolveUserRole(token)).toBe('organizer');
  });

  test('returns organizer when myhive-manager-{token} is set in localStorage', () => {
    const token = 'abc123';
    localStorage.setItem(`myhive-manager-${token}`, '1');
    expect(resolveUserRole(token)).toBe('organizer');
  });

  test('returns participant when neither initiator nor manager key is set', () => {
    expect(resolveUserRole('abc123')).toBe('participant');
  });
});
