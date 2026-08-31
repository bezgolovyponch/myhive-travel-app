import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import StartGroupVoteModal from './StartGroupVoteModal';
import voteApi from '../../services/voteApi';
import { pushEvent } from '../../utils/analytics';
import { getAttribution } from '../../utils/attribution';
import { getCookie } from '../../utils/cookies';

jest.mock('../../services/voteApi', () => ({
  __esModule: true,
  default: { createCartSession: jest.fn(), createSession: jest.fn() },
}));

jest.mock('../../utils/analytics', () => ({ pushEvent: jest.fn() }));

jest.mock('../../utils/attribution', () => ({ getAttribution: jest.fn() }));

jest.mock('../../utils/cookies', () => ({ getCookie: jest.fn() }));

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

beforeEach(() => {
  getAttribution.mockReturnValue({});
  getCookie.mockReturnValue(null);
});

afterEach(() => {
  localStorage.clear();
});

test('does not ask for an email — it is collected on the booking page instead', () => {
  renderModal();
  expect(screen.queryByLabelText('Your email')).not.toBeInTheDocument();
});

test('creates the session, stores tokens and navigates to waiting', async () => {
  voteApi.createCartSession.mockResolvedValue({ shareToken: 't-1', managerToken: 'm-1' });
  renderModal();

  await userEvent.click(screen.getByRole('button', { name: 'Create vote' }));

  await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/vote/t-1/waiting'));
  expect(voteApi.createCartSession).toHaveBeenCalledWith({
    destinationId: 'd-1',
    numberOfTravelers: 4,
    startDate: '2026-08-01',
    endDate: '2026-08-03',
    activityIds: ['a-1', 'a-2'],
    fbp: null,
    fbc: null,
    fbclid: undefined,
  });
  expect(localStorage.getItem('myhive-manager-t-1')).toBe('m-1');
  expect(localStorage.getItem('myhive-initiator-t-1')).toBe('true');
  expect(localStorage.getItem('myhive-trip-vote-session')).toBe('t-1');
  expect(pushEvent).toHaveBeenCalledWith('vote_launched', {
    nights: 2,
    group_size: 4,
    activities_count: 2,
    vote_id: 't-1',
    source_campaign: undefined,
    trip_id: 't-1',
    user_role: 'organizer',
    selected_count: 2,
  });
});

test('passes fbp/fbc from cookies and fbclid from attribution to createCartSession', async () => {
  getCookie.mockImplementation((name) => ({ _fbp: 'fb.1.1.2', _fbc: 'fb.1.1.abc' }[name] ?? null));
  getAttribution.mockReturnValue({ fbclid: 'click-123' });
  voteApi.createCartSession.mockResolvedValue({ shareToken: 't-3', managerToken: 'm-3' });
  renderModal();

  await userEvent.click(screen.getByRole('button', { name: 'Create vote' }));

  await waitFor(() => expect(voteApi.createCartSession).toHaveBeenCalledWith(
    expect.objectContaining({ fbp: 'fb.1.1.2', fbc: 'fb.1.1.abc', fbclid: 'click-123' }),
  ));
});

test('passes fbp/fbc/fbclid to createSession (QUIZ mode)', async () => {
  getCookie.mockImplementation((name) => ({ _fbp: 'fb.1.1.2', _fbc: 'fb.1.1.abc' }[name] ?? null));
  getAttribution.mockReturnValue({ fbclid: 'click-123' });
  voteApi.createSession.mockResolvedValue({ shareToken: 'tok-9', managerToken: 'mgr-9' });
  renderModal({ voteMode: 'QUIZ', quizResponses: [{ questionId: 'q1', answerId: 'a1' }], budget: null });

  await userEvent.click(screen.getByRole('button', { name: 'Create vote' }));

  await waitFor(() => expect(voteApi.createSession).toHaveBeenCalledWith(
    expect.objectContaining({ fbp: 'fb.1.1.2', fbc: 'fb.1.1.abc', fbclid: 'click-123' }),
  ));
});

