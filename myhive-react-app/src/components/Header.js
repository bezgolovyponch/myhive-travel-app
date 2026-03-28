import {Link, useLocation, useNavigate} from 'react-router-dom';
import {useContext, useState} from 'react';
import {AppContext} from '../context/AppContext';

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
        <Link to="/" className="logo">MyHive</Link>
        <button className="hamburger-btn" onClick={() => setMobileNavOpen(!mobileNavOpen)}>
          <span className={`hamburger-icon ${mobileNavOpen ? 'open' : ''}`}/>
        </button>
        <nav className={`nav-links ${mobileNavOpen ? 'nav-open' : ''}`}>
          <a href="/" onClick={(e) => {
            handleDestinationsClick(e);
            setMobileNavOpen(false);
          }}>Destinations</a>
            <Link to="/about" onClick={() => setMobileNavOpen(false)}>
            About
            </Link>
          <Link to="/blog" onClick={() => setMobileNavOpen(false)}>
            Blog
          </Link>
        </nav>
          <div className="trip-builder-wrapper">
              <button className="trip-builder-btn" onClick={handleTripBuilderClick}>
                  TRIP BUILDER
                  {state.tripItems.length > 0 && (
                      <span className="trip-builder-count">{state.tripItems.length}</span>
                  )}
              </button>
              {state.tripBuilderModalOpen && (
                  <div className="trip-builder-dropdown">
                      <div className="trip-builder-dropdown-header">
                          <h3>Trip Builder</h3>
                          <button className="app-modal-close-btn"
                                  onClick={() => dispatch({type: 'CLOSE_TRIP_BUILDER_MODAL'})}>×
                          </button>
                      </div>
                      <div className="trip-builder-dropdown-body">
                          {state.tripItems.length > 0 ? (
                              <>
                                  <div className="trip-modal-items">
                                      {state.tripItems.map(item => {
                                          const img = item.imageUrl || 'https://images.unsplash.com/photo-1544551763-46a013bb70d5?w=400&h=300&fit=crop';
                                          const price = typeof item.price === 'number' ? `€${item.price}` : item.price;
                                          return (
                                              <div key={item.id} className="trip-modal-item">
                                                  <img src={img} alt={item.name} className="trip-modal-item-image"/>
                                                  <div className="trip-modal-item-info">
                                                      <span
                                                          className="trip-modal-item-name">{item.name || item.title}</span>
                                                      <span className="trip-modal-item-price">{price}</span>
                                                  </div>
                                                  <button
                                                      className="trip-modal-item-remove"
                                                      onClick={() => dispatch({
                                                          type: 'REMOVE_FROM_TRIP',
                                                          activityId: item.id
                                                      })}
                                                  >×
                                                  </button>
                                              </div>
                                          );
                                      })}
                                  </div>
                                  <div className="trip-modal-total">
                                      <span>Total</span>
                                      <span className="trip-modal-total-price">
                        €{state.tripItems.reduce((sum, item) => sum + (typeof item.price === 'number' ? item.price : 0), 0)}
                      </span>
                                  </div>
                                  <button
                                      className="trip-builder-complete-btn"
                                      onClick={() => {
                                          const destId = state.tripItems[0]?.destinationId;
                                          dispatch({type: 'CLOSE_TRIP_BUILDER_MODAL'});
                                          if (destId) {
                                              navigate(`/destination/${destId}?tab=trip-builder`);
                                          }
                                      }}
                                  >
                                      Complete Booking
                                  </button>
                              </>
                          ) : (
                              <div className="empty-trip-state">
                                  <p>No activities added yet. Browse and add activities to build your trip!</p>
                              </div>
                          )}
                      </div>
                  </div>
              )}
          </div>
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
