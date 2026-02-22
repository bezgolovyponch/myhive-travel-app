const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8081/api';

export const api = {
  // Destinations
  async getDestinations() {
    const response = await fetch(`${API_BASE_URL}/destinations`);
    if (!response.ok) throw new Error('Failed to fetch destinations');
    return response.json();
  },

  async getDestination(id) {
    const response = await fetch(`${API_BASE_URL}/destinations/${id}`);
    if (!response.ok) throw new Error('Failed to fetch destination');
    return response.json();
  },

  // Activities
  async getActivities(destinationId = null, category = null) {
    let url = `${API_BASE_URL}/activities`;
    const params = new URLSearchParams();
    
    if (destinationId) params.append('destinationId', destinationId);
    if (category) params.append('category', category);
    
    if (params.toString()) url += `?${params.toString()}`;
    
    const response = await fetch(url);
    if (!response.ok) throw new Error('Failed to fetch activities');
    return response.json();
  },

  async getActivity(id) {
    const response = await fetch(`${API_BASE_URL}/activities/${id}`);
    if (!response.ok) throw new Error('Failed to fetch activity');
    return response.json();
  },

  // Bookings
  async createBooking(bookingData) {
    const response = await fetch(`${API_BASE_URL}/bookings`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(bookingData),
    });
    if (!response.ok) throw new Error('Failed to create booking');
    return response.json();
  },

  async getBooking(id) {
    const response = await fetch(`${API_BASE_URL}/bookings/${id}`);
    if (!response.ok) throw new Error('Failed to fetch booking');
    return response.json();
  },

  async getBookingsByEmail(email) {
    const response = await fetch(`${API_BASE_URL}/bookings?email=${encodeURIComponent(email)}`);
    if (!response.ok) throw new Error('Failed to fetch bookings');
    return response.json();
  },

  async updateBookingStatus(id, status, stripeSessionId = null) {
    const response = await fetch(`${API_BASE_URL}/bookings/${id}/status`, {
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ status, stripeSessionId }),
    });
    if (!response.ok) throw new Error('Failed to update booking status');
    return response.json();
  },

    // Google Sheets
    async getGoogleSheetsStatus() {
        const response = await fetch(`${API_BASE_URL}/google-sheets/status`);
        if (!response.ok) throw new Error('Failed to check Google Sheets status');
        return response.json();
    },

    async exportTripToSheets(tripData) {
        const response = await fetch(`${API_BASE_URL}/google-sheets/export-trip`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(tripData),
        });
        if (!response.ok) throw new Error('Failed to export trip to Google Sheets');
        return response.json();
    },

    async createSpreadsheet(title) {
        const response = await fetch(`${API_BASE_URL}/google-sheets/create-spreadsheet`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({title}),
        });
        if (!response.ok) throw new Error('Failed to create spreadsheet');
        return response.json();
    },

    async getSpreadsheetSheets(spreadsheetId) {
        const response = await fetch(`${API_BASE_URL}/google-sheets/spreadsheet/${spreadsheetId}/sheets`);
        if (!response.ok) throw new Error('Failed to get spreadsheet sheets');
        return response.json();
    },
};

export default api;
