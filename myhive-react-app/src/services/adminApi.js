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

    logout() {
        removeToken();
    },

    getToken,
    isAuthenticated() {
        return !!getToken();
    },
};

export default adminApi;
