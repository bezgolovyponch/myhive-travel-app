import {useEffect, useRef, useState} from 'react';
import {useSearchParams} from 'react-router-dom';
import {useTrip} from '../context/TripContext';
import api from '../services/api';
import voteApi from '../services/voteApi';
import {paymentApi} from '../services/paymentApi';
import {capitalizeFirst, formatDate, formatPrice, formatPricePerPerson} from '../utils/format';
import {computeTripTotal, groupTripItems} from '../utils/tripPricing';
import {pushEvent} from '../utils/analytics';
import {getAttribution, getRef} from '../utils/attribution';
import {generateUuid} from '../utils/uuid';
import ContactForm from './ContactForm';
import SuccessModal from './SuccessModal';
import StartGroupVoteModal from './vote/StartGroupVoteModal';
import './TripBuilder.css';

const VISIBLE_CATEGORY_COUNT = 12;

// Cart vote results carry per-activity like counts instead of a single winner —
// reduce them into a lookup keyed by activityId, alongside the participant
// count needed to size each item's mini progress bar.
function buildVoteAnnotation(result) {
  const counts = {};
  (result.result || []).forEach(row => {
    counts[row.activityId] = row.likeCount;
  });
  return {counts, participantCount: result.participantCount};
}

function TripBuilder({ destinationId, destinationSlug }) {
  const {state, dispatch} = useTrip();
  const [browseFilter, setBrowseFilter] = useState('all');
  const [categories, setCategories] = useState([]);
  const [browseActivities, setBrowseActivities] = useState([]);
  const [showAllCategories, setShowAllCategories] = useState(false);
  const [showContactForm, setShowContactForm] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);
  // Resolved trip id for the current booking session — computed when the form opens.
  const [effectiveTripId, setEffectiveTripId] = useState(null);
  const [showSuccessModal, setShowSuccessModal] = useState(false);
  const [successContactData, setSuccessContactData] = useState(null);
  const [voteResult, setVoteResult] = useState(null);
  const [voteError, setVoteError] = useState(false);
  const [showVoteModal, setShowVoteModal] = useState(false);

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
  // Annotation token: an explicit URL param (shared link) takes priority, else
  // fall back to the vote session this browser itself started (read once on
  // mount — StartGroupVoteModal writes it, handleContactSubmit clears it).
  const [storedVoteSession] = useState(() => localStorage.getItem('myhive-trip-vote-session'));
  const annotationToken = voteSession || storedVoteSession;
  // Cart vote annotation (counts + participantCount) — display-only, never
  // mutates tripItems/localStorage. Set for CART sessions; QUIZ sessions
  // instead hydrate the cart itself via voteResult/dispatch below.
  const [voteAnnotation, setVoteAnnotation] = useState(null);

  useEffect(() => {
    if (!annotationToken) return;
    let cancelled = false;
    setVoteError(false);
    voteApi.getResult(annotationToken)
        .then(result => {
            if (cancelled) return;
            if (result.voteMode === 'CART') {
                // Cart votes annotate the initiator's existing cart — they never seed items.
                setVoteAnnotation(buildVoteAnnotation(result));
                return;
            }
            if (!voteSession) return; // QUIZ hydration only ever runs from an explicit URL param
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
            // Map activityId → id so TripContext keys it the same as live activities.
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
        .catch(e => {
            if (cancelled) return;
            if (e.message === 'Result not available yet') {
                return; // vote still running — nothing to annotate yet
            }
            if (voteSession) {
                setVoteError(true);
            }
        });
    return () => {
        cancelled = true;
    };
  }, [annotationToken, voteSession, dispatch]);

  // A16b: fire trip_builder_viewed (→ Meta InitiateCheckout) once the user lands
  // on the trip-builder/checkout screen with a non-empty trip — one funnel step
  // earlier than booking_form_viewed (which fires on the "Complete Booking"
  // click). A ref guards within a mount; per-trip sessionStorage guards across
  // mounts so it fires at most once per trip.
  const checkoutViewedRef = useRef(false);
  useEffect(() => {
    if (checkoutViewedRef.current || state.tripItems.length === 0) {
      return;
    }
    let tripId = voteSession || state.tripId;
    if (!tripId) {
      tripId = generateUuid();
      dispatch({ type: 'SET_TRIP_ID', tripId });
    }
    checkoutViewedRef.current = true;
    const viewedKey = `myhive-tb-viewed-${tripId}`;
    if (sessionStorage.getItem(viewedKey)) {
      return;
    }
    sessionStorage.setItem(viewedKey, '1');
    pushEvent('trip_builder_viewed', {
      trip_id: tripId,
      value: computeTripTotal(state.tripItems, state.tripTravelers || 1),
      currency: 'EUR',
      items_count: state.tripItems.length,
    });
  }, [state.tripItems, state.tripId, state.tripTravelers, voteSession, dispatch]);

  const handleRemoveActivity = (activityId) => {
    dispatch({ type: 'REMOVE_FROM_TRIP', activityId });
  };

  const handleAddActivity = (activity) => {
      dispatch({type: 'ADD_TO_TRIP', activity, silent: true});
  };

  const handleConfirmTrip = () => {
    // Resolve the trip_id: voteSession (share token) takes priority, then the
    // client-minted id from TripContext, then mint a fresh one as a last resort.
    let tripId = voteSession || state.tripId;
    if (!tripId) {
      tripId = generateUuid();
      dispatch({ type: 'SET_TRIP_ID', tripId });
    }
    setEffectiveTripId(tripId);

    // A17: fire booking_form_viewed at most once per trip_id (sessionStorage dedup
    // prevents double-fire on rapid double-clicks for the same trip).
    const formViewedKey = `myhive-form-viewed-${tripId}`;
    if (!sessionStorage.getItem(formViewedKey)) {
      sessionStorage.setItem(formViewedKey, '1');
      const tripTotal = computeTripTotal(state.tripItems, travelers);
      pushEvent('booking_form_viewed', { trip_id: tripId, value: tripTotal, currency: 'EUR' });
    }

    setShowContactForm(true);
  };

  // Shared trip → booking payload, used by both the lead ("Submit Booking") and the 30% deposit flow.
  const buildBookingData = (contactData) => {
    const attribution = getAttribution();
    return {
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
      notes: `Full Name: ${contactData.fullName} | Special requirements: ${contactData.specialRequirements || 'None'} | Contact method: ${contactData.contactMethod} | Number of travelers: ${contactData.numberOfTravelers}`,
      // A19: thread trip_id and attribution into the request body so the backend
      // can tie campaign data to the booking (utm → trip_id → money chain).
      tripId: effectiveTripId,
      ...attribution,
      ref: getRef(),
    };
  };

  const handleContactSubmit = async (contactData) => {
    if (state.tripItems.length === 0) {
      alert('Please add some activities to your trip before submitting.');
      return;
    }

    setIsSubmitting(true);
    setSubmitError(null);

    try {
      const bookingData = buildBookingData(contactData);

      await api.createBookingFromTrip(bookingData);

      // A18: fire booking_submitted exactly once per trip_id (sessionStorage dedup
      // prevents re-firing on re-render or accidental double-submit).
      // Use the submitted traveler count so the event matches the actual booking.
      const submittedTravelers = parseInt(contactData.numberOfTravelers, 10) || 1;
      const dedupKey = `myhive-booked-${effectiveTripId}`;
      if (!sessionStorage.getItem(dedupKey)) {
        sessionStorage.setItem(dedupKey, '1');
        const destinationSlug = state.tripItems[0]?.destinationSlug || '';
        pushEvent('booking_submitted', {
          trip_id: effectiveTripId,
          value: computeTripTotal(state.tripItems, submittedTravelers),
          currency: 'EUR',
          activities_count: state.tripItems.length,
          destination: destinationSlug,
          group_size: submittedTravelers,
          ...getAttribution(),
          ref: getRef(),
        });
      }

      dispatch({ type: 'CANCEL_TRIP_SETUP' });
      dispatch({ type: 'UPDATE_TRIP_TRAVELERS', travelers: 1 });
      dispatch({ type: 'UPDATE_TRIP_DATES', startDate: '', endDate: '' });
      dispatch({ type: 'CLOSE_TRIP_BUILDER_MODAL' });
      localStorage.removeItem('myhive-trip-vote-session');
      setVoteAnnotation(null);
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

  const handleDepositSubmit = async (contactData, turnstileToken) => {
    if (state.tripItems.length === 0) {
      alert('Please add some activities to your trip before submitting.');
      return;
    }

    setIsSubmitting(true);
    setSubmitError(null);

    try {
      const bookingData = buildBookingData(contactData);
      const {checkoutUrl} = await paymentApi.createTripDepositSession(bookingData, turnstileToken);
      // Hand off to Stripe Checkout; keep isSubmitting true through the redirect so the modal stays locked.
      window.location.assign(checkoutUrl);
    } catch (error) {
      console.error('Deposit checkout error:', error);
      setSubmitError(error.message || 'Failed to start payment. Please try again.');
      setIsSubmitting(false);
    }
  };

  const travelers = state.tripTravelers || 1;

  const {standalone, groups: groupsArray} = groupTripItems(state.tripItems);
  // Display-only ranking for a completed cart vote — ties/unballoted items keep
  // cart order (stable sort), unballoted land last via the `?? -1` fallback.
  // tripItems state/localStorage are never reordered.
  const sortedStandalone = voteAnnotation
      ? [...standalone].sort((a, b) =>
          (voteAnnotation.counts[b.id] ?? -1) - (voteAnnotation.counts[a.id] ?? -1))
      : standalone;
  const hasForeignStandalone = !!destinationSlug
      && standalone.some(item => item.destinationSlug && item.destinationSlug !== destinationSlug);
  const canStartVote = standalone.length > 0 && !hasForeignStandalone;
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
              {sortedStandalone.map(item => (
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
                    {voteAnnotation && voteAnnotation.counts[item.id] != null && (
                        <div className="itinerary-item-votes">
                          <span className="itinerary-item-votes-count">♥ {voteAnnotation.counts[item.id]}</span>
                          <span className="itinerary-item-votes-bar">
                            <span
                                className="itinerary-item-votes-fill"
                                style={{width: `${Math.min(100,
                                    (voteAnnotation.counts[item.id]
                                        / Math.max(1, voteAnnotation.participantCount)) * 100)}%`}}
                            />
                          </span>
                        </div>
                    )}
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
              {standalone.length > 0 && (
                  <button
                      type="button"
                      className="btn btn--full-width start-vote-btn"
                      onClick={() => setShowVoteModal(true)}
                      disabled={!canStartVote}
                      title={hasForeignStandalone
                          ? 'Group voting works for one destination at a time — remove activities from other destinations first.'
                          : undefined}
                  >
                    Let your mates vote
                  </button>
              )}
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
          submitLabel="Complete booking — we'll call you"
          onDepositSubmit={handleDepositSubmit}
          depositLabel="Complete booking & pay 30% deposit"
          tripData={{tripItems: state.tripItems, travelers}}
          initialValues={{
            numberOfTravelers: travelers,
            startDate: state.tripStartDate,
            endDate: state.tripEndDate
          }}
          isSubmitting={isSubmitting}
          submitError={submitError}
      />

      <StartGroupVoteModal
          isOpen={showVoteModal}
          onClose={() => setShowVoteModal(false)}
          destinationId={destinationId}
          activityIds={standalone.map(item => item.id)}
          numberOfTravelers={travelers}
          startDate={state.tripStartDate}
          endDate={state.tripEndDate}
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
