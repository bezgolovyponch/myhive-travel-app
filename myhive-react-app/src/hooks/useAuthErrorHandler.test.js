import {renderHook} from '@testing-library/react';
import {useAuthErrorHandler} from './useAuthErrorHandler';

const mockLogout = jest.fn();
jest.mock('../context/AuthContext', () => ({
    useAuth: () => ({logout: mockLogout}),
}));

describe('useAuthErrorHandler', () => {
    afterEach(() => {
        mockLogout.mockClear();
    });

    it.each([401, 403])('logs out and returns true for status %i', (status) => {
        const {result} = renderHook(() => useAuthErrorHandler());

        const handled = result.current({status});

        expect(handled).toBe(true);
        expect(mockLogout).toHaveBeenCalledTimes(1);
    });

    it.each([500, undefined])('does not log out and returns false for status %s', (status) => {
        const {result} = renderHook(() => useAuthErrorHandler());

        const handled = result.current({status});

        expect(handled).toBe(false);
        expect(mockLogout).not.toHaveBeenCalled();
    });
});
