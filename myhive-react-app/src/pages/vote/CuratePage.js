import {useEffect, useMemo, useRef, useState} from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import voteApi from '../../services/voteApi';
import SwipeCard from '../../components/SwipeCard';
import {useTrip} from '../../context/TripContext';
import { pushEvent } from '../../utils/analytics';
import { writeQuizFlow } from '../../utils/quizFlow';
import VoteMeta from './VoteMeta';
import './CuratePage.css';

function CurateContent() {
  const location = useLocation();
  const navigate = useNavigate();
  const {dispatch} = useTrip();
  const setup = location.state?.setup;
  // Stable reference so the effects below don't re-run on every render
  // (the ?? [] fallback would otherwise be a fresh array each time).
  const responses = useMemo(() => location.state?.responses ?? [], [location.state]);

  const [pool, setPool] = useState(null);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [error, setError] = useState(null);
  const pickedRef = useRef([]);
  // Once-guard for the deck-completion handoff (A11 analytics + navigation).
  // Reset whenever the deck restarts (currentIndex back to 0 on start-over).
  const completionHandledRef = useRef(false);

  useEffect(() => {
    if (!setup) {
      navigate('/');
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
  }, [setup, responses, navigate]);

  const isComplete = pool !== null && pool.length > 0 && currentIndex >= pool.length;

  useEffect(() => {
    if (currentIndex === 0) {
      completionHandledRef.current = false;
    }
  }, [currentIndex]);

  // Deck exhausted: fire A11 shortlist_completed once, and with ≥1 pick seed
  // the trip and land the organizer straight in the Trip Builder (the old
  // finalize screen is gone). replace:true so the browser Back button returns
  // to the quiz — a spent deck would be a dead end.
  useEffect(() => {
    if (!isComplete || completionHandledRef.current) {
      return;
    }
    completionHandledRef.current = true;
    const picked = pool.filter(a => pickedRef.current.includes(a.id));
    pushEvent('shortlist_completed', { selected_count: picked.length });
    if (picked.length === 0) {
      return; // no picks — stay here and offer a restart
    }
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
    writeQuizFlow({ setup, responses });
    navigate(`/destination/${setup.destination.slug}?tab=trip-builder`, { replace: true });
  }, [isComplete, pool, setup, responses, dispatch, navigate]);

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

  if (isComplete) {
    if (pickedRef.current.length > 0) {
      // Handoff in flight — the completion effect above is navigating away.
      return <div className="curate-page-loading">Building your trip…</div>;
    }
    return (
      <div className="curate-finalize">
        <p className="curate-finalize-empty">
          You didn&apos;t pick anything. Start over and swipe right on what the group should vote on.
        </p>
        <button type="button" className="curate-finalize-reset" onClick={handleStartOver}>
          Start over
        </button>
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
