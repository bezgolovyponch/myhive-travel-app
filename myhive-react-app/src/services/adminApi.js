import {API_BASE_URL} from './config';

export function createAdminApi(getAccessToken) {
    async function authHeaders() {
        const token = await getAccessToken();
        return {
            Authorization: `Bearer ${token}`,
            'Content-Type': 'application/json',
        };
    }

    async function handleError(response, fallbackMessage) {
        if (response.status === 401 || response.status === 403) {
            throw new Error('Unauthorized');
        }
        if (!response.ok) {
            const contentType = response.headers.get('content-type') || '';
            if (contentType.includes('application/json')) {
                try {
                    const body = await response.json();
                    throw new Error(body.message || fallbackMessage);
                } catch (e) {
                    if (e.message !== fallbackMessage && e.message !== 'Unauthorized') {
                        throw e;
                    }
                }
            }
            throw new Error(fallbackMessage);
        }
    }

    return {
        async getBookings() {
            const response = await fetch(`${API_BASE_URL}/admin/bookings`, {
                headers: await authHeaders(),
            });
            await handleError(response, 'Failed to fetch bookings');
            return response.json();
        },

        async getBookingById(id) {
            const response = await fetch(`${API_BASE_URL}/admin/bookings/${id}`, {
                headers: await authHeaders(),
            });
            await handleError(response, 'Failed to fetch booking');
            return response.json();
        },

        async getBookingStats() {
            const response = await fetch(`${API_BASE_URL}/admin/bookings/stats`, {
                headers: await authHeaders(),
            });
            await handleError(response, 'Failed to fetch booking stats');
            return response.json();
        },

        async getActivities() {
            const response = await fetch(`${API_BASE_URL}/admin/activities`, {
                headers: await authHeaders(),
            });
            await handleError(response, 'Failed to fetch activities');
            return response.json();
        },

        async getActivitiesPaged(page = 0, size = 10) {
            const response = await fetch(`${API_BASE_URL}/admin/activities/paged?page=${page}&size=${size}`, {
                headers: await authHeaders(),
            });
            await handleError(response, 'Failed to fetch activities');
            return response.json();
        },

        async createActivity(activity) {
            const headers = await authHeaders();
            const response = await fetch(`${API_BASE_URL}/admin/activities`, {
                method: 'POST',
                headers,
                body: JSON.stringify(activity),
            });
            await handleError(response, 'Failed to create activity');
            return response.json();
        },

        async updateActivity(id, activity) {
            const headers = await authHeaders();
            const response = await fetch(`${API_BASE_URL}/admin/activities/${id}`, {
                method: 'PUT',
                headers,
                body: JSON.stringify(activity),
            });
            await handleError(response, 'Failed to update activity');
            return response.json();
        },

        async deleteActivity(id) {
            const response = await fetch(`${API_BASE_URL}/admin/activities/${id}`, {
                method: 'DELETE',
                headers: await authHeaders(),
            });
            await handleError(response, 'Failed to delete activity');
        },

        async exportActivitiesCsv(destinationId) {
            const token = await getAccessToken();
            const url = destinationId
                ? `${API_BASE_URL}/admin/activities/export?destinationId=${encodeURIComponent(destinationId)}`
                : `${API_BASE_URL}/admin/activities/export`;
            const response = await fetch(url, {
                headers: {Authorization: `Bearer ${token}`},
            });
            await handleError(response, 'Failed to export activities');
            const blob = await response.blob();
            // Browser will pick up filename from Content-Disposition; this is the fallback
            const filename = `activities-${new Date().toISOString().slice(0, 10)}.csv`;
            const link = document.createElement('a');
            const objectUrl = URL.createObjectURL(blob);
            link.href = objectUrl;
            link.download = filename;
            document.body.appendChild(link);
            link.click();
            link.remove();
            URL.revokeObjectURL(objectUrl);
        },

        async previewActivityImport(file) {
            const token = await getAccessToken();
            const formData = new FormData();
            formData.append('file', file);
            const response = await fetch(`${API_BASE_URL}/admin/activities/import/preview`, {
                method: 'POST',
                headers: {Authorization: `Bearer ${token}`},
                body: formData,
            });
            await handleError(response, 'Failed to preview import');
            return response.json();
        },

        async applyActivityImport(importToken) {
            const headers = await authHeaders();
            const response = await fetch(`${API_BASE_URL}/admin/activities/import/apply`, {
                method: 'POST',
                headers,
                body: JSON.stringify({token: importToken}),
            });
            await handleError(response, 'Failed to apply import');
            return response.json();
        },

        async uploadImage(file) {
            const token = await getAccessToken();
            const formData = new FormData();
            formData.append('file', file);
            const response = await fetch(`${API_BASE_URL}/admin/upload`, {
                method: 'POST',
                headers: {Authorization: `Bearer ${token}`},
                body: formData,
            });
            await handleError(response, 'Failed to upload image');
            return response.json();
        },

        async getBlogPosts() {
            const response = await fetch(`${API_BASE_URL}/admin/blog`, {
                headers: await authHeaders(),
            });
            await handleError(response, 'Failed to fetch blog posts');
            return response.json();
        },

        async getBlogPostsPaged(page = 0, size = 10) {
            const response = await fetch(`${API_BASE_URL}/admin/blog/paged?page=${page}&size=${size}`, {
                headers: await authHeaders(),
            });
            await handleError(response, 'Failed to fetch blog posts');
            return response.json();
        },

        async createBlogPost(post) {
            const headers = await authHeaders();
            const response = await fetch(`${API_BASE_URL}/admin/blog`, {
                method: 'POST',
                headers,
                body: JSON.stringify(post),
            });
            await handleError(response, 'Failed to create blog post');
            return response.json();
        },

        async updateBlogPost(id, post) {
            const headers = await authHeaders();
            const response = await fetch(`${API_BASE_URL}/admin/blog/${id}`, {
                method: 'PUT',
                headers,
                body: JSON.stringify(post),
            });
            await handleError(response, 'Failed to update blog post');
            return response.json();
        },

        async deleteBlogPost(id) {
            const response = await fetch(`${API_BASE_URL}/admin/blog/${id}`, {
                method: 'DELETE',
                headers: await authHeaders(),
            });
            await handleError(response, 'Failed to delete blog post');
        },

        async getCategories() {
            const response = await fetch(`${API_BASE_URL}/admin/categories`, {
                headers: await authHeaders(),
            });
            await handleError(response, 'Failed to fetch categories');
            return response.json();
        },

        async getCategoriesPaged(page = 0, size = 10) {
            const response = await fetch(`${API_BASE_URL}/admin/categories/paged?page=${page}&size=${size}`, {
                headers: await authHeaders(),
            });
            await handleError(response, 'Failed to fetch categories');
            return response.json();
        },

        async createCategory(category) {
            const headers = await authHeaders();
            const response = await fetch(`${API_BASE_URL}/admin/categories`, {
                method: 'POST',
                headers,
                body: JSON.stringify(category),
            });
            await handleError(response, 'Failed to create category');
            return response.json();
        },

        async updateCategory(id, category) {
            const headers = await authHeaders();
            const response = await fetch(`${API_BASE_URL}/admin/categories/${id}`, {
                method: 'PUT',
                headers,
                body: JSON.stringify(category),
            });
            await handleError(response, 'Failed to update category');
            return response.json();
        },

        async deleteCategory(id) {
            const response = await fetch(`${API_BASE_URL}/admin/categories/${id}`, {
                method: 'DELETE',
                headers: await authHeaders(),
            });
            await handleError(response, 'Failed to delete category');
        },

        async getDestinations() {
            const response = await fetch(`${API_BASE_URL}/admin/destinations`, {
                headers: await authHeaders(),
            });
            await handleError(response, 'Failed to fetch destinations');
            return response.json();
        },

        async getDestinationsPaged(page = 0, size = 10) {
            const response = await fetch(`${API_BASE_URL}/admin/destinations/paged?page=${page}&size=${size}`, {
                headers: await authHeaders(),
            });
            await handleError(response, 'Failed to fetch destinations');
            return response.json();
        },

        async createDestination(destination) {
            const headers = await authHeaders();
            const response = await fetch(`${API_BASE_URL}/admin/destinations`, {
                method: 'POST',
                headers,
                body: JSON.stringify(destination),
            });
            await handleError(response, 'Failed to create destination');
            return response.json();
        },

        async updateDestination(id, destination) {
            const headers = await authHeaders();
            const response = await fetch(`${API_BASE_URL}/admin/destinations/${id}`, {
                method: 'PUT',
                headers,
                body: JSON.stringify(destination),
            });
            await handleError(response, 'Failed to update destination');
            return response.json();
        },

        async deleteDestination(id) {
            const response = await fetch(`${API_BASE_URL}/admin/destinations/${id}`, {
                method: 'DELETE',
                headers: await authHeaders(),
            });
            await handleError(response, 'Failed to delete destination');
        },
    };
}
