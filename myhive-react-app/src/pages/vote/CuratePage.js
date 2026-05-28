import { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import voteApi from '../../services/voteApi';
import { getOrCreateVoterToken } from '../../utils/voterToken';
import SwipeCard from '../../components/SwipeCard';
import './CuratePage.css';

export default function CuratePage() {
  const location = useLocation();
  const navigate = useNavigate();
  const setup = location.state?.setup;
  const responses = location.state?.responses ?? [];

  const [pool, setPool] = useState(null);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);
  const pickedRef = useRef([]);

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

  const handleSwipe = (direction, activityId) => {
    if (direction === 'right') {
      pickedRef.current = [...pickedRef.current, activityId];
    }
    setCurrentIndex(prev => prev + 1);
  };

  const handleStartOver = () => {
    pickedRef.current = [];
    setCurrentIndex(0);
    setError(null);
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
          <ul className="curate-finalize-list">
            {pickedActivities.map(a => (
              <li key={a.id}>
                {a.name} — €{a.price}/person
              </li>
            ))}
          </ul>
        )}
        {error && <p className="curate-finalize-error">{error}</p>}
        <div className="curate-finalize-actions">
          <button type="button" className="curate-finalize-reset" onClick={handleStartOver}>
            Start over
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
      </div>
    );
  }

  return (
    <SwipeCard
      cards={pool}
      currentIndex={currentIndex}
      onSwipe={handleSwipe}
      title="Pick activities for the group to vote on"
      subtitle="Swipe right to include, left to skip"
      getCardLink={null}
    />
  );
}
