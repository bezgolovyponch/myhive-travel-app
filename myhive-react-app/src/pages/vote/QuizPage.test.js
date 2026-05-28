import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import QuizPage from './QuizPage';
import voteApi from '../../services/voteApi';

jest.mock('../../services/voteApi');

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
