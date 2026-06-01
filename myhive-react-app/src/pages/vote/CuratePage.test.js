import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router-dom';
import CuratePage from './CuratePage';
import voteApi from '../../services/voteApi';
import { AppContext } from '../../context/AppContext';

jest.mock('../../services/voteApi');

const setup = {
  destination: { id: 'dest1', slug: 'bali' },
  travelers: 2,
  startDate: '2026-08-01',
  endDate: '2026-08-10',
  email: 'a@b.c',
  budget: 3000,
};

// Destination stub with a back button so tests can simulate the browser back
// button and assert the curate page restores its finalized state.
function DestinationStub() {
  const navigate = useNavigate();
  return (
    <div>
      destination page
      <button type="button" onClick={() => navigate(-1)}>go back</button>
    </div>
  );
}

function renderWith(state, dispatch = jest.fn()) {
  return render(
    <AppContext.Provider value={{ state: { tripItems: [] }, dispatch }}>
      <MemoryRouter initialEntries={[{ pathname: '/vote/new/curate', state }]}>
        <Routes>
          <Route path="/vote/new/curate" element={<CuratePage />} />
          <Route path="/vote/:shareToken/waiting" element={<div>waiting page</div>} />
          <Route path="/destination/:slug" element={<DestinationStub />} />
          <Route path="/" element={<div>home</div>} />
        </Routes>
      </MemoryRouter>
    </AppContext.Provider>
  );
}

test('swipe-right one card, swipe-left one card, then create session', async () => {
  voteApi.buildPool.mockResolvedValue({
    pool: [
      { activityId: 'act1', name: 'Tank Driving', price: 150, imageUrl: null, categories: ['Extreme'] },
      { activityId: 'act2', name: 'Spa Day', price: 80, imageUrl: null, categories: ['Chillout'] },
    ],
  });
  voteApi.createSession.mockResolvedValue({ shareToken: 'tok-abc', managerToken: 'mgr-xyz' });

  renderWith({ setup, responses: [] });

  // SwipeCard renders Like (heart) and Dislike (cross) buttons via aria-label.
  expect(await screen.findByLabelText('Like')).toBeInTheDocument();

  await userEvent.click(screen.getByLabelText('Like'));      // include act1
  await userEvent.click(screen.getByLabelText('Dislike'));   // skip act2

  expect(await screen.findByText(/Your voting list/i)).toBeInTheDocument();
  expect(screen.getByText(/Tank Driving/)).toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: /Create & get link/i }));

  await waitFor(() => expect(voteApi.createSession).toHaveBeenCalled());
  const arg = voteApi.createSession.mock.calls[0][0];
  expect(arg.activityIds).toEqual(['act1']);
  expect(arg.budget).toBe(3000);
  expect(await screen.findByText('waiting page')).toBeInTheDocument();
});

test('start over resets the picked list', async () => {
  voteApi.buildPool.mockResolvedValue({
    pool: [
      { activityId: 'act1', name: 'Tank Driving', price: 150, imageUrl: null, categories: [] },
    ],
  });

  renderWith({ setup, responses: [] });

  expect(await screen.findByLabelText('Like')).toBeInTheDocument();
  await userEvent.click(screen.getByLabelText('Like'));

  expect(await screen.findByText(/Your voting list \(1\)/i)).toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: /Start over/i }));

  // Back to the swipe UI
  expect(await screen.findByLabelText('Like')).toBeInTheDocument();
});

test('no setup state redirects home', async () => {
  render(
    <AppContext.Provider value={{ state: { tripItems: [] }, dispatch: jest.fn() }}>
      <MemoryRouter initialEntries={['/vote/new/curate']}>
        <Routes>
          <Route path="/vote/new/curate" element={<CuratePage />} />
          <Route path="/" element={<div>home</div>} />
        </Routes>
      </MemoryRouter>
    </AppContext.Provider>
  );
  expect(await screen.findByText('home')).toBeInTheDocument();
});

test('empty pool shows empty-state message', async () => {
  voteApi.buildPool.mockResolvedValue({ pool: [] });
  renderWith({ setup, responses: [] });
  expect(await screen.findByText(/no activities match/i)).toBeInTheDocument();
});

