import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import StartGroupVoteModal from './StartGroupVoteModal';
import voteApi from '../../services/voteApi';
import { pushEvent } from '../../utils/analytics';

jest.mock('../../services/voteApi', () => ({
  __esModule: true,
  default: { createCartSession: jest.fn(), createSession: jest.fn() },
}));

jest.mock('../../utils/analytics', () => ({ pushEvent: jest.fn() }));

const mockNavigate = jest.fn();
jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useNavigate: () => mockNavigate,
}));

const SUBMIT = 'Get the link for your group';

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

// Step 1 → step 2: the email screen only appears after "Create vote".
async function goToEmailStep() {
  await userEvent.click(screen.getByRole('button', { name: 'Create vote' }));
  return screen.getByLabelText('Email');
}

async function launchWith(email) {
  const input = await goToEmailStep();
  await userEvent.type(input, email);
  await userEvent.click(screen.getByRole('button', { name: SUBMIT }));
}

afterEach(() => {
  localStorage.clear();
});

test('step 1 has no email input; "Create vote" reveals the one-input email screen', async () => {
  renderModal();
  expect(screen.queryByLabelText('Email')).not.toBeInTheDocument();

  await userEvent.click(screen.getByRole('button', { name: 'Create vote' }));

  expect(screen.getByRole('heading', { name: 'Your vote is saved.' })).toBeInTheDocument();
  expect(screen.getByText('Where should we send the results?')).toBeInTheDocument();
  const input = screen.getByLabelText('Email');
  expect(input).toHaveAttribute('type', 'email');
  expect(input).toHaveAttribute('autocomplete', 'email');
  await waitFor(() => expect(input).toHaveFocus());
  expect(screen.getAllByRole('textbox')).toHaveLength(1);
  expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
  expect(screen.getByText(/reminder message you can paste into the chat/)).toBeInTheDocument();
  expect(screen.getByRole('button', { name: SUBMIT })).toBeInTheDocument();
  expect(voteApi.createCartSession).not.toHaveBeenCalled();
  expect(pushEvent).toHaveBeenCalledWith('organizer_voted', { vote_mode: 'CART', selected_count: 2 });
  expect(pushEvent).toHaveBeenCalledWith('email_screen_view', { vote_mode: 'CART' });
});

test('empty email shows the error, keeps focus and never calls the API', async () => {
  renderModal();
  await goToEmailStep();

  await userEvent.click(screen.getByRole('button', { name: SUBMIT }));

  expect(screen.getByText('Please check the email address.')).toBeInTheDocument();
  expect(screen.getByLabelText('Email')).toHaveFocus();
  expect(voteApi.createCartSession).not.toHaveBeenCalled();
  expect(pushEvent).toHaveBeenCalledWith('email_invalid_attempt', { vote_mode: 'CART', reason: 'empty' });
});

test('malformed email keeps the typed value', async () => {
  renderModal();
  const input = await goToEmailStep();
  await userEvent.type(input, 'sam@nowhere');

  await userEvent.click(screen.getByRole('button', { name: SUBMIT }));

  expect(screen.getByText('Please check the email address.')).toBeInTheDocument();
  expect(input).toHaveValue('sam@nowhere');
  expect(pushEvent).toHaveBeenCalledWith('email_invalid_attempt', { vote_mode: 'CART', reason: 'format' });
  expect(voteApi.createCartSession).not.toHaveBeenCalled();
});

