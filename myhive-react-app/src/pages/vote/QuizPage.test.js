import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import QuizPage from './QuizPage';
import voteApi from '../../services/voteApi';
import { pushEvent } from '../../utils/analytics';

jest.mock('../../services/voteApi');
jest.mock('../../utils/analytics');

// Four-question quiz that mirrors the "Define Your Stag Style" structure:
// Q1 daytime, Q2 adrenaline, Q3 food, Q4 classy.
const FOUR_Q_QUIZ = {
  questions: [
    { id: 'q1', prompt: 'Daytime activity?', answers: [{ id: 'a-day', label: 'Beach' }, { id: 'a-day2', label: 'Museum' }] },
    { id: 'q2', prompt: 'Adrenaline level?', answers: [{ id: 'a-adr', label: 'High' }, { id: 'a-adr2', label: 'Low' }] },
    { id: 'q3', prompt: 'Food preference?', answers: [{ id: 'a-food', label: 'BBQ' }, { id: 'a-food2', label: 'Vegan' }] },
    { id: 'q4', prompt: 'Classy or rowdy?', answers: [{ id: 'a-cls', label: 'Classy' }, { id: 'a-cls2', label: 'Rowdy' }] },
  ],
};

const ORGANIZER_SETUP = {
  destination: { id: 'dest1' },
  travelers: 2,
  startDate: '2026-08-01',
  endDate: '2026-08-10',
  email: 'a@b.c',
  budget: 3000,
};

function renderOrganizer(setup) {
  return render(
    <MemoryRouter initialEntries={[{ pathname: '/vote/new/quiz', state: { setup } }]}>
      <Routes>
        <Route path="/vote/new/quiz" element={<QuizPage />} />
        <Route path="/vote/new/curate" element={<div>curate page</div>} />
        <Route path="/" element={<div>home</div>} />
      </Routes>
    </MemoryRouter>
  );
}

function renderParticipant(shareToken) {
  return render(
    <MemoryRouter initialEntries={[`/vote/${shareToken}/quiz`]}>
      <Routes>
        <Route path="/vote/:shareToken/quiz" element={<QuizPage />} />
        <Route path="/vote/:shareToken/activities" element={<div>activities page</div>} />
      </Routes>
    </MemoryRouter>
  );
}

// Helper: answer all 4 questions in order using the first answer of each.
async function answerAllQuestions() {
  // Q1 daytime
  expect(await screen.findByText('Daytime activity?')).toBeInTheDocument();
  await userEvent.click(screen.getByText('Beach'));

  // Q2 adrenaline
  expect(await screen.findByText('Adrenaline level?')).toBeInTheDocument();
  await userEvent.click(screen.getByText('High'));

  // Q3 food
  expect(await screen.findByText('Food preference?')).toBeInTheDocument();
  await userEvent.click(screen.getByText('BBQ'));

  // Q4 classy — the last question
  expect(await screen.findByText('Classy or rowdy?')).toBeInTheDocument();
  await userEvent.click(screen.getByText('Classy'));
}

// ─── Pre-existing tests (kept intact) ──────────────────────────────────────

test('organizer: loads quiz, answers question, navigates to curate', async () => {
  voteApi.getPublicQuizForDestination.mockResolvedValue({
    questions: [
      { id: 'q1', prompt: 'Daytime or 4am?', answers: [
        { id: 'a1', label: 'Daytime' },
        { id: 'a2', label: '4am' },
      ] },
    ],
  });
  renderOrganizer({ destination: { id: 'dest1' }, travelers: 2, startDate: '2026-08-01', endDate: '2026-08-10', email: 'a@b.c', budget: 3000 });

  expect(await screen.findByText('Daytime or 4am?')).toBeInTheDocument();
  await userEvent.click(screen.getByText('4am'));

  expect(await screen.findByText('curate page')).toBeInTheDocument();
});

test('organizer: empty quiz auto-skips to curate', async () => {
  voteApi.getPublicQuizForDestination.mockResolvedValue({ questions: [] });
  renderOrganizer({ destination: { id: 'dest1' } });

  expect(await screen.findByText('curate page')).toBeInTheDocument();
});

test('organizer: no setup state redirects home', async () => {
  render(
    <MemoryRouter initialEntries={['/vote/new/quiz']}>
      <Routes>
        <Route path="/vote/new/quiz" element={<QuizPage />} />
        <Route path="/" element={<div>home</div>} />
      </Routes>
    </MemoryRouter>
  );
  expect(await screen.findByText('home')).toBeInTheDocument();
});

