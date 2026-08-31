import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import TripSetupModal from './TripSetupModal';
import {CatalogContext} from '../context/CatalogContext';
import {TripContext, initialState as tripInitialState} from '../context/TripContext';

// The flag is off in real config while Prague is the only live destination;
// these tests cover the picker logic that comes back once it is re-enabled.
jest.mock('../services/config', () => ({
  ...jest.requireActual('../services/config'),
  DESTINATION_PICKER_ENABLED: true,
}));

// Replace DayPicker-based DateRangePicker with two simple inputs so tests can
// set dates without calendar interaction.
jest.mock('./DateRangePicker', () =>
  function MockDateRangePicker({ from, to, onChange }) {
    return (
      <>
        <input
          data-testid="date-from"
          value={from}
          onChange={e => onChange(e.target.value, to)}
        />
        <input
          data-testid="date-to"
          value={to}
          onChange={e => onChange(from, e.target.value)}
        />
      </>
    );
  }
);

jest.mock('../utils/analytics', () => ({ pushEvent: jest.fn() }));
jest.mock('../utils/attribution', () => ({ getRef: jest.fn() }));
jest.mock('../utils/consent', () => ({ hasConsent: jest.fn() }));
jest.mock('../utils/uuid', () => ({ generateUuid: jest.fn() }));

const { pushEvent } = require('../utils/analytics');
const { getRef } = require('../utils/attribution');
const { hasConsent } = require('../utils/consent');
const { generateUuid } = require('../utils/uuid');

const baseCatalogState = {
  destinations: [
    { id: 'd1', slug: 'prague', name: 'Prague' },
    { id: 'd2', slug: 'budapest', name: 'Budapest' },
  ],
  loading: false,
  error: null,
};

const singleDestCatalogState = {
  destinations: [{ id: 'd1', slug: 'prague', name: 'Prague' }],
  loading: false,
  error: null,
};

// Mirror the real TripContext contract so fallback branches aren't the only
// thing under test.
const baseTripState = {...tripInitialState};

