// One error shape for both API layers (api.js and adminApi.js): prefer the
// backend's message when there is one, always attach status and parsed body.
export async function parseApiError(response, fallbackMessage) {
    let body = null;
    const contentType = response.headers.get('content-type') || '';
    if (contentType.includes('application/json')) {
        try {
            body = await response.json();
        } catch (e) {
            body = null; // non-JSON error page — keep the fallback message
        }
    }
    const err = new Error((body && body.message) || fallbackMessage);
    err.status = response.status;
    err.body = body;
    return err;
}
