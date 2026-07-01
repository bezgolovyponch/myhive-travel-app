import {render, screen} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import ProtectedRoute from './ProtectedRoute';

jest.mock('../context/AuthContext', () => ({
    useAuth: jest.fn(),
}));

const {useAuth} = require('../context/AuthContext');

function renderRoute({requiredRole, roles = [], isAuthenticated = true} = {}) {
    useAuth.mockReturnValue({
        isAuthenticated,
        loading: false,
        user: isAuthenticated ? {email: 'x@y.z', roles} : null,
    });
    return render(
        <MemoryRouter>
            <ProtectedRoute requiredRole={requiredRole}>
                <div>Protected Content</div>
            </ProtectedRoute>
        </MemoryRouter>
    );
}

test('allows access when no requiredRole is specified', () => {
    renderRoute();
    expect(screen.getByText('Protected Content')).toBeInTheDocument();
});

test('allows ADMIN when requiredRole is a single string "ADMIN"', () => {
    renderRoute({requiredRole: 'ADMIN', roles: ['ADMIN']});
    expect(screen.getByText('Protected Content')).toBeInTheDocument();
});

test('blocks USER when requiredRole is a single string "ADMIN"', () => {
    renderRoute({requiredRole: 'ADMIN', roles: ['USER']});
    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument();
});

test('allows MANAGER when requiredRole is ["ADMIN", "MANAGER"]', () => {
    renderRoute({requiredRole: ['ADMIN', 'MANAGER'], roles: ['MANAGER']});
    expect(screen.getByText('Protected Content')).toBeInTheDocument();
});

test('allows ADMIN when requiredRole is ["ADMIN", "MANAGER"]', () => {
    renderRoute({requiredRole: ['ADMIN', 'MANAGER'], roles: ['ADMIN']});
    expect(screen.getByText('Protected Content')).toBeInTheDocument();
});

test('blocks USER when requiredRole is ["ADMIN", "MANAGER"]', () => {
    renderRoute({requiredRole: ['ADMIN', 'MANAGER'], roles: ['USER']});
    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument();
});

test('redirects unauthenticated users away from protected content', () => {
    renderRoute({isAuthenticated: false});
    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument();
});
