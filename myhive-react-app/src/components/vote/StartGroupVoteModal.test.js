import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import StartGroupVoteModal from './StartGroupVoteModal';
import voteApi from '../../services/voteApi';
import leadApi from '../../services/leadApi';
import { pushEvent } from '../../utils/analytics';

jest.mock('../../services/voteApi', () => ({
  __esModule: true,
  default: { createCartSession: jest.fn(), createSession: jest.fn() },
}));

jest.mock('../../services/leadApi', () => ({
  __esModule: true,
  default: { createLead: jest.fn() },
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
  jest.useRealTimers(); // leak-proof: a failing fake-timer test must not stall later tests
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

test('shows the email consent notice (GDPR soft opt-in basis)', () => {
  renderModal();

  expect(screen.getByText(/We['’]ll email you a link to your trip and a couple of reminders\. Unsubscribe anytime\./))
    .toBeInTheDocument();
});

test('QUIZ mode creates a QUIZ session with quiz payload and calls onLaunched', async () => {
  voteApi.createSession.mockResolvedValue({ shareToken: 'tok1', managerToken: 'mgr1' });
  const onLaunched = jest.fn();
  renderModal({
    voteMode: 'QUIZ', quizResponses: [{ questionId: 'q1', answerId: 'a1' }], budget: null, onLaunched,
  });

  await userEvent.type(screen.getByLabelText('Your email'), 'sam@example.com');
  await userEvent.click(screen.getByRole('button', { name: 'Create vote' }));

  await waitFor(() => expect(voteApi.createSession).toHaveBeenCalledWith(
    expect.objectContaining({
      initiatorEmail: 'sam@example.com',
      quizResponses: [{ questionId: 'q1', answerId: 'a1' }],
      numberOfTravelers: 4,
      startDate: '2026-08-01',
      endDate: '2026-08-03',
      activityIds: ['a-1', 'a-2'],
    }),
  ));
  expect(voteApi.createCartSession).not.toHaveBeenCalled();
  expect(onLaunched).toHaveBeenCalled();
  expect(localStorage.getItem('myhive-trip-vote-session')).toBeNull();
  expect(mockNavigate).toHaveBeenCalledWith('/vote/tok1/waiting', { state: { managerToken: 'mgr1' } });
});

test('typing a valid email captures a lead after the debounce', async () => {
  jest.useFakeTimers();
  leadApi.createLead.mockResolvedValue({ id: 'l1', restoreToken: 't1' });
  renderModal();

  fireEvent.change(screen.getByLabelText('Your email'), { target: { value: 'sam@example.com' } });
  await act(async () => jest.advanceTimersByTime(2000));

  expect(leadApi.createLead).toHaveBeenCalledWith(expect.objectContaining({ email: 'sam@example.com' }));
  jest.useRealTimers();
});

test('captures the typed trip dates when the modal collects them itself', async () => {
  jest.useFakeTimers();
  leadApi.createLead.mockResolvedValue({ id: 'l1', restoreToken: 't1' });
  renderModal({ startDate: '', endDate: '' });

  fireEvent.change(screen.getByLabelText('Start date'), { target: { value: '2026-09-04' } });
  fireEvent.change(screen.getByLabelText('End date'), { target: { value: '2026-09-06' } });
  fireEvent.change(screen.getByLabelText('Your email'), { target: { value: 'sam@example.com' } });
  await act(async () => jest.advanceTimersByTime(2000));

  expect(leadApi.createLead).toHaveBeenCalledWith(expect.objectContaining({
    email: 'sam@example.com', startDate: '2026-09-04', endDate: '2026-09-06',
  }));
  jest.useRealTimers();
});

test('value-promise microcopy is shown', () => {
  renderModal();
  expect(screen.getByText(/live vote results and your saved shortlist/i)).toBeInTheDocument();
});

test('closing without launching fires modal_abandoned with has_email', async () => {
  const onClose = jest.fn();
  renderModal({ onClose });

  await userEvent.type(screen.getByLabelText('Your email'), 'sam@example.com');
  await userEvent.click(screen.getByRole('button', { name: 'Close' }));

  expect(pushEvent).toHaveBeenCalledWith('modal_abandoned', {
    modal: 'start_vote', vote_mode: 'CART', has_email: true,
  });
  expect(onClose).toHaveBeenCalled();
});

test('closing before entering an email fires modal_abandoned with has_email: false', async () => {
  const onClose = jest.fn();
  renderModal({ onClose });

  await userEvent.click(screen.getByRole('button', { name: 'Close' }));

  expect(pushEvent).toHaveBeenCalledWith('modal_abandoned', {
    modal: 'start_vote', vote_mode: 'CART', has_email: false,
  });
});

test('does not fire modal_abandoned when closed after a successful launch', async () => {
  voteApi.createCartSession.mockResolvedValue({ shareToken: 't-1', managerToken: 'm-1' });
  const onClose = jest.fn();
  renderModal({ onClose });

  await userEvent.type(screen.getByLabelText('Your email'), 'stag@example.com');
  await userEvent.click(screen.getByRole('button', { name: 'Create vote' }));
  await waitFor(() => expect(mockNavigate).toHaveBeenCalled());

  pushEvent.mockClear();
  await userEvent.click(screen.getByRole('button', { name: 'Close' }));

  expect(pushEvent).not.toHaveBeenCalledWith('modal_abandoned', expect.anything());
});
