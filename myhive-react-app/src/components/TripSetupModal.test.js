import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import TripSetupModal from './TripSetupModal';
import {CatalogContext} from '../context/CatalogContext';
import {TripContext} from '../context/TripContext';

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

const baseTripState = {
  tripSetupModalOpen: false,
};

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

function renderTripModal(catalogState = singleDestCatalogState) {
  const mockDispatch = jest.fn();
  const tripState = { tripSetupModalOpen: true };
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

// ---------------------------------------------------------------------------
// Pre-existing destination picker tests
// ---------------------------------------------------------------------------

test('shows the destination picker when enabled and several destinations exist', () => {
  renderVoteModal();

  expect(screen.getByLabelText('Destination *')).toBeInTheDocument();
  expect(screen.getByRole('option', { name: 'Budapest' })).toBeInTheDocument();
});

test('auto-selects the only destination even with the picker enabled', () => {
  renderVoteModal({
    ...baseCatalogState,
    destinations: [{ id: 'd1', slug: 'prague', name: 'Prague' }],
  });

  expect(screen.getByText('Prague')).toBeInTheDocument();
  expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
});

// ---------------------------------------------------------------------------
// A8 — tb_start
// ---------------------------------------------------------------------------

test('A8: tb_start fires once when the modal opens in vote mode', () => {
  renderVoteModal();

  expect(pushEvent).toHaveBeenCalledTimes(1);
  expect(pushEvent).toHaveBeenCalledWith('tb_start', { ref: null });
});

test('A8: tb_start does NOT fire when the modal is open in trip/direct mode', () => {
  renderTripModal();

  expect(pushEvent).not.toHaveBeenCalledWith('tb_start', expect.anything());
  // Trip-mode open should emit no analytics at all (tb_group_submitted only
  // fires on confirm), so guard against any unexpected event on open.
  expect(pushEvent).not.toHaveBeenCalled();
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

test('A9: tb_group_submitted does NOT fire when vote form is invalid (missing dates/email)', async () => {
  // renderVoteModal with multiple destinations → voteFormValid is false (no destination selected, no dates, no email)
  renderVoteModal();

  const submitBtn = screen.getByRole('button', { name: /continue to categories/i });
  await userEvent.click(submitBtn);

  const submittedCalls = pushEvent.mock.calls.filter(([event]) => event === 'tb_group_submitted');
  expect(submittedCalls).toHaveLength(0);
});

test('A9: tb_group_submitted does NOT fire when budget is invalid (negative)', async () => {
  // All required fields valid, but a negative budget hits the second early return.
  renderVoteModal(singleDestCatalogState);

  await userEvent.type(screen.getByTestId('date-from'), '2026-08-01');
  await userEvent.type(screen.getByTestId('date-to'), '2026-08-07');
  await userEvent.clear(screen.getByLabelText(/your email/i));
  await userEvent.type(screen.getByLabelText(/your email/i), 'test@example.com');
  await userEvent.type(screen.getByLabelText(/group budget/i), '-100');

  const submitBtn = screen.getByRole('button', { name: /continue to categories/i });
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
  // Fill email
  await userEvent.clear(screen.getByLabelText(/your email/i));
  await userEvent.type(screen.getByLabelText(/your email/i), 'test@example.com');
  // Submit
  const submitBtn = screen.getByRole('button', { name: /continue to categories/i });
  await userEvent.click(submitBtn);
}

test('A9: vote mode valid submit fires tb_group_submitted with destination/group_size/has_budget, no trip_id', async () => {
  // Single destination → auto-selected (voteFormValid becomes true once dates+email filled)
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

test('A9: vote mode includes email when hasConsent("ad_storage") is true', async () => {
  hasConsent.mockReturnValue(true);
  renderVoteModal(singleDestCatalogState);

  await fillVoteFormAndSubmit();

  const submittedCalls = pushEvent.mock.calls.filter(([event]) => event === 'tb_group_submitted');
  expect(submittedCalls).toHaveLength(1);
  expect(submittedCalls[0][1].email).toBe('test@example.com');
  expect(submittedCalls[0][1].trip_id).toBeUndefined();
});

test('A9: vote mode OMITS email when hasConsent("ad_storage") is false', async () => {
  hasConsent.mockReturnValue(false);
  renderVoteModal(singleDestCatalogState);

  await fillVoteFormAndSubmit();

  const submittedCalls = pushEvent.mock.calls.filter(([event]) => event === 'tb_group_submitted');
  expect(submittedCalls).toHaveLength(1);
  expect(submittedCalls[0][1].email).toBeUndefined();
});

test('A9: vote mode sets has_budget true when budget is entered', async () => {
  hasConsent.mockReturnValue(false);
  renderVoteModal(singleDestCatalogState);

  await userEvent.type(screen.getByTestId('date-from'), '2026-08-01');
  await userEvent.type(screen.getByTestId('date-to'), '2026-08-07');
  await userEvent.clear(screen.getByLabelText(/your email/i));
  await userEvent.type(screen.getByLabelText(/your email/i), 'test@example.com');
  await userEvent.type(screen.getByLabelText(/group budget/i), '3000');

  const submitBtn = screen.getByRole('button', { name: /continue to categories/i });
  await userEvent.click(submitBtn);

  const submittedCalls = pushEvent.mock.calls.filter(([event]) => event === 'tb_group_submitted');
  expect(submittedCalls).toHaveLength(1);
  expect(submittedCalls[0][1].has_budget).toBe(true);
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
