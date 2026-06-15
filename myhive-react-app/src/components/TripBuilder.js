import {useContext, useEffect, useRef, useState} from 'react';
import {useSearchParams} from 'react-router-dom';
import {AppContext} from '../context/AppContext';
import api from '../services/api';
import voteApi from '../services/voteApi';
import {capitalizeFirst, formatDate, formatPrice, formatPricePerPerson} from '../utils/format';
import {computeTripTotal, groupTripItems} from '../utils/tripPricing';
import ContactForm from './ContactForm';
import SuccessModal from './SuccessModal';
import './TripBuilder.css';

const VISIBLE_CATEGORY_COUNT = 12;

function TripBuilder({ destinationId }) {
  const { state, dispatch } = useContext(AppContext);
  const [browseFilter, setBrowseFilter] = useState('all');
  const [categories, setCategories] = useState([]);
  const [browseActivities, setBrowseActivities] = useState([]);
  const [showAllCategories, setShowAllCategories] = useState(false);
  const [showContactForm, setShowContactForm] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);
  const [showSuccessModal, setShowSuccessModal] = useState(false);
  const [successContactData, setSuccessContactData] = useState(null);
  const [voteResult, setVoteResult] = useState(null);
  const [voteError, setVoteError] = useState(false);

  const leftRef = useRef(null);
  const rightRef = useRef(null);

  useEffect(() => {
    const left = leftRef.current;
    const right = rightRef.current;
    if (!left || !right) return;
    const mql = window.matchMedia('(min-width: 769px)');
    const sync = () => {
      if (mql.matches) {
        right.style.height = `${left.offsetHeight}px`;
      } else {
        right.style.height = '';
      }
    };
    const obs = new ResizeObserver(sync);
    obs.observe(left);
    mql.addEventListener('change', sync);
    sync();
    return () => {
      obs.disconnect();
      mql.removeEventListener('change', sync);
    };
  }, []);

  useEffect(() => {
    if (!destinationId) return;
    let cancelled = false;
    api.getCategoriesForDestination(destinationId)
        .then(c => {
            if (!cancelled) setCategories(c);
        })
        .catch(() => {});
    api.getActivities(destinationId)
        .then(a => {
            if (!cancelled) setBrowseActivities(a);
        })
        .catch(() => {});
    return () => {
        cancelled = true;
    };
  }, [destinationId]);

  const [searchParams] = useSearchParams();
  // Depend on the token string, not the searchParams object — its identity
  // changes on every navigation (e.g. ?tab= switches) and re-running this
  // effect would re-seed travelers/dates/budget over the user's edits.
  const voteSession = searchParams.get('voteSession');

  useEffect(() => {
    if (!voteSession) return;
    let cancelled = false;
    setVoteError(false);
    voteApi.getResult(voteSession)
        .then(result => {
            if (cancelled) return;
            setVoteResult(result);
            // Seed trip travelers + dates from the result so callers (email link,
            // End-voting button, etc.) don't need to dispatch these separately.
            if (result.numberOfTravelers && result.numberOfTravelers > 0) {
                dispatch({ type: 'UPDATE_TRIP_TRAVELERS', travelers: result.numberOfTravelers });
            }
            if (result.startDate || result.endDate) {
                dispatch({
                    type: 'UPDATE_TRIP_DATES',
                    startDate: result.startDate ?? '',
                    endDate: result.endDate ?? '',
                });
            }
            dispatch({ type: 'UPDATE_TRIP_BUDGET', budget: result.budget ?? null });
            // New shape: result.result[] is ResultActivityDTO with snapshot name+price
            // plus live slug/destinationSlug/imageUrl/duration/description/includes.
            // Map activityId → id so AppContext keys it the same as live activities.
            (result.result || []).forEach(row => {
                dispatch({
                    type: 'ADD_TO_TRIP',
                    silent: true,
                    activity: {
                        id: row.activityId,
                        name: row.name,
                        price: row.price,
                        slug: row.slug,
                        destinationSlug: row.destinationSlug,
                        imageUrl: row.imageUrl,
                        duration: row.duration,
                        description: row.description,
                        includes: row.includes,
                    },
                });
            });
        })
        .catch(() => {
            if (!cancelled) {
                setVoteError(true);
            }
        });
    return () => {
        cancelled = true;
    };
  }, [voteSession, dispatch]);

  const handleRemoveActivity = (activityId) => {
    dispatch({ type: 'REMOVE_FROM_TRIP', activityId });
  };

  const handleAddActivity = (activity) => {
      dispatch({type: 'ADD_TO_TRIP', activity, silent: true});
  };

  const handleConfirmTrip = () => {
    setShowContactForm(true);
  };

  const handleContactSubmit = async (contactData) => {
    if (state.tripItems.length === 0) {
      alert('Please add some activities to your trip before submitting.');
      return;
    }

    setIsSubmitting(true);
    setSubmitError(null);

    try {
      const bookingData = {
        tripName: 'Booking',
        userEmail: contactData.email,
        customerName: contactData.fullName,
        phone: contactData.phone,
        numberOfTravelers: parseInt(contactData.numberOfTravelers, 10) || 1,
        destinations: [{
          destinationName: 'Custom Travel Package',
          country: 'Not specified',
          duration: contactData.startDate && contactData.endDate ?
              Math.ceil((new Date(contactData.endDate) - new Date(contactData.startDate)) / (1000 * 60 * 60 * 24)) + 1 : 1,
          startDate: contactData.startDate,
          endDate: contactData.endDate,
          activities: state.tripItems.map(item => ({
            activityId: item.id,
            activityName: item.name,
            category: (item.categories && item.categories.length > 0)
                ? item.categories.map(c => c.name).join(', ')
                : 'General',
            description: item.description || '',
            price: item.price || 0,
            duration: item.duration || 0,
            timeOfDay: item.timeOfDay || 'Any',
            packageId: item.packageId || null,
            packageName: item.packageName || null,
            packageDiscountPct: item.packageDiscountPct || null,
          }))
        }],
        notes: `Full Name: ${contactData.fullName} | Special requirements: ${contactData.specialRequirements || 'None'} | Contact method: ${contactData.contactMethod} | Number of travelers: ${contactData.numberOfTravelers}`
      };

      await api.createBookingFromTrip(bookingData);

      dispatch({ type: 'CANCEL_TRIP_SETUP' });
      dispatch({ type: 'UPDATE_TRIP_TRAVELERS', travelers: 1 });
      dispatch({ type: 'UPDATE_TRIP_DATES', startDate: '', endDate: '' });
      dispatch({ type: 'CLOSE_TRIP_BUILDER_MODAL' });
      setShowContactForm(false);
      setSuccessContactData(contactData);
      setShowSuccessModal(true);
    } catch (error) {
      console.error('Booking submission error:', error);
      setSubmitError(error.message || 'Failed to submit booking. Please try again.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const travelers = state.tripTravelers || 1;

  const {standalone, groups: groupsArray} = groupTripItems(state.tripItems);
  const totalPrice = computeTripTotal(state.tripItems, travelers);

  const filteredBrowseActivities = browseFilter === 'all'
      ? browseActivities
      : browseActivities.filter(a => (a.categories || []).some(c => c.slug === browseFilter));

  return (
    <div className="trip-builder-layout">
      <div className="trip-builder-left" ref={leftRef}>
        <div className="itinerary-header">
          <h2>Your Itinerary</h2>
          <p>{state.tripItems.length} {state.tripItems.length === 1 ? 'activity' : 'activities'} selected</p>
          {state.tripItems.length > 0 && (
              <div className="itinerary-trip-info">
                <div className="trip-info-row">
                  <label htmlFor="trip-travelers">Travelers:</label>
                  <input
                      type="number"
                      id="trip-travelers"
                      className="trip-info-input"
                      value={travelers}
                      onChange={e => dispatch({
                        type: 'UPDATE_TRIP_TRAVELERS',
                        travelers: Math.max(1, parseInt(e.target.value, 10) || 1)
                      })}
                      min="1"
                      max="20"
                  />
                </div>
                {(state.tripStartDate || state.tripEndDate) && (
                    <div className="trip-info-row">
                      <label>Dates:</label>
                      <span>{formatDate(state.tripStartDate)} — {formatDate(state.tripEndDate)}</span>
                    </div>
                )}
              </div>
          )}
        </div>
        <div className="itinerary-list">
          {state.tripItems.length > 0 ? (
            <>
              {groupsArray.map(group => (
                <div key={group.packageId} className="package-group">
                  <div className="package-group-header">
                    <span className="package-group-name">{group.packageName}</span>
                    {group.packageDiscountPct > 0 && (
                      <span className="package-group-discount">{group.packageDiscountPct}% off</span>
                    )}
                    <button
                      type="button"
                      className="remove-item-btn"
                      aria-label={`Remove ${group.packageName}`}
                      onClick={() => dispatch({type: 'REMOVE_PACKAGE_FROM_TRIP', packageId: group.packageId})}
                    >
                      ×
                    </button>
                  </div>
                  <div className="package-group-items">
                    {group.items.map(item => (
                      <div key={item.id} className="itinerary-item package-group-activity">
                        <img src={item.imageUrl} alt={item.name}
                             className="itinerary-item-image" loading="lazy"/>
                        <div className="itinerary-item-content">
                          <div className="itinerary-item-title">{item.name}</div>
                          <div className="itinerary-item-price">
                            {travelers > 1
                                ? `${formatPrice(item.price)} × ${travelers} = ${formatPrice(item.price * travelers)}`
                                : formatPricePerPerson(item.price)}
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              ))}
              {standalone.map(item => (
                <div key={item.id} className="itinerary-item">
                  <img src={item.imageUrl} alt={item.name} className="itinerary-item-image"
                       loading="lazy"/>
                  <div className="itinerary-item-content">
                    <div className="itinerary-item-title">{item.name}</div>
                    <div className="itinerary-item-price">
                      {travelers > 1
                          ? `${formatPrice(item.price)} × ${travelers} = ${formatPrice(item.price * travelers)}`
                          : formatPricePerPerson(item.price)}
                    </div>
                  </div>
                  <button
                    type="button"
                    className="remove-item-btn"
                    aria-label={`Remove ${item.name}`}
                    onClick={() => handleRemoveActivity(item.id)}
                  >
                    ×
                  </button>
                </div>
              ))}
            </>
          ) : (
            <div className="empty-state">
              <p>Start building your trip by adding activities!</p>
            </div>
          )}
        </div>
        {state.tripBudget != null && (
            <div className="trip-vote-budget">
              <div className="trip-vote-budget-row">
                <span>Spent</span>
                <span>{formatPrice(totalPrice)}</span>
              </div>
              <div className="trip-vote-budget-row">
                <span>Budget</span>
                <span>{formatPrice(state.tripBudget)}</span>
              </div>
              <div className={`trip-vote-budget-row ${state.tripBudget - totalPrice < 0 ? 'trip-vote-budget-over' : ''}`}>
                <span>Remaining</span>
                <span>{formatPrice(state.tripBudget - totalPrice)}</span>
              </div>
            </div>
        )}
        {state.tripItems.length > 0 && (
            <div className="trip-actions">
              <div className="itinerary-total">
                <span>Total</span>
                <span className="itinerary-total-price">{formatPrice(totalPrice)}</span>
              </div>
            <button className="btn btn--primary btn--full-width confirm-btn" onClick={handleConfirmTrip}>
              Complete Booking
            </button>
              {submitError && (
                  <div className="export-error">
                    <p>{submitError}</p>
                  </div>
              )}
            </div>
        )}
      </div>
      <div className="trip-builder-right" ref={rightRef}>
        {voteError && (
            <p className="text-error">
              Couldn't load your group's vote results. Refresh the page to try again.
            </p>
        )}
        {voteResult && voteResult.suggestions && voteResult.suggestions.length > 0 && (
            <div className="trip-vote-suggestions">
              <h3>Group suggestions</h3>
              <p className="trip-vote-suggestions-sub">From your group&apos;s quiz answers</p>
              <div className="browse-activities">
                {voteResult.suggestions.map(s => {
                    const isAdded = state.tripItems.some(item => item.id === s.activityId);
                    return (
                        <div key={s.activityId} className="browse-activity-item">
                          {s.imageUrl && (
                              <img src={s.imageUrl} alt={s.name}
                                   className="browse-activity-image" loading="lazy"/>
                          )}
                          <div className="browse-activity-content">
                            <div className="browse-activity-title">{s.name}</div>
                            <div className="browse-activity-price">{formatPricePerPerson(s.price)}</div>
                          </div>
                          <button
                              className="browse-add-btn"
                              onClick={() => handleAddActivity({
                                  id: s.activityId,
                                  name: s.name,
                                  price: s.price,
                                  slug: s.slug,
                                  destinationSlug: s.destinationSlug,
                                  imageUrl: s.imageUrl,
                                  description: s.description,
                                  includes: s.includes,
                                  categories: (s.categories || []).map(name => ({ name })),
                              })}
                              disabled={isAdded}
                          >
                            {isAdded ? 'Added' : 'Add'}
                          </button>
                        </div>
                    );
                })}
              </div>
            </div>
        )}
        <div className="browse-header">
          <h3>Browse More Activities</h3>
          <div className="filter-group">
            <div className="browse-filters">
              <button
                  key="all"
                  className={`filter-btn ${browseFilter === 'all' ? 'active' : ''}`}
                  onClick={() => setBrowseFilter('all')}
              >
                All
              </button>
              {(showAllCategories ? categories : categories.slice(0, VISIBLE_CATEGORY_COUNT)).map(category => (
                  <button
                      key={category.slug}
                      className={`filter-btn ${browseFilter === category.slug ? 'active' : ''}`}
                      onClick={() => setBrowseFilter(category.slug)}
                  >
                    {capitalizeFirst(category.name)}
                  </button>
              ))}
            </div>
            {categories.length > VISIBLE_CATEGORY_COUNT && (
                <button
                    type="button"
                    className="filter-toggle"
                    onClick={() => setShowAllCategories(!showAllCategories)}
                >
                  {showAllCategories ? 'Show less' : `Show all (${categories.length})`}
                </button>
            )}
          </div>
        </div>
        <div className="browse-activities">
          {filteredBrowseActivities.map(activity => {
            const isAdded = state.tripItems.some(item => item.id === activity.id);
            return (
                <div key={activity.id} className="browse-activity-item">
                  <img src={activity.imageUrl} alt={activity.name}
                       className="browse-activity-image" loading="lazy"/>
                  <div className="browse-activity-content">
                    <div className="browse-activity-title">{activity.name}</div>
                    <div className="browse-activity-price">{formatPricePerPerson(activity.price)}</div>
                  </div>
                  <button
                      className="browse-add-btn"
                      onClick={() => handleAddActivity(activity)}
                      disabled={isAdded}
                  >
                    {isAdded ? 'Added' : 'Add'}
                  </button>
                </div>
            );
          })}
        </div>
      </div>

      <ContactForm
          isOpen={showContactForm}
          onClose={() => setShowContactForm(false)}
          onSubmit={handleContactSubmit}
          tripData={{tripItems: state.tripItems, travelers}}
          initialValues={{
            numberOfTravelers: travelers,
            startDate: state.tripStartDate,
            endDate: state.tripEndDate
          }}
          isSubmitting={isSubmitting}
          submitError={submitError}
      />

      <SuccessModal
          isOpen={showSuccessModal}
          onClose={() => setShowSuccessModal(false)}
          userName={successContactData?.fullName || 'Traveler'}
          userEmail={successContactData?.email || ''}
      />
    </div>
  );
}

export default TripBuilder;
