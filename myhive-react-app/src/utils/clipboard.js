// Safe clipboard write: resolves false instead of throwing when the API is
// unavailable (insecure context) or the write is rejected.
export async function copyToClipboard(text) {
    if (!navigator.clipboard) {
        return false;
    }
    try {
        await navigator.clipboard.writeText(text);
        return true;
    } catch (e) {
        return false; // permission denied / insecure context — caller keeps a manual fallback
    }
}
