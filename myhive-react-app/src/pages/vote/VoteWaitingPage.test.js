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

test('with managerToken already in localStorage and no param, shows End voting early and leaves URL clean', async () => {
  localStorage.setItem('myhive-manager-tok-3', 'mgr-pre');
  localStorage.setItem('myhive-initiator-tok-3', 'true');

  renderAt('/vote/tok-3/waiting');

  expect(await screen.findByText(/End voting early/i)).toBeInTheDocument();
  expect(screen.getByTestId('search').textContent).toBe('');
});

test('shows "already voted" confirmation when the voted flag is set for this session', async () => {
  localStorage.setItem('myhive-voted-tok-4', 'true');

  renderAt('/vote/tok-4/waiting');

  expect(await screen.findByText(/You('|’)ve already voted in this session/i)).toBeInTheDocument();
});

test('redirects to the trip builder when the session reports COMPLETED', async () => {
  voteApi.getSession.mockResolvedValue({
    destinationName: 'Bali',
    destinationSlug: 'bali',
    status: 'COMPLETED',
    participantCount: 3,
  });

  render(
    <MemoryRouter initialEntries={['/vote/tok-6/waiting']}>
      <Routes>
        <Route path="/vote/:shareToken/waiting" element={<VoteWaitingPage />} />
        <Route path="/destination/:slug" element={<div data-testid="dest-page" />} />
      </Routes>
    </MemoryRouter>
  );

  expect(await screen.findByTestId('dest-page')).toBeInTheDocument();
});

test('does not show "already voted" when the voted flag is missing or belongs to another session', async () => {
  localStorage.setItem('myhive-voted-other-token', 'true');

  renderAt('/vote/tok-5/waiting');

  expect(await screen.findByText(/Share with friends/i)).toBeInTheDocument();
  expect(screen.queryByText(/already voted/i)).not.toBeInTheDocument();
});