// Prefill tests need dates that stay in the future no matter when the suite
// runs — past dates are deliberately dropped by the seeding logic.
function isoDaysFromNow(days) {
  const d = new Date();
  d.setDate(d.getDate() + days);
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${month}-${day}`;
}

function renderVoteModal(catalogState = baseCatalogState, tripState = baseTripState, props = {}) {
  const mockDispatch = jest.fn();
  const result = render(
    <CatalogContext.Provider value={{ state: catalogState, dispatch: jest.fn() }}>
      <TripContext.Provider value={{ state: tripState, dispatch: mockDispatch }}>
        <TripSetupModal
          isVoteMode
          voteOpen
          onVoteConfirm={jest.fn()}
          onVoteCancel={jest.fn()}
          {...props}
        />
      </TripContext.Provider>
    </CatalogContext.Provider>
  );
  return { ...result, mockDispatch };
}

function renderTripModal(catalogState = singleDestCatalogState, tripStateOverrides = {}) {
  const mockDispatch = jest.fn();
  const tripState = {...tripInitialState, tripSetupModalOpen: true, ...tripStateOverrides};
  const result = render(
    <CatalogContext.Provider value={{ state: catalogState, dispatch: jest.fn() }}>
      <TripContext.Provider value={{ state: tripState, dispatch: mockDispatch }}>
        <TripSetupModal />
      </TripContext.Provider>
    </CatalogContext.Provider>
  );
  return { ...result, mockDispatch };
}

beforeEach(() => {
  // CRA sets resetMocks: true — mock implementations are reset before every test.
  // Re-establish the defaults here so every test starts with a clean but working state.
  getRef.mockReturnValue(null);
  hasConsent.mockReturnValue(false);
  generateUuid.mockReturnValue('test-uuid-1234');
});

afterEach(() => localStorage.clear());

// Sets the travelers stepper input directly (mirrors user typing + blur).
function setTravelers(value) {
  const input = screen.getByLabelText(/number of travelers/i);
  fireEvent.change(input, { target: { value } });
  fireEvent.blur(input);
}

// Sets both date fields via the mocked DateRangePicker's testids.
function pickDates(from, to) {
  fireEvent.change(screen.getByTestId('date-from'), { target: { value: from } });
  fireEvent.change(screen.getByTestId('date-to'), { target: { value: to } });
}

function reopenVoteModal(rerender) {
  rerender(voteModalTree(false, jest.fn()));
  rerender(voteModalTree(true, jest.fn()));
}

// ---------------------------------------------------------------------------
// Pre-existing destination picker tests
// ---------------------------------------------------------------------------

test('never shows a destination picker — the destination is decided at booking', () => {
  renderVoteModal();

  expect(screen.queryByLabelText(/destination/i)).toBeNull();
  expect(screen.queryByRole('combobox')).toBeNull();
});

test('vote mode collects no email', () => {
  renderVoteModal();

  expect(screen.queryByLabelText(/email/i)).toBeNull();
  expect(screen.queryByText(/reminders\. unsubscribe anytime/i)).toBeNull();
});

test('no read-only destination row either — it is not shown in the modal at all', () => {
  renderVoteModal({
    ...baseCatalogState,
    destinations: [{ id: 'd1', slug: 'prague', name: 'Prague' }],
  });

  expect(screen.queryByText('Prague')).toBeNull();
  expect(screen.queryByRole('combobox')).toBeNull();
});

// ---------------------------------------------------------------------------
// Prefill from trip state (dates chosen when the first activity was added)
// ---------------------------------------------------------------------------

test('vote mode prefills travelers and dates from trip state', () => {
  const expectedStart = isoDaysFromNow(30);
  const expectedEnd = isoDaysFromNow(37);
  renderVoteModal(singleDestCatalogState, {
    ...baseTripState,
    tripTravelers: 4,
    tripStartDate: expectedStart,
    tripEndDate: expectedEnd,
  });

  expect(screen.getByRole('spinbutton', { name: /number of travelers/i })).toHaveValue(4);
  expect(screen.getByTestId('date-from')).toHaveValue(expectedStart);
  expect(screen.getByTestId('date-to')).toHaveValue(expectedEnd);
});

test('vote mode leaves fields at defaults when no trip setup exists', () => {
  renderVoteModal(singleDestCatalogState, baseTripState);

  expect(screen.getByRole('spinbutton', { name: /number of travelers/i })).toHaveValue(1);
  expect(screen.getByTestId('date-from')).toHaveValue('');
  expect(screen.getByTestId('date-to')).toHaveValue('');
});

test('vote mode drops a stale date range but keeps travelers', () => {
  renderVoteModal(singleDestCatalogState, {
    ...baseTripState,
    tripTravelers: 4,
    tripStartDate: '2020-01-01',
    tripEndDate: '2020-01-07',
  });

  expect(screen.getByRole('spinbutton', { name: /number of travelers/i })).toHaveValue(4);
  expect(screen.getByTestId('date-from')).toHaveValue('');
  expect(screen.getByTestId('date-to')).toHaveValue('');
});

test('vote mode with prefilled setup is ready to continue without an email', async () => {
  const expectedStart = isoDaysFromNow(30);
  const expectedEnd = isoDaysFromNow(37);
  const onVoteConfirm = jest.fn();
  renderVoteModal(
    singleDestCatalogState,
    {
      ...baseTripState,
      tripTravelers: 4,
      tripStartDate: expectedStart,
      tripEndDate: expectedEnd,
    },
    { onVoteConfirm }
  );

  await userEvent.click(screen.getByRole('button', { name: /^continue$/i }));

  expect(onVoteConfirm).toHaveBeenCalledWith(expect.objectContaining({
    travelers: 4,
    startDate: expectedStart,
    endDate: expectedEnd,
  }));
});

test('confirm is enabled once dates are set (no email needed) and payload has no email', () => {
  const onVoteConfirm = jest.fn();
  renderVoteModal(singleDestCatalogState, baseTripState, { onVoteConfirm });
  pickDates('2026-09-04', '2026-09-06');
  fireEvent.click(screen.getByRole('button', { name: /^continue$/i }));
  expect(onVoteConfirm).toHaveBeenCalledWith(expect.not.objectContaining({ email: expect.anything() }));
});

test('trip/direct mode prefills from trip state and confirms with those values', async () => {
  const expectedStart = isoDaysFromNow(30);
  const expectedEnd = isoDaysFromNow(37);
  const { mockDispatch } = renderTripModal(singleDestCatalogState, {
    tripTravelers: 4,
    tripStartDate: expectedStart,
    tripEndDate: expectedEnd,
  });

  expect(screen.getByRole('spinbutton', { name: /number of travelers/i })).toHaveValue(4);
  expect(screen.getByTestId('date-from')).toHaveValue(expectedStart);
  expect(screen.getByTestId('date-to')).toHaveValue(expectedEnd);

  await userEvent.click(screen.getByRole('button', { name: /confirm/i }));

  expect(mockDispatch).toHaveBeenCalledWith({
    type: 'SET_TRIP_SETUP',
    travelers: 4,
    startDate: expectedStart,
    endDate: expectedEnd,
  });
});

// ---------------------------------------------------------------------------
// A8 — tb_start
// ---------------------------------------------------------------------------

test('A8: tb_start fires once when the modal opens in vote mode', () => {
  renderVoteModal();

  expect(pushEvent).toHaveBeenCalledTimes(1);
  expect(pushEvent).toHaveBeenCalledWith('tb_start', { ref: null, vote_mode: 'VOTE' });
});

// ТЗ §8 triggers tb_start on opening the Trip Builder's first screen, not on
// arriving through a vote link. Direct entry used to be silent, which cut every
// organizer who never came through an invite out of the funnel's first step.
test('A8: tb_start fires for direct entry too, tagged as the trip path', () => {
  renderTripModal();

  expect(pushEvent).toHaveBeenCalledTimes(1);
  expect(pushEvent).toHaveBeenCalledWith('tb_start', { ref: null, vote_mode: 'TRIP' });
});

function voteModalTree(voteOpen, mockDispatch) {
  return (
    <CatalogContext.Provider value={{ state: baseCatalogState, dispatch: jest.fn() }}>
      <TripContext.Provider value={{ state: baseTripState, dispatch: mockDispatch }}>
        <TripSetupModal
          isVoteMode
          voteOpen={voteOpen}
          onVoteConfirm={jest.fn()}
          onVoteCancel={jest.fn()}
        />
      </TripContext.Provider>
    </CatalogContext.Provider>
  );
}

test('A8: tb_start fires only once even after a re-render', () => {
  const { rerender, mockDispatch } = renderVoteModal();

  // Force a re-render by supplying the same props again
  rerender(voteModalTree(true, mockDispatch));

  const tbStartCalls = pushEvent.mock.calls.filter(([event]) => event === 'tb_start');
  expect(tbStartCalls).toHaveLength(1);
});

test('A8: tb_start fires again after the modal is closed and reopened', () => {
  const mockDispatch = jest.fn();
  const { rerender } = render(voteModalTree(true, mockDispatch));

  expect(pushEvent.mock.calls.filter(([event]) => event === 'tb_start')).toHaveLength(1);

  // Close, then reopen — the once-per-open ref should reset and fire again.
  rerender(voteModalTree(false, mockDispatch));
  rerender(voteModalTree(true, mockDispatch));

  const tbStartCalls = pushEvent.mock.calls.filter(([event]) => event === 'tb_start');
  expect(tbStartCalls).toHaveLength(2);
});

// ---------------------------------------------------------------------------
// A9 — tb_group_submitted — common: invalid submit does NOT fire
// ---------------------------------------------------------------------------

test('A9: tb_group_submitted does NOT fire when vote form is invalid (missing dates)', async () => {
  // renderVoteModal with multiple destinations → voteFormValid is false (no destination selected, no dates)
  renderVoteModal();

  const submitBtn = screen.getByRole('button', { name: /^continue$/i });
  await userEvent.click(submitBtn);

  const submittedCalls = pushEvent.mock.calls.filter(([event]) => event === 'tb_group_submitted');
  expect(submittedCalls).toHaveLength(0);
});

// ---------------------------------------------------------------------------
// A9 — tb_group_submitted — vote mode, valid submit
// ---------------------------------------------------------------------------

async function fillVoteFormAndSubmit() {
  // Fill dates
  await userEvent.type(screen.getByTestId('date-from'), '2026-08-01');
  await userEvent.type(screen.getByTestId('date-to'), '2026-08-07');
  // Submit
  const submitBtn = screen.getByRole('button', { name: /^continue$/i });
  await userEvent.click(submitBtn);
}

test('A9: vote mode valid submit fires tb_group_submitted with destination/group_size/has_budget, no trip_id', async () => {
  // Single destination → auto-selected (voteFormValid becomes true once dates filled)
  renderVoteModal(singleDestCatalogState);

  await fillVoteFormAndSubmit();

  const submittedCalls = pushEvent.mock.calls.filter(([event]) => event === 'tb_group_submitted');
  expect(submittedCalls).toHaveLength(1);

  const [, params] = submittedCalls[0];
  expect(params.destination).toBe('prague');
  expect(params.group_size).toBe(1);
  expect(params.has_budget).toBe(false);
  expect(params.trip_id).toBeUndefined();
});

test('A9: vote mode never includes email, regardless of ad_storage consent', async () => {
  hasConsent.mockReturnValue(true);
  renderVoteModal(singleDestCatalogState);

  await fillVoteFormAndSubmit();

  const submittedCalls = pushEvent.mock.calls.filter(([event]) => event === 'tb_group_submitted');
  expect(submittedCalls).toHaveLength(1);
  expect(submittedCalls[0][1].email).toBeUndefined();
});

// ---------------------------------------------------------------------------
// Budget question removed from the vote setup
// ---------------------------------------------------------------------------

test('vote setup has no budget field', () => {
  renderVoteModal(singleDestCatalogState);
  expect(screen.queryByLabelText(/budget/i)).not.toBeInTheDocument();
});

test('confirm sends budget: null in the vote payload', async () => {
  const onVoteConfirm = jest.fn();
  renderVoteModal(singleDestCatalogState, baseTripState, { onVoteConfirm });

  await fillVoteFormAndSubmit();

  expect(onVoteConfirm).toHaveBeenCalledWith(expect.objectContaining({ budget: null }));
});

// ---------------------------------------------------------------------------
// Traveler count — stepper (− / editable number / +)
// ---------------------------------------------------------------------------

test('travelers stepper: plus/minus adjust the count and minus disables at 1', async () => {
  renderVoteModal(singleDestCatalogState, { ...baseTripState, tripTravelers: 3 });
  const num = screen.getByRole('spinbutton', { name: /number of travelers/i });
  expect(num).toHaveValue(3); // seeded from tripTravelers

  await userEvent.click(screen.getByRole('button', { name: /increase travelers/i }));
  expect(num).toHaveValue(4);

  const minus = screen.getByRole('button', { name: /decrease travelers/i });
  await userEvent.click(minus);
  await userEvent.click(minus);
  await userEvent.click(minus);
  expect(num).toHaveValue(1);
  expect(minus).toBeDisabled(); // can't go below one traveler
});

test('typing a large group size is kept, not clamped', async () => {
  const expectedStart = isoDaysFromNow(30);
  const expectedEnd = isoDaysFromNow(37);
  const onVoteConfirm = jest.fn();
  renderVoteModal(
    singleDestCatalogState,
    { ...baseTripState, tripStartDate: expectedStart, tripEndDate: expectedEnd },
    { onVoteConfirm }
  );

  const num = screen.getByRole('spinbutton', { name: /number of travelers/i });
  await userEvent.clear(num);
  await userEvent.type(num, '35');
  fireEvent.blur(num);
  expect(num).toHaveValue(35);

  await userEvent.click(screen.getByRole('button', { name: /^continue$/i }));

  expect(onVoteConfirm).toHaveBeenCalledWith(expect.objectContaining({ travelers: 35 }));
});

// ---------------------------------------------------------------------------
// A9 — tb_group_submitted — trip/direct mode
// ---------------------------------------------------------------------------

test('A9: trip/direct mode fires tb_group_submitted with trip_id and dispatches SET_TRIP_ID', async () => {
  const { mockDispatch } = renderTripModal();

  // In trip/direct mode there is no validation guard — submit always proceeds
  const submitBtn = screen.getByRole('button', { name: /confirm/i });
  await userEvent.click(submitBtn);

  const submittedCalls = pushEvent.mock.calls.filter(([event]) => event === 'tb_group_submitted');
  expect(submittedCalls).toHaveLength(1);

  const [, params] = submittedCalls[0];
  expect(params.trip_id).toBe('test-uuid-1234');
  expect(params.email).toBeUndefined();

  // Verify dispatch was called with SET_TRIP_ID
  expect(mockDispatch).toHaveBeenCalledWith({ type: 'SET_TRIP_ID', tripId: 'test-uuid-1234' });
});

test('A9: trip/direct mode destination param is the default destination slug', async () => {
  renderTripModal();

  const submitBtn = screen.getByRole('button', { name: /confirm/i });
  await userEvent.click(submitBtn);

  const submittedCalls = pushEvent.mock.calls.filter(([event]) => event === 'tb_group_submitted');
  expect(submittedCalls).toHaveLength(1);
  expect(submittedCalls[0][1].destination).toBe('prague');
});

// ---------------------------------------------------------------------------
// Ghost button style for Cancel
// ---------------------------------------------------------------------------

test('Cancel uses the neutral ghost style, not the teal secondary', () => {
  renderVoteModal();
  const cancel = screen.getByRole('button', {name: /cancel/i});
  expect(cancel).toHaveClass('btn--ghost');
  expect(cancel).not.toHaveClass('btn--secondary');
});

// ---------------------------------------------------------------------------
// Draft persistence + modal_abandoned on cancel
// ---------------------------------------------------------------------------

test('closing without submit saves a draft, fires modal_abandoned, and reopen re-seeds it', () => {
  const { rerender } = renderVoteModal();
  setTravelers('6');
  fireEvent.click(screen.getByRole('button', { name: /cancel/i }));

  expect(pushEvent).toHaveBeenCalledWith('modal_abandoned', expect.objectContaining({
    modal: 'trip_setup', vote_mode: 'VOTE', has_travelers: true,
  }));

  reopenVoteModal(rerender);
  expect(screen.getByLabelText(/number of travelers/i)).toHaveValue(6);
});

test('modal_abandoned reflects has_dates and has_travelers correctly', () => {
  renderVoteModal();
  pickDates('2026-09-04', '2026-09-06');
  fireEvent.click(screen.getByRole('button', { name: /cancel/i }));

  expect(pushEvent).toHaveBeenCalledWith('modal_abandoned', {
    modal: 'trip_setup', vote_mode: 'VOTE', has_travelers: false, has_dates: true,
  });
});

// Dismissing only closes the dialog: it must not throw away what the user has
// already put in the cart. CANCEL_TRIP_SETUP still exists and still empties the
// trip, but it belongs to the deliberate clears (lead submit, payment success),
// not to a dismissed modal.
test('trip/direct mode cancel fires modal_abandoned with vote_mode false and closes without emptying the cart', () => {
  const { mockDispatch } = renderTripModal();
  fireEvent.click(screen.getByRole('button', { name: /cancel/i }));

  expect(pushEvent).toHaveBeenCalledWith('modal_abandoned', expect.objectContaining({
    modal: 'trip_setup', vote_mode: 'TRIP',
  }));
  expect(mockDispatch).toHaveBeenCalledWith({ type: 'CLOSE_TRIP_SETUP_MODAL' });
  expect(mockDispatch).not.toHaveBeenCalledWith({ type: 'CANCEL_TRIP_SETUP' });
});

// ---------------------------------------------------------------------------
// Backdrop click closes the modal
// ---------------------------------------------------------------------------

test('backdrop click closes the modal', () => {
  const onVoteCancel = jest.fn();
  renderVoteModal(baseCatalogState, baseTripState, { onVoteCancel });
  fireEvent.click(screen.getByRole('dialog')); // overlay div
  expect(onVoteCancel).toHaveBeenCalled();
});
