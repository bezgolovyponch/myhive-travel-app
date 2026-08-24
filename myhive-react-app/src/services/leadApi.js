import { API_BASE_URL } from './config';
import { localeField } from '../i18n/routes';

const leadApi = {
  // Fire-and-forget capture at quiz-vote setup; server dedups by email.
  async createLead({ email, destinationId, numberOfTravelers, startDate, endDate, budget }) {
    const response = await fetch(`${API_BASE_URL}/leads`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      // locale: language of the reminder emails and their links.
      body: JSON.stringify({ email, destinationId, numberOfTravelers, startDate, endDate, budget, ...localeField() }),
    });
    if (!response.ok) throw new Error('Failed to save trip lead');
    return response.json();
  },

  async syncLead(id, body) {
    const response = await fetch(`${API_BASE_URL}/leads/${encodeURIComponent(id)}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    if (response.status === 404) throw new Error('LEAD_GONE');
    if (!response.ok) throw new Error('Failed to sync trip lead');
  },

  async restoreLead(token) {
    const response = await fetch(`${API_BASE_URL}/leads/restore/${encodeURIComponent(token)}`);
    if (!response.ok) throw new Error('Failed to restore trip');
    return response.json();
  },

  async unsubscribe(token) {
    const response = await fetch(`${API_BASE_URL}/leads/unsubscribe`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token }),
    });
    if (!response.ok) throw new Error('Failed to unsubscribe');
  },
};

export default leadApi;
