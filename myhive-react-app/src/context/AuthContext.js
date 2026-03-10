import {createContext, useCallback, useContext, useEffect, useState} from 'react';
import adminApi from '../services/adminApi';

const AuthContext = createContext();

export function useAuth() {
    const context = useContext(AuthContext);
    if (!context) throw new Error('useAuth must be used within AuthProvider');
    return context;
}

export function AuthProvider({children}) {
    const [user, setUser] = useState(null);
    const [loading, setLoading] = useState(true);

    const validateSession = useCallback(async () => {
        if (!adminApi.isAuthenticated()) {
            setUser(null);
            setLoading(false);
            return;
        }
        try {
            const result = await adminApi.validateToken();
            if (result.valid) {
                setUser({email: result.email, role: result.role});
            } else {
                adminApi.logout();
                setUser(null);
            }
        } catch {
            adminApi.logout();
            setUser(null);
        }
        setLoading(false);
    }, []);

    useEffect(() => {
        validateSession();
    }, [validateSession]);

    const login = async (email, password) => {
        await adminApi.login(email, password);
        const result = await adminApi.validateToken();
        if (result.valid) {
            setUser({email: result.email, role: result.role});
        }
    };

    const logout = () => {
        adminApi.logout();
        setUser(null);
    };

    const value = {
        user,
        isAuthenticated: !!user,
        loading,
        login,
        logout,
    };

    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