test('does not fire vote_launched when createCartSession rejects', async () => {
  voteApi.createCartSession.mockRejectedValue(new Error('activityId x does not exist'));
  renderModal();

  await userEvent.click(screen.getByRole('button', { name: 'Create vote' }));

  await screen.findByText('activityId x does not exist');
  expect(pushEvent).not.toHaveBeenCalledWith('vote_launched', expect.anything());
});

test('shows date inputs when the trip has no dates yet and requires them', async () => {
  renderModal({ startDate: '', endDate: '' });
  await userEvent.click(screen.getByRole('button', { name: 'Create vote' }));
  expect(screen.getByText('Trip dates are required')).toBeInTheDocument();
  expect(voteApi.createCartSession).not.toHaveBeenCalled();
});

test('accepts dates typed into its own date inputs', async () => {
  voteApi.createCartSession.mockResolvedValue({ shareToken: 't-2', managerToken: 'm-2' });
  renderModal({ startDate: '', endDate: '' });

  fireEvent.change(screen.getByLabelText('Start date'), { target: { value: '2026-09-04' } });
  fireEvent.change(screen.getByLabelText('End date'), { target: { value: '2026-09-06' } });
  await userEvent.click(screen.getByRole('button', { name: 'Create vote' }));

  await waitFor(() => expect(voteApi.createCartSession).toHaveBeenCalledWith(
    expect.objectContaining({ startDate: '2026-09-04', endDate: '2026-09-06' }),
  ));
});

test('shows the API error message on failure', async () => {
  voteApi.createCartSession.mockRejectedValue(new Error('activityId x does not exist'));
  renderModal();
  await userEvent.click(screen.getByRole('button', { name: 'Create vote' }));
  expect(await screen.findByText('activityId x does not exist')).toBeInTheDocument();
});

test('QUIZ mode creates a QUIZ session with quiz payload and calls onLaunched', async () => {
  voteApi.createSession.mockResolvedValue({ shareToken: 'tok1', managerToken: 'mgr1' });
  const onLaunched = jest.fn();
  renderModal({
    voteMode: 'QUIZ', quizResponses: [{ questionId: 'q1', answerId: 'a1' }], budget: null, onLaunched,
  });

  await userEvent.click(screen.getByRole('button', { name: 'Create vote' }));

  await waitFor(() => expect(voteApi.createSession).toHaveBeenCalledWith(
    expect.objectContaining({
      quizResponses: [{ questionId: 'q1', answerId: 'a1' }],
      numberOfTravelers: 4,
      startDate: '2026-08-01',
      endDate: '2026-08-03',
      activityIds: ['a-1', 'a-2'],
    }),
  ));
  expect(voteApi.createSession).toHaveBeenCalledWith(
    expect.not.objectContaining({ initiatorEmail: expect.anything() }),
  );
  expect(voteApi.createCartSession).not.toHaveBeenCalled();
  expect(onLaunched).toHaveBeenCalled();
  expect(localStorage.getItem('myhive-trip-vote-session')).toBeNull();
  expect(mockNavigate).toHaveBeenCalledWith('/vote/tok1/waiting', { state: { managerToken: 'mgr1' } });
});

test('value-promise microcopy is shown', () => {
  renderModal();
  expect(screen.getByText(/share the link with your mates/i)).toBeInTheDocument();
});

test('closing without launching fires modal_abandoned', async () => {
  const onClose = jest.fn();
  renderModal({ onClose });

  await userEvent.click(screen.getByRole('button', { name: 'Close' }));

  expect(pushEvent).toHaveBeenCalledWith('modal_abandoned', {
    modal: 'start_vote', vote_mode: 'CART', has_email: false,
  });
  expect(onClose).toHaveBeenCalled();
});

test('does not fire modal_abandoned when closed after a successful launch', async () => {
  voteApi.createCartSession.mockResolvedValue({ shareToken: 't-1', managerToken: 'm-1' });
  const onClose = jest.fn();
  renderModal({ onClose });

  await userEvent.click(screen.getByRole('button', { name: 'Create vote' }));
  await waitFor(() => expect(mockNavigate).toHaveBeenCalled());

  pushEvent.mockClear();
  await userEvent.click(screen.getByRole('button', { name: 'Close' }));

  expect(pushEvent).not.toHaveBeenCalledWith('modal_abandoned', expect.anything());
});