// ─── quiz_completed analytics tests ────────────────────────────────────────

describe('quiz_completed analytics', () => {
  beforeEach(() => {
    voteApi.getPublicQuizForDestination.mockResolvedValue(FOUR_Q_QUIZ);
    voteApi.getParticipantQuiz.mockResolvedValue(FOUR_Q_QUIZ);
    voteApi.submitParticipantQuiz.mockResolvedValue({});
  });

  test('organizer: answering a non-last question does NOT fire quiz_completed', async () => {
    renderOrganizer(ORGANIZER_SETUP);

    // Answer only the first question (not the last).
    expect(await screen.findByText('Daytime activity?')).toBeInTheDocument();
    await userEvent.click(screen.getByText('Beach'));

    // Second question should now be shown.
    expect(await screen.findByText('Adrenaline level?')).toBeInTheDocument();
    expect(pushEvent).not.toHaveBeenCalledWith('quiz_completed', expect.anything());
  });

  test('organizer: answering the last question fires quiz_completed once with q_* params and no trip_id/user_role', async () => {
    renderOrganizer(ORGANIZER_SETUP);

    await answerAllQuestions();

    expect(await screen.findByText('curate page')).toBeInTheDocument();

    expect(pushEvent).toHaveBeenCalledTimes(1);
    expect(pushEvent).toHaveBeenCalledWith('quiz_completed', {
      q_daytime: 'a-day',
      q_adrenaline: 'a-adr',
      q_food: 'a-food',
      q_classy: 'a-cls',
    });
    // Must NOT include trip_id or user_role.
    const call = pushEvent.mock.calls[0];
    expect(call[1]).not.toHaveProperty('trip_id');
    expect(call[1]).not.toHaveProperty('user_role');
  });

  test('participant: answering a non-last question does NOT fire quiz_completed', async () => {
    renderParticipant('tok-abc');

    // Answer only the first question.
    expect(await screen.findByText('Daytime activity?')).toBeInTheDocument();
    await userEvent.click(screen.getByText('Beach'));

    expect(await screen.findByText('Adrenaline level?')).toBeInTheDocument();
    expect(pushEvent).not.toHaveBeenCalledWith('quiz_completed', expect.anything());
  });

  test('participant: after submit succeeds, fires quiz_completed once with q_* params + trip_id and user_role', async () => {
    renderParticipant('tok-abc');

    await answerAllQuestions();

    expect(await screen.findByText('activities page')).toBeInTheDocument();

    expect(pushEvent).toHaveBeenCalledTimes(1);
    expect(pushEvent).toHaveBeenCalledWith('quiz_completed', {
      q_daytime: 'a-day',
      q_adrenaline: 'a-adr',
      q_food: 'a-food',
      q_classy: 'a-cls',
      trip_id: 'tok-abc',
      user_role: 'participant',
    });
  });

  test('participant: does NOT fire quiz_completed when submit fails', async () => {
    voteApi.submitParticipantQuiz.mockRejectedValue(new Error('network error'));
    renderParticipant('tok-abc');

    await answerAllQuestions();

    // Wait for the error state to appear (the page shows the error, not activities).
    expect(await screen.findByText('network error')).toBeInTheDocument();
    expect(pushEvent).not.toHaveBeenCalledWith('quiz_completed', expect.anything());
  });
});

// ─── Endowed progress bar tests ────────────────────────────────────────

describe('endowed progress bar', () => {
  beforeEach(() => {
    voteApi.getPublicQuizForDestination.mockResolvedValue(FOUR_Q_QUIZ);
    voteApi.getParticipantQuiz.mockResolvedValue(FOUR_Q_QUIZ);
  });

  test('organizer sees an endowed progress bar (setup counts as a done step)', async () => {
    renderOrganizer(ORGANIZER_SETUP);

    await screen.findByText('1 / 4');
    const fill = document.querySelector('.quiz-progress-fill');
    // 1 completed (setup) of 5 total steps = 20%
    expect(fill.style.width).toBe('20%');
  });

  test('participant progress starts at zero', async () => {
    renderParticipant('tok-abc');

    await screen.findByText('1 / 4');
    expect(document.querySelector('.quiz-progress-fill').style.width).toBe('0%');
  });
});
