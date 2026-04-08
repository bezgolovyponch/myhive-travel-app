import {createContext, useCallback, useContext, useMemo} from 'react';
import {AuthProvider as OidcAuthProvider, useAuth as useOidcAuth} from 'react-oidc-context';

const ROLES_CLAIM = process.env.REACT_APP_OIDC_ROLES_CLAIM || 'https://trivlu.com/roles';

const AuthContext = createContext();

const oidcConfig = {
    authority: process.env.REACT_APP_OIDC_AUTHORITY,
    client_id: process.env.REACT_APP_OIDC_CLIENT_ID,
    redirect_uri: process.env.REACT_APP_OIDC_REDIRECT_URI || window.location.origin + '/admin',
    scope: 'openid profile email',
    extraQueryParams: {
        audience: process.env.REACT_APP_OIDC_AUDIENCE,
    },
    onSigninCallback: () => {
        window.history.replaceState({}, document.title, window.location.pathname);
    },
};

export function useAuth() {
    const context = useContext(AuthContext);
    if (!context) throw new Error('useAuth must be used within AuthProvider');
    return context;
}

function AuthContextBridge({children}) {
    const auth = useOidcAuth();

    const getAccessToken = useCallback(async () => auth.user?.access_token, [auth.user?.access_token]);

    const value = useMemo(() => {
        const idTokenClaims = auth.user?.profile;
        const roles = idTokenClaims?.[ROLES_CLAIM] || [];
        const email = idTokenClaims?.email;
        const isProcessingCallback = !!auth.activeNavigator;

        return {
            user: auth.isAuthenticated ? {email, roles, role: roles[0] || null} : null,
            isAuthenticated: auth.isAuthenticated,
            loading: auth.isLoading || isProcessingCallback,
            login: () => auth.signinRedirect(),
            logout: () => auth.signoutRedirect({post_logout_redirect_uri: window.location.origin}),
            getAccessToken,
        };
    }, [auth, getAccessToken]);

    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function AuthProvider({children}) {
    return (
        <OidcAuthProvider {...oidcConfig}>
            <AuthContextBridge>{children}</AuthContextBridge>
        </OidcAuthProvider>
    );
}
