import { API_BASE_URL } from './config';

const pricingApi = {
  // "from €X" for a plan: priced on the server from the catalog, never in the browser.
  async quote({ activityIds, travelers }) {
    const response = await fetch(`${API_BASE_URL}/pricing/quote`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ activityIds, travelers }),
    });
    if (!response.ok) throw new Error('Failed to price the plan');
    return response.json();
  },
};

export default pricingApi;
