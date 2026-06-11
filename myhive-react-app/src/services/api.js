import {API_BASE_URL} from './config';

// Mirror adminApi's error shape: surface the backend's message when there is
// one (validation errors on bookings/contact), and always attach the status
// so callers can branch on it.
async function parseError(response, fallbackMessage) {
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

export const api = {
  // Destinations
  async getDestinations() {
    const response = await fetch(`${API_BASE_URL}/destinations`);
    if (!response.ok) throw await parseError(response, 'Failed to fetch destinations');
    return response.json();
  },

  async getDestination(id) {
    const response = await fetch(`${API_BASE_URL}/destinations/${id}`);
    if (!response.ok) throw await parseError(response, 'Failed to fetch destination');
    return response.json();
  },

    async getDestinationBySlug(slug) {
        const response = await fetch(`${API_BASE_URL}/destinations/slug/${slug}`);
        if (!response.ok) throw await parseError(response, 'Failed to fetch destination');
        return response.json();
    },

  // Activities
    async getActivities(destinationId = null, categorySlug = null) {
    let url = `${API_BASE_URL}/activities`;
    const params = new URLSearchParams();

    if (destinationId) params.append('destinationId', destinationId);
        if (categorySlug) params.append('categorySlug', categorySlug);

    if (params.toString()) url += `?${params.toString()}`;

    const response = await fetch(url);
    if (!response.ok) throw await parseError(response, 'Failed to fetch activities');
    return response.json();
  },

    async getActivitiesPaged(destinationId, {page = 0, size = 12, categorySlug = null} = {}) {
        const params = new URLSearchParams();
        params.append('destinationId', destinationId);
        params.append('page', page);
        params.append('size', size);
        if (categorySlug) params.append('categorySlug', categorySlug);

        const response = await fetch(`${API_BASE_URL}/activities/paged?${params.toString()}`);
        if (!response.ok) throw await parseError(response, 'Failed to fetch activities');
        return response.json();
    },

    async getFeaturedActivities() {
        const response = await fetch(`${API_BASE_URL}/activities?featured=true`);
        if (!response.ok) {
            throw await parseError(response, 'Failed to fetch featured activities');
        }
        return response.json();
    },

    // Categories
    async getCategories() {
        const response = await fetch(`${API_BASE_URL}/categories`);
        if (!response.ok) throw await parseError(response, 'Failed to fetch categories');
        return response.json();
    },

    async getCategoriesForDestination(destinationId) {
        const response = await fetch(`${API_BASE_URL}/destinations/${destinationId}/categories`);
        if (!response.ok) throw await parseError(response, 'Failed to fetch categories for destination');
        return response.json();
    },

  async getActivity(id) {
    const response = await fetch(`${API_BASE_URL}/activities/${id}`);
    if (!response.ok) throw await parseError(response, 'Failed to fetch activity');
    return response.json();
  },

    async getActivityBySlug(slug) {
        const response = await fetch(`${API_BASE_URL}/activities/slug/${slug}`);
        if (!response.ok) throw await parseError(response, 'Failed to fetch activity');
        return response.json();
    },

    // Packages
    async getPackagesByDestination(destinationId) {
        const response = await fetch(`${API_BASE_URL}/packages?destinationId=${encodeURIComponent(destinationId)}`);
        if (!response.ok) throw await parseError(response, 'Failed to fetch packages');
        return response.json();
    },

    async getPackageBySlug(slug) {
        const response = await fetch(`${API_BASE_URL}/packages/slug/${slug}`);
        if (!response.ok) throw await parseError(response, 'Failed to fetch package');
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
    if (!response.ok) throw await parseError(response, 'Failed to create booking');
    return response.json();
  },

  async getBooking(id) {
    const response = await fetch(`${API_BASE_URL}/bookings/${id}`);
    if (!response.ok) throw await parseError(response, 'Failed to fetch booking');
    return response.json();
  },

  async getBookingsByEmail(email) {
    const response = await fetch(`${API_BASE_URL}/bookings?email=${encodeURIComponent(email)}`);
    if (!response.ok) throw await parseError(response, 'Failed to fetch bookings');
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
    if (!response.ok) throw await parseError(response, 'Failed to update booking status');
    return response.json();
  },

    // Blog
    async getBlogPosts() {
        const response = await fetch(`${API_BASE_URL}/blog`);
        if (!response.ok) throw await parseError(response, 'Failed to fetch blog posts');
        return response.json();
    },

    async getBlogPost(id) {
        const response = await fetch(`${API_BASE_URL}/blog/${id}`);
        if (!response.ok) throw await parseError(response, 'Failed to fetch blog post');
        return response.json();
    },

    async getBlogPostBySlug(slug) {
        const response = await fetch(`${API_BASE_URL}/blog/slug/${slug}`);
        if (!response.ok) throw await parseError(response, 'Failed to fetch blog post');
        return response.json();
    },

    // Contact
    async submitContactForm(contactData) {
        const response = await fetch(`${API_BASE_URL}/contact`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(contactData),
        });
        if (!response.ok) throw await parseError(response, 'Failed to send message');
        return response.json();
    },

  // Trip booking
  async createBookingFromTrip(tripData) {
    const response = await fetch(`${API_BASE_URL}/bookings/trip`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(tripData),
        });
    if (!response.ok) throw await parseError(response, 'Failed to create booking');
        return response.json();
    },
};

export default api;
