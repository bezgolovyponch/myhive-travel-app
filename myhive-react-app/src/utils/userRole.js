export function resolveUserRole(shareToken) {
  if (!shareToken) {
    return 'organizer';
  }
  try {
    if (localStorage.getItem(`myhive-initiator-${shareToken}`) ||
        localStorage.getItem(`myhive-manager-${shareToken}`)) {
      return 'organizer';
    }
  } catch (_e) {
    return 'participant';
  }
  return 'participant';
}
