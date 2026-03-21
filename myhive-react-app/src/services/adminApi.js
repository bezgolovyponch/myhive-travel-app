const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080';

const TOKEN_KEY = 'myhive-admin-token';

function getToken() {
    return localStorage.getItem(TOKEN_KEY);
}

function setToken(token) {
    localStorage.setItem(TOKEN_KEY, token);
}

function removeToken() {
    localStorage.removeItem(TOKEN_KEY);
}

function authHeaders() {
    const token = getToken();
    return token ? {Authorization: `Bearer ${token}`} : {};
}

const adminApi = {
    async login(email, password) {
        const response = await fetch(`${API_BASE_URL}/auth/login`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({email, password}),
        });
        if (!response.ok) {
            const status = response.status;
            if (status === 401 || status === 403) throw new Error('Invalid credentials');
            throw new Error('Login failed');
        }
        const data = await response.json();
        setToken(data.token);
        return data;
    },

    async validateToken() {
        const token = getToken();
        if (!token) return {valid: false};

        const response = await fetch(`${API_BASE_URL}/auth/validate`, {
            headers: {Authorization: `Bearer ${token}`},
        });
        if (!response.ok) {
            removeToken();
            return {valid: false};
        }
        return response.json();
    },

    async getBookings() {
        const response = await fetch(`${API_BASE_URL}/api/admin/bookings`, {
            headers: authHeaders(),
        });
        if (response.status === 401 || response.status === 403) {
            removeToken();
            throw new Error('Unauthorized');
        }
        if (!response.ok) throw new Error('Failed to fetch bookings');
        return response.json();
    },

    async getBookingById(id) {
        const response = await fetch(`${API_BASE_URL}/api/admin/bookings/${id}`, {
            headers: authHeaders(),
        });
        if (response.status === 401 || response.status === 403) {
            removeToken();
            throw new Error('Unauthorized');
        }
        if (!response.ok) throw new Error('Failed to fetch booking');
        return response.json();
    },

    async getBookingStats() {
        const response = await fetch(`${API_BASE_URL}/api/admin/bookings/stats`, {
            headers: authHeaders(),
        });
        if (response.status === 401 || response.status === 403) {
            removeToken();
            throw new Error('Unauthorized');
        }
        if (!response.ok) throw new Error('Failed to fetch booking stats');
        return response.json();
    },

    async getActivities() {
        const response = await fetch(`${API_BASE_URL}/api/admin/activities`, {
            headers: authHeaders(),
        });
        if (response.status === 401 || response.status === 403) {
            removeToken();
            throw new Error('Unauthorized');
        }
        if (!response.ok) throw new Error('Failed to fetch activities');
        return response.json();
    },

    async createActivity(activity) {
        const response = await fetch(`${API_BASE_URL}/api/admin/activities`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json', ...authHeaders()},
            body: JSON.stringify(activity),
        });
        if (response.status === 401 || response.status === 403) {
            removeToken();
            throw new Error('Unauthorized');
        }
        if (!response.ok) throw new Error('Failed to create activity');
        return response.json();
    },

    async updateActivity(id, activity) {
        const response = await fetch(`${API_BASE_URL}/api/admin/activities/${id}`, {
            method: 'PUT',
            headers: {'Content-Type': 'application/json', ...authHeaders()},
            body: JSON.stringify(activity),
        });
        if (response.status === 401 || response.status === 403) {
            removeToken();
            throw new Error('Unauthorized');
        }
        if (!response.ok) throw new Error('Failed to update activity');
        return response.json();
    },

    async deleteActivity(id) {
        const response = await fetch(`${API_BASE_URL}/api/admin/activities/${id}`, {
            method: 'DELETE',
            headers: authHeaders(),
        });
        if (response.status === 401 || response.status === 403) {
            removeToken();
            throw new Error('Unauthorized');
        }
        if (!response.ok) throw new Error('Failed to delete activity');
    },

    async uploadImage(file) {
        const formData = new FormData();
        formData.append('file', file);
        const response = await fetch(`${API_BASE_URL}/api/admin/upload`, {
            method: 'POST',
            headers: authHeaders(),
            body: formData,
        });
        if (response.status === 401 || response.status === 403) {
            removeToken();
            throw new Error('Unauthorized');
        }
        if (!response.ok) throw new Error('Failed to upload image');
        return response.json();
    },

    async getDestinations() {
        const response = await fetch(`${API_BASE_URL}/destinations`, {
            headers: authHeaders(),
        });
        if (!response.ok) throw new Error('Failed to fetch destinations');
        return response.json();
    },

    logout() {
        removeToken();
    },

    getToken,
    isAuthenticated() {
        return !!getToken();
    },
};

export default adminApi;
