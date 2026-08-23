import {API_BASE_URL} from './config';
import {parseApiError} from './httpError';
import {currentLocale, localeField} from '../i18n/routes';

// Public catalog reads carry the page's locale so the backend resolves the
// translatable fields in place (same response shape, see the backend's
// Translations). Read from the URL, not React context: this module is plain JS
// shared by contexts, hooks and components alike, and the locale prefix is the
// one thing every caller's page has in common. No window (tests, SSR) → en.
function localized(url) {
    return `${url}${url.includes('?') ? '&' : '?'}locale=${currentLocale()}`;
}

export const api = {
  // Destinations
  async getDestinations() {
    const response = await fetch(localized(`${API_BASE_URL}/destinations`));
    if (!response.ok) throw await parseApiError(response, 'Failed to fetch destinations');
    return response.json();
  },

  async getDestination(id) {
    const response = await fetch(localized(`${API_BASE_URL}/destinations/${id}`));
    if (!response.ok) throw await parseApiError(response, 'Failed to fetch destination');
    return response.json();
  },

    async getDestinationBySlug(slug) {
        const response = await fetch(localized(`${API_BASE_URL}/destinations/slug/${slug}`));
        if (!response.ok) throw await parseApiError(response, 'Failed to fetch destination');
        return response.json();
    },

  // Activities
    async getActivities(destinationId = null, categorySlug = null) {
    let url = `${API_BASE_URL}/activities`;
    const params = new URLSearchParams();

    if (destinationId) params.append('destinationId', destinationId);
        if (categorySlug) params.append('categorySlug', categorySlug);

    if (params.toString()) url += `?${params.toString()}`;

    const response = await fetch(localized(url));
    if (!response.ok) throw await parseApiError(response, 'Failed to fetch activities');
    return response.json();
  },

    async getActivitiesPaged(destinationId, {page = 0, size = 12, categorySlug = null} = {}) {
        const params = new URLSearchParams();
        params.append('destinationId', destinationId);
        params.append('page', page);
        params.append('size', size);
        if (categorySlug) params.append('categorySlug', categorySlug);

        const response = await fetch(localized(`${API_BASE_URL}/activities/paged?${params.toString()}`));
        if (!response.ok) throw await parseApiError(response, 'Failed to fetch activities');
        return response.json();
    },

    async getFeaturedActivities() {
        const response = await fetch(localized(`${API_BASE_URL}/activities?featured=true`));
        if (!response.ok) {
            throw await parseApiError(response, 'Failed to fetch featured activities');
        }
        return response.json();
    },

    // Categories
    async getCategories() {
        const response = await fetch(localized(`${API_BASE_URL}/categories`));
        if (!response.ok) throw await parseApiError(response, 'Failed to fetch categories');
        return response.json();
    },

    async getCategoriesForDestination(destinationId) {
        const response = await fetch(localized(`${API_BASE_URL}/destinations/${destinationId}/categories`));
        if (!response.ok) throw await parseApiError(response, 'Failed to fetch categories for destination');
        return response.json();
    },

  async getActivity(id) {
    const response = await fetch(localized(`${API_BASE_URL}/activities/${id}`));
    if (!response.ok) throw await parseApiError(response, 'Failed to fetch activity');
    return response.json();
  },

    async getActivityBySlug(slug) {
        const response = await fetch(localized(`${API_BASE_URL}/activities/slug/${slug}`));
        if (!response.ok) throw await parseApiError(response, 'Failed to fetch activity');
        return response.json();
    },

    // Packages
    async getPackagesByDestination(destinationId) {
        const response = await fetch(localized(`${API_BASE_URL}/packages?destinationId=${encodeURIComponent(destinationId)}`));
        if (!response.ok) throw await parseApiError(response, 'Failed to fetch packages');
        return response.json();
    },

    async getPackageBySlug(slug) {
        const response = await fetch(localized(`${API_BASE_URL}/packages/slug/${slug}`));
        if (!response.ok) throw await parseApiError(response, 'Failed to fetch package');
        return response.json();
    },

  // Bookings
  async createBooking(bookingData) {
    const response = await fetch(`${API_BASE_URL}/bookings`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      // locale: language of the customer's confirmation emails.
      body: JSON.stringify({...bookingData, ...localeField()}),
    });
    if (!response.ok) throw await parseApiError(response, 'Failed to create booking');
    return response.json();
  },

    // Blog
    async getBlogPosts() {
        const response = await fetch(localized(`${API_BASE_URL}/blog`));
        if (!response.ok) throw await parseApiError(response, 'Failed to fetch blog posts');
        return response.json();
    },

    async getBlogPost(id) {
        const response = await fetch(localized(`${API_BASE_URL}/blog/${id}`));
        if (!response.ok) throw await parseApiError(response, 'Failed to fetch blog post');
        return response.json();
    },

    async getBlogPostBySlug(slug) {
        const response = await fetch(localized(`${API_BASE_URL}/blog/slug/${slug}`));
        if (!response.ok) throw await parseApiError(response, 'Failed to fetch blog post');
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
        if (!response.ok) throw await parseApiError(response, 'Failed to send message');
        return response.json();
    },

  // Trip booking
  async createBookingFromTrip(tripData) {
    const response = await fetch(`${API_BASE_URL}/bookings/trip`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            // locale: language of the customer's confirmation emails.
            body: JSON.stringify({...tripData, ...localeField()}),
        });
    if (!response.ok) throw await parseApiError(response, 'Failed to create booking');
        return response.json();
    },
};

export default api;
