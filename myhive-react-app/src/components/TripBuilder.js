import {useEffect, useRef, useState} from 'react';
import {useNavigate, useSearchParams} from 'react-router-dom';
import {useTrip} from '../context/TripContext';
import api from '../services/api';
import voteApi from '../services/voteApi';
import {capitalizeFirst, formatDate, formatPrice, formatPricePerPerson} from '../utils/format';
import {computeTripTotal, groupMinApplied, groupTripItems, lineTotal} from '../utils/tripPricing';
import {pushEvent} from '../utils/analytics';
import {resolveUserRole} from '../utils/userRole';
import {getAttribution, getRef} from '../utils/attribution';
import {generateUuid} from '../utils/uuid';
import {clearQuizFlow, readQuizFlow} from '../utils/quizFlow';
import {clearTripLead} from '../utils/tripLead';
import {useTripLeadRestore} from '../hooks/useTripLeadRestore';
import {useEmailLeadCapture} from '../hooks/useEmailLeadCapture';
import ContactForm from './ContactForm';
import SuccessModal from './SuccessModal';
import StartGroupVoteModal from './vote/StartGroupVoteModal';
import ActiveVoteModal from './vote/ActiveVoteModal';
import ActivityPreviewModal from './ActivityPreviewModal';
import AppModal from './AppModal';
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

