import {Link, useLocation, useNavigate} from 'react-router-dom';
import {useContext} from 'react';
import {AppContext} from '../context/AppContext';

function Header() {
  const navigate = useNavigate();
  const location = useLocation();
  const {state, dispatch} = useContext(AppContext);

  const handleTripBuilderClick = () => {
    if (state.tripItems.length > 0) {
      // If has items, navigate to trip-builder tab
      navigate('/destination/tenerife?tab=trip-builder');
    } else {
      // Show empty state modal
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
        <Link to="/" className="logo">MyHive</Link>
        <nav className="nav-links">
          <a href="/" onClick={handleDestinationsClick}>Destinations</a>
          <a 
            href="#" 
            onClick={(e) => { e.preventDefault(); alert('About page coming soon!'); }}
          >
            About
          </a>
          <a 
            href="#" 
            onClick={(e) => { e.preventDefault(); alert('Contact page coming soon!'); }}
          >
            Contact
          </a>
        </nav>
        <button className="trip-builder-btn" onClick={handleTripBuilderClick}>TRIP BUILDER</button>
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
