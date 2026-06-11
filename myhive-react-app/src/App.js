import 'bootstrap/dist/css/bootstrap.min.css';
import './styles/global.css';
import {lazy, Suspense} from 'react';
import {BrowserRouter as Router, Route, Routes} from 'react-router-dom';
import {HelmetProvider} from 'react-helmet-async';
import {AppProvider} from './context/AppContext';
import Layout from './components/Layout';

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
            <Router>
                <Routes>
                    {/* Admin routes — single AuthProvider instance */}
                    <Route path="/admin/*" element={
                        <Suspense fallback={<div style={{padding: '4rem', textAlign: 'center'}}>Loading…</div>}>
                            <AdminApp/>
                        </Suspense>
                    }/>

                    {/* Public routes — existing app */}
                    <Route path="/*" element={
                        <AppProvider>
                            <Layout/>
                        </AppProvider>
                    }/>
                </Routes>
            </Router>
        </HelmetProvider>
    );
}

export default App;
