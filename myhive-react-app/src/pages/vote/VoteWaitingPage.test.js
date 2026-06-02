import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import VoteWaitingPage from './VoteWaitingPage';
import voteApi from '../../services/voteApi';

jest.mock('../../services/voteApi');

function LocationSearch() {
  const location = useLocation();
  return <div data-testid="search">{location.search}</div>;
}

function renderAt(entry) {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <Routes>
        <Route
          path="/vote/:shareToken/waiting"
          element={<><VoteWaitingPage /><LocationSearch /></>}
        />
      </Routes>
    </MemoryRouter>
  );
}

beforeEach(() => {
  localStorage.clear();
  voteApi.getSession.mockResolvedValue({
    destinationName: 'Bali',
    destinationSlug: 'bali',
    status: 'ACTIVE',
    expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
    participantCount: 0,
    numberOfTravelers: 2,
  });
  voteApi.getParticipantCount.mockResolvedValue({ count: 0 });
});

test('adopts managerToken from ?manager=, shows End voting early, strips token from URL', async () => {
  renderAt('/vote/tok-1/waiting?manager=mgr-9');

  expect(await screen.findByText(/End voting early/i)).toBeInTheDocument();
  expect(localStorage.getItem('myhive-manager-tok-1')).toBe('mgr-9');
  expect(localStorage.getItem('myhive-initiator-tok-1')).toBe('true');
  await waitFor(() => expect(screen.getByTestId('search').textContent).toBe(''));
});

test('without manager param or localStorage, End voting early is absent', async () => {
  renderAt('/vote/tok-2/waiting');

  expect(await screen.findByText(/Share with friends/i)).toBeInTheDocument();
  expect(screen.queryByText(/End voting early/i)).not.toBeInTheDocument();
});