function TripBuilder({ destinationId, destinationSlug, destinationName }) {
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
  // Id of the booking just created by the lead submit — anchors the success screen's deposit CTA.
  const [successBookingId, setSuccessBookingId] = useState(null);
  const [voteResult, setVoteResult] = useState(null);
  const [voteError, setVoteError] = useState(false);
  const [showVoteModal, setShowVoteModal] = useState(false);
  const [activeVoteToken, setActiveVoteToken] = useState(null);
  const [checkingVote, setCheckingVote] = useState(false);
  // Organizer quiz-flow handoff (CuratePage writes it): active only for the
  // destination the quiz ran for — other destinations get the plain builder.
  const [quizFlow, setQuizFlow] = useState(() => readQuizFlow());
  const {pendingRestore, confirmRestore, cancelRestore} = useTripLeadRestore(flow => setQuizFlow(flow));
  const quizMode = quizFlow != null && quizFlow.setup?.destination?.id === destinationId;
  const captureCheckoutEmail = useEmailLeadCapture({
    destinationId,
    numberOfTravelers: state.tripTravelers || 1,
    startDate: state.tripStartDate || null,
    endDate: state.tripEndDate || null,
    budget: state.tripBudget,
  });
  const [recommended, setRecommended] = useState([]);
  const [previewActivity, setPreviewActivity] = useState(null);
  const navigate = useNavigate();

  // Quiz-flow recommendations: the quiz-matched pool for this destination,
  // left-swiped cards included on purpose (second look). In-cart items render
  // as a disabled "Added". Failures are silent — the browse column still works.
  useEffect(() => {
    if (!quizMode) {
      setRecommended([]);
      return;
    }
    let cancelled = false;
    voteApi.buildPool({ destinationId, responses: quizFlow.responses })
        .then(data => {
          if (!cancelled) {
            setRecommended((data.pool || []).map(a => ({ ...a, id: a.activityId })));
          }
        })
        .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [quizMode, quizFlow, destinationId]);

  // The booking form now replaces the browse section at the bottom of the main
  // column. On mobile that's below the fold — scroll it into view. Called from
  // every "Complete Booking" click, not an on-open effect: a repeat click while
  // the form is already open must scroll back down too. The form mounts only
  // once showContactForm flips true, so defer to the next frame — on the first
  // click the ref is still null on this tick.
  const bookingFormRef = useRef(null);
  const scrollBookingFormIntoView = () => {
    if (!window.matchMedia('(max-width: 768px)').matches) {
      return; // desktop shows the form inline with the sticky rail visible
    }
    const scrollNow = () => {
      const form = bookingFormRef.current;
      if (!form) {
        return;
      }
      const top = form.getBoundingClientRect().top + window.scrollY - 12;
      window.scrollTo({top, behavior: 'smooth'});
    };
    // rAF when available (browser); fall back to a sync call (jsdom/tests, where
    // the form is already mounted on a repeat click and geometry is static).
    if (typeof window.requestAnimationFrame === 'function') {
      window.requestAnimationFrame(scrollNow);
    } else {
      scrollNow();
    }
  };

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

  const [searchParams, setSearchParams] = useSearchParams();
  // Depend on the token string, not the searchParams object — its identity
  // changes on every navigation (e.g. ?tab= switches) and re-running this
  // effect would re-seed travelers/dates/budget over the user's edits.
  const voteSession = searchParams.get('voteSession');
  // Annotation token: an explicit URL param (shared link) takes priority, else
  // fall back to the vote session this browser itself started (read once on
  // mount — StartGroupVoteModal writes it, handleContactSubmit and the
  // emptied-cart reset below clear it).
  const [storedVoteSession, setStoredVoteSession] = useState(() => localStorage.getItem('myhive-trip-vote-session'));
  const annotationToken = voteSession || storedVoteSession;
  // Adds user_role to a funnel-event payload when a vote token is involved;
  // plain trips (no annotationToken) get no user_role field at all.
  const withUserRole = (payload) => (annotationToken
      ? { ...payload, user_role: resolveUserRole(annotationToken) }
      : payload);
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
                        minPrice: row.minPrice,
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
            if (e.message === 'Vote session not found' && !voteSession) {
                // Storage-only token (no URL param): the backend's 7-day session
                // cleanup already removed this session. Self-heal by dropping the
                // stale key so future mounts stop re-fetching a session that's gone.
                localStorage.removeItem('myhive-trip-vote-session');
                return;
            }
            if (voteSession) {
                setVoteError(true);
            }
        });
    return () => {
        cancelled = true;
    };
  }, [annotationToken, voteSession, dispatch]);

  // A finished vote (CART annotation or hydrated QUIZ result) hides the vote
  // button (see voteEnded below) until the trip is booked or the initiator
  // empties the cart. Emptying the cart is the reset: drop the finished
  // session everywhere it's remembered (storage, state, URL param) so the
  // next itinerary can start a fresh vote — and so a page refresh doesn't
  // resurrect the parked state.
  useEffect(() => {
    if ((!voteAnnotation && !voteResult) || state.tripItems.length > 0) {
      return;
    }
    localStorage.removeItem('myhive-trip-vote-session');
    setStoredVoteSession(null);
    setVoteAnnotation(null);
    setVoteResult(null);
    if (voteSession) {
      setSearchParams(params => {
        params.delete('voteSession');
        return params;
      }, {replace: true});
    }
  }, [voteAnnotation, voteResult, state.tripItems.length, voteSession, setSearchParams]);

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
    // Resolve the trip_id: the full annotation token (URL param or stored CART
    // session — see annotationToken above) takes priority, then the client-minted
    // id from TripContext, then mint a fresh one as a last resort.
    let tripId = annotationToken || state.tripId;
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
    pushEvent('trip_builder_viewed', withUserRole({
      trip_id: tripId,
      value: computeTripTotal(state.tripItems, state.tripTravelers || 1),
      currency: 'EUR',
      items_count: state.tripItems.length,
    }));
    // withUserRole is derived solely from annotationToken (already a dep below)
    // and the stable resolveUserRole import, so it's safe to omit here.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state.tripItems, state.tripId, state.tripTravelers, annotationToken, dispatch]);

  const handleRemoveActivity = (activityId) => {
    dispatch({ type: 'REMOVE_FROM_TRIP', activityId });
  };

  const handleAddActivity = (activity) => {
      dispatch({type: 'ADD_TO_TRIP', activity, silent: true});
  };

  const getPreviewLink = (activity) => {
    if (!activity || !activity.slug || !activity.destinationSlug) {
      return null;
    }
    return `/destination/${activity.destinationSlug}/activity/${activity.slug}`;
  };

  // Used by handleStartVoteClick (both CART and quiz-mode vote modals): true —
  // and pops the "vote already running" modal — if `token` points at a CART
  // vote that's still ACTIVE. A failed lookup (session deleted/404 or a
  // transient network error) self-heals by dropping the stale key and
  // returns false so the caller falls through to opening the vote modal,
  // whose own create call will surface any real error.
  const isActiveCartVoteSession = async (token) => {
    try {
      const session = await voteApi.getSession(token);
      if (session.status === 'ACTIVE' && session.voteMode === 'CART') {
        setActiveVoteToken(token);
        return true;
      }
      return false;
    } catch (e) {
      localStorage.removeItem('myhive-trip-vote-session');
      return false;
    }
  };

  // Guard against starting a second vote while one this browser started is
  // still ACTIVE — reads localStorage fresh (not the stale mount-time
  // storedVoteSession) since a vote may have been created/ended since mount.
  const handleStartVoteClick = async () => {
    // Fires on every click regardless of which modal ends up opening — this is
    // the intent signal; vote_launched (on actual session creation) is the
    // conversion. cta_label/block mirror the taxonomy HomePage/HowItWorksSection
    // already use for cta_click.
    pushEvent('cta_click', {
      cta_label: 'Let your mates vote',
      block: 'trip_builder',
    });
    if (checkingVote) {
      return;
    }
    const token = localStorage.getItem('myhive-trip-vote-session');
    if (!token) {
      setShowVoteModal(true);
      return;
    }
    setCheckingVote(true);
    try {
      if (!(await isActiveCartVoteSession(token))) {
        setShowVoteModal(true);
      }
    } finally {
      setCheckingVote(false);
    }
  };

  const handleQuizStartOver = () => {
    pushEvent('cta_click', { cta_label: 'Start Over', block: 'trip_builder' });
    dispatch({ type: 'CANCEL_TRIP_SETUP' });
    dispatch({ type: 'UPDATE_TRIP_TRAVELERS', travelers: 1 });
    dispatch({ type: 'UPDATE_TRIP_DATES', startDate: '', endDate: '' });
    clearQuizFlow();
    setQuizFlow(null);
    navigate('/vote/new/quiz', { state: { setup: quizFlow.setup } });
  };

  const startOverButton = (
    <button
      type="button"
      className="btn btn--full-width start-vote-btn"
      onClick={handleQuizStartOver}
    >
      Start Over
    </button>
  );

  const handleConfirmTrip = () => {
    // Resolve the trip_id: the full annotation token (URL param or stored CART
    // session — see annotationToken above) takes priority, then the client-minted
    // id from TripContext, then mint a fresh one as a last resort.
    let tripId = annotationToken || state.tripId;
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
      pushEvent('booking_form_viewed', withUserRole({ trip_id: tripId, value: tripTotal, currency: 'EUR' }));
    }

    // A13 — vote_skipped: in the quiz flow, heading into booking without
    // having launched a vote is the moment the organizer skips voting
    // (launching a vote clears quizFlow, so quizMode implies "no vote yet").
    // selected_count mirrors vote_launched's standalone.length — package
    // items aren't individually selectable, so they must not skew the count.
    // (standalone is declared later in this component body, but this handler
    // only runs from the "Complete Booking" onClick, after render has already
    // assigned it for this closure.)
    if (quizMode) {
      const skipKey = `myhive-vote-skipped-${tripId}`;
      if (!sessionStorage.getItem(skipKey)) {
        sessionStorage.setItem(skipKey, '1');
        pushEvent('vote_skipped', { trip_id: tripId, selected_count: standalone.length });
      }
    }

    setShowContactForm(true);
    scrollBookingFormIntoView();
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
          minPrice: item.minPrice ?? null,
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

      const booking = await api.createBookingFromTrip(bookingData);

      // A18: fire booking_submitted exactly once per trip_id (sessionStorage dedup
      // prevents re-firing on re-render or accidental double-submit).
      // Use the submitted traveler count so the event matches the actual booking.
      const submittedTravelers = parseInt(contactData.numberOfTravelers, 10) || 1;
      const dedupKey = `myhive-booked-${effectiveTripId}`;
      if (!sessionStorage.getItem(dedupKey)) {
        sessionStorage.setItem(dedupKey, '1');
        const destinationSlug = state.tripItems[0]?.destinationSlug || '';
        pushEvent('booking_submitted', withUserRole({
          trip_id: effectiveTripId,
          value: computeTripTotal(state.tripItems, submittedTravelers),
          currency: 'EUR',
          activities_count: state.tripItems.length,
          destination: destinationSlug,
          group_size: submittedTravelers,
          ...getAttribution(),
          ref: getRef(),
        }));
      }

      dispatch({ type: 'CANCEL_TRIP_SETUP' });
      dispatch({ type: 'UPDATE_TRIP_TRAVELERS', travelers: 1 });
      dispatch({ type: 'UPDATE_TRIP_DATES', startDate: '', endDate: '' });
      dispatch({ type: 'CLOSE_TRIP_BUILDER_MODAL' });
      localStorage.removeItem('myhive-trip-vote-session');
      clearQuizFlow();
      clearTripLead();
      // TripBuilder does not unmount on a successful booking, so a debounced
      // capture armed just before submit could otherwise fire after the
      // clear above and re-create a lead for a customer who just booked.
      captureCheckoutEmail.cancel();
      setQuizFlow(null);
      setVoteAnnotation(null);
      setVoteResult(null);
      setShowContactForm(false);
      setSuccessContactData(contactData);
      // The success screen offers a 30% deposit checkout anchored to this booking.
      setSuccessBookingId(booking?.id || null);
      setShowSuccessModal(true);
    } catch (error) {
      console.error('Booking submission error:', error);
      setSubmitError(error.message || 'Failed to submit booking. Please try again.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const travelers = state.tripTravelers || 1;

  // One price label for both standalone and package lines; shows the floored
  // total with a marker whenever the group minimum binds — including travelers = 1.
  const itemPriceLabel = (item) => {
    if (groupMinApplied(item, travelers)) {
      return `${formatPrice(item.price)} × ${travelers} = ${formatPrice(lineTotal(item, travelers))} (group min)`;
    }
    return travelers > 1
        ? `${formatPrice(item.price)} × ${travelers} = ${formatPrice(item.price * travelers)}`
        : formatPricePerPerson(item.price);
  };

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
  // A loaded annotation (CART) or hydrated result (QUIZ) means this trip's
  // vote has completed — hide the vote button until booking clears the
  // session (handleContactSubmit) or the emptied-cart reset effect above
  // fires.
  const voteEnded = voteAnnotation != null || voteResult != null;
  const canStartVote = standalone.length > 0 && !hasForeignStandalone;
  let voteButtonTitle;
  if (hasForeignStandalone) {
    voteButtonTitle = 'Group voting works for one destination at a time — remove activities from other destinations first.';
  }
  const totalPrice = computeTripTotal(state.tripItems, travelers);

  const filteredBrowseActivities = browseFilter === 'all'
      ? browseActivities
      : browseActivities.filter(a => (a.categories || []).some(c => c.slug === browseFilter));

  const tripSummary = state.tripItems.length > 0 && (
      <div className="itinerary-trip-info">
        {destinationName && (
            <div className="trip-info-row">
              <label>Destination:</label>
              <span>{destinationName}</span>
            </div>
        )}
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
  );

  return (
    <div className="trip-builder-layout">
      <div className="trip-builder-main">
        <div className="itinerary-header">
          <h2>Your Itinerary</h2>
          <p>{state.tripItems.length} {state.tripItems.length === 1 ? 'activity' : 'activities'} selected</p>
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
                            {itemPriceLabel(item)}
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
                      {itemPriceLabel(item)}
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
              {quizMode && startOverButton}
            </div>
          )}
        </div>
        {/* Below the itinerary in the main column: the booking form takes over
            when active, otherwise the suggestions + Browse More Activities. */}
        {showContactForm ? (
            <div className="trip-booking-form" ref={bookingFormRef}>
              <ContactForm
                  inline
                  isOpen
                  onClose={() => setShowContactForm(false)}
                  onSubmit={handleContactSubmit}
                  submitLabel="Send booking request"
                  tripData={{tripItems: state.tripItems, travelers, destinationName}}
                  initialValues={{
                    numberOfTravelers: travelers,
                    startDate: state.tripStartDate,
                    endDate: state.tripEndDate
                  }}
                  isSubmitting={isSubmitting}
                  submitError={submitError}
                  onEmailChange={captureCheckoutEmail}
                  showConsentNote
              />
            </div>
        ) : (
        <>
        {voteError && (
            <p className="text-error">
              Couldn't load your group's vote results. Refresh the page to try again.
            </p>
        )}
        {quizMode && recommended.length > 0 && (
            <div className="trip-vote-suggestions">
              <h3>Recommended for you</h3>
              <p className="trip-vote-suggestions-sub">Based on your quiz answers</p>
              <div className="browse-activities">
                {recommended.map(a => {
                    const isAdded = state.tripItems.some(item => item.id === a.id);
                    return (
                        <div key={a.id} className="browse-activity-item">
                          {a.imageUrl && (
                              <img src={a.imageUrl} alt={a.name}
                                   className="browse-activity-image" loading="lazy"/>
                          )}
                          <div className="browse-activity-content">
                            <button
                                type="button"
                                className="browse-activity-title browse-activity-link"
                                aria-haspopup="dialog"
                                onClick={() => setPreviewActivity(a)}
                            >
                              {a.name}
                            </button>
                            <div className="browse-activity-price">{formatPricePerPerson(a.price)}</div>
                          </div>
                          <button
                              className="browse-add-btn"
                              onClick={() => handleAddActivity({
                                  id: a.id,
                                  name: a.name,
                                  price: a.price,
                                  minPrice: a.minPrice,
                                  slug: a.slug,
                                  destinationSlug: a.destinationSlug,
                                  imageUrl: a.imageUrl,
                                  description: a.description,
                                  includes: a.includes,
                                  categories: (a.categories || []).map(name => ({ name })),
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
                                  minPrice: s.minPrice,
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
        </>
        )}
      </div>

      {/* Sticky summary + CTA rail — a side rail on desktop, a pinned bottom
          bar on mobile. Carries the trip summary, budget, total, and both the
          "Start group vote" and "Complete Booking" actions. */}
      <aside className="trip-builder-rail">
        {tripSummary}
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
              {standalone.length > 0 && !voteEnded && (
                  <button
                      type="button"
                      className="btn btn--full-width start-vote-btn"
                      onClick={handleStartVoteClick}
                      disabled={!canStartVote || checkingVote}
                      title={voteButtonTitle}
                  >
                    Start group vote
                  </button>
              )}
              <button className="btn btn--primary btn--full-width confirm-btn" onClick={handleConfirmTrip}>
                Complete Booking
              </button>
              {quizMode && startOverButton}
              {submitError && (
                  <div className="export-error">
                    <p>{submitError}</p>
                  </div>
              )}
            </div>
        )}
      </aside>

      <StartGroupVoteModal
          isOpen={showVoteModal}
          onClose={() => setShowVoteModal(false)}
          destinationId={destinationId}
          activityIds={standalone.map(item => item.id)}
          numberOfTravelers={travelers}
          startDate={state.tripStartDate}
          endDate={state.tripEndDate}
          voteMode={quizMode ? 'QUIZ' : 'CART'}
          quizResponses={quizMode ? quizFlow.responses : null}
          budget={quizMode ? state.tripBudget : null}
          onLaunched={quizMode ? () => { clearQuizFlow(); setQuizFlow(null); } : undefined}
      />

      <AppModal
        isOpen={pendingRestore != null}
        onClose={cancelRestore}
        title="Replace your current trip?"
        footer={(
          <>
            <button type="button" className="btn btn--secondary" onClick={cancelRestore}>Keep current</button>
            <button type="button" className="btn btn--primary" onClick={confirmRestore}>Open saved trip</button>
          </>
        )}
      >
        <p>Opening your saved trip will replace the activities currently in your itinerary.</p>
      </AppModal>

      <ActiveVoteModal
          isOpen={!!activeVoteToken}
          onClose={() => setActiveVoteToken(null)}
          shareToken={activeVoteToken}
      />

      <SuccessModal
          isOpen={showSuccessModal}
          onClose={() => setShowSuccessModal(false)}
          userName={successContactData?.fullName || 'Traveler'}
          userEmail={successContactData?.email || ''}
          bookingId={successBookingId}
      />

      <ActivityPreviewModal
          activity={previewActivity}
          link={previewActivity ? getPreviewLink(previewActivity) : null}
          onClose={() => setPreviewActivity(null)}
      />
    </div>
  );
}

export default TripBuilder;
