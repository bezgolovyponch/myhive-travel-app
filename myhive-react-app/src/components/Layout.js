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
import {AppContext} from '../context/AppContext';

function Layout() {
  const {state, dispatch} = useContext(AppContext);

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
        </Routes>
      </main>
        <Footer/>
        <CookieConsent/>
        <div className={`app-modal ${state.destinationModalOpen ? '' : 'hidden'}`}>
            <div className="app-modal-content">
                <div className="app-modal-header">
            <h2>Coming Soon</h2>
                    <button className="app-modal-close-btn" onClick={() => dispatch({type: 'CLOSE_DESTINATION_MODAL'})}>
              ×
            </button>
          </div>
                <div className="app-modal-body">
            <div className="empty-trip-state">
              <h3>{state.selectedDestination?.name || 'This destination'} is coming soon!</h3>
              <p>We're working hard to bring you amazing experiences here. Stay tuned!</p>
              <button className="btn btn--primary" onClick={() => dispatch({type: 'CLOSE_DESTINATION_MODAL'})}>
                Got it
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default Layout;
