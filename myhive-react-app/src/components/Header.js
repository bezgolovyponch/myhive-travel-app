import {Link, useLocation, useNavigate} from 'react-router-dom';
import {useState} from 'react';
import {useCatalog} from '../context/CatalogContext';
import {useTrip} from '../context/TripContext';
import TripBuilderDropdown from './TripBuilderDropdown';
import TripSetupModal from './TripSetupModal';
import {scrollToHomeSection} from '../utils/scrollToHomeSection';
import './Header.css';

function Header() {
  const navigate = useNavigate();
  const location = useLocation();
  const {state: catalog} = useCatalog();
  const {state, dispatch} = useTrip();
  const [mobileNavOpen, setMobileNavOpen] = useState(false);

  const handleTripBuilderClick = () => {
      if (state.tripBuilderModalOpen) {
          dispatch({type: 'CLOSE_TRIP_BUILDER_MODAL'});
    } else {
      dispatch({type: 'OPEN_TRIP_BUILDER_MODAL'});
    }
  };

  const destinationMatch = location.pathname.match(/^\/destination\/([^/?]+)/);
  const destinationSlug = destinationMatch ? destinationMatch[1] : null;
  const destination = destinationSlug
      ? catalog.destinations.find((item) => item.slug === destinationSlug)
      : null;
  const showBreadcrumbs = Boolean(destinationSlug);
  const isPackagePage = /^\/destination\/[^/?]+\/package\//.test(location.pathname);
  const isActivityPage = /^\/destination\/[^/?]+\/activity\//.test(location.pathname);
  const currentTabLabel = isPackagePage
      ? 'Package'
      : isActivityPage
          ? 'Activity'
          : (new URLSearchParams(location.search).get('tab') || 'activities')
              .replace('-', ' ')
              .replace(/\b\w/g, (char) => char.toUpperCase());

  const handleActivitiesClick = (event) => {
    event.preventDefault();
    scrollToHomeSection(navigate, 'activities');
  };

  return (
    <header className="header">
      <div className="header-content">
        <Link to="/" className="logo">
          <img src="/logo-white.png" alt="Trivlu" className="logo-img"/>
        </Link>
        <nav className={`nav-links ${mobileNavOpen ? 'nav-open' : ''}`}>
          <a href="/#activities" onClick={(e) => {
            handleActivitiesClick(e);
            setMobileNavOpen(false);
          }}>Activities</a>
            <Link to="/about" onClick={() => setMobileNavOpen(false)}>About</Link>
            <Link to="/blog" onClick={() => setMobileNavOpen(false)}>Blog</Link>
            <Link to="/contact" onClick={() => setMobileNavOpen(false)}>Contact</Link>
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
        <TripSetupModal/>
          <button
              type="button"
              className="hamburger-btn"
              aria-label="Menu"
              aria-expanded={mobileNavOpen}
              onClick={() => setMobileNavOpen(!mobileNavOpen)}
          >
              <span className={`hamburger-icon ${mobileNavOpen ? 'open' : ''}`}/>
          </button>
      </div>
      {showBreadcrumbs && (
          <div className="breadcrumbs">
            <div className="breadcrumbs-content">
              <Link className="breadcrumb-item" to="/">Home</Link>
              <span className="breadcrumb-separator">&gt;</span>
              <Link className="breadcrumb-item" to={`/destination/${destinationSlug}?tab=activities`}>
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
