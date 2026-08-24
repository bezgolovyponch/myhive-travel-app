import 'bootstrap/dist/css/bootstrap.min.css';
import './styles/global.css';
import {lazy, Suspense} from 'react';
import {BrowserRouter as Router, Route, Routes} from 'react-router-dom';
import {HelmetProvider} from 'react-helmet-async';
import {AppProviders} from './context/AppProviders';
import Layout from './components/Layout';
import {DEFAULT_LOCALE, splitLocale} from './i18n/routes';

// The Next shell mounts this SPA under the locale prefix too (/de/vote/...).
// BrowserRouter's basename makes every route match and every <Link>/navigate
// locale-aware without touching the route table; on the bare (English) URLs
// and in the standalone CRA build it is simply ''.
function routerBasename() {
    if (typeof window === 'undefined') return '';
    const {locale} = splitLocale(window.location.pathname);
    return locale === DEFAULT_LOCALE ? '' : `/${locale}`;
}

// Admin pages + the OIDC client are heavy and irrelevant to public visitors —
// load that whole subtree only when /admin is actually opened. A failed chunk
// load (stale cached index.html after a redeploy) must not white-screen the app.
const AdminApp = lazy(() => import('./AdminApp').catch(() => ({
    default: () => (
        <div style={{padding: '4rem', textAlign: 'center'}}>
            Failed to load the admin console. Please refresh the page.
        </div>
    ),
})));

function App() {
    return (
        <HelmetProvider>
            <Router basename={routerBasename()}>
                <Routes>
                    {/* Admin routes — single AuthProvider instance */}
                    <Route path="/admin/*" element={
                        <Suspense fallback={<div style={{padding: '4rem', textAlign: 'center'}}>Loading…</div>}>
                            <AdminApp/>
                        </Suspense>
                    }/>

                    {/* Public routes — existing app */}
                    <Route path="/*" element={
                        <AppProviders>
                            <Layout/>
                        </AppProviders>
                    }/>
                </Routes>
            </Router>
        </HelmetProvider>
    );
}

export default App;
