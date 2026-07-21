import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import CuratePage from './CuratePage';
import voteApi from '../../services/voteApi';
import {TripContext} from '../../context/TripContext';
import { pushEvent } from '../../utils/analytics';

jest.mock('../../services/voteApi');
jest.mock('../../utils/analytics', () => ({ pushEvent: jest.fn() }));

beforeEach(() => {
  sessionStorage.clear();
});

const setup = {
  destination: { id: 'dest1', slug: 'bali' },
  travelers: 2,
  startDate: '2026-08-01',
  endDate: '2026-08-10',
  email: 'a@b.c',
  budget: 3000,
};

// Destination stub exposing its location (to assert the handoff URL) and a
// back button (to assert the replace-navigation killed the deck entry).
function DestinationStub() {
  const navigate = useNavigate();
  const location = useLocation();
  return (
    <div>
      destination page
      <div data-testid="dest-location">{location.pathname + location.search}</div>
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
          <Route path="/destination/:slug" element={<DestinationStub />} />
          <Route path="/" element={<div>home</div>} />
        </Routes>
      </MemoryRouter>
    </TripContext.Provider>
  );
}

const pool = [
  { activityId: 'act1', name: 'Tank Driving', price: 150, imageUrl: null, slug: 'tank', destinationSlug: 'bali', categories: ['Extreme'] },
  { activityId: 'act2', name: 'Spa Day', price: 80, imageUrl: null, slug: 'spa', destinationSlug: 'bali', categories: ['Chillout'] },
];

test('last swipe seeds the trip, stores the quiz-flow context, and lands in the trip builder', async () => {
  const dispatch = jest.fn();
  voteApi.buildPool.mockResolvedValue({ pool });

  renderWith({ setup, responses: [] }, dispatch);

  expect(await screen.findByLabelText('Like')).toBeInTheDocument();
  await userEvent.click(screen.getByLabelText('Like'));      // include act1
  await userEvent.click(screen.getByLabelText('Dislike'));   // skip act2

  expect(await screen.findByText('destination page')).toBeInTheDocument();
  expect(screen.getByTestId('dest-location')).toHaveTextContent('/destination/bali?tab=trip-builder');

  expect(dispatch).toHaveBeenCalledWith({ type: 'UPDATE_TRIP_TRAVELERS', travelers: 2 });
  expect(dispatch).toHaveBeenCalledWith({ type: 'UPDATE_TRIP_DATES', startDate: '2026-08-01', endDate: '2026-08-10' });
  expect(dispatch).toHaveBeenCalledWith({ type: 'UPDATE_TRIP_BUDGET', budget: 3000 });

  const addCalls = dispatch.mock.calls.filter(c => c[0].type === 'ADD_TO_TRIP');
  expect(addCalls).toHaveLength(1);
  expect(addCalls[0][0].silent).toBe(true);
  expect(addCalls[0][0].activity).toMatchObject({ id: 'act1', name: 'Tank Driving', categories: [{ name: 'Extreme' }] });

  expect(JSON.parse(sessionStorage.getItem('myhive-quiz-flow'))).toEqual({ setup, responses: [] });
});

test('A11: shortlist_completed fires exactly once with the picked count', async () => {
  voteApi.buildPool.mockResolvedValue({ pool });

  renderWith({ setup, responses: [] });

  expect(await screen.findByLabelText('Like')).toBeInTheDocument();
  expect(pushEvent).not.toHaveBeenCalledWith('shortlist_completed', expect.anything());

  await userEvent.click(screen.getByLabelText('Like'));
  expect(pushEvent).not.toHaveBeenCalledWith('shortlist_completed', expect.anything());

  await userEvent.click(screen.getByLabelText('Dislike'));

  expect(await screen.findByText('destination page')).toBeInTheDocument();
  expect(pushEvent).toHaveBeenCalledTimes(1);
  expect(pushEvent).toHaveBeenCalledWith('shortlist_completed', { selected_count: 1 });
});

test('undo drops the pick — only the re-swiped selection reaches the trip', async () => {
  const dispatch = jest.fn();
  voteApi.buildPool.mockResolvedValue({ pool });

  renderWith({ setup, responses: [] }, dispatch);

  expect(await screen.findByLabelText('Like')).toBeInTheDocument();
  await userEvent.click(screen.getByLabelText('Like'));            // accidentally include act1
  await userEvent.click(screen.getByLabelText('Undo last swipe')); // take it back
  await userEvent.click(screen.getByLabelText('Dislike'));         // skip act1 this time
  await userEvent.click(screen.getByLabelText('Like'));            // include act2

  expect(await screen.findByText('destination page')).toBeInTheDocument();
  const addCalls = dispatch.mock.calls.filter(c => c[0].type === 'ADD_TO_TRIP');
  expect(addCalls).toHaveLength(1);
  expect(addCalls[0][0].activity).toMatchObject({ id: 'act2', name: 'Spa Day' });
});

test('back from the trip builder does not return to the spent deck (replace navigation)', async () => {
  voteApi.buildPool.mockResolvedValue({ pool });

  renderWith({ setup, responses: [] });

  expect(await screen.findByLabelText('Like')).toBeInTheDocument();
  await userEvent.click(screen.getByLabelText('Like'));
  await userEvent.click(screen.getByLabelText('Dislike'));

  expect(await screen.findByText('destination page')).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: /go back/i }));

  // The curate entry was replaced — back cannot land on the deck again.
  expect(screen.getByText('destination page')).toBeInTheDocument();
  expect(screen.queryByLabelText('Like')).not.toBeInTheDocument();
});

test('zero picks stays on the page, offers a restart, and re-fires analytics after the redo', async () => {
  const dispatch = jest.fn();
  voteApi.buildPool.mockResolvedValue({
    pool: [
      { activityId: 'act1', name: 'Tank Driving', price: 150, imageUrl: null, slug: 'tank', destinationSlug: 'bali', categories: [] },
    ],
  });

  renderWith({ setup, responses: [] }, dispatch);

  expect(await screen.findByLabelText('Dislike')).toBeInTheDocument();
  await userEvent.click(screen.getByLabelText('Dislike'));   // skip everything

  expect(await screen.findByText(/You didn't pick anything/i)).toBeInTheDocument();
  expect(pushEvent).toHaveBeenCalledWith('shortlist_completed', { selected_count: 0 });
  expect(screen.queryByText('destination page')).not.toBeInTheDocument();
  expect(sessionStorage.getItem('myhive-quiz-flow')).toBeNull();
  expect(dispatch.mock.calls.filter(c => c[0].type === 'ADD_TO_TRIP')).toHaveLength(0);

  await userEvent.click(screen.getByRole('button', { name: /Start over/i }));
  expect(await screen.findByLabelText('Like')).toBeInTheDocument();

  await userEvent.click(screen.getByLabelText('Like'));      // pick this time
  expect(await screen.findByText('destination page')).toBeInTheDocument();
  // Fresh completion → the event fired a second time.
  expect(pushEvent.mock.calls.filter(([e]) => e === 'shortlist_completed')).toHaveLength(2);
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
