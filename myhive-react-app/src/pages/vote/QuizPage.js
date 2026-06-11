import { useEffect, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import voteApi from '../../services/voteApi';
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
    if (isOrganizer) {
      navigate('/vote/new/curate', { state: { setup, responses: updated } });
      return;
    }
    setSubmitting(true);
    try {
      await voteApi.submitParticipantQuiz(shareToken, {
        voterToken: getOrCreateVoterToken(),
        responses: updated,
      });
      navigate(`/vote/${shareToken}/activities`);
    } catch (e) {
      setError(e.message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="quiz-page">
      <div className="quiz-progress">{stepIndex + 1} / {quiz.questions.length}</div>
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
