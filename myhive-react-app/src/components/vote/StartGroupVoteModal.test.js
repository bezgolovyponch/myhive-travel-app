import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import StartGroupVoteModal from './StartGroupVoteModal';
import voteApi from '../../services/voteApi';
import { pushEvent } from '../../utils/analytics';
import { openWhatsApp } from '../../utils/openWhatsApp';

jest.mock('../../services/voteApi', () => ({
  __esModule: true,
  default: { createCartSession: jest.fn(), createSession: jest.fn() },
}));

jest.mock('../../utils/analytics', () => ({ pushEvent: jest.fn() }));
jest.mock('../../utils/openWhatsApp', () => ({ openWhatsApp: jest.fn() }));
jest.mock('../../utils/uuid', () => ({ generateUuid: () => 'tok-1' }));

// The modal uses the site's DateRangePicker (DayPicker); two plain inputs stand
// in for it so tests set dates without calendar interaction (as in TripSetupModal.test).
jest.mock('../DateRangePicker', () =>
  function MockDateRangePicker({ from, to, onChange }) {
    return (
      <>
        <input data-testid="date-from" value={from} onChange={e => onChange(e.target.value, to)} />
        <input data-testid="date-to" value={to} onChange={e => onChange(from, e.target.value)} />
      </>
    );
  }
);

const mockNavigate = jest.fn();
jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useNavigate: () => mockNavigate,
}));

const WHATSAPP = 'Send to WhatsApp group';
const START = 'Start planning together';
const PHONE = 'Your WhatsApp number';
const EMAIL = 'Your email';

function renderModal(props = {}) {
  return render(
    <MemoryRouter>
      <StartGroupVoteModal
        isOpen
        onClose={jest.fn()}
        destinationId="d-1"
        destinationName="Prague"
        destinationSlug="prague"
        activityIds={['a-1', 'a-2']}
        numberOfTravelers={10}
        startDate="2026-10-16"
        endDate="2026-10-18"
        {...props}
      />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  voteApi.createCartSession.mockResolvedValue({ shareToken: 'tok-1', managerToken: 'mgr-1' });
  voteApi.createSession.mockResolvedValue({ shareToken: 'tok-1', managerToken: 'mgr-1' });
});

afterEach(() => {
  localStorage.clear();
  jest.clearAllMocks();
});

test('shows the headline, what the vote gives back and the message for the group', () => {
  renderModal();

  expect(screen.getByRole('heading', { name: 'Your group votes. You get the result.' })).toBeInTheDocument();
  expect(screen.getByText("The group's choice")).toBeInTheDocument();
  expect(screen.getByText('9 of 10 voted')).toBeInTheDocument();
  expect(screen.getByText('AK-47 shooting')).toBeInTheDocument();
  expect(screen.getByText('✕ 6 no')).toBeInTheDocument();
  expect(screen.getByText("Group's recommendations")).toBeInTheDocument();
  expect(screen.getByText('Vote on the Prague stag plan')).toBeInTheDocument();
  expect(screen.getByText(/^Lads! Prague stag, .*16.*18 Oct\. Vote yes or no on the plan\. Takes 1 minute/))
      .toBeInTheDocument();
  expect(screen.getByText('We only write to you about this trip.')).toBeInTheDocument();
  // The old sheet's "How it works" steps are gone.
  expect(screen.queryByText('Share to group')).not.toBeInTheDocument();
});

test('Germany is the country picked first; any other code can be typed', async () => {
  renderModal();
  const send = screen.getByRole('button', { name: WHATSAPP });
  expect(screen.getByLabelText('Country code')).toHaveValue('+49');
  expect(screen.getByRole('option', { name: 'DE +49' }).selected).toBe(true);

  await userEvent.selectOptions(screen.getByLabelText('Country code'), 'Other…');
  // The picker becomes a field for the code.
  expect(screen.getByLabelText('Country code')).toHaveValue('+');
  await userEvent.type(screen.getByLabelText(PHONE), '612345678');
  expect(send).toBeDisabled(); // a bare "+" is not a code
  await userEvent.type(screen.getByLabelText('Country code'), '33');
  expect(send).toBeEnabled();

  await userEvent.click(send);
  await waitFor(() => expect(voteApi.createCartSession).toHaveBeenCalledWith(
      expect.objectContaining({ initiatorPhone: '+33612345678' })));
});

