import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import {CatalogContext} from '../context/CatalogContext';
import {TripContext} from '../context/TripContext';
import { useStartGroupVote } from './useStartGroupVote';

function QuizStub() {
  const location = useLocation();
  return <div data-testid="quiz-setup">{JSON.stringify(location.state?.setup)}</div>;
}

function Harness() {
  const { voteSetupOpen, openVoteSetup, handleVoteConfirm } = useStartGroupVote();
  return (
    <div>
      <span data-testid="open-state">{voteSetupOpen ? 'open' : 'closed'}</span>
      <button onClick={openVoteSetup}>open setup</button>
      <button
        onClick={() =>
          handleVoteConfirm({
            travelers: 4,
            startDate: '2026-08-01',
            endDate: '2026-08-03',
            email: 'a@b.c',
            destination: { id: 'd1', slug: 'prague' },
            budget: null,
          })
        }
      >
        confirm
      </button>
    </div>
  );
}

function renderHarness(dispatch = jest.fn()) {
  const catalogState = { destinations: [{ id: 'd1', slug: 'prague', name: 'Prague' }] };
  const tripState = { tripItems: [] };
  return render(
    <CatalogContext.Provider value={{ state: catalogState, dispatch }}>
      <TripContext.Provider value={{ state: tripState, dispatch }}>
        <MemoryRouter initialEntries={['/']}>
          <Routes>
            <Route path="/" element={<Harness />} />
            <Route path="/vote/new/quiz" element={<QuizStub />} />
          </Routes>
        </MemoryRouter>
      </TripContext.Provider>
    </CatalogContext.Provider>
  );
}

test('openVoteSetup flips voteSetupOpen', async () => {
  renderHarness();

  expect(screen.getByTestId('open-state')).toHaveTextContent('closed');
  await userEvent.click(screen.getByText('open setup'));
  expect(screen.getByTestId('open-state')).toHaveTextContent('open');
});

test('handleVoteConfirm navigates to the quiz with setup state', async () => {
  const dispatch = jest.fn();
  renderHarness(dispatch);

  await userEvent.click(screen.getByText('confirm'));

  const setupNode = await screen.findByTestId('quiz-setup');
  expect(JSON.parse(setupNode.textContent)).toEqual({
    travelers: 4,
    startDate: '2026-08-01',
    endDate: '2026-08-03',
    email: 'a@b.c',
    destination: { id: 'd1', slug: 'prague' },
    budget: null,
  });
  expect(dispatch).toHaveBeenCalledWith({ type: 'CLOSE_TRIP_BUILDER_MODAL' });
});
