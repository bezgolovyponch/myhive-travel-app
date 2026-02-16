import {Link, Route, Routes} from 'react-router-dom';
import {useContext} from 'react';
import Header from './Header';
import HomePage from '../pages/HomePage';
import DestinationPage from '../pages/DestinationPage';
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
        </Routes>
      </main>
      {/* <ChatPanel /> */}
      <div className={`modal ${state.tripBuilderModalOpen ? '' : 'hidden'}`}>
        <div className="modal-content">
          <div className="modal-header">
            <h2>Trip Builder</h2>
            <button className="modal-close-btn" onClick={() => dispatch({type: 'CLOSE_TRIP_BUILDER_MODAL'})}>
              ×
            </button>
          </div>
          <div className="modal-body">
            <div className="empty-trip-state">
              <h3>Start Planning Your Trip</h3>
              <p>Choose a destination first, then add activities to build your perfect getaway!</p>
              <Link className="btn btn--primary" to="/" onClick={() => dispatch({type: 'CLOSE_TRIP_BUILDER_MODAL'})}>
                Browse Destinations
              </Link>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default Layout;
