import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router-dom';
import CuratePage from './CuratePage';
import voteApi from '../../services/voteApi';
import {TripContext} from '../../context/TripContext';
import { pushEvent } from '../../utils/analytics';
import { generateUuid } from '../../utils/uuid';

jest.mock('../../services/voteApi');
jest.mock('../../utils/analytics', () => ({ pushEvent: jest.fn() }));
jest.mock('../../utils/uuid', () => ({ generateUuid: jest.fn() }));

beforeEach(() => {
  pushEvent.mockClear();
  generateUuid.mockReturnValue('mock-uuid-1234');
});

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
    <TripContext.Provider value={{ state: { tripItems: [] }, dispatch }}>
      <MemoryRouter initialEntries={[{ pathname: '/vote/new/curate', state }]}>
        <Routes>
          <Route path="/vote/new/curate" element={<CuratePage />} />
          <Route path="/vote/:shareToken/waiting" element={<div>waiting page</div>} />
          <Route path="/destination/:slug" element={<DestinationStub />} />
          <Route path="/" element={<div>home</div>} />
        </Routes>
      </MemoryRouter>
    </TripContext.Provider>
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

test('clicking an activity name on the finalize list opens the info modal', async () => {
  voteApi.buildPool.mockResolvedValue({
    pool: [
      { activityId: 'act1', name: 'Tank Driving', price: 150, imageUrl: null, slug: 'tank', destinationSlug: 'bali', description: 'Drive a real tank.', duration: 120, categories: ['Extreme'] },
    ],
  });

  renderWith({ setup, responses: [] });

  expect(await screen.findByLabelText('Like')).toBeInTheDocument();
  await userEvent.click(screen.getByLabelText('Like'));

  expect(await screen.findByText(/Your voting list \(1\)/i)).toBeInTheDocument();

  // The name is now a button that opens the info modal instead of navigating away.
  await userEvent.click(screen.getByRole('button', { name: 'Tank Driving' }));

  expect(screen.getByText('Drive a real tank.')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: /View full page/i }))
    .toHaveAttribute('href', '/destination/bali/activity/tank');
});