test('build my own trip seeds setup, adds picks, and navigates to trip builder', async () => {
  const dispatch = jest.fn();
  voteApi.buildPool.mockResolvedValue({
    pool: [
      { activityId: 'act1', name: 'Tank Driving', price: 150, imageUrl: null, slug: 'tank', destinationSlug: 'bali', categories: ['Extreme'] },
      { activityId: 'act2', name: 'Spa Day', price: 80, imageUrl: null, slug: 'spa', destinationSlug: 'bali', categories: ['Chillout'] },
    ],
  });

  renderWith({ setup, responses: [] }, dispatch);

  expect(await screen.findByLabelText('Like')).toBeInTheDocument();
  await userEvent.click(screen.getByLabelText('Like'));      // include act1
  await userEvent.click(screen.getByLabelText('Dislike'));   // skip act2

  expect(await screen.findByText(/Your voting list/i)).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: /Build my own trip/i }));

  expect(dispatch).toHaveBeenCalledWith({ type: 'UPDATE_TRIP_TRAVELERS', travelers: 2 });
  expect(dispatch).toHaveBeenCalledWith({ type: 'UPDATE_TRIP_DATES', startDate: '2026-08-01', endDate: '2026-08-10' });
  expect(dispatch).toHaveBeenCalledWith({ type: 'UPDATE_TRIP_BUDGET', budget: 3000 });

  const addCalls = dispatch.mock.calls.filter(c => c[0].type === 'ADD_TO_TRIP');
  expect(addCalls).toHaveLength(1);
  expect(addCalls[0][0].silent).toBe(true);
  expect(addCalls[0][0].activity).toMatchObject({ id: 'act1', name: 'Tank Driving', categories: [{ name: 'Extreme' }] });

  expect(await screen.findByText('destination page')).toBeInTheDocument();
});

test('build my own trip is disabled when nothing was picked', async () => {
  voteApi.buildPool.mockResolvedValue({
    pool: [
      { activityId: 'act1', name: 'Tank Driving', price: 150, imageUrl: null, slug: 'tank', destinationSlug: 'bali', categories: [] },
    ],
  });

  renderWith({ setup, responses: [] });

  expect(await screen.findByLabelText('Dislike')).toBeInTheDocument();
  await userEvent.click(screen.getByLabelText('Dislike'));   // skip everything

  expect(await screen.findByText(/Your voting list \(0\)/i)).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /Build my own trip/i })).toBeDisabled();
});

test('back from trip builder restores the finalize page with picks', async () => {
  voteApi.buildPool.mockResolvedValue({
    pool: [
      { activityId: 'act1', name: 'Tank Driving', price: 150, imageUrl: null, slug: 'tank', destinationSlug: 'bali', categories: ['Extreme'] },
      { activityId: 'act2', name: 'Spa Day', price: 80, imageUrl: null, slug: 'spa', destinationSlug: 'bali', categories: ['Chillout'] },
    ],
  });

  renderWith({ setup, responses: [] });

  expect(await screen.findByLabelText('Like')).toBeInTheDocument();
  await userEvent.click(screen.getByLabelText('Like'));      // include act1
  await userEvent.click(screen.getByLabelText('Dislike'));   // skip act2

  expect(await screen.findByText(/Your voting list \(1\)/i)).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: /Build my own trip/i }));

  expect(await screen.findByText('destination page')).toBeInTheDocument();

  // Simulate the browser back button.
  await userEvent.click(screen.getByRole('button', { name: /go back/i }));

  // The finalize page is restored with the same picks — not the swipe deck.
  expect(await screen.findByText(/Your voting list \(1\)/i)).toBeInTheDocument();
  expect(screen.getByText(/Tank Driving/)).toBeInTheDocument();
  expect(screen.queryByLabelText('Like')).not.toBeInTheDocument();

  // The pool was restored from the snapshot, not rebuilt over the network.
  expect(voteApi.buildPool).toHaveBeenCalledTimes(1);
});

test('start over after returning via back clears the snapshot and rebuilds the deck', async () => {
  voteApi.buildPool.mockResolvedValue({
    pool: [
      { activityId: 'act1', name: 'Tank Driving', price: 150, imageUrl: null, slug: 'tank', destinationSlug: 'bali', categories: ['Extreme'] },
    ],
  });

  renderWith({ setup, responses: [] });

  expect(await screen.findByLabelText('Like')).toBeInTheDocument();
  await userEvent.click(screen.getByLabelText('Like'));   // include act1 → finalize

  expect(await screen.findByText(/Your voting list \(1\)/i)).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: /Build my own trip/i }));
  expect(await screen.findByText('destination page')).toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: /go back/i }));
  expect(await screen.findByText(/Your voting list \(1\)/i)).toBeInTheDocument();

  // Start over from the restored finalize page: deck returns and the snapshot is
  // dropped, so the pool is rebuilt (a later remount won't jump back to the list).
  await userEvent.click(screen.getByRole('button', { name: /Start over/i }));
  expect(await screen.findByLabelText('Like')).toBeInTheDocument();
  await waitFor(() => expect(voteApi.buildPool).toHaveBeenCalledTimes(2));
});