test('each button waits for its own contact', async () => {
  renderModal();
  await userEvent.selectOptions(screen.getByLabelText('Country code'), '+44');

  expect(screen.getByRole('button', { name: WHATSAPP })).toBeDisabled();
  expect(screen.getByRole('button', { name: START })).toBeDisabled();

  await userEvent.type(screen.getByLabelText(PHONE), '7700 900123');
  expect(screen.getByRole('button', { name: WHATSAPP })).toBeEnabled();
  expect(screen.getByRole('button', { name: START })).toBeDisabled();

  await userEvent.type(screen.getByLabelText(EMAIL), 'max@example.com');
  expect(screen.getByRole('button', { name: START })).toBeEnabled();
});

test('half a number does not count: it has to be a whole number for the country picked', async () => {
  renderModal();
  const send = screen.getByRole('button', { name: WHATSAPP });
  await userEvent.selectOptions(screen.getByLabelText('Country code'), '+44');

  // UK mobiles are 10 digits after the trunk 0.
  await userEvent.type(screen.getByLabelText(PHONE), '7700 900');
  expect(send).toBeDisabled();
  await userEvent.type(screen.getByLabelText(PHONE), '123');
  expect(send).toBeEnabled();

  // The same field under another country: Czech numbers are 9 digits, so 7 are not one.
  await userEvent.selectOptions(screen.getByLabelText('Country code'), '+420');
  await userEvent.clear(screen.getByLabelText(PHONE));
  await userEvent.type(screen.getByLabelText(PHONE), '6085940');
  expect(send).toBeDisabled();
  await userEvent.type(screen.getByLabelText(PHONE), '12');
  expect(send).toBeEnabled();
});

test('WhatsApp: opens the group message with the link in the same tap, creates the vote with the number, opens the dashboard', async () => {
  const onClose = jest.fn();
  renderModal({ onClose });
  await userEvent.selectOptions(screen.getByLabelText('Country code'), '+420');
  await userEvent.type(screen.getByLabelText(PHONE), '602 123 456');

  await userEvent.click(screen.getByRole('button', { name: WHATSAPP }));

  expect(openWhatsApp).toHaveBeenCalledTimes(1);
  const { webUrl, appUrl } = openWhatsApp.mock.calls[0][0];
  const text = decodeURIComponent(webUrl.split('text=')[1]);
  expect(text).toMatch(/^Lads! Prague stag/);
  expect(text).toContain('/vote/tok-1/activities?ref=invite');
  expect(appUrl).toMatch(/^whatsapp:\/\/send\?text=/);
  await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith(
      '/destination/prague?tab=trip-builder&voteSession=tok-1'));
  expect(voteApi.createCartSession).toHaveBeenCalledWith(expect.objectContaining({
    destinationId: 'd-1',
    initiatorPhone: '+420602123456',
    initiatorEmail: undefined,
    shareToken: 'tok-1',
    numberOfTravelers: 10,
    startDate: '2026-10-16',
    endDate: '2026-10-18',
    activityIds: ['a-1', 'a-2'],
  }));
  expect(localStorage.getItem('myhive-manager-tok-1')).toBe('mgr-1');
  expect(localStorage.getItem('myhive-initiator-tok-1')).toBe('true');
  expect(localStorage.getItem('myhive-trip-vote-session')).toBe('tok-1');
  expect(pushEvent).toHaveBeenCalledWith('contact_captured',
      expect.objectContaining({ trip_id: 'tok-1', channel: 'whatsapp' }));
  expect(pushEvent).toHaveBeenCalledWith('group_message_sent', expect.anything());
  // Closed, so the organiser comes back from WhatsApp to the dashboard, not the modal.
  expect(onClose).toHaveBeenCalled();
  expect(pushEvent).not.toHaveBeenCalledWith('modal_abandoned', expect.anything());
});

test('a UK number typed with the leading 0 is sent without it', async () => {
  renderModal();
  await userEvent.selectOptions(screen.getByLabelText('Country code'), '+44');
  await userEvent.type(screen.getByLabelText(PHONE), '07700 900123');

  await userEvent.click(screen.getByRole('button', { name: WHATSAPP }));

  await waitFor(() => expect(voteApi.createCartSession).toHaveBeenCalledWith(
      expect.objectContaining({ initiatorPhone: '+447700900123' })));
});

