// Minimal cookie reader — used for Meta's _fbp/_fbc first-party cookies (set by
// the GTM-loaded Pixel). No cookie writing here.
export function getCookie(name) {
  try {
    const match = document.cookie
      .split('; ')
      .find((row) => row.startsWith(name + '='));
    return match ? decodeURIComponent(match.slice(name.length + 1)) : null;
  } catch (_e) {
    return null;
  }
}
