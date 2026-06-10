import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import ActivityVotePage from './ActivityVotePage';
import voteApi from '../../services/voteApi';

jest.mock('../../services/voteApi');

function renderAt(entry) {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <Routes>
        <Route path="/vote/:shareToken/activities" element={<ActivityVotePage />} />
      </Routes>
    </MemoryRouter>
  );
}

beforeEach(() => {
  localStorage.clear();
});

test('shows a friendly message when the vote session is not found', async () => {
  voteApi.getActivities.mockRejectedValue(new Error('Vote session not found'));

  renderAt('/vote/tok-404/activities');

  expect(await screen.findByText(/this vote session no longer exists/i)).toBeInTheDocument();
  expect(screen.getByText(/ask the organiser for a new link/i)).toBeInTheDocument();
});

test('shows the generic error message on other failures', async () => {
  voteApi.getActivities.mockRejectedValue(new Error('Failed to fetch vote activities'));

  renderAt('/vote/tok-500/activities');

  expect(await screen.findByText(/failed to fetch vote activities/i)).toBeInTheDocument();
});
