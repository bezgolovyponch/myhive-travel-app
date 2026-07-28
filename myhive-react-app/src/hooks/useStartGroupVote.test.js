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

test('confirm navigates to the quiz without creating a lead and without email in state', () => {
    const {result, dispatch} = renderStartGroupVote();
    act(() => result.current.handleVoteConfirm({
        travelers: 8, startDate: '2026-09-04', endDate: '2026-09-06',
        destination: {id: 'd1', slug: 'prague'}, budget: null,
    }));
    expect(leadApi.createLead).not.toHaveBeenCalled();
    expect(mockNavigate).toHaveBeenCalledWith('/vote/new/quiz', {
        state: {setup: expect.not.objectContaining({email: expect.anything()})},
    });
    expect(dispatch).toHaveBeenCalledWith({ type: 'CLOSE_TRIP_BUILDER_MODAL' });
});
