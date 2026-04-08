import {useMemo} from 'react';
import {useAuth} from '../context/AuthContext';
import {createAdminApi} from '../services/adminApi';

export function useAdminApi() {
    const {getAccessToken} = useAuth();
    return useMemo(() => createAdminApi(getAccessToken), [getAccessToken]);
}
