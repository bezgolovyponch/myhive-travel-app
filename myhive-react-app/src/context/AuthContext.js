import {createContext, useCallback, useContext, useMemo, useRef} from 'react';
import {AuthProvider as OidcAuthProvider, useAuth as useOidcAuth} from 'react-oidc-context';
import {WebStorageStateStore} from 'oidc-client-ts';

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
    userStore: new WebStorageStateStore({store: window.localStorage}),
    automaticSilentRenew: true,
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

    // Stable identity: silent token renewal must not recreate consumers
    // (useAdminApi memoizes on this), so read the freshest user via a ref.
    const userRef = useRef(auth.user);
    userRef.current = auth.user;
    const getAccessToken = useCallback(async () => userRef.current?.access_token, []);

    const value = useMemo(() => {
        const idTokenClaims = auth.user?.profile;
        const roles = idTokenClaims?.[ROLES_CLAIM] || [];
        const email = idTokenClaims?.email;
        const isProcessingCallback = !!auth.activeNavigator;

        return {
            user: auth.isAuthenticated ? {email, roles} : null,
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
