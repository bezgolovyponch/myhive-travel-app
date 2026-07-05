import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import CartVoteList from './CartVoteList';
import voteApi from '../../services/voteApi';

jest.mock('../../services/voteApi', () => ({
  __esModule: true,
  default: { castVotes: jest.fn() },
}));

const mockNavigate = jest.fn();
jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useNavigate: () => mockNavigate,
}));

const activities = [
  { id: 'a-1', name: 'Bar Crawl', price: 45, imageUrl: 'x.jpg', description: 'Pub tour of Prague' },
  { id: 'a-2', name: 'Karting', price: 60, imageUrl: 'y.jpg', description: 'Indoor karting' },
];

function renderList() {
  return render(
    <MemoryRouter>
      <CartVoteList shareToken="t-1" activities={activities} voterToken="v-1" />
    </MemoryRouter>,
  );
}

afterEach(() => {
  localStorage.clear();
});

test('submit is disabled until at least one activity is selected', async () => {
  renderList();
  expect(screen.getByRole('button', { name: /Submit vote/ })).toBeDisabled();
  await userEvent.click(screen.getAllByRole('button', { name: '♥ Vote' })[0]);
  expect(screen.getByRole('button', { name: /Submit vote/ })).toBeEnabled();
});

test('a second tap withdraws the vote', async () => {
  renderList();
  await userEvent.click(screen.getAllByRole('button', { name: '♥ Vote' })[0]);
  await userEvent.click(screen.getByRole('button', { name: '♥ Voted' }));
  expect(screen.getByRole('button', { name: /Submit vote/ })).toBeDisabled();
});

test('submits only upvotes, marks voted and navigates to waiting', async () => {
  voteApi.castVotes.mockResolvedValue();
  renderList();

  await userEvent.click(screen.getAllByRole('button', { name: '♥ Vote' })[0]);
  await userEvent.click(screen.getByRole('button', { name: /Submit vote/ }));

  await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/vote/t-1/waiting'));
  expect(voteApi.castVotes).toHaveBeenCalledWith('t-1', {
    voterToken: 'v-1',
    votes: [{ activityId: 'a-1', liked: true }],
  });
  expect(localStorage.getItem('myhive-voted-t-1')).toBe('true');
});

test('info button opens the activity preview modal', async () => {
  renderList();
  await userEvent.click(screen.getByRole('button', { name: 'About Bar Crawl' }));
  expect(screen.getByText('Pub tour of Prague')).toBeInTheDocument();
});
