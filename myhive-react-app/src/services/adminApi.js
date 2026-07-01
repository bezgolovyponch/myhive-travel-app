import {API_BASE_URL} from './config';
import {parseApiError} from './httpError';

function parseContentDispositionFilename(header) {
    if (!header) {
        return null;
    }
    // RFC 6266: prefer filename*=UTF-8''... if present, else filename="..."
    const utf8Match = header.match(/filename\*=UTF-8''([^;]+)/i);
    if (utf8Match) {
        try {
            return decodeURIComponent(utf8Match[1]);
        } catch (e) {
            // fall through to plain filename
        }
    }
    const plainMatch = header.match(/filename="?([^";]+)"?/i);
    return plainMatch ? plainMatch[1] : null;
}

export function createAdminApi(getAccessToken) {
    async function authHeaders() {
        const token = await getAccessToken();
        return {
            Authorization: `Bearer ${token}`,
            'Content-Type': 'application/json',
        };
    }

    async function handleError(response, fallbackMessage) {
        if (response.ok) {
            return;
        }
        if (response.status === 401 || response.status === 403) {
            // useAuthErrorHandler logs out on these statuses; the message is
            // only a fallback for the brief moment before redirect.
            const err = new Error('Unauthorized');
            err.status = response.status;
            throw err;
        }
        throw await parseApiError(response, fallbackMessage);
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

        async getActivitiesPaged(page = 0, size = 10, destinationId = null) {
            const params = new URLSearchParams({page, size});
            if (destinationId) {
                params.append('destinationId', destinationId);
            }
            const response = await fetch(`${API_BASE_URL}/admin/activities/paged?${params}`, {
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
            const filename = parseContentDispositionFilename(response.headers.get('content-disposition'))
                || `activities-${new Date().toISOString().slice(0, 10)}.csv`;
            const objectUrl = URL.createObjectURL(blob);
            const link = document.createElement('a');
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

        async getCategoryUsage(id) {
            const response = await fetch(`${API_BASE_URL}/admin/categories/${id}/usage`, {
                headers: await authHeaders(),
            });
            await handleError(response, 'Failed to fetch category usage');
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

        async updateDestinationCategories(id, categoryIds) {
            const headers = await authHeaders();
            const response = await fetch(`${API_BASE_URL}/admin/destinations/${id}/categories`, {
                method: 'PUT',
                headers,
                body: JSON.stringify(categoryIds),
            });
            await handleError(response, 'Failed to update destination categories');
        },

        async getDestinationQuiz(destinationId) {
            const response = await fetch(`${API_BASE_URL}/admin/destinations/${destinationId}/quiz`, {
                headers: await authHeaders(),
            });
            await handleError(response, 'Failed to fetch quiz');
            return response.json();
        },

        async putDestinationQuiz(destinationId, quizDto) {
            const headers = await authHeaders();
            const response = await fetch(`${API_BASE_URL}/admin/destinations/${destinationId}/quiz`, {
                method: 'PUT',
                headers,
                body: JSON.stringify(quizDto),
            });
            await handleError(response, 'Failed to save quiz');
            return response.json();
        },

        async getPackages() {
            const response = await fetch(`${API_BASE_URL}/admin/packages`, {
                headers: await authHeaders(),
            });
            await handleError(response, 'Failed to fetch packages');
            return response.json();
        },

        async getPackagesPaged(page = 0, size = 10) {
            const response = await fetch(`${API_BASE_URL}/admin/packages/paged?page=${page}&size=${size}`, {
                headers: await authHeaders(),
            });
            await handleError(response, 'Failed to fetch packages');
            return response.json();
        },

        async createPackage(pkg) {
            const headers = await authHeaders();
            const response = await fetch(`${API_BASE_URL}/admin/packages`, {
                method: 'POST',
                headers,
                body: JSON.stringify(pkg),
            });
            await handleError(response, 'Failed to create package');
            return response.json();
        },

        async updatePackage(id, pkg) {
            const headers = await authHeaders();
            const response = await fetch(`${API_BASE_URL}/admin/packages/${id}`, {
                method: 'PUT',
                headers,
                body: JSON.stringify(pkg),
            });
            await handleError(response, 'Failed to update package');
            return response.json();
        },

        async deletePackage(id) {
            const response = await fetch(`${API_BASE_URL}/admin/packages/${id}`, {
                method: 'DELETE',
                headers: await authHeaders(),
            });
            await handleError(response, 'Failed to delete package');
        },

        async createBookingPaymentLink(id, amountCents) {
            const headers = await authHeaders();
            const response = await fetch(`${API_BASE_URL}/admin/bookings/${id}/payment-link`, {
                method: 'POST',
                headers,
                body: JSON.stringify({amountCents}),
            });
            await handleError(response, 'Failed to create payment link');
            return response.json();
        },
    };
}
