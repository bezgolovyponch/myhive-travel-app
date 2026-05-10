import { API_BASE_URL } from './config';

const voteApi = {
  async createSession({ destinationId, initiatorEmail, numberOfTravelers, startDate, endDate, likedCategoryIds }) {
    const response = await fetch(`${API_BASE_URL}/vote/sessions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ destinationId, initiatorEmail, numberOfTravelers, startDate, endDate, likedCategoryIds }),
    });
    if (!response.ok) throw new Error('Failed to create vote session');
    return response.json();
  },

  async getSession(shareToken) {
    const response = await fetch(`${API_BASE_URL}/vote/sessions/${shareToken}`);
    if (!response.ok) throw new Error('Failed to fetch vote session');
    return response.json();
  },

  async getActivities(shareToken) {
    const response = await fetch(`${API_BASE_URL}/vote/sessions/${shareToken}/activities`);
    if (!response.ok) throw new Error('Failed to fetch vote activities');
    return response.json();
  },

  async castVote(shareToken, { voterToken, activityId, liked }) {
    const response = await fetch(`${API_BASE_URL}/vote/sessions/${shareToken}/votes`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ voterToken, activityId, liked }),
    });
    if (response.status === 403) throw new Error('Session is full');
    if (!response.ok) throw new Error('Failed to cast vote');
  },

  async getParticipantCount(shareToken) {
    const response = await fetch(`${API_BASE_URL}/vote/sessions/${shareToken}/participant-count`);
    if (!response.ok) throw new Error('Failed to fetch participant count');
    return response.json();
  },

  async getResult(shareToken) {
    const response = await fetch(`${API_BASE_URL}/vote/sessions/${shareToken}/result`);
    if (response.status === 404) throw new Error('Result not available yet');
    if (!response.ok) throw new Error('Failed to fetch vote result');
    return response.json();
  },
};

export default voteApi;
