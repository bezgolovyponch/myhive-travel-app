import {useCallback} from 'react';
import {useAuth} from '../context/AuthContext';

export function useAuthErrorHandler() {
    const {logout} = useAuth();

    return useCallback((err) => {
        if (err.message === 'Unauthorized') {
            logout();
            return true;
        }
        return false;
    }, [logout]);
}
