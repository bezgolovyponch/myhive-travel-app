import { API_BASE_URL } from './config';
import { parseApiError } from './httpError';

// Public AI planner client (contract: docs/api/ai-planner-api.md). The admin
// console has its own copy in adminApi.js because it sends a staff token.
// Errors carry `status` and `body.error` (SESSION_NOT_FOUND, LLM_TIMEOUT, ...)
// so callers can branch on the code rather than the message.

// An edit turn can call the model twice at 20 s each; the contract asks for >= 45 s.
const MESSAGE_TIMEOUT_MS = 45000;

const JSON_HEADERS = { 'Content-Type': 'application/json' };

async function request(path, { method = 'GET', body, timeoutMs } = {}, fallbackMessage) {
  const controller = timeoutMs ? new AbortController() : null;
  const timer = controller ? setTimeout(() => controller.abort(), timeoutMs) : null;
  try {
    const response = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers: body === undefined ? undefined : JSON_HEADERS,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller?.signal,
    });
    if (!response.ok) {
      throw await parseApiError(response, fallbackMessage);
    }
    return response.json();
  } catch (e) {
    if (e.name === 'AbortError') {
      const err = new Error(fallbackMessage);
      err.status = 504;
      err.body = { error: 'LLM_TIMEOUT' };
      throw err;
    }
    throw e;
  } finally {
    if (timer) clearTimeout(timer);
  }
}

const aiPlannerApi = {
  // preset: {days, groupSize, arrival, departure, initialMessage}, each optional.
  createSession(destinationSlug, locale, preset = {}) {
    return request('/ai/sessions', {
      method: 'POST',
      body: { destinationSlug, locale, ...preset },
      timeoutMs: preset.initialMessage ? MESSAGE_TIMEOUT_MS : undefined,
    }, 'Failed to start a planner chat');
  },

  getSession(token) {
    return request(`/ai/sessions/${encodeURIComponent(token)}`, {}, 'Failed to load the planner chat');
  },

  sendMessage(token, content) {
    return request(`/ai/sessions/${encodeURIComponent(token)}/messages`, {
      method: 'POST',
      body: { content },
      timeoutMs: MESSAGE_TIMEOUT_MS,
    }, 'The planner did not answer');
  },

  // One tap in the trip draft: {op: 'ADD'|'REMOVE', activityId, packageKey}.
  // No model call, so no long timeout; the answer is a turn body like sendMessage's.
  editDraft(token, edit) {
    return request(`/ai/sessions/${encodeURIComponent(token)}/edits`, {
      method: 'POST',
      body: edit,
    }, 'Failed to change the trip draft');
  },

  getGeneration(id) {
    return request(`/ai/generations/${encodeURIComponent(id)}`, {}, 'Failed to load the packages');
  },

  selectPackage(id, packageKey) {
    return request(`/ai/generations/${encodeURIComponent(id)}/select`, {
      method: 'POST',
      body: { packageKey },
    }, 'Failed to select the package');
  },
};

export default aiPlannerApi;