test('valid email creates the session with initiatorEmail, stores tokens, fires the funnel, navigates', async () => {
  voteApi.createCartSession.mockResolvedValue({ shareToken: 't-1', managerToken: 'm-1' });
  renderModal();

  await launchWith('sam@example.com');

  await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/vote/t-1/waiting'));
  expect(voteApi.createCartSession).toHaveBeenCalledWith({
    destinationId: 'd-1',
    initiatorEmail: 'sam@example.com',
    numberOfTravelers: 4,
    startDate: '2026-08-01',
    endDate: '2026-08-03',
    activityIds: ['a-1', 'a-2'],
  });
  expect(localStorage.getItem('myhive-manager-t-1')).toBe('m-1');
  expect(localStorage.getItem('myhive-initiator-t-1')).toBe('true');
  expect(localStorage.getItem('myhive-trip-vote-session')).toBe('t-1');
  expect(pushEvent).toHaveBeenCalledWith('contact_captured', {
    trip_id: 't-1', vote_mode: 'CART', source: 'vote_email_screen',
  });
  expect(pushEvent).toHaveBeenCalledWith('vote_launched', {
    trip_id: 't-1', user_role: 'organizer', selected_count: 2,
  });
  expect(pushEvent).toHaveBeenCalledWith('link_revealed', { trip_id: 't-1', vote_mode: 'CART' });
  const order = pushEvent.mock.calls.map(([name]) => name);
  expect(order.indexOf('contact_captured')).toBeLessThan(order.indexOf('vote_launched'));
  expect(order.indexOf('vote_launched')).toBeLessThan(order.indexOf('link_revealed'));
});

test('API failure keeps the email, fires no launch events, and allows a retry', async () => {
  voteApi.createCartSession
    .mockRejectedValueOnce(new Error('activityId x does not exist'))
    .mockResolvedValueOnce({ shareToken: 't-3', managerToken: 'm-3' });
  renderModal();

  await launchWith('sam@example.com');

  expect(await screen.findByText('activityId x does not exist')).toBeInTheDocument();
  expect(screen.getByLabelText('Email')).toHaveValue('sam@example.com');
  expect(pushEvent).not.toHaveBeenCalledWith('vote_launched', expect.anything());
  expect(pushEvent).not.toHaveBeenCalledWith('link_revealed', expect.anything());

  await userEvent.click(screen.getByRole('button', { name: SUBMIT }));

  await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/vote/t-3/waiting'));
  expect(voteApi.createCartSession).toHaveBeenCalledTimes(2);
});

test('missing trip dates block the email step', async () => {
  renderModal({ startDate: '', endDate: '' });

  await userEvent.click(screen.getByRole('button', { name: 'Create vote' }));

  expect(screen.getByText('Trip dates are required')).toBeInTheDocument();
  expect(screen.queryByLabelText('Email')).not.toBeInTheDocument();
  expect(voteApi.createCartSession).not.toHaveBeenCalled();
});

test('accepts dates typed into its own date inputs', async () => {
  voteApi.createCartSession.mockResolvedValue({ shareToken: 't-2', managerToken: 'm-2' });
  renderModal({ startDate: '', endDate: '' });
  fireEvent.change(screen.getByLabelText('Start date'), { target: { value: '2026-09-04' } });
  fireEvent.change(screen.getByLabelText('End date'), { target: { value: '2026-09-06' } });

  await launchWith('sam@example.com');

  await waitFor(() => expect(voteApi.createCartSession).toHaveBeenCalledWith(
    expect.objectContaining({ startDate: '2026-09-04', endDate: '2026-09-06', initiatorEmail: 'sam@example.com' }),
  ));
});

