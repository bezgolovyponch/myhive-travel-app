import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import {CatalogContext} from '../context/CatalogContext';
import {TripContext} from '../context/TripContext';
import { useStartGroupVote } from './useStartGroupVote';
import leadApi from '../services/leadApi';

jest.mock('../services/leadApi');

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

beforeEach(() => {
  // CRA sets resetMocks: true — re-establish a working default so tests that
  // don't care about lead capture aren't tripped up by an unmocked promise.
  leadApi.createLead.mockResolvedValue({ id: 'lead-default', restoreToken: 'tok-default' });
});

afterEach(() => {
  localStorage.clear();
});

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

test('captures a trip lead on vote confirm and stores its tokens', async () => {
  leadApi.createLead.mockResolvedValue({ id: 'lead-1', restoreToken: 'tok-1' });
  renderHarness();

  await userEvent.click(screen.getByText('confirm'));

  await waitFor(() => expect(leadApi.createLead).toHaveBeenCalledWith(
    expect.objectContaining({ email: 'a@b.c', destinationId: 'd1' })));
  await waitFor(() => expect(
    JSON.parse(localStorage.getItem('myhive-trip-lead'))).toEqual(
    { id: 'lead-1', restoreToken: 'tok-1' }));
});

test('a failed lead capture does not block navigation to the quiz', async () => {
  leadApi.createLead.mockRejectedValue(new Error('down'));
  renderHarness();

  await userEvent.click(screen.getByText('confirm'));

  expect(await screen.findByTestId('quiz-setup')).toBeInTheDocument();
});
