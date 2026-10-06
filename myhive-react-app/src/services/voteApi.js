import { API_BASE_URL } from './config';
import { localeField, withLocaleParam } from '../i18n/routes';

const voteApi = {
  // Public quiz fetch (organizer pre-session, by destinationId)
  async getPublicQuizForDestination(destinationId) {
    const response = await fetch(withLocaleParam(`${API_BASE_URL}/vote/destinations/${destinationId}/quiz`));
    if (response.status === 404) return { questions: [] };
    if (!response.ok) throw new Error('Failed to fetch quiz');
    return response.json();
  },

  // Build the pool (stateless, pre-session)
  async buildPool({ destinationId, responses }) {
    const response = await fetch(withLocaleParam(`${API_BASE_URL}/vote/pool`), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ destinationId, responses }),
    });
    if (!response.ok) throw new Error('Failed to build pool');
    return response.json();
  },

  // Atomic session creation
  // shareToken: the link token the browser picked, so the group's WhatsApp
  // message can carry the link before this request returns.
  async createSession({ destinationId, initiatorEmail, initiatorPhone, shareToken, numberOfTravelers, startDate,
                        endDate, budget, voterToken, quizResponses, activityIds }) {
    const response = await fetch(`${API_BASE_URL}/vote/sessions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        destinationId, initiatorEmail, initiatorPhone, shareToken, numberOfTravelers, startDate, endDate,
        budget, voterToken, quizResponses, activityIds,
        // Language of the organizer's emails (vote created / result) and their links.
        ...localeField(),
      }),
    });
    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      throw new Error(body.message || 'Failed to create vote session');
    }
    return response.json();
  },

  async getSession(shareToken) {
    const response = await fetch(withLocaleParam(`${API_BASE_URL}/vote/sessions/${encodeURIComponent(shareToken)}`));
    if (!response.ok) throw new Error('Failed to fetch vote session');
    return response.json();
  },

  // Curated voting list (same URL as before — backend now serves curated for new sessions)
  async getActivities(shareToken) {
    const response = await fetch(withLocaleParam(`${API_BASE_URL}/vote/sessions/${encodeURIComponent(shareToken)}/activities`));
    if (response.status === 404) throw new Error('Vote session not found');
    if (!response.ok) throw new Error('Failed to fetch vote activities');
    return response.json();
  },

  // Participant quiz
  async getParticipantQuiz(shareToken) {
    const response = await fetch(withLocaleParam(`${API_BASE_URL}/vote/sessions/${encodeURIComponent(shareToken)}/quiz`));
    if (!response.ok) throw new Error('Failed to fetch participant quiz');
    return response.json();
  },

  async submitParticipantQuiz(shareToken, { voterToken, responses }) {
    const response = await fetch(`${API_BASE_URL}/vote/sessions/${encodeURIComponent(shareToken)}/quiz`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ voterToken, responses }),
    });
    if (response.status === 409) throw new Error('Quiz already submitted');
    if (!response.ok) throw new Error('Failed to submit quiz');
  },

  async castVote(shareToken, { voterToken, activityId, liked }) {
    const response = await fetch(`${API_BASE_URL}/vote/sessions/${encodeURIComponent(shareToken)}/votes`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ voterToken, activityId, liked }),
    });
    if (response.status === 409) throw new Error('Session is full');
    if (!response.ok) throw new Error('Failed to cast vote');
  },

  // One ballot per friend: the votes and the recommendations go together, once.
  async castVotes(shareToken, { voterToken, votes, recommendedActivityIds = [] }) {
    const response = await fetch(`${API_BASE_URL}/vote/sessions/${encodeURIComponent(shareToken)}/votes/batch`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ voterToken, votes, recommendedActivityIds }),
    });
    if (response.status === 409) {
      const body = await response.json().catch(() => ({}));
      throw new Error(body.message === 'You have already voted' ? 'Already voted' : 'Session is full');
    }
    if (!response.ok) throw new Error('Failed to cast votes');
  },

  async closeSession(shareToken, managerToken) {
    const base = `${API_BASE_URL}/vote/sessions/${encodeURIComponent(shareToken)}/close`;
    // managerToken arrives via a shared URL's query param — encode it so it
    // cannot inject extra query parameters into the request.
    const url = managerToken
        ? `${base}?managerToken=${encodeURIComponent(managerToken)}`
        : base;
    const response = await fetch(url, { method: 'POST' });
    if (!response.ok && response.status !== 400) throw new Error('Failed to close session');
  },

  async getResult(shareToken) {
    const response = await fetch(withLocaleParam(`${API_BASE_URL}/vote/sessions/${encodeURIComponent(shareToken)}/result`));
    if (response.status === 404) throw new Error('Vote session not found');
    if (response.status === 409) throw new Error('Result not available yet');
    if (!response.ok) throw new Error('Failed to fetch vote result');
    return response.json();
  },

  // Cart-seeded session creation (no quiz) — the ballot is the initiator's cart.
  async createCartSession({ destinationId, initiatorEmail, initiatorPhone, shareToken, numberOfTravelers,
                            startDate, endDate, activityIds }) {
    const response = await fetch(`${API_BASE_URL}/vote/sessions/cart`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        destinationId, initiatorEmail, initiatorPhone, shareToken, numberOfTravelers, startDate, endDate,
        activityIds,
        ...localeField(),
      }),
    });
    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      throw new Error(body.message || 'Failed to create vote session');
    }
    return response.json();
  },

  // Live tally (CART sessions): the organiser's dashboard only — it needs the managerToken.
  async getTally(shareToken, { managerToken } = {}) {
    const params = new URLSearchParams();
    if (managerToken) {
      params.set('managerToken', managerToken);
    }
    const response = await fetch(
        withLocaleParam(`${API_BASE_URL}/vote/sessions/${encodeURIComponent(shareToken)}/tally?${params}`));
    if (response.status === 403) throw new Error('Only the organiser sees the live tally');
    if (!response.ok) throw new Error('Failed to fetch tally');
    return response.json();
  },

  // The organiser's other contact (email or WhatsApp number), added to the same vote.
  async updateContact(shareToken, managerToken, { initiatorEmail, initiatorPhone }) {
    const response = await fetch(managerUrl(shareToken, '/contact', managerToken), {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ initiatorEmail, initiatorPhone }),
    });
    if (!response.ok) throw new Error('Failed to save the contact');
  },

  // The organiser drops an activity from the running vote / brings it back / adds one.
  async excludeActivity(shareToken, managerToken, activityId) {
    const response = await fetch(
        managerUrl(shareToken, `/activities/${encodeURIComponent(activityId)}/exclude`, managerToken),
        { method: 'POST' });
    if (!response.ok) throw new Error('Failed to drop the activity');
  },

  async restoreActivity(shareToken, managerToken, activityId) {
    const response = await fetch(
        managerUrl(shareToken, `/activities/${encodeURIComponent(activityId)}/restore`, managerToken),
        { method: 'POST' });
    if (!response.ok) throw new Error('Failed to restore the activity');
  },

  async addActivity(shareToken, managerToken, activityId) {
    const response = await fetch(managerUrl(shareToken, '/activities', managerToken), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ activityId }),
    });
    if (!response.ok) throw new Error('Failed to add the activity');
  },
};

// managerToken arrives via a shared URL's query param — encode it so it cannot
// inject extra query parameters into the request.
function managerUrl(shareToken, path, managerToken) {
  return `${API_BASE_URL}/vote/sessions/${encodeURIComponent(shareToken)}${path}`
      + `?managerToken=${encodeURIComponent(managerToken)}`;
}

export default voteApi;
