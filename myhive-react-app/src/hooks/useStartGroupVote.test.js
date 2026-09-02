import { act, renderHook } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import {CatalogContext} from '../context/CatalogContext';
import {TripContext} from '../context/TripContext';
import { useStartGroupVote } from './useStartGroupVote';
import leadApi from '../services/leadApi';

jest.mock('../services/leadApi');

const mockNavigate = jest.fn();
jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useNavigate: () => mockNavigate,
}));

function renderStartGroupVote(dispatch = jest.fn()) {
  const catalogState = { destinations: [{ id: 'd1', slug: 'prague', name: 'Prague' }] };
  const tripState = { tripItems: [] };
  const wrapper = ({ children }) => (
    <CatalogContext.Provider value={{ state: catalogState, dispatch }}>
      <TripContext.Provider value={{ state: tripState, dispatch }}>
        <MemoryRouter initialEntries={['/']}>{children}</MemoryRouter>
      </TripContext.Provider>
    </CatalogContext.Provider>
  );
  return { ...renderHook(() => useStartGroupVote(), { wrapper }), dispatch };
}

beforeEach(() => {
  mockNavigate.mockClear();
});

afterEach(() => {
  localStorage.clear();
});

test('openVoteSetup flips voteSetupOpen', () => {
  const {result} = renderStartGroupVote();

  expect(result.current.voteSetupOpen).toBe(false);
  act(() => result.current.openVoteSetup());
  expect(result.current.voteSetupOpen).toBe(true);
});

test('confirm enters /vote/new with the setup in the URL, without creating a lead or leaking email', () => {
    const {result, dispatch} = renderStartGroupVote();
    act(() => result.current.handleVoteConfirm({
        travelers: 8, startDate: '2026-09-04', endDate: '2026-09-06',
        destination: {id: 'd1', slug: 'prague'}, budget: null,
    }));
    expect(leadApi.createLead).not.toHaveBeenCalled();
    // The URL carries the setup so it survives the full page load out of a
    // server-rendered mount; the state rides along for the SPA's client hop.
    expect(mockNavigate).toHaveBeenCalledWith(
        '/vote/new?travelers=8&start=2026-09-04&end=2026-09-06&dest=prague',
        {state: {setup: expect.not.objectContaining({email: expect.anything()})}},
    );
    expect(dispatch).toHaveBeenCalledWith({ type: 'CLOSE_TRIP_BUILDER_MODAL' });
});

test('continueToQuiz hands the setup to the quiz via location state', () => {
    const {result, dispatch} = renderStartGroupVote();
    act(() => result.current.continueToQuiz({
        travelers: 8, startDate: '2026-09-04', endDate: '2026-09-06',
        destination: {id: 'd1', slug: 'prague'}, budget: null, email: 'x@y.z',
    }, {replace: true}));
    expect(mockNavigate).toHaveBeenCalledWith('/vote/new/quiz', {
        state: {setup: expect.not.objectContaining({email: expect.anything()})},
        replace: true,
    });
    expect(dispatch).toHaveBeenCalledWith({ type: 'CLOSE_TRIP_BUILDER_MODAL' });
});
