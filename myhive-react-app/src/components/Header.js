import {Link, useLocation, useNavigate} from 'react-router-dom';
import {useContext, useState} from 'react';
import {AppContext} from '../context/AppContext';
import TripBuilderDropdown from './TripBuilderDropdown';
import './Header.css';

function Header() {
  const navigate = useNavigate();
  const location = useLocation();
  const {state, dispatch} = useContext(AppContext);
  const [mobileNavOpen, setMobileNavOpen] = useState(false);

  const handleTripBuilderClick = () => {
      if (state.tripBuilderModalOpen) {
          dispatch({type: 'CLOSE_TRIP_BUILDER_MODAL'});
    } else {
      dispatch({type: 'OPEN_TRIP_BUILDER_MODAL'});
    }
  };

  const destinationMatch = location.pathname.match(/^\/destination\/(.+)$/);
  const destinationId = destinationMatch ? destinationMatch[1] : null;
  const destination = destinationId
      ? state.destinations.find((item) => item.id === destinationId)
      : null;
  const showBreadcrumbs = Boolean(destinationId);
  const currentTabLabel = state.currentTab
      ? state.currentTab.replace('-', ' ').replace(/\b\w/g, (char) => char.toUpperCase())
      : 'Activities';

  const handleDestinationsClick = (event) => {
    event.preventDefault();
    navigate('/');
    setTimeout(() => {
      const section = document.getElementById('destinations');
      if (section) {
        section.scrollIntoView({behavior: 'smooth'});
      }
    }, 0);
  };

  return (
    <header className="header">
      <div className="header-content">
        <Link to="/" className="logo">Trivlu</Link>
        <nav className={`nav-links ${mobileNavOpen ? 'nav-open' : ''}`}>
          <a href="/" onClick={(e) => {
            handleDestinationsClick(e);
            setMobileNavOpen(false);
          }}>Destinations</a>
            <Link to="/about" onClick={() => setMobileNavOpen(false)}>About</Link>
            <Link to="/blog" onClick={() => setMobileNavOpen(false)}>Blog</Link>
        </nav>
          <div className="trip-builder-wrapper">
              <button className="trip-builder-btn" onClick={handleTripBuilderClick}>
                  TRIP BUILDER
                  {state.tripItems.length > 0 && (
                      <span className="trip-builder-count">{state.tripItems.length}</span>
                  )}
              </button>
              <TripBuilderDropdown/>
          </div>
          <button className="hamburger-btn" onClick={() => setMobileNavOpen(!mobileNavOpen)}>
              <span className={`hamburger-icon ${mobileNavOpen ? 'open' : ''}`}/>
          </button>
      </div>
      {showBreadcrumbs && (
          <div className="breadcrumbs">
            <div className="breadcrumbs-content">
              <Link className="breadcrumb-item" to="/">Home</Link>
              <span className="breadcrumb-separator">&gt;</span>
              <Link className="breadcrumb-item" to={`/destination/${destinationId}?tab=activities`}>
                {destination?.name || 'Destination'}
              </Link>
              <span className="breadcrumb-separator">&gt;</span>
              <span className="breadcrumb-item current">{currentTabLabel}</span>
            </div>
          </div>
      )}
    </header>
  );
}

export default Header;
