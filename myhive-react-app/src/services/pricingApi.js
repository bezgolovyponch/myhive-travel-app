import { API_BASE_URL } from './config';

const pricingApi = {
  // "from €X" for a plan: priced on the server from the catalog, never in the browser. Each item
  // names the package it was added as part of ({activityId, packageId}), so the server applies the
  // package's discount as the booking will.
  async quote({ items, travelers }) {
    const response = await fetch(`${API_BASE_URL}/pricing/quote`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ items, travelers }),
    });
    if (!response.ok) throw new Error('Failed to price the plan');
    return response.json();
  },
};

export default pricingApi;
