import { useEffect, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import voteApi from '../../services/voteApi';
import { pushEvent } from '../../utils/analytics';
import { getOrCreateVoterToken } from '../../utils/voterToken';
import VoteMeta from './VoteMeta';
import './QuizPage.css';

function QuizContent() {
  const { shareToken } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const setup = location.state?.setup;

  const [quiz, setQuiz] = useState(null);
  const [stepIndex, setStepIndex] = useState(0);
  const [responses, setResponses] = useState([]);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const isOrganizer = !shareToken;

  useEffect(() => {
    if (isOrganizer && !setup) {
      navigate('/');
      return;
    }
    let cancelled = false;
    async function load() {
      try {
        const data = isOrganizer
          ? await voteApi.getPublicQuizForDestination(setup.destination.id)
          : await voteApi.getParticipantQuiz(shareToken);
        if (cancelled) {
          return;
        }
        if (!data.questions || data.questions.length === 0) {
          if (isOrganizer) {
            navigate('/vote/new/curate', { state: { setup, responses: [] } });
          } else {
            navigate(`/vote/${shareToken}/activities`);
          }
          return;
        }
        setQuiz(data);
      } catch (e) {
        if (!cancelled) {
          setError(e.message || 'Failed to load quiz');
        }
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, [isOrganizer, setup, shareToken, navigate]);

  if (error) {
    return <div className="quiz-page-error">{error}</div>;
  }
  if (!quiz) {
    return <div className="quiz-page-loading">Loading quiz…</div>;
  }

  const question = quiz.questions[stepIndex];

  const pickAnswer = async (answerId) => {
    const updated = [...responses, { questionId: question.id, answerId }];
    setResponses(updated);
    if (stepIndex + 1 < quiz.questions.length) {
      setStepIndex(stepIndex + 1);
      return;
    }
    const quizParams = {
      q_daytime: updated[0]?.answerId,
      q_adrenaline: updated[1]?.answerId,
      q_food: updated[2]?.answerId,
      q_classy: updated[3]?.answerId,
    };
    if (isOrganizer) {
      pushEvent('quiz_completed', quizParams);
      navigate('/vote/new/curate', { state: { setup, responses: updated } });
      return;
    }
    setSubmitting(true);
    try {
      await voteApi.submitParticipantQuiz(shareToken, {
        voterToken: getOrCreateVoterToken(),
        responses: updated,
      });
      pushEvent('quiz_completed', { ...quizParams, trip_id: shareToken, user_role: 'participant' });
      navigate(`/vote/${shareToken}/activities`);
    } catch (e) {
      setError(e.message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="quiz-page">
      {(() => {
        // Endowed progress: the organizer already completed the setup step, so
        // their bar starts pre-filled — started journeys get finished more.
        const endow = isOrganizer ? 1 : 0;
        const total = quiz.questions.length + endow;
        const done = stepIndex + endow;
        return (
          <div className="quiz-progress">
            <div className="quiz-progress-track">
              <div className="quiz-progress-fill" style={{width: `${Math.round((done / total) * 100)}%`}}/>
            </div>
            <span className="quiz-progress-label">{stepIndex + 1} / {quiz.questions.length}</span>
          </div>
        );
      })()}
      <h2 className="quiz-prompt">{question.prompt}</h2>
      <div className="quiz-answers">
        {question.answers.map((a) => (
          <button
            key={a.id}
            className="quiz-answer"
            disabled={submitting}
            onClick={() => pickAnswer(a.id)}
          >
            {a.label}
          </button>
        ))}
      </div>
    </div>
  );
}

export default function QuizPage() {
    return (
        <>
            <VoteMeta title="Group quiz"/>
            <QuizContent/>
        </>
    );
}
