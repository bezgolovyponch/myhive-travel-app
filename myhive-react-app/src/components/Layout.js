import {Route, Routes} from 'react-router-dom';
import {useContext} from 'react';
import Header from './Header';
import Footer from './Footer';
import CookieConsent from './CookieConsent';
import HomePage from '../pages/HomePage';
import DestinationPage from '../pages/DestinationPage';
import ActivityDetailPage from '../pages/ActivityDetailPage';
import PackageDetailPage from '../pages/PackageDetailPage';
import AboutPage from '../pages/AboutPage';
import BlogPage from '../pages/BlogPage';
import BlogPostPage from '../pages/BlogPostPage';
import ContactPage from '../pages/ContactPage';
import QuizPage from '../pages/vote/QuizPage';
import CuratePage from '../pages/vote/CuratePage';
import ActivityVotePage from '../pages/vote/ActivityVotePage';
import VoteWaitingPage from '../pages/vote/VoteWaitingPage';
import VoteResultPage from '../pages/vote/VoteResultPage';
import {AppContext} from '../context/AppContext';
import AppModal from './AppModal';

function Layout() {
  const {state, dispatch} = useContext(AppContext);
  const closeDestinationModal = () => dispatch({type: 'CLOSE_DESTINATION_MODAL'});

  return (
    <div className="app-container">
      <Header />
      <main>
        <Routes>
          <Route path="/" element={<HomePage />} />
            <Route path="/destination/:slug" element={<DestinationPage/>}/>
            <Route path="/destination/:destinationSlug/activity/:slug" element={<ActivityDetailPage/>}/>
            <Route path="/destination/:destSlug/package/:slug" element={<PackageDetailPage/>}/>
            <Route path="/about" element={<AboutPage/>}/>
            <Route path="/blog" element={<BlogPage/>}/>
            <Route path="/blog/:slug" element={<BlogPostPage/>}/>
            <Route path="/contact" element={<ContactPage/>}/>
            <Route path="/vote/new/quiz" element={<QuizPage />} />
            <Route path="/vote/new/curate" element={<CuratePage />} />
            <Route path="/vote/:shareToken/quiz" element={<QuizPage />} />
            <Route path="/vote/:shareToken/activities" element={<ActivityVotePage />} />
            <Route path="/vote/:shareToken/waiting" element={<VoteWaitingPage />} />
            <Route path="/vote/:shareToken/result" element={<VoteResultPage />} />
        </Routes>
      </main>
        <Footer/>
        <CookieConsent/>
        <AppModal
            isOpen={state.destinationModalOpen}
            onClose={closeDestinationModal}
            title="Coming Soon"
        >
            <div className="empty-trip-state">
                <h3>{state.selectedDestination?.name || 'This destination'} is coming soon!</h3>
                <p>We're working hard to bring you amazing experiences here. Stay tuned!</p>
                <button className="btn btn--primary" onClick={closeDestinationModal}>
                    Got it
                </button>
            </div>
        </AppModal>
    </div>
  );
}

export default Layout;