test('email: creates the vote with the email and opens the dashboard, without opening WhatsApp', async () => {
  const onClose = jest.fn();
  renderModal({ onClose });
  await userEvent.type(screen.getByLabelText(EMAIL), 'max@example.com');

  await userEvent.click(screen.getByRole('button', { name: START }));

  await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith(
      '/destination/prague?tab=trip-builder&voteSession=tok-1'));
  expect(openWhatsApp).not.toHaveBeenCalled();
  expect(voteApi.createCartSession).toHaveBeenCalledWith(expect.objectContaining({
    initiatorEmail: 'max@example.com', initiatorPhone: undefined,
  }));
  expect(pushEvent).toHaveBeenCalledWith('contact_captured',
      expect.objectContaining({ trip_id: 'tok-1', channel: 'email' }));
  expect(onClose).toHaveBeenCalled();
});

test('both contacts typed: both go with the vote', async () => {
  renderModal();
  await userEvent.selectOptions(screen.getByLabelText('Country code'), '+44');
  await userEvent.type(screen.getByLabelText(PHONE), '7700900123');
  await userEvent.type(screen.getByLabelText(EMAIL), 'max@example.com');

  await userEvent.click(screen.getByRole('button', { name: START }));

  await waitFor(() => expect(voteApi.createCartSession).toHaveBeenCalledWith(expect.objectContaining({
    initiatorEmail: 'max@example.com', initiatorPhone: '+447700900123',
  })));
});

test('a failed create shows an error and keeps the modal; a retry reuses the same link', async () => {
  voteApi.createCartSession.mockRejectedValueOnce(new Error('boom'));
  renderModal();
  await userEvent.type(screen.getByLabelText(EMAIL), 'max@example.com');

  await userEvent.click(screen.getByRole('button', { name: START }));

  expect(await screen.findByRole('alert')).toHaveTextContent("We couldn't start the vote. Please try again.");
  expect(mockNavigate).not.toHaveBeenCalled();

  await userEvent.click(screen.getByRole('button', { name: START }));

  await waitFor(() => expect(mockNavigate).toHaveBeenCalled());
  expect(voteApi.createCartSession.mock.calls.map(c => c[0].shareToken)).toEqual(['tok-1', 'tok-1']);
});

test('quiz mode creates a quiz vote with the quiz answers and the contact', async () => {
  renderModal({ voteMode: 'QUIZ', quizResponses: [{ questionId: 'q', answerId: 'a' }], budget: 500 });
  await userEvent.type(screen.getByLabelText(EMAIL), 'max@example.com');

  await userEvent.click(screen.getByRole('button', { name: START }));

  await waitFor(() => expect(voteApi.createSession).toHaveBeenCalledWith(expect.objectContaining({
    initiatorEmail: 'max@example.com', budget: 500, quizResponses: [{ questionId: 'q', answerId: 'a' }],
  })));
  expect(localStorage.getItem('myhive-trip-vote-session')).toBeNull();
});

test('without trip dates the dates are asked for and both buttons wait for them', async () => {
  renderModal({ startDate: null, endDate: null });
  await userEvent.type(screen.getByLabelText(EMAIL), 'max@example.com');

  expect(screen.getByText('Trip dates')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: START })).toBeDisabled();

  fireEvent.change(screen.getByTestId('date-from'), { target: { value: '2026-10-16' } });
  fireEvent.change(screen.getByTestId('date-to'), { target: { value: '2026-10-18' } });

  expect(screen.getByRole('button', { name: START })).toBeEnabled();
  await userEvent.click(screen.getByRole('button', { name: START }));
  await waitFor(() => expect(voteApi.createCartSession).toHaveBeenCalledWith(
      expect.objectContaining({ startDate: '2026-10-16', endDate: '2026-10-18' })));
});

test('closing without launching reports which contact was typed', async () => {
  const onClose = jest.fn();
  renderModal({ onClose });
  await userEvent.type(screen.getByLabelText(PHONE), '77');

  await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Close' }));

  expect(onClose).toHaveBeenCalled();
  expect(pushEvent).toHaveBeenCalledWith('modal_abandoned',
      expect.objectContaining({ modal: 'start_vote', has_phone: true, has_email: false }));
});
