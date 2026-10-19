import {lazy, Suspense} from 'react';
import {Route, Routes, useLocation} from 'react-router-dom';
import Header from './Header';
import Footer from './Footer';
import WhatsAppWidget from './WhatsAppWidget';
import ScrollToTop from './ScrollToTop';
import HomePage from '../pages/HomePage';
import DestinationPage from '../pages/DestinationPage';
import ActivityDetailPage from '../pages/ActivityDetailPage';
import PackageDetailPage from '../pages/PackageDetailPage';
import AboutPage from '../pages/AboutPage';
import BlogPage from '../pages/BlogPage';
import BlogPostPage from '../pages/BlogPostPage';
import ContactPage from '../pages/ContactPage';
import CookiePolicyPage from '../pages/CookiePolicyPage';
import PrivacyPolicyPage from '../pages/PrivacyPolicyPage';
import TermsPage from '../pages/TermsPage';
import RefundPolicyPage from '../pages/RefundPolicyPage';
import UnsubscribePage from '../pages/UnsubscribePage';
import QuizPage from '../pages/vote/QuizPage';
import VoteEntryPage from '../pages/vote/VoteEntryPage';
import CuratePage from '../pages/vote/CuratePage';
import ActivityVotePage from '../pages/vote/ActivityVotePage';
import VoteWaitingPage from '../pages/vote/VoteWaitingPage';
import VoteResultPage from '../pages/vote/VoteResultPage';
import PaymentSuccessPage from '../pages/PaymentSuccessPage';
import PaymentCancelledPage from '../pages/PaymentCancelledPage';
import {useDestinationModal} from '../context/DestinationModalContext';
import {useTripLeadSync} from '../hooks/useTripLeadSync';
import {DESTINATION_HUBS, SUPPORTED_LOCALES, routePath, useT} from '../i18n';
import AppModal from './AppModal';

// Stag Do AI pulls in assistant-ui (~120 kB gz) — load it only when /plan is
// opened, the way App.js splits off the admin console. A failed chunk load
// (stale cached HTML after a redeploy) must not white-screen the app.
const AiPlannerPage = lazy(() => import('../pages/AiPlannerPage').catch(() => ({
    default: () => (
        <div style={{padding: '4rem', textAlign: 'center'}}>
            This page failed to load. Please refresh the page.
        </div>
    ),
})));

// One <Route> per locale variant: '/prague-stag-do' and '/jga-prag' both render the Prague catalog — the SPA's BrowserRouter only strips the locale prefix, the segments stay as the public URL spells them.
function localizedPaths(pattern) {
  return [...new Set(SUPPORTED_LOCALES.map((l) => routePath(pattern, l)))];
}

function hubRoutes() {
  return Object.entries(DESTINATION_HUBS).flatMap(([destinationSlug, hub]) => [
    ...localizedPaths(hub).map((p) => (
      <Route key={p} path={p} element={<DestinationPage destinationSlug={destinationSlug}/>}/>
    )),
    ...localizedPaths(`${hub}/activities/:slug`).map((p) => (
      <Route key={p} path={p} element={<ActivityDetailPage destinationSlug={destinationSlug}/>}/>
    )),
    ...localizedPaths(`${hub}/packages/:slug`).map((p) => (
      <Route key={p} path={p} element={<PackageDetailPage destinationSlug={destinationSlug}/>}/>
    )),
  ]);
}

function Layout() {
  useTripLeadSync();
  const t = useT('chrome');
  const {pathname} = useLocation();
  const {state, dispatch} = useDestinationModal();
  const closeDestinationModal = () => dispatch({type: 'CLOSE_DESTINATION_MODAL'});

  return (
    <div className="app-container">
      <ScrollToTop />
      <Header />
      <main>
        <Routes>
          <Route path="/" element={<HomePage />} />
            {hubRoutes()}
            <Route path="/destination/:slug" element={<DestinationPage/>}/>
            <Route path="/destination/:destinationSlug/activity/:slug" element={<ActivityDetailPage/>}/>
            <Route path="/destination/:destSlug/package/:slug" element={<PackageDetailPage/>}/>
            <Route path="/about" element={<AboutPage/>}/>
            {localizedPaths('/ideas').map((p) => <Route key={p} path={p} element={<BlogPage/>}/>)}
            {localizedPaths('/ideas/:slug').map((p) => <Route key={p} path={p} element={<BlogPostPage/>}/>)}
            <Route path="/contact" element={<ContactPage/>}/>
            <Route path="/cookie-policy" element={<CookiePolicyPage/>}/>
            <Route path="/privacy-policy" element={<PrivacyPolicyPage/>}/>
            <Route path="/terms" element={<TermsPage/>}/>
            <Route path="/refund-policy" element={<RefundPolicyPage/>}/>
            <Route path="/unsubscribe" element={<UnsubscribePage/>}/>
            <Route path="/plan" element={
                <Suspense fallback={<div style={{padding: '4rem', textAlign: 'center'}}>Loading…</div>}>
                    <AiPlannerPage/>
                </Suspense>
            }/>
            <Route path="/vote/new" element={<VoteEntryPage />} />
            <Route path="/vote/new/quiz" element={<QuizPage />} />
            <Route path="/vote/new/curate" element={<CuratePage />} />
            <Route path="/vote/:shareToken/quiz" element={<QuizPage />} />
            <Route path="/vote/:shareToken/activities" element={<ActivityVotePage />} />
            <Route path="/vote/:shareToken/waiting" element={<VoteWaitingPage />} />
            <Route path="/vote/:shareToken/result" element={<VoteResultPage />} />
            <Route path="/payment/success" element={<PaymentSuccessPage />} />
            <Route path="/payment/cancelled" element={<PaymentCancelledPage />} />
        </Routes>
      </main>
        {/* /plan is a full-height chat with a fixed dock: a footer under it is only a gap. */}
        {pathname !== '/plan' && <Footer/>}
        <WhatsAppWidget/>
        <AppModal
            isOpen={state.destinationModalOpen}
            onClose={closeDestinationModal}
            title={t('comingSoonTitle')}
        >
            <div className="empty-trip-state">
                <h3>{t('comingSoonHeading', {name: state.selectedDestination?.name || t('fallbackDestination')})}</h3>
                <p>{t('comingSoonBody')}</p>
                <button className="btn btn--primary" onClick={closeDestinationModal}>
                    {t('gotIt')}
                </button>
            </div>
        </AppModal>
    </div>
  );
}

export default Layout;
