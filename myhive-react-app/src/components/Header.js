import {Link, useLocation, useNavigate} from 'react-router-dom';
import {useState} from 'react';
import {useCatalog} from '../context/CatalogContext';
import {useTrip} from '../context/TripContext';
import TripBuilderDropdown from './TripBuilderDropdown';
import TripSetupModal from './TripSetupModal';
import {scrollToHomeSection} from '../utils/scrollToHomeSection';
import './Header.css';

function Header() {
  const location = useLocation();
  const navigate = useNavigate();
  const {state: catalog} = useCatalog();
  const {state, dispatch} = useTrip();
  const [mobileNavOpen, setMobileNavOpen] = useState(false);

  const handleTripBuilderClick = () => {
      if (state.tripBuilderModalOpen) {
          dispatch({type: 'CLOSE_TRIP_BUILDER_MODAL'});
    } else {
      // Only one panel open at a time — close the nav menu if it's showing.
      setMobileNavOpen(false);
      dispatch({type: 'OPEN_TRIP_BUILDER_MODAL'});
    }
  };

  const handleBurgerClick = () => {
      // Opening the nav menu closes the cart dropdown, so the two never overlap.
      if (!mobileNavOpen && state.tripBuilderModalOpen) {
          dispatch({type: 'CLOSE_TRIP_BUILDER_MODAL'});
      }
      setMobileNavOpen(!mobileNavOpen);
  };

  const destinationMatch = location.pathname.match(/^\/destination\/([^/?]+)/);
  const destinationSlug = destinationMatch ? destinationMatch[1] : null;
  const destination = destinationSlug
      ? catalog.destinations.find((item) => item.slug === destinationSlug)
      : null;
  // Activity/package detail pages render their own richer breadcrumbs (with the
  // actual item name), so the header only shows breadcrumbs on destination pages.
  const isDetailPage = /^\/destination\/[^/?]+\/(activity|package)\//.test(location.pathname);
  const showBreadcrumbs = Boolean(destinationSlug) && !isDetailPage;
  const currentTabLabel = (new URLSearchParams(location.search).get('tab') || 'activities')
      .replace('-', ' ')
      .replace(/\b\w/g, (char) => char.toUpperCase());

  // The Complete Booking flow is a focused checkout: the global header (and its
  // breadcrumb subheader) collapse away so nothing competes with the form.
  if (state.checkoutOpen) {
    return null;
  }

  return (
    // The header bar (logo + nav + breadcrumbs) now scrolls away with the page
    // on every route (2026-08-03). Only the cart + burger cluster below stays
    // pinned — it's rendered as a sibling fixed to the viewport, not part of
    // this in-flow bar.
    <header className="header header--transparent">
      <div className="header-content">
        <Link to="/" className="logo">
          <img src="/logo-trivlu.svg?v=4" alt="Trivlu" className="logo-img"/>
        </Link>
        <nav className={`nav-links ${mobileNavOpen ? 'nav-open' : ''}`}>
          <a
              href="/#activities"
              onClick={(e) => {
                e.preventDefault();
                setMobileNavOpen(false);
                scrollToHomeSection(navigate, 'activities');
              }}
          >
            Activities
          </a>
          <Link to="/about" onClick={() => setMobileNavOpen(false)}>About</Link>
          <Link to="/blog" onClick={() => setMobileNavOpen(false)}>Blog</Link>
          <Link to="/contact" onClick={() => setMobileNavOpen(false)}>Contact</Link>
        </nav>
        <TripSetupModal/>
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

      {/* Pinned action cluster: burger + cart stay fixed at the top-right of the
          viewport on every page, even as the header bar above scrolls away. */}
      <div className="header-actions">
        <div className="trip-builder-wrapper">
          <button
              className="cart-btn"
              onClick={handleTripBuilderClick}
              aria-label={state.tripItems.length > 0
                  ? `Cart, ${state.tripItems.length} item${state.tripItems.length === 1 ? '' : 's'}`
                  : 'Cart'}
          >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"
                   strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"
                   aria-hidden="true">
                  <circle cx="9" cy="21" r="1"/>
                  <circle cx="20" cy="21" r="1"/>
                  <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6"/>
              </svg>
              {state.tripItems.length > 0 && (
                  <span className="cart-count">{state.tripItems.length}</span>
              )}
          </button>
          <TripBuilderDropdown/>
        </div>
        <button
            type="button"
            className="hamburger-btn"
            aria-label="Menu"
            aria-expanded={mobileNavOpen}
            onClick={handleBurgerClick}
        >
            <span className={`hamburger-icon ${mobileNavOpen ? 'open' : ''}`}/>
        </button>
      </div>
    </header>
  );
}

export default Header;
