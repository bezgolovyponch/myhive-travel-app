const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';

export function createAdminApi(getAccessToken) {
    async function authHeaders() {
        const token = await getAccessToken();
        return {
            Authorization: `Bearer ${token}`,
            'Content-Type': 'application/json',
        };
    }

    return {
        async getBookings() {
            const response = await fetch(`${API_BASE_URL}/admin/bookings`, {
                headers: await authHeaders(),
            });
            if (response.status === 401 || response.status === 403) {
                throw new Error('Unauthorized');
            }
            if (!response.ok) throw new Error('Failed to fetch bookings');
            return response.json();
        },

        async getBookingById(id) {
            const response = await fetch(`${API_BASE_URL}/admin/bookings/${id}`, {
                headers: await authHeaders(),
            });
            if (response.status === 401 || response.status === 403) {
                throw new Error('Unauthorized');
            }
            if (!response.ok) throw new Error('Failed to fetch booking');
            return response.json();
        },

        async getBookingStats() {
            const response = await fetch(`${API_BASE_URL}/admin/bookings/stats`, {
                headers: await authHeaders(),
            });
            if (response.status === 401 || response.status === 403) {
                throw new Error('Unauthorized');
            }
            if (!response.ok) throw new Error('Failed to fetch booking stats');
            return response.json();
        },

        async getActivities() {
            const response = await fetch(`${API_BASE_URL}/admin/activities`, {
                headers: await authHeaders(),
            });
            if (response.status === 401 || response.status === 403) {
                throw new Error('Unauthorized');
            }
            if (!response.ok) throw new Error('Failed to fetch activities');
            return response.json();
        },

        async createActivity(activity) {
            const headers = await authHeaders();
            const response = await fetch(`${API_BASE_URL}/admin/activities`, {
                method: 'POST',
                headers,
                body: JSON.stringify(activity),
            });
            if (response.status === 401 || response.status === 403) {
                throw new Error('Unauthorized');
            }
            if (!response.ok) throw new Error('Failed to create activity');
            return response.json();
        },

        async updateActivity(id, activity) {
            const headers = await authHeaders();
            const response = await fetch(`${API_BASE_URL}/admin/activities/${id}`, {
                method: 'PUT',
                headers,
                body: JSON.stringify(activity),
            });
            if (response.status === 401 || response.status === 403) {
                throw new Error('Unauthorized');
            }
            if (!response.ok) throw new Error('Failed to update activity');
            return response.json();
        },

        async deleteActivity(id) {
            const response = await fetch(`${API_BASE_URL}/admin/activities/${id}`, {
                method: 'DELETE',
                headers: await authHeaders(),
            });
            if (response.status === 401 || response.status === 403) {
                throw new Error('Unauthorized');
            }
            if (!response.ok) throw new Error('Failed to delete activity');
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
            if (response.status === 401 || response.status === 403) {
                throw new Error('Unauthorized');
            }
            if (!response.ok) throw new Error('Failed to upload image');
            return response.json();
        },

        async getBlogPosts() {
            const response = await fetch(`${API_BASE_URL}/admin/blog`, {
                headers: await authHeaders(),
            });
            if (response.status === 401 || response.status === 403) {
                throw new Error('Unauthorized');
            }
            if (!response.ok) throw new Error('Failed to fetch blog posts');
            return response.json();
        },

        async createBlogPost(post) {
            const headers = await authHeaders();
            const response = await fetch(`${API_BASE_URL}/admin/blog`, {
                method: 'POST',
                headers,
                body: JSON.stringify(post),
            });
            if (response.status === 401 || response.status === 403) {
                throw new Error('Unauthorized');
            }
            if (!response.ok) throw new Error('Failed to create blog post');
            return response.json();
        },

        async updateBlogPost(id, post) {
            const headers = await authHeaders();
            const response = await fetch(`${API_BASE_URL}/admin/blog/${id}`, {
                method: 'PUT',
                headers,
                body: JSON.stringify(post),
            });
            if (response.status === 401 || response.status === 403) {
                throw new Error('Unauthorized');
            }
            if (!response.ok) throw new Error('Failed to update blog post');
            return response.json();
        },

        async deleteBlogPost(id) {
            const response = await fetch(`${API_BASE_URL}/admin/blog/${id}`, {
                method: 'DELETE',
                headers: await authHeaders(),
            });
            if (response.status === 401 || response.status === 403) {
                throw new Error('Unauthorized');
            }
            if (!response.ok) throw new Error('Failed to delete blog post');
        },

        async getDestinations() {
            const response = await fetch(`${API_BASE_URL}/destinations`, {
                headers: await authHeaders(),
            });
            if (!response.ok) throw new Error('Failed to fetch destinations');
            return response.json();
        },
    };
}