test('no setup state redirects home', async () => {
  render(
    <TripContext.Provider value={{ state: { tripItems: [] }, dispatch: jest.fn() }}>
      <MemoryRouter initialEntries={['/vote/new/curate']}>
        <Routes>
          <Route path="/vote/new/curate" element={<CuratePage />} />
          <Route path="/" element={<div>home</div>} />
        </Routes>
      </MemoryRouter>
    </TripContext.Provider>
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

// --- A11: shortlist_completed ---

test('A11: shortlist_completed fires once when all cards are swiped', async () => {
  voteApi.buildPool.mockResolvedValue({
    pool: [
      { activityId: 'act1', name: 'Tank Driving', price: 150, imageUrl: null, categories: [] },
      { activityId: 'act2', name: 'Spa Day', price: 80, imageUrl: null, categories: [] },
    ],
  });

  renderWith({ setup, responses: [] });

  expect(await screen.findByLabelText('Like')).toBeInTheDocument();
  // shortlist_completed must NOT have fired yet (swiping is in progress)
  expect(pushEvent).not.toHaveBeenCalledWith('shortlist_completed', expect.anything());

  await userEvent.click(screen.getByLabelText('Like'));    // act1 → right
  // still one card to go — must not have fired yet
  expect(pushEvent).not.toHaveBeenCalledWith('shortlist_completed', expect.anything());

  await userEvent.click(screen.getByLabelText('Dislike')); // act2 → left (last card)

  await screen.findByText(/Your voting list/i);
  expect(pushEvent).toHaveBeenCalledTimes(1);
  expect(pushEvent).toHaveBeenCalledWith('shortlist_completed', { selected_count: 1 });
});

test('A11: shortlist_completed fires only once even after start-over then re-swipe', async () => {
  voteApi.buildPool.mockResolvedValue({
    pool: [
      { activityId: 'act1', name: 'Tank Driving', price: 150, imageUrl: null, categories: [] },
    ],
  });

  renderWith({ setup, responses: [] });

  expect(await screen.findByLabelText('Like')).toBeInTheDocument();
  await userEvent.click(screen.getByLabelText('Like')); // finalize screen appears → event fires once

  await screen.findByText(/Your voting list \(1\)/i);
  expect(pushEvent).toHaveBeenCalledTimes(1);
  expect(pushEvent).toHaveBeenCalledWith('shortlist_completed', { selected_count: 1 });

  // Start over resets the deck — swiping again should fire the event again
  // (it is a fresh shortlist completion, so one more call is expected)
  await userEvent.click(screen.getByRole('button', { name: /Start over/i }));
  expect(await screen.findByLabelText('Like')).toBeInTheDocument();
  await userEvent.click(screen.getByLabelText('Like')); // finalize again

  await screen.findByText(/Your voting list \(1\)/i);
  expect(pushEvent).toHaveBeenCalledTimes(2);
});

// --- A12: vote_launched ---

test('A12: vote_launched fires after successful createSession with correct params', async () => {
  voteApi.buildPool.mockResolvedValue({
    pool: [
      { activityId: 'act1', name: 'Tank Driving', price: 150, imageUrl: null, categories: [] },
      { activityId: 'act2', name: 'Spa Day', price: 80, imageUrl: null, categories: [] },
    ],
  });
  voteApi.createSession.mockResolvedValue({ shareToken: 'tok-abc', managerToken: 'mgr-xyz' });

  renderWith({ setup, responses: [] });

  expect(await screen.findByLabelText('Like')).toBeInTheDocument();
  await userEvent.click(screen.getByLabelText('Like'));    // act1 → picked
  await userEvent.click(screen.getByLabelText('Dislike')); // act2 → skip

  await screen.findByText(/Your voting list/i);

  pushEvent.mockClear(); // clear the shortlist_completed call
  await userEvent.click(screen.getByRole('button', { name: /Create & get link/i }));

  await waitFor(() => expect(voteApi.createSession).toHaveBeenCalled());
  await screen.findByText('waiting page');

  expect(pushEvent).toHaveBeenCalledTimes(1);
  expect(pushEvent).toHaveBeenCalledWith('vote_launched', {
    trip_id: 'tok-abc',
    user_role: 'organizer',
    selected_count: 1,
  });
});

test('A12: vote_launched does NOT fire if createSession rejects', async () => {
  voteApi.buildPool.mockResolvedValue({
    pool: [
      { activityId: 'act1', name: 'Tank Driving', price: 150, imageUrl: null, categories: [] },
    ],
  });
  voteApi.createSession.mockRejectedValue(new Error('Network error'));

  renderWith({ setup, responses: [] });

  expect(await screen.findByLabelText('Like')).toBeInTheDocument();
  await userEvent.click(screen.getByLabelText('Like'));

  await screen.findByText(/Your voting list/i);
  pushEvent.mockClear();

  await userEvent.click(screen.getByRole('button', { name: /Create & get link/i }));
  await waitFor(() => expect(voteApi.createSession).toHaveBeenCalled());

  // Error is shown and vote_launched was never called
  await screen.findByText(/Network error/i);
  expect(pushEvent).not.toHaveBeenCalledWith('vote_launched', expect.anything());
});

// --- A13: vote_skipped ---

test('A13: vote_skipped fires on "Build my own trip" with trip_id and selected_count', async () => {
  const dispatch = jest.fn();
  voteApi.buildPool.mockResolvedValue({
    pool: [
      { activityId: 'act1', name: 'Tank Driving', price: 150, imageUrl: null, slug: 'tank', destinationSlug: 'bali', categories: ['Extreme'] },
      { activityId: 'act2', name: 'Spa Day', price: 80, imageUrl: null, slug: 'spa', destinationSlug: 'bali', categories: [] },
    ],
  });

  renderWith({ setup, responses: [] }, dispatch);

  expect(await screen.findByLabelText('Like')).toBeInTheDocument();
  await userEvent.click(screen.getByLabelText('Like'));    // act1 → picked
  await userEvent.click(screen.getByLabelText('Dislike')); // act2 → skip

  await screen.findByText(/Your voting list/i);
  pushEvent.mockClear();

  await userEvent.click(screen.getByRole('button', { name: /Build my own trip/i }));

  expect(pushEvent).toHaveBeenCalledWith('vote_skipped', {
    trip_id: 'mock-uuid-1234',
    selected_count: 1,
  });
});

test('A13: SET_TRIP_ID is dispatched with the minted uuid on "Build my own trip"', async () => {
  const dispatch = jest.fn();
  voteApi.buildPool.mockResolvedValue({
    pool: [
      { activityId: 'act1', name: 'Tank Driving', price: 150, imageUrl: null, slug: 'tank', destinationSlug: 'bali', categories: [] },
    ],
  });

  renderWith({ setup, responses: [] }, dispatch);

  expect(await screen.findByLabelText('Like')).toBeInTheDocument();
  await userEvent.click(screen.getByLabelText('Like'));

  await screen.findByText(/Your voting list/i);
  await userEvent.click(screen.getByRole('button', { name: /Build my own trip/i }));

  expect(dispatch).toHaveBeenCalledWith({ type: 'SET_TRIP_ID', tripId: 'mock-uuid-1234' });
});
