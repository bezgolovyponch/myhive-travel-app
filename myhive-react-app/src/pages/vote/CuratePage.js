import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import voteApi from '../../services/voteApi';
import { getOrCreateVoterToken } from '../../utils/voterToken';
import './CuratePage.css';

export default function CuratePage() {
  const location = useLocation();
  const navigate = useNavigate();
  const setup = location.state?.setup;
  const responses = location.state?.responses ?? [];

  const [pool, setPool] = useState(null);
  const [picked, setPicked] = useState(new Set());
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);

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
        if (!cancelled) {
          setPool(data.pool || []);
        }
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

  const togglePick = (id) => {
    const next = new Set(picked);
    if (next.has(id)) {
      next.delete(id);
    } else {
      next.add(id);
    }
    setPicked(next);
  };

  const handleCreate = async () => {
    if (picked.size === 0 || submitting) {
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
        activityIds: Array.from(picked),
      });
      navigate(`/vote/${session.shareToken}/waiting`, {
        state: { managerToken: session.managerToken },
      });
    } catch (e) {
      setError(e.message);
      setSubmitting(false);
    }
  };

  if (error) {
    return <div className="curate-page-error">{error}</div>;
  }
  if (!pool) {
    return <div className="curate-page-loading">Loading pool…</div>;
  }
  if (pool.length === 0) {
    return <div className="curate-page-empty">No activities match your quiz. Try a different destination.</div>;
  }

  return (
    <div className="curate-page">
      <h1>Pick activities for the vote</h1>
      <p className="curate-subtitle">Selected: {picked.size}</p>
      <div className="curate-grid">
        {pool.map(a => (
          <div key={a.activityId} className={`curate-card ${picked.has(a.activityId) ? 'picked' : ''}`}>
            {a.imageUrl && <img src={a.imageUrl} alt={a.name} className="curate-card-image" />}
            <div className="curate-card-body">
              <h3>{a.name}</h3>
              <p className="curate-card-price">{a.price} per person</p>
              {a.categories && a.categories.length > 0 && (
                <p className="curate-card-cats">{a.categories.join(' · ')}</p>
              )}
              <button
                type="button"
                className="curate-card-btn"
                onClick={() => togglePick(a.activityId)}
              >
                {picked.has(a.activityId) ? 'Remove' : 'Add'}
              </button>
            </div>
          </div>
        ))}
      </div>
      <button
        type="button"
        className="curate-create-btn"
        disabled={picked.size === 0 || submitting}
        onClick={handleCreate}
      >
        {submitting ? 'Creating…' : 'Create & get link'}
      </button>
    </div>
  );
}
