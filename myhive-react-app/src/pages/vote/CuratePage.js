import {useEffect, useMemo, useRef, useState} from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import voteApi from '../../services/voteApi';
import { getOrCreateVoterToken } from '../../utils/voterToken';
import SwipeCard from '../../components/SwipeCard';
import ActivityPreviewModal from '../../components/ActivityPreviewModal';
import { formatPricePerPerson } from '../../utils/format';
import {useTrip} from '../../context/TripContext';
import { pushEvent } from '../../utils/analytics';
import { generateUuid } from '../../utils/uuid';
import VoteMeta from './VoteMeta';
import './CuratePage.css';

function CurateContent() {
  const location = useLocation();
  const navigate = useNavigate();
  const {dispatch} = useTrip();
  const setup = location.state?.setup;
  // Stable reference so the effect below doesn't re-run on every render
  // (the ?? [] fallback would otherwise be a fresh array each time).
  const responses = useMemo(() => location.state?.responses ?? [], [location.state]);

  const [pool, setPool] = useState(null);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);
  const [selected, setSelected] = useState(null);
  const pickedRef = useRef([]);
  // A11 once-guard: tracks whether shortlist_completed has fired for the current
  // swipe session. Reset to false on start-over (via currentIndex reset).
  const shortlistFiredRef = useRef(false);

  // Set when returning here via the browser back button (see handleBuildMyTrip):
  // the finalized state is stashed in the history entry so we can restore it.
  const restoreSnapshot = location.state?.snapshot;

  useEffect(() => {
    if (!setup) {
      navigate('/');
      return;
    }
    if (restoreSnapshot) {
      // Snapshot pool items already carry `.id` (remapped on the initial load
      // below before being stashed), so no remap is needed here.
      pickedRef.current = restoreSnapshot.picked;
      setPool(restoreSnapshot.pool);
      setCurrentIndex(restoreSnapshot.currentIndex);
      return;
    }
    let cancelled = false;
    async function load() {
      try {
        const data = await voteApi.buildPool({
          destinationId: setup.destination.id,
          responses,
        });
        if (cancelled) {
          return;
        }
        // SwipeCard expects card.id; the pool DTO ships activityId. Remap.
        const mapped = (data.pool || []).map(a => ({ ...a, id: a.activityId }));
        setPool(mapped);
      } catch (e) {
        if (!cancelled) {
          setError(e.message);
        }
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, [setup, responses, navigate, restoreSnapshot]);

  // A11 — shortlist_completed: fire once when the organizer finishes swiping all
  // cards (finalize screen appears). The once-guard prevents double-firing on
  // re-renders. Reset the guard whenever currentIndex drops back to 0 (start-over).
  const isFinalized = pool !== null && pool.length > 0 && currentIndex >= pool.length;
  useEffect(() => {
    if (currentIndex === 0) {
      shortlistFiredRef.current = false;
    }
  }, [currentIndex]);
  useEffect(() => {
    if (isFinalized && !shortlistFiredRef.current) {
      shortlistFiredRef.current = true;
      pushEvent('shortlist_completed', { selected_count: pickedRef.current.length });
    }
  }, [isFinalized]);

  const getCardLink = (activity) => {
    if (!activity || !activity.slug || !activity.destinationSlug) {
      return null;
    }
    return `/destination/${activity.destinationSlug}/activity/${activity.slug}`;
  };

  const handleSwipe = (direction, activityId) => {
    if (direction === 'right') {
      pickedRef.current = [...pickedRef.current, activityId];
    }
    setCurrentIndex(prev => prev + 1);
  };

  const handleUndo = () => {
    if (currentIndex === 0) {
      return;
    }
    const prevId = pool[currentIndex - 1].id;
    pickedRef.current = pickedRef.current.filter(id => id !== prevId);
    setCurrentIndex(prev => prev - 1);
  };

  const handleStartOver = () => {
    pickedRef.current = [];
    setCurrentIndex(0);
    setError(null);
    if (restoreSnapshot) {
      // Drop the stashed snapshot so a later remount (e.g. a refresh) shows the
      // fresh deck instead of jumping back to the finalized list.
      navigate(location.pathname + location.search, {
        replace: true,
        state: { setup, responses },
      });
    }
  };

  const handleBuildMyTrip = () => {
    const picked = pool.filter(a => pickedRef.current.includes(a.id));
    if (picked.length === 0) {
      return;
    }
    // A13 — vote_skipped: organizer is leaving the vote flow to build a trip
    // directly. Mint a client-side trip_id so the direct-book funnel is tracked.
    const tripId = generateUuid();
    dispatch({ type: 'SET_TRIP_ID', tripId });
    pushEvent('vote_skipped', { trip_id: tripId, selected_count: pickedRef.current.length });
    dispatch({ type: 'UPDATE_TRIP_TRAVELERS', travelers: setup.travelers });
    dispatch({
      type: 'UPDATE_TRIP_DATES',
      startDate: setup.startDate ?? '',
      endDate: setup.endDate ?? '',
    });
    dispatch({ type: 'UPDATE_TRIP_BUDGET', budget: setup.budget ?? null });
    picked.forEach(a => {
      dispatch({
        type: 'ADD_TO_TRIP',
        silent: true,
        activity: {
          id: a.id,
          name: a.name,
          price: a.price,
          slug: a.slug,
          destinationSlug: a.destinationSlug,
          imageUrl: a.imageUrl,
          categories: (a.categories || []).map(name => ({ name })),
        },
      });
    });
    // Stash the finalized state in this history entry so the browser back
    // button returns the organizer to their list instead of the swipe deck.
    navigate(location.pathname + location.search, {
      replace: true,
      state: { setup, responses, snapshot: { pool, currentIndex, picked: pickedRef.current } },
    });
    navigate(`/destination/${setup.destination.slug}?tab=trip-builder`);
  };

  const handleCreate = async () => {
    if (pickedRef.current.length === 0 || submitting) {
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const session = await voteApi.createSession({
        destinationId: setup.destination.id,
        initiatorEmail: setup.email,
        numberOfTravelers: setup.travelers,
        startDate: setup.startDate,
        endDate: setup.endDate,
        budget: setup.budget,
        voterToken: getOrCreateVoterToken(),
        quizResponses: responses,
        activityIds: pickedRef.current,
      });
      // Mark this browser as the organizer so VoteWaitingPage shows the
      // "End voting early" button and remembers the managerToken.
      localStorage.setItem(`myhive-initiator-${session.shareToken}`, 'true');
      if (session.managerToken) {
        localStorage.setItem(`myhive-manager-${session.shareToken}`, session.managerToken);
      }
      // A12 — vote_launched: session created successfully; shareToken is the trip_id.
      pushEvent('vote_launched', {
        trip_id: session.shareToken,
        user_role: 'organizer',
        selected_count: pickedRef.current.length,
      });
      navigate(`/vote/${session.shareToken}/waiting`, {
        state: { managerToken: session.managerToken },
      });
    } catch (e) {
      setError(e.message);
      setSubmitting(false);
    }
  };

  if (error && !pool) {
    return <div className="curate-page-error">{error}</div>;
  }
  if (!pool) {
    return <div className="curate-page-loading">Loading pool…</div>;
  }
  if (pool.length === 0) {
    return <div className="curate-page-empty">No activities match your quiz. Try a different destination.</div>;
  }

  // Finalize step — all cards have been swiped.
  if (currentIndex >= pool.length) {
    const pickedActivities = pool.filter(a => pickedRef.current.includes(a.id));
    return (
      <div className="curate-finalize">
        <h2>Your voting list ({pickedActivities.length})</h2>
        {pickedActivities.length === 0 ? (
          <p className="curate-finalize-empty">
            You didn&apos;t pick anything. Start over and swipe right on what the group should vote on.
          </p>
        ) : (
          <div className="curate-finalize-grid">
            {pickedActivities.map(a => (
              <div key={a.id} className="curate-finalize-card">
                {a.imageUrl && (
                  <img src={a.imageUrl} alt={a.name} className="curate-finalize-card-image" />
                )}
                <div className="curate-finalize-card-body">
                  <div className="curate-finalize-card-name">
                    <button
                      type="button"
                      className="curate-finalize-card-link"
                      aria-haspopup="dialog"
                      onClick={() => setSelected(a)}
                    >
                      {a.name}
                    </button>
                  </div>
                  <div className="curate-finalize-card-price">{formatPricePerPerson(a.price)}</div>
                  {a.categories && a.categories.length > 0 && (
                    <div className="curate-finalize-card-cats">{a.categories.join(' · ')}</div>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
        {error && <p className="curate-finalize-error">{error}</p>}
        <div className="curate-finalize-actions">
          <button type="button" className="curate-finalize-reset" onClick={handleStartOver}>
            Start over
          </button>
          <button
            type="button"
            className="curate-finalize-build"
            disabled={pickedActivities.length === 0}
            onClick={handleBuildMyTrip}
          >
            Build my own trip
          </button>
          <button
            type="button"
            className="curate-finalize-create"
            disabled={pickedActivities.length === 0 || submitting}
            onClick={handleCreate}
          >
            {submitting ? 'Creating…' : 'Create & get link'}
          </button>
        </div>
        <ActivityPreviewModal
          activity={selected}
          link={selected ? getCardLink(selected) : null}
          onClose={() => setSelected(null)}
        />
      </div>
    );
  }

  return (
    <SwipeCard
      cards={pool}
      currentIndex={currentIndex}
      onSwipe={handleSwipe}
      onUndo={handleUndo}
      canUndo={currentIndex > 0}
      title="Pick activities for the group to vote on"
      subtitle="Swipe right to include, left to skip"
      getCardLink={getCardLink}
    />
  );
}

export default function CuratePage() {
    return (
        <>
            <VoteMeta title="Pick activities"/>
            <CurateContent/>
        </>
    );
}
