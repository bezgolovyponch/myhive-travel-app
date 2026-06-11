import {useCallback} from 'react';
import {useAuth} from '../context/AuthContext';

export function useAuthErrorHandler() {
    const {logout} = useAuth();

    return useCallback((err) => {
        if (err.status === 401 || err.status === 403) {
            logout();
            return true;
        }
        return false;
    }, [logout]);
}
