import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { CatalogContext } from '../../context/CatalogContext';
import { TripContext } from '../../context/TripContext';
import VoteEntryPage from './VoteEntryPage';

const mockNavigate = jest.fn();
jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useNavigate: () => mockNavigate,
}));

const destinations = [{ id: 'd1', slug: 'prague', name: 'Prague' }];

function renderEntry(path, catalogState = { destinations, loading: false }) {
  return render(
    <CatalogContext.Provider value={{ state: catalogState, dispatch: jest.fn() }}>
      <TripContext.Provider value={{ state: { tripItems: [] }, dispatch: jest.fn() }}>
        <MemoryRouter initialEntries={[path]}>
          <VoteEntryPage />
        </MemoryRouter>
      </TripContext.Provider>
    </CatalogContext.Provider>
  );
}

beforeEach(() => {
  mockNavigate.mockClear();
});

test('a setup in the query string continues straight into the quiz, replacing this hop', () => {
  renderEntry('/vote/new?travelers=8&start=2026-09-04&end=2026-09-06&dest=prague');
  expect(mockNavigate).toHaveBeenCalledWith('/vote/new/quiz', {
    state: {
      setup: {
        travelers: 8,
        startDate: '2026-09-04',
        endDate: '2026-09-06',
        destination: destinations[0],
        budget: null,
      },
    },
    replace: true,
  });
  expect(screen.queryByText('Set Up Your Trip')).not.toBeInTheDocument();
});

test('waits for the catalog before resolving the destination', () => {
  renderEntry('/vote/new?travelers=8&start=2026-09-04&end=2026-09-06&dest=prague', {
    destinations: [],
    loading: true,
  });
  expect(mockNavigate).not.toHaveBeenCalled();
  expect(screen.queryByText('Set Up Your Trip')).not.toBeInTheDocument();
});

test('without params the setup modal opens, exactly as before', () => {
  renderEntry('/vote/new');
  expect(mockNavigate).not.toHaveBeenCalled();
  expect(screen.getByText('Set Up Your Trip')).toBeInTheDocument();
});

test('an unresolvable destination falls back to the modal instead of a dead end', () => {
  renderEntry('/vote/new?travelers=8&start=2026-09-04&end=2026-09-06&dest=nowhere');
  expect(mockNavigate).not.toHaveBeenCalled();
  expect(screen.getByText('Set Up Your Trip')).toBeInTheDocument();
});
