import {Route, Routes} from 'react-router-dom';
import {useContext} from 'react';
import Header from './Header';
import Footer from './Footer';
import HomePage from '../pages/HomePage';
import DestinationPage from '../pages/DestinationPage';
import ActivityDetailPage from '../pages/ActivityDetailPage';
import {AppContext} from '../context/AppContext';

function Layout() {
  const {state, dispatch} = useContext(AppContext);

  return (
    <div className="app-container">
      <Header />
      <main>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/destination/:id" element={<DestinationPage />} />
            <Route path="/activity/:id" element={<ActivityDetailPage/>}/>
        </Routes>
      </main>
        <Footer/>
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