test('QUIZ mode creates a QUIZ session with quiz payload and email, and calls onLaunched', async () => {
  voteApi.createSession.mockResolvedValue({ shareToken: 'tok1', managerToken: 'mgr1' });
  const onLaunched = jest.fn();
  renderModal({
    voteMode: 'QUIZ', quizResponses: [{ questionId: 'q1', answerId: 'a1' }], budget: null, onLaunched,
  });

  await launchWith('sam@example.com');

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

test('value-promise microcopy is shown on step 1', () => {
  renderModal();
  expect(screen.getByText(/share the link with your mates/i)).toBeInTheDocument();
});

test('closing on step 1 fires modal_abandoned without an email', async () => {
  const onClose = jest.fn();
  renderModal({ onClose });

  await userEvent.click(screen.getByRole('button', { name: 'Close' }));

  expect(pushEvent).toHaveBeenCalledWith('modal_abandoned', {
    modal: 'start_vote', vote_mode: 'CART', has_email: false, step: 'details',
  });
  expect(onClose).toHaveBeenCalled();
});

test('closing on the email step reports whether an address was typed', async () => {
  const onClose = jest.fn();
  renderModal({ onClose });
  const input = await goToEmailStep();
  await userEvent.type(input, 'sam@example.com');

  await userEvent.click(screen.getByRole('button', { name: 'Close' }));

  expect(pushEvent).toHaveBeenCalledWith('modal_abandoned', {
    modal: 'start_vote', vote_mode: 'CART', has_email: true, step: 'email',
  });
});

test('reopening after closing on the email step starts at step 1 again', async () => {
  const onClose = jest.fn();
  const { rerender } = renderModal({ onClose });
  const input = await goToEmailStep();
  await userEvent.type(input, 'sam@nowhere');
  await userEvent.click(screen.getByRole('button', { name: SUBMIT }));
  expect(screen.getByText('Please check the email address.')).toBeInTheDocument();

  // The modal stays mounted between openings, so a reopen must not resume mid-flow.
  rerender(
    <MemoryRouter>
      <StartGroupVoteModal
        isOpen={false}
        onClose={onClose}
        destinationId="d-1"
        activityIds={['a-1', 'a-2']}
        numberOfTravelers={4}
        startDate="2026-08-01"
        endDate="2026-08-03"
      />
    </MemoryRouter>,
  );
  rerender(
    <MemoryRouter>
      <StartGroupVoteModal
        isOpen
        onClose={onClose}
        destinationId="d-1"
        activityIds={['a-1', 'a-2']}
        numberOfTravelers={4}
        startDate="2026-08-01"
        endDate="2026-08-03"
      />
    </MemoryRouter>,
  );

  expect(screen.getByRole('button', { name: 'Create vote' })).toBeInTheDocument();
  expect(screen.queryByLabelText('Email')).not.toBeInTheDocument();
  expect(screen.queryByText('Please check the email address.')).not.toBeInTheDocument();

  // Every open counts once in the funnel: the ratio link_revealed / email_screen_view
  // is the metric the rollback decision reads.
  await userEvent.click(screen.getByRole('button', { name: 'Create vote' }));
  expect(pushEvent.mock.calls.filter(([name]) => name === 'email_screen_view')).toHaveLength(2);
  // The typed address survives as a draft, like the dates in TripSetupModal.
  expect(screen.getByLabelText('Email')).toHaveValue('sam@nowhere');
});

test('Enter in the email field submits, and a fixed address clears the error', async () => {
  voteApi.createCartSession.mockResolvedValue({ shareToken: 't-4', managerToken: 'm-4' });
  renderModal();
  const input = await goToEmailStep();
  await userEvent.type(input, 'sam@nowhere');
  await userEvent.click(screen.getByRole('button', { name: SUBMIT }));
  expect(screen.getByText('Please check the email address.')).toBeInTheDocument();
  expect(input).toHaveAttribute('aria-describedby', 'start-vote-email-error');

  await userEvent.clear(input);
  await userEvent.type(input, 'sam@example.com{Enter}');

  await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/vote/t-4/waiting'));
  expect(screen.queryByText('Please check the email address.')).not.toBeInTheDocument();
  expect(voteApi.createCartSession).toHaveBeenCalledWith(
    expect.objectContaining({ initiatorEmail: 'sam@example.com' }),
  );
});

test('does not fire modal_abandoned when closed after a successful launch', async () => {
  voteApi.createCartSession.mockResolvedValue({ shareToken: 't-1', managerToken: 'm-1' });
  const onClose = jest.fn();
  renderModal({ onClose });

  await launchWith('sam@example.com');
  await waitFor(() => expect(mockNavigate).toHaveBeenCalled());

  pushEvent.mockClear();
  await userEvent.click(screen.getByRole('button', { name: 'Close' }));

  expect(pushEvent).not.toHaveBeenCalledWith('modal_abandoned', expect.anything());
});
