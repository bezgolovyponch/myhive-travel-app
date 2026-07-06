import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import StartGroupVoteModal from './StartGroupVoteModal';
import voteApi from '../../services/voteApi';
import { pushEvent } from '../../utils/analytics';

jest.mock('../../services/voteApi', () => ({
  __esModule: true,
  default: { createCartSession: jest.fn() },
}));

jest.mock('../../utils/analytics', () => ({ pushEvent: jest.fn() }));

const mockNavigate = jest.fn();
jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useNavigate: () => mockNavigate,
}));

function renderModal(props = {}) {
  return render(
    <MemoryRouter>
      <StartGroupVoteModal
        isOpen
        onClose={jest.fn()}
        destinationId="d-1"
        activityIds={['a-1', 'a-2']}
        numberOfTravelers={4}
        startDate="2026-08-01"
        endDate="2026-08-03"
        {...props}
      />
    </MemoryRouter>,
  );
}

afterEach(() => {
  localStorage.clear();
});

test('rejects an invalid email without calling the API', async () => {
  renderModal();
  await userEvent.type(screen.getByLabelText('Your email'), 'not-an-email');
  await userEvent.click(screen.getByRole('button', { name: 'Create vote' }));
  expect(screen.getByText('Email is invalid')).toBeInTheDocument();
  expect(voteApi.createCartSession).not.toHaveBeenCalled();
});

test('creates the session, stores tokens and navigates to waiting', async () => {
  voteApi.createCartSession.mockResolvedValue({ shareToken: 't-1', managerToken: 'm-1' });
  renderModal();

  await userEvent.type(screen.getByLabelText('Your email'), 'stag@example.com');
  await userEvent.click(screen.getByRole('button', { name: 'Create vote' }));

  await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/vote/t-1/waiting'));
  expect(voteApi.createCartSession).toHaveBeenCalledWith({
    destinationId: 'd-1',
    initiatorEmail: 'stag@example.com',
    numberOfTravelers: 4,
    startDate: '2026-08-01',
    endDate: '2026-08-03',
    activityIds: ['a-1', 'a-2'],
  });
  expect(localStorage.getItem('myhive-manager-t-1')).toBe('m-1');
  expect(localStorage.getItem('myhive-initiator-t-1')).toBe('true');
  expect(localStorage.getItem('myhive-trip-vote-session')).toBe('t-1');
  expect(pushEvent).toHaveBeenCalledWith('vote_launched', {
    trip_id: 't-1',
    user_role: 'organizer',
    selected_count: 2,
  });
});

test('does not fire vote_launched when createCartSession rejects', async () => {
  voteApi.createCartSession.mockRejectedValue(new Error('activityId x does not exist'));
  renderModal();

  await userEvent.type(screen.getByLabelText('Your email'), 'stag@example.com');
  await userEvent.click(screen.getByRole('button', { name: 'Create vote' }));

  await screen.findByText('activityId x does not exist');
  expect(pushEvent).not.toHaveBeenCalledWith('vote_launched', expect.anything());
});

test('shows date inputs when the trip has no dates yet and requires them', async () => {
  renderModal({ startDate: '', endDate: '' });
  await userEvent.type(screen.getByLabelText('Your email'), 'stag@example.com');
  await userEvent.click(screen.getByRole('button', { name: 'Create vote' }));
  expect(screen.getByText('Trip dates are required')).toBeInTheDocument();
  expect(voteApi.createCartSession).not.toHaveBeenCalled();
});

test('shows the API error message on failure', async () => {
  voteApi.createCartSession.mockRejectedValue(new Error('activityId x does not exist'));
  renderModal();
  await userEvent.type(screen.getByLabelText('Your email'), 'stag@example.com');
  await userEvent.click(screen.getByRole('button', { name: 'Create vote' }));
  expect(await screen.findByText('activityId x does not exist')).toBeInTheDocument();
});
