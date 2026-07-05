import { API_BASE_URL } from './config';

const voteApi = {
  // Public quiz fetch (organizer pre-session, by destinationId)
  async getPublicQuizForDestination(destinationId) {
    const response = await fetch(`${API_BASE_URL}/vote/destinations/${destinationId}/quiz`);
    if (response.status === 404) return { questions: [] };
    if (!response.ok) throw new Error('Failed to fetch quiz');
    return response.json();
  },

  // Build the pool (stateless, pre-session)
  async buildPool({ destinationId, responses }) {
    const response = await fetch(`${API_BASE_URL}/vote/pool`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ destinationId, responses }),
    });
    if (!response.ok) throw new Error('Failed to build pool');
    return response.json();
  },

  // Atomic session creation
  async createSession({ destinationId, initiatorEmail, numberOfTravelers, startDate, endDate,
                        budget, voterToken, quizResponses, activityIds }) {
    const response = await fetch(`${API_BASE_URL}/vote/sessions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        destinationId, initiatorEmail, numberOfTravelers, startDate, endDate,
        budget, voterToken, quizResponses, activityIds,
      }),
    });
    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      throw new Error(body.message || 'Failed to create vote session');
    }
    return response.json();
  },

  async getSession(shareToken) {
    const response = await fetch(`${API_BASE_URL}/vote/sessions/${encodeURIComponent(shareToken)}`);
    if (!response.ok) throw new Error('Failed to fetch vote session');
    return response.json();
  },

  // Curated voting list (same URL as before — backend now serves curated for new sessions)
  async getActivities(shareToken) {
    const response = await fetch(`${API_BASE_URL}/vote/sessions/${encodeURIComponent(shareToken)}/activities`);
    if (response.status === 404) throw new Error('Vote session not found');
    if (!response.ok) throw new Error('Failed to fetch vote activities');
    return response.json();
  },

  // Participant quiz
  async getParticipantQuiz(shareToken) {
    const response = await fetch(`${API_BASE_URL}/vote/sessions/${encodeURIComponent(shareToken)}/quiz`);
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

  async castVotes(shareToken, { voterToken, votes }) {
    const response = await fetch(`${API_BASE_URL}/vote/sessions/${encodeURIComponent(shareToken)}/votes/batch`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ voterToken, votes }),
    });
    if (response.status === 409) throw new Error('Session is full');
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
    const response = await fetch(`${API_BASE_URL}/vote/sessions/${encodeURIComponent(shareToken)}/result`);
    if (response.status === 404) throw new Error('Vote session not found');
    if (response.status === 409) throw new Error('Result not available yet');
    if (!response.ok) throw new Error('Failed to fetch vote result');
    return response.json();
  },

  // Cart-seeded session creation (no quiz) — the ballot is the initiator's cart.
  async createCartSession({ destinationId, initiatorEmail, numberOfTravelers,
                            startDate, endDate, activityIds }) {
    const response = await fetch(`${API_BASE_URL}/vote/sessions/cart`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        destinationId, initiatorEmail, numberOfTravelers, startDate, endDate, activityIds,
      }),
    });
    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      throw new Error(body.message || 'Failed to create vote session');
    }
    return response.json();
  },

  // Live tally (CART sessions): server requires that voterToken has voted, or a managerToken.
  async getTally(shareToken, { voterToken, managerToken } = {}) {
    const params = new URLSearchParams();
    if (voterToken) {
      params.set('voterToken', voterToken);
    }
    if (managerToken) {
      params.set('managerToken', managerToken);
    }
    const response = await fetch(
        `${API_BASE_URL}/vote/sessions/${encodeURIComponent(shareToken)}/tally?${params}`);
    if (response.status === 403) throw new Error('Vote first to see the live tally');
    if (!response.ok) throw new Error('Failed to fetch tally');
    return response.json();
  },
};

export default voteApi;
