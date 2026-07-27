# Lead-Capture Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove friction from the first trip-setup form, move email capture to the moment of value (vote-link send / booking checkout), restructure the homepage for above-the-fold merchandising, and add conversion add-ons (WhatsApp widget, sticky mobile CTA, endowed quiz progress, abandonment analytics) — to increase captured emails.

**Architecture:** All work is in `myhive-react-app` (CRA + React Router + Context; tests are Jest + React Testing Library colocated as `*.test.js`). The shared `TripSetupModal` shrinks to travelers + dates (calendar becomes a popover inside `DateRangePicker`); email moves to `StartGroupVoteModal` (which gains a QUIZ mode replacing TripBuilder's one-click quiz vote) and the booking `ContactForm`. A new `useEmailLeadCapture` hook debounces `leadApi.createLead` on valid email input at both capture points.

**Tech Stack:** React 18 (CRA), react-day-picker, Jest/RTL, plain CSS files per component.

**Spec:** `docs/superpowers/specs/2026-07-27-lead-capture-improvements-design.md`

## Global Constraints

- Working directory for all commands: `myhive-react-app/` (run `npm test -- --watchAll=false <pattern>`).
- Lead capture is fire-and-forget: a failed `leadApi` call must never block or error any user flow.
- Analytics params never carry raw email into new events; `modal_abandoned` sends booleans only.
- Hero title copy (exact): `The Easiest Prague Stag Do. All Sorted For You.`
- Email microcopy (exact): `We'll send you the live vote results and your saved shortlist.` (vote modal) and keep `EmailConsentNote` wherever an email is captured.
- No new dependencies.
- Existing behaviour preserved unless a task explicitly changes it (e.g. destination auto-select logic in `TripSetupModal` is untouched).
- Commit after every task (each task ends with a commit step). Branch: current (`fixes-main-flow`).

---

### Task 1: `btn--ghost` button style + neutral Cancel in `TripSetupModal`

**Files:**
- Modify: `src/styles/global.css` (after the `.btn--accent:hover` rule, ~line 179)
- Modify: `src/components/TripSetupModal.js:135`
- Test: `src/components/TripSetupModal.test.js`

**Interfaces:**
- Produces: global CSS class `btn--ghost` (transparent background, muted text) used by later tasks for modal Cancel buttons.

- [ ] **Step 1: Write the failing test**

Add to `src/components/TripSetupModal.test.js` (follow the file's existing render helpers — it already renders the modal in vote mode via providers; reuse the existing `renderModal`-style helper found in that file):

```js
test('Cancel uses the neutral ghost style, not the teal secondary', () => {
    renderVoteModal(); // existing helper in this file that renders isVoteMode + voteOpen
    const cancel = screen.getByRole('button', {name: /cancel/i});
    expect(cancel).toHaveClass('btn--ghost');
    expect(cancel).not.toHaveClass('btn--secondary');
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm test -- --watchAll=false TripSetupModal`
Expected: FAIL — Cancel has class `btn--secondary`.

- [ ] **Step 3: Implement**

In `src/styles/global.css`, after `.btn--accent:hover`:

```css
.btn--ghost {
    background: transparent;
    color: var(--text-muted, #6c757d);
    border: 1px solid var(--border, rgba(255, 255, 255, 0.18));
}

.btn--ghost:hover {
    color: var(--text, inherit);
    border-color: var(--text-muted, #6c757d);
}
```

(Check the top of `global.css` for the actual custom-property names — use the file's existing variables for muted text/border; the fallbacks above are only a safety net.)

In `src/components/TripSetupModal.js` line 135:

```jsx
<button className="btn btn--ghost" onClick={handleCancel}>Cancel</button>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm test -- --watchAll=false TripSetupModal`
Expected: PASS (the pre-existing suite must stay green too).

- [ ] **Step 5: Commit**

```bash
git add src/styles/global.css src/components/TripSetupModal.js src/components/TripSetupModal.test.js
git commit -m "feat(ui): ghost style for modal Cancel buttons"
```

---

### Task 2: `DateRangePicker` popover mode (small calendar popup)

**Files:**
- Modify: `src/components/DateRangePicker.js`
- Modify: `src/components/DateRangePicker.css`
- Test: `src/components/DateRangePicker.test.js`

**Interfaces:**
- Consumes: nothing new.
- Produces: `<DateRangePicker from to onChange popover />` — new boolean prop `popover` (default `false`). In popover mode: the Start/End fields render as today, but the calendar (a) is hidden until a field is clicked, (b) renders one month only inside an absolutely-positioned dropdown (`.drp-pop`), (c) closes when the range completes or on click outside. Existing `collapsible` mode and default mode are unchanged.

- [ ] **Step 1: Write the failing tests**

Add to `src/components/DateRangePicker.test.js`:

```js
describe('popover mode', () => {
    test('calendar is hidden until a field is clicked', () => {
        render(<DateRangePicker from="" to="" onChange={jest.fn()} popover />);
        expect(document.querySelector('.drp-cal-wrap')).toBeNull();
        fireEvent.click(screen.getByText('Add date', {selector: '.drp-field-value--empty'}) ||
            document.querySelector('.drp-field'));
        expect(document.querySelector('.drp-pop .drp-cal-wrap')).toBeInTheDocument();
    });

    test('click outside closes the popover', () => {
        render(<div><span data-testid="outside">out</span>
            <DateRangePicker from="" to="" onChange={jest.fn()} popover /></div>);
        fireEvent.click(document.querySelector('.drp-field'));
        expect(document.querySelector('.drp-pop')).toBeInTheDocument();
        fireEvent.mouseDown(screen.getByTestId('outside'));
        expect(document.querySelector('.drp-pop')).toBeNull();
    });
});
```

(Adapt the field-click selector to whatever the first test renders — clicking the first `.drp-field` div is sufficient.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `npm test -- --watchAll=false DateRangePicker`
Expected: FAIL — in default mode the calendar is always visible and `.drp-pop` does not exist.

- [ ] **Step 3: Implement**

In `src/components/DateRangePicker.js`:

```js
function DateRangePicker({ from, to, onChange, collapsible = false, popover = false }) {
  const [numMonths, setNumMonths] = useState(() => window.innerWidth >= 640 ? 2 : 1);
  const [popOpen, setPopOpen] = useState(false);
  const rootRef = useRef(null);
  // ...existing state...
```

Close on outside click (new effect):

```js
  useEffect(() => {
    if (!popover || !popOpen) return undefined;
    const onDocMouseDown = (e) => {
      if (rootRef.current && !rootRef.current.contains(e.target)) {
        setPopOpen(false);
      }
    };
    document.addEventListener('mousedown', onDocMouseDown);
    return () => document.removeEventListener('mousedown', onDocMouseDown);
  }, [popover, popOpen]);
```

Field click and select handlers:

```js
  const handleFieldClick = () => {
    if (popover) { setPopOpen(true); return; }
    if (collapsible) { setReopened(true); }
  };

  const handleSelect = (range) => {
    const newFrom = toISO(range?.from);
    const newTo = toISO(range?.to);
    if (newFrom && newTo && newFrom === newTo) {
      onChange(newFrom, '');
      return;
    }
    if (newFrom && newTo) {
      setReopened(false);
      setPopOpen(false); // popover: range complete — close the popup
    }
    onChange(newFrom, newTo);
  };
```

Visibility + wrapper (replace the current `calendarVisible` expression and calendar JSX):

```jsx
  const calendarVisible = popover
    ? popOpen
    : (!collapsible || !(from && to) || reopened);

  // root div gets the ref and a modifier class:
  <div className={`drp${popover ? ' drp--popover' : ''}`} ref={rootRef}>
    ...
    {calendarVisible && (
      <div className={popover ? 'drp-pop' : undefined}>
        <div className="drp-cal-wrap" ref={calWrapRef}>
          <DayPicker
            mode="range"
            numberOfMonths={popover ? 1 : numMonths}
            ...unchanged props...
          />
        </div>
      </div>
    )}
```

In `src/components/DateRangePicker.css` append:

```css
/* Popover mode: small anchored calendar instead of the inline block */
.drp--popover { position: relative; }
.drp-pop {
    position: absolute;
    top: calc(100% + 6px);
    left: 0;
    z-index: 30;
    background: var(--surface, #1c1c24);
    border: 1px solid var(--border, rgba(255, 255, 255, 0.12));
    border-radius: 12px;
    box-shadow: 0 12px 32px rgba(0, 0, 0, 0.45);
    padding: 8px;
}
```

(Match the surface/border variables actually used in `DateRangePicker.css`.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `npm test -- --watchAll=false DateRangePicker`
Expected: PASS, including the pre-existing suite.

- [ ] **Step 5: Commit**

```bash
git add src/components/DateRangePicker.js src/components/DateRangePicker.css src/components/DateRangePicker.test.js
git commit -m "feat(ui): popover mode for DateRangePicker"
```

---

### Task 3: Shrink `TripSetupModal` — no email, popover dates, backdrop close, draft persistence, `modal_abandoned`

**Files:**
- Modify: `src/components/TripSetupModal.js`
- Create: `src/utils/setupDraft.js`
- Test: `src/utils/setupDraft.test.js`, `src/components/TripSetupModal.test.js`

**Interfaces:**
- Consumes: `DateRangePicker` `popover` prop (Task 2), `btn--ghost` (Task 1).
- Produces:
  - `onVoteConfirm({travelers, startDate, endDate, destination, budget})` — **no `email` key anymore** (Task 4/5 consume this).
  - `src/utils/setupDraft.js`: `readSetupDraft() -> {travelers, startDate, endDate} | null`, `writeSetupDraft(draft)`, `clearSetupDraft()` — localStorage key `myhive-setup-draft`, same try/catch style as `utils/tripLead.js`.
  - Analytics event `modal_abandoned` `{modal: 'trip_setup', vote_mode: <bool>, has_travelers: <bool>, has_dates: <bool>}`.

- [ ] **Step 1: Write the failing tests for `setupDraft`**

`src/utils/setupDraft.test.js` (mirror the structure of `src/utils/tripLead.test.js`):

```js
import {readSetupDraft, writeSetupDraft, clearSetupDraft} from './setupDraft';

afterEach(() => localStorage.clear());

test('round-trips a draft', () => {
    writeSetupDraft({travelers: 8, startDate: '2026-09-04', endDate: '2026-09-06'});
    expect(readSetupDraft()).toEqual({travelers: 8, startDate: '2026-09-04', endDate: '2026-09-06'});
});

test('returns null when empty or malformed', () => {
    expect(readSetupDraft()).toBeNull();
    localStorage.setItem('myhive-setup-draft', '{not json');
    expect(readSetupDraft()).toBeNull();
});

test('clearSetupDraft removes the draft', () => {
    writeSetupDraft({travelers: 2, startDate: '', endDate: ''});
    clearSetupDraft();
    expect(readSetupDraft()).toBeNull();
});
```

- [ ] **Step 2: Run, verify FAIL, implement `setupDraft.js`**

Run: `npm test -- --watchAll=false setupDraft` → FAIL (module missing). Then create `src/utils/setupDraft.js`:

```js
// Unsubmitted trip-setup values (travelers/dates). Survives close/reopen so a
// user who dismisses the modal never re-types what they already entered.
const SETUP_DRAFT_KEY = 'myhive-setup-draft';

export function readSetupDraft() {
  try {
    const raw = localStorage.getItem(SETUP_DRAFT_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch (e) {
    return null;
  }
}

export function writeSetupDraft(draft) {
  try {
    localStorage.setItem(SETUP_DRAFT_KEY, JSON.stringify(draft));
  } catch (e) {
    // Blocked storage must never break the modal.
  }
}

export function clearSetupDraft() {
  try {
    localStorage.removeItem(SETUP_DRAFT_KEY);
  } catch (e) {
    // Same rationale as writeSetupDraft.
  }
}
```

Run again → PASS.

- [ ] **Step 3: Write the failing `TripSetupModal` tests**

In `src/components/TripSetupModal.test.js` (reuse the file's existing providers/helpers; the existing "consent notice" test pinning `EmailConsentNote` in this modal must be **updated to assert absence**):

```js
test('vote mode collects no email', () => {
    renderVoteModal();
    expect(screen.queryByLabelText(/email/i)).toBeNull();
    expect(screen.queryByText(/reminders\. unsubscribe anytime/i)).toBeNull();
});

test('confirm is enabled once dates are set (no email needed) and payload has no email', () => {
    const onVoteConfirm = jest.fn();
    renderVoteModal({onVoteConfirm});
    pickDates('2026-09-04', '2026-09-06'); // use the file's existing date-picking helper
    fireEvent.click(screen.getByRole('button', {name: /continue to categories/i}));
    expect(onVoteConfirm).toHaveBeenCalledWith(expect.not.objectContaining({email: expect.anything()}));
});

test('closing without submit saves a draft, fires modal_abandoned, and reopen re-seeds it', () => {
    window.dataLayer = [];
    const {rerender} = renderVoteModal();
    setTravelers('6'); // existing stepper/input helper
    fireEvent.click(screen.getByRole('button', {name: /cancel/i}));
    expect(window.dataLayer).toContainEqual(expect.objectContaining({
        event: 'modal_abandoned', modal: 'trip_setup', has_travelers: true,
    }));
    reopenVoteModal(rerender);
    expect(screen.getByLabelText(/number of travelers/i)).toHaveValue(6);
});

test('backdrop click closes the modal', () => {
    const onVoteCancel = jest.fn();
    renderVoteModal({onVoteCancel});
    fireEvent.click(document.querySelector('.app-modal')); // overlay div
    expect(onVoteCancel).toHaveBeenCalled();
});
```

- [ ] **Step 4: Run to verify FAIL**

Run: `npm test -- --watchAll=false TripSetupModal`
Expected: FAIL on all four (email field exists; closeOnBackdrop is false; no draft; no event).

- [ ] **Step 5: Implement the modal changes**

In `src/components/TripSetupModal.js`:

1. Drop imports `EmailConsentNote`, `hasConsent`; add:
   ```js
   import {clearSetupDraft, readSetupDraft, writeSetupDraft} from '../utils/setupDraft';
   ```
2. Delete the `email` state. Seed from draft first, trip state second (replace the body of the `isOpen` effect):
   ```js
   const draft = readSetupDraft();
   const draftDatesCurrent = Boolean(draft?.startDate) && draft.startDate >= todayLocalIso();
   const datesAreCurrent = Boolean(state.tripStartDate) && state.tripStartDate >= todayLocalIso();
   setTravelers(String(draft?.travelers || state.tripTravelers || 1));
   setStartDate(draftDatesCurrent ? draft.startDate : (datesAreCurrent ? state.tripStartDate : ''));
   setEndDate(draftDatesCurrent ? draft.endDate : (datesAreCurrent ? state.tripEndDate : ''));
   setSelectedDestinationId('');
   ```
3. `handleCancel` saves the draft and fires the abandonment event before closing:
   ```js
   const handleCancel = () => {
       writeSetupDraft({travelers: parseInt(travelers, 10) || 1, startDate, endDate});
       pushEvent('modal_abandoned', {
           modal: 'trip_setup',
           vote_mode: isVoteMode,
           has_travelers: (parseInt(travelers, 10) || 1) > 1,
           has_dates: Boolean(startDate && endDate),
       });
       if (isVoteMode) { onVoteCancel(); } else { dispatch({type: 'CANCEL_TRIP_SETUP'}); }
   };
   ```
4. `voteFormValid` loses email: `const voteFormValid = startDate && endDate && destination;`
5. `handleConfirm`: remove the email param from the event and payload, and clear the draft on success:
   ```js
   pushEvent('tb_group_submitted', {
       destination: destination ? destination.slug : undefined,
       group_size: travelersNum,
       has_budget: false,
   });
   clearSetupDraft();
   onVoteConfirm({ travelers: travelersNum, startDate, endDate, destination, budget: null });
   ```
   (Same `clearSetupDraft()` in the non-vote branch after `SET_TRIP_SETUP`.)
6. Delete the whole `{isVoteMode && (... voteEmail ...)}` block (lines 216-229).
7. `<AppModal ... closeOnBackdrop>` and route backdrop/× through `handleCancel` (it already is via `onClose={handleCancel}`).
8. `<DateRangePicker ... popover />`.
9. Update the intro copy (the modal is now genuinely quick):
   ```jsx
   <p className="trip-setup-description">
       Two quick details so we can price your weekend right.
   </p>
   ```

- [ ] **Step 6: Run the full modal suite**

Run: `npm test -- --watchAll=false TripSetupModal setupDraft`
Expected: PASS. Fix any pre-existing tests that asserted the email field / consent note in this modal (they should now assert absence — that's this task's intended behaviour change).

- [ ] **Step 7: Commit**

```bash
git add src/components/TripSetupModal.js src/components/TripSetupModal.test.js src/utils/setupDraft.js src/utils/setupDraft.test.js
git commit -m "feat(leads): small setup modal - dates+travelers only, draft persistence, backdrop close"
```

---

### Task 4: `useEmailLeadCapture` hook (debounced lead on valid email)

**Files:**
- Create: `src/hooks/useEmailLeadCapture.js`
- Test: `src/hooks/useEmailLeadCapture.test.js`

**Interfaces:**
- Consumes: `leadApi.createLead` (existing), `writeTripLead`/`readTripLead` (existing).
- Produces: `useEmailLeadCapture(context) -> (email: string) => void` where `context = {destinationId, numberOfTravelers, startDate, endDate, budget}`. Debounce 2000 ms (match `useTripLeadSync`'s `SYNC_DEBOUNCE_MS`); fires `leadApi.createLead` once per distinct valid email; stores the result via `writeTripLead`; all failures silent. Consumed by Tasks 5 and 6.

- [ ] **Step 1: Write the failing tests**

`src/hooks/useEmailLeadCapture.test.js` (model on `src/hooks/useTripLeadSync.test.js` — it already shows the fake-timers + mocked `leadApi` pattern used in this repo):

```js
import {renderHook, act} from '@testing-library/react';
import {useEmailLeadCapture} from './useEmailLeadCapture';
import leadApi from '../services/leadApi';
import {readTripLead} from '../utils/tripLead';

jest.mock('../services/leadApi');

const CTX = {destinationId: 'd1', numberOfTravelers: 8, startDate: '2026-09-04', endDate: '2026-09-06', budget: null};

beforeEach(() => {
    jest.useFakeTimers();
    localStorage.clear();
    leadApi.createLead.mockResolvedValue({id: 'lead-1', restoreToken: 'tok'});
});
afterEach(() => jest.useRealTimers());

test('creates a lead 2s after a valid email, and stores it', async () => {
    const {result} = renderHook(() => useEmailLeadCapture(CTX));
    act(() => result.current('sam@example.com'));
    act(() => jest.advanceTimersByTime(1999));
    expect(leadApi.createLead).not.toHaveBeenCalled();
    await act(async () => jest.advanceTimersByTime(1));
    expect(leadApi.createLead).toHaveBeenCalledWith({email: 'sam@example.com', ...CTX});
    expect(readTripLead()).toEqual({id: 'lead-1', restoreToken: 'tok'});
});

test('invalid email never fires; retyping resets the timer; same email not re-captured', async () => {
    const {result} = renderHook(() => useEmailLeadCapture(CTX));
    act(() => result.current('sam@'));
    await act(async () => jest.advanceTimersByTime(3000));
    expect(leadApi.createLead).not.toHaveBeenCalled();

    act(() => result.current('sam@example.com'));
    await act(async () => jest.advanceTimersByTime(2000));
    act(() => result.current('sam@example.com'));
    await act(async () => jest.advanceTimersByTime(2000));
    expect(leadApi.createLead).toHaveBeenCalledTimes(1);
});

test('createLead rejection is silent', async () => {
    leadApi.createLead.mockRejectedValue(new Error('boom'));
    const {result} = renderHook(() => useEmailLeadCapture(CTX));
    act(() => result.current('sam@example.com'));
    await act(async () => jest.advanceTimersByTime(2000));
    expect(readTripLead()).toBeNull(); // no lead stored, no throw
});
```

- [ ] **Step 2: Run to verify FAIL**

Run: `npm test -- --watchAll=false useEmailLeadCapture`
Expected: FAIL — module does not exist.

- [ ] **Step 3: Implement**

`src/hooks/useEmailLeadCapture.js`:

```js
import {useEffect, useRef} from 'react';
import leadApi from '../services/leadApi';
import {writeTripLead} from '../utils/tripLead';

const CAPTURE_DEBOUNCE_MS = 2000;
const EMAIL_RE = /\S+@\S+\.\S+/;

/**
 * Debounced lead capture at an email input: once a valid address sits
 * unchanged for 2s, create the lead so an abandoner still gets the reminder
 * flow. Fire-and-forget; each distinct address is captured at most once
 * (the server also dedups by email).
 */
export function useEmailLeadCapture(context) {
  const timerRef = useRef(null);
  const capturedRef = useRef(null);
  const contextRef = useRef(context);
  contextRef.current = context;

  useEffect(() => () => clearTimeout(timerRef.current), []);

  return (email) => {
    clearTimeout(timerRef.current);
    const trimmed = (email || '').trim();
    if (!EMAIL_RE.test(trimmed) || capturedRef.current === trimmed) {
      return;
    }
    timerRef.current = setTimeout(() => {
      capturedRef.current = trimmed;
      leadApi.createLead({email: trimmed, ...contextRef.current})
        .then(writeTripLead)
        .catch(() => {
          capturedRef.current = null; // allow a retry on the next keystroke
        });
    }, CAPTURE_DEBOUNCE_MS);
  };
}
```

- [ ] **Step 4: Run to verify PASS**

Run: `npm test -- --watchAll=false useEmailLeadCapture`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/hooks/useEmailLeadCapture.js src/hooks/useEmailLeadCapture.test.js
git commit -m "feat(leads): debounced email lead capture hook"
```

---

### Task 5: `StartGroupVoteModal` — QUIZ mode, lead capture, microcopy, abandonment, backdrop close

**Files:**
- Modify: `src/components/vote/StartGroupVoteModal.js`
- Test: `src/components/vote/StartGroupVoteModal.test.js`

**Interfaces:**
- Consumes: `useEmailLeadCapture` (Task 4), `voteApi.createSession` (existing — see `TripBuilder.js:582-592` for the exact payload), `voteApi.createCartSession` (existing).
- Produces: new optional props, all backward-compatible:
  - `voteMode: 'CART' | 'QUIZ'` (default `'CART'`)
  - `quizResponses: array` and `budget` (QUIZ payload)
  - `onLaunched: () => void` — called after a successful session creation, before navigation (TripBuilder uses it in Task 6 to clear quiz state).
  - In QUIZ mode the modal calls `voteApi.createSession({destinationId, initiatorEmail, numberOfTravelers, startDate, endDate, budget, voterToken: getOrCreateVoterToken(), quizResponses, activityIds})`, stores `myhive-initiator-…`/`myhive-manager-…` keys but **not** `myhive-trip-vote-session` (QUIZ parity — see the comment at `TripBuilder.js:556-559`), and navigates to `/vote/{shareToken}/waiting` with `{state: {managerToken: session.managerToken}}`.
  - Analytics `modal_abandoned` `{modal: 'start_vote', vote_mode: 'CART'|'QUIZ', has_email: <bool>}` on close-without-launch.

- [ ] **Step 1: Write the failing tests**

Add to `src/components/vote/StartGroupVoteModal.test.js` (reuse its existing router/mocking setup; `voteApi` is already mocked there):

```js
test('QUIZ mode creates a QUIZ session with quiz payload and calls onLaunched', async () => {
    voteApi.createSession.mockResolvedValue({shareToken: 'tok1', managerToken: 'mgr1'});
    const onLaunched = jest.fn();
    renderModal({
        voteMode: 'QUIZ', quizResponses: [{questionId: 'q1', answerId: 'a1'}], budget: null,
        destinationId: 'd1', activityIds: ['a', 'b'], numberOfTravelers: 8,
        startDate: '2026-09-04', endDate: '2026-09-06', onLaunched,
    });
    fireEvent.change(screen.getByLabelText(/your email/i), {target: {value: 'sam@example.com'}});
    fireEvent.click(screen.getByRole('button', {name: /create vote/i}));
    await waitFor(() => expect(voteApi.createSession).toHaveBeenCalledWith(
        expect.objectContaining({initiatorEmail: 'sam@example.com', quizResponses: [{questionId: 'q1', answerId: 'a1'}]})));
    expect(voteApi.createCartSession).not.toHaveBeenCalled();
    expect(onLaunched).toHaveBeenCalled();
    expect(localStorage.getItem('myhive-trip-vote-session')).toBeNull();
});

test('typing a valid email captures a lead after the debounce', async () => {
    jest.useFakeTimers();
    leadApi.createLead.mockResolvedValue({id: 'l1', restoreToken: 't1'});
    renderModal({destinationId: 'd1', activityIds: [], numberOfTravelers: 4,
        startDate: '2026-09-04', endDate: '2026-09-06'});
    fireEvent.change(screen.getByLabelText(/your email/i), {target: {value: 'sam@example.com'}});
    await act(async () => jest.advanceTimersByTime(2000));
    expect(leadApi.createLead).toHaveBeenCalledWith(expect.objectContaining({email: 'sam@example.com'}));
    jest.useRealTimers();
});

test('value-promise microcopy is shown', () => {
    renderModal({destinationId: 'd1', activityIds: [], numberOfTravelers: 4,
        startDate: '2026-09-04', endDate: '2026-09-06'});
    expect(screen.getByText(/live vote results and your saved shortlist/i)).toBeInTheDocument();
});

test('closing without launching fires modal_abandoned with has_email', () => {
    window.dataLayer = [];
    const onClose = jest.fn();
    renderModal({onClose, destinationId: 'd1', activityIds: [], numberOfTravelers: 4,
        startDate: '2026-09-04', endDate: '2026-09-06'});
    fireEvent.change(screen.getByLabelText(/your email/i), {target: {value: 'sam@example.com'}});
    fireEvent.click(screen.getByRole('button', {name: /close/i}));
    expect(window.dataLayer).toContainEqual(expect.objectContaining({
        event: 'modal_abandoned', modal: 'start_vote', has_email: true,
    }));
    expect(onClose).toHaveBeenCalled();
});
```

(`leadApi` needs adding to that file's jest mocks: `jest.mock('../../services/leadApi')`.)

- [ ] **Step 2: Run to verify FAIL**

Run: `npm test -- --watchAll=false StartGroupVoteModal`
Expected: FAIL — no QUIZ branch, no lead capture, old copy, no abandonment event.

- [ ] **Step 3: Implement**

In `src/components/vote/StartGroupVoteModal.js`:

```js
import { useEmailLeadCapture } from '../../hooks/useEmailLeadCapture';
import { getOrCreateVoterToken } from '../../utils/voterToken';
```

Props and capture wiring:

```js
function StartGroupVoteModal({
    isOpen, onClose, destinationId, activityIds, numberOfTravelers, startDate, endDate,
    voteMode = 'CART', quizResponses = null, budget = null, onLaunched,
}) {
    // ...existing state...
    const launchedRef = useRef(false);
    const captureEmail = useEmailLeadCapture({
        destinationId, numberOfTravelers,
        startDate: startDate || null, endDate: endDate || null, budget,
    });

    const handleEmailChange = (value) => {
        setEmail(value);
        captureEmail(value);
    };

    const handleClose = () => {
        if (!launchedRef.current) {
            pushEvent('modal_abandoned', {
                modal: 'start_vote', vote_mode: voteMode, has_email: EMAIL_RE.test(email.trim()),
            });
        }
        onClose();
    };
```

(`useRef` joins the react import.) The email input's `onChange` becomes `(e) => handleEmailChange(e.target.value)`; `AppModal` gets `onClose={handleClose}` and `closeOnBackdrop`.

`handleCreate` branches by mode (replace the single `createCartSession` call):

```js
        try {
            const resolvedStart = needsDates ? voteStartDate : startDate;
            const resolvedEnd = needsDates ? voteEndDate : endDate;
            const session = voteMode === 'QUIZ'
                ? await voteApi.createSession({
                    destinationId,
                    initiatorEmail: email.trim(),
                    numberOfTravelers,
                    startDate: resolvedStart,
                    endDate: resolvedEnd,
                    budget,
                    voterToken: getOrCreateVoterToken(),
                    quizResponses,
                    activityIds,
                })
                : await voteApi.createCartSession({
                    destinationId,
                    initiatorEmail: email.trim(),
                    numberOfTravelers,
                    startDate: resolvedStart,
                    endDate: resolvedEnd,
                    activityIds,
                });
            localStorage.setItem(`myhive-initiator-${session.shareToken}`, 'true');
            if (session.managerToken) {
                localStorage.setItem(`myhive-manager-${session.shareToken}`, session.managerToken);
            }
            if (voteMode === 'CART') {
                // QUIZ parity: quiz sessions intentionally do not set this key.
                localStorage.setItem('myhive-trip-vote-session', session.shareToken);
            }
            clearTripLead();
            pushEvent('vote_launched', {
                trip_id: session.shareToken,
                user_role: 'organizer',
                selected_count: activityIds.length,
            });
            launchedRef.current = true;
            if (onLaunched) onLaunched();
            navigate(`/vote/${session.shareToken}/waiting`,
                voteMode === 'QUIZ' ? { state: { managerToken: session.managerToken } } : undefined);
        } catch (e) { /* unchanged */ }
```

Microcopy (replace the `start-vote-modal-sub` paragraph):

```jsx
<p className="start-vote-modal-sub">
    We&apos;ll send you the live vote results and your saved shortlist.
    Voting closes automatically after 24 hours.
</p>
```

- [ ] **Step 4: Run to verify PASS**

Run: `npm test -- --watchAll=false StartGroupVoteModal`
Expected: PASS including the pre-existing CART tests (unchanged behaviour for CART except the abandonment event and copy — update any test pinning the old copy string).

- [ ] **Step 5: Commit**

```bash
git add src/components/vote/StartGroupVoteModal.js src/components/vote/StartGroupVoteModal.test.js
git commit -m "feat(leads): StartGroupVoteModal quiz mode, debounced lead capture, abandonment event"
```

---

### Task 6: Rewire the flows — setup without email, quiz vote via modal, checkout capture

**Files:**
- Modify: `src/hooks/useStartGroupVote.js`
- Modify: `src/components/TripBuilder.js` (delete `handleQuizVoteCreate` ~560-614; button wiring ~757; modal props ~951-959; contact form wiring)
- Modify: `src/components/ContactForm.js` (email field: consent note, microcopy, `onEmailChange` prop)
- Test: `src/hooks/useStartGroupVote.test.js`, `src/components/TripBuilder.test.js`, `src/components/ContactForm.test.js`

**Interfaces:**
- Consumes: Task 3's email-less `onVoteConfirm` payload; Task 5's `voteMode`/`onLaunched` props; Task 4's hook.
- Produces:
  - `useStartGroupVote.handleVoteConfirm({travelers, startDate, endDate, destination, budget})` — no email, no `leadApi` call; quiz `setup` navigate-state has no `email`.
  - `ContactForm` new optional props: `onEmailChange: (email) => void` and `showConsentNote: boolean` (default `false`).

- [ ] **Step 1: Write the failing tests**

`src/hooks/useStartGroupVote.test.js` — update the existing lead-capture expectations:

```js
test('confirm navigates to the quiz without creating a lead and without email in state', () => {
    const {result} = renderStartGroupVote(); // existing helper
    act(() => result.current.handleVoteConfirm({
        travelers: 8, startDate: '2026-09-04', endDate: '2026-09-06',
        destination: {id: 'd1', slug: 'prague'}, budget: null,
    }));
    expect(leadApi.createLead).not.toHaveBeenCalled();
    expect(mockNavigate).toHaveBeenCalledWith('/vote/new/quiz', {
        state: {setup: expect.not.objectContaining({email: expect.anything()})},
    });
});
```

`src/components/ContactForm.test.js`:

```js
test('reports email input via onEmailChange and shows consent note when asked', () => {
    const onEmailChange = jest.fn();
    render(<ContactForm isOpen inline tripData={{tripItems: []}}
                        onClose={jest.fn()} onSubmit={jest.fn()}
                        onEmailChange={onEmailChange} showConsentNote />);
    fireEvent.change(screen.getByLabelText(/email address/i), {target: {value: 'sam@example.com'}});
    expect(onEmailChange).toHaveBeenCalledWith('sam@example.com');
    expect(screen.getByText(/reminders\. unsubscribe anytime/i)).toBeInTheDocument();
});
```

`src/components/TripBuilder.test.js` — the quiz one-click path becomes modal-open (adapt to that file's existing quiz-mode setup helpers):

```js
test('quiz mode: "Let your mates vote" opens StartGroupVoteModal instead of creating a session directly', async () => {
    renderTripBuilderInQuizMode(); // existing helper/pattern in this file
    fireEvent.click(screen.getByRole('button', {name: /let your mates vote/i}));
    expect(await screen.findByText(/let your mates vote/i, {selector: '.app-modal-title, h2'})).toBeInTheDocument();
    expect(voteApi.createSession).not.toHaveBeenCalled();
});
```

- [ ] **Step 2: Run to verify FAIL**

Run: `npm test -- --watchAll=false useStartGroupVote ContactForm TripBuilder`
Expected: FAIL — lead still created in the hook; `ContactForm` has no such props; quiz mode creates the session inline.

- [ ] **Step 3: Implement `useStartGroupVote`**

Remove the `leadApi`/`writeTripLead` imports and the whole `leadApi.createLead(...)` block; the confirm handler becomes:

```js
    const handleVoteConfirm = ({travelers, startDate, endDate, destination, budget}) => {
        setVoteSetupOpen(false);
        dispatch({type: 'CLOSE_TRIP_BUILDER_MODAL'});
        navigate('/vote/new/quiz', {
            state: {
                setup: {travelers, startDate, endDate, destination, budget},
            },
        });
    };
```

- [ ] **Step 4: Implement `TripBuilder` rewiring**

1. Delete `handleQuizVoteCreate` entirely (lines ~560-614) and the now-unused `creatingVote`/`setCreatingVote` state + `getOrCreateVoterToken` import if unused elsewhere.
2. Line ~757: `onClick={handleStartVoteClick}` for both modes (the CART-vote-active guard in `handleStartVoteClick` already covers quiz mode — it opens the modal when no active CART vote exists).
3. Extend the modal render (~line 951):
   ```jsx
   <StartGroupVoteModal
       isOpen={showVoteModal}
       onClose={() => setShowVoteModal(false)}
       destinationId={destinationId}
       activityIds={standalone.map(item => item.id)}
       numberOfTravelers={travelers}
       startDate={state.tripStartDate}
       endDate={state.tripEndDate}
       voteMode={quizMode ? 'QUIZ' : 'CART'}
       quizResponses={quizMode ? quizFlow.responses : null}
       budget={quizMode ? state.tripBudget : null}
       onLaunched={quizMode ? () => { clearQuizFlow(); setQuizFlow(null); } : undefined}
   />
   ```
4. Checkout capture — where `ContactForm` is rendered in TripBuilder (search `<ContactForm`), add:
   ```js
   const captureCheckoutEmail = useEmailLeadCapture({
       destinationId,
       numberOfTravelers: travelers,
       startDate: state.tripStartDate || null,
       endDate: state.tripEndDate || null,
       budget: state.tripBudget,
   });
   ```
   (hook call at component top level, with the other hooks) and pass:
   ```jsx
   onEmailChange={captureCheckoutEmail}
   showConsentNote
   ```

- [ ] **Step 5: Implement `ContactForm` props**

```js
function ContactForm({isOpen, onClose, onSubmit, submitLabel = 'Submit Booking', inline = false,
                      tripData, initialValues, isSubmitting, submitError,
                      onEmailChange, showConsentNote = false}) {
```

In `handleInputChange`, after `setFormData`:

```js
        if (name === 'email' && onEmailChange) {
            onEmailChange(value);
        }
```

Under the email input (after its `error-message` span):

```jsx
        {showConsentNote && (
            <>
                <p className="email-value-note">
                    We&apos;ll save your trip to this address so you can pick it up anytime.
                </p>
                <EmailConsentNote />
            </>
        )}
```

with `import EmailConsentNote from './EmailConsentNote';` and in `ContactForm.css`:

```css
.email-value-note {
    font-size: 0.8rem;
    margin: 4px 0 0;
}
```

- [ ] **Step 6: Run the affected suites**

Run: `npm test -- --watchAll=false useStartGroupVote ContactForm TripBuilder StartGroupVoteModal`
Expected: PASS. Any TripBuilder tests pinning `quizFlow.setup.email` / one-click session creation must be updated to the modal path (that's the intended change).

- [ ] **Step 7: Commit**

```bash
git add src/hooks/useStartGroupVote.js src/hooks/useStartGroupVote.test.js src/components/TripBuilder.js src/components/TripBuilder.test.js src/components/ContactForm.js src/components/ContactForm.test.js
git commit -m "feat(leads): email capture at vote-link and checkout; quiz vote goes through the modal"
```

---

### Task 7: Homepage — section order + Prague title

**Files:**
- Modify: `src/pages/HomePage.js` (lines 22-24 Helmet, 33 title, 95-99 section order)
- Test: `src/pages/HomePage.test.js`

**Interfaces:**
- Consumes: nothing new. Produces: nothing consumed later (VoteDemoCard extraction is Task 8).

- [ ] **Step 1: Write the failing tests**

```js
test('hero title mentions Prague', () => {
    renderHomePage(); // existing helper
    expect(screen.getByRole('heading', {level: 1}))
        .toHaveTextContent('The Easiest Prague Stag Do. All Sorted For You.');
});

test('activities section comes directly after the hero', () => {
    renderHomePage();
    const sections = document.querySelectorAll('.homepage > section, .homepage > div > section');
    // Simpler and robust: assert DOM order of the two blocks
    const hero = document.querySelector('.hero');
    const activities = document.querySelector('.featured-activities') || document.querySelector('#activities');
    const trust = document.querySelector('.trust-bar'); // check TrustBar.css for the real root class
    expect(hero.compareDocumentPosition(activities) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(activities.compareDocumentPosition(trust) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
});
```

(FeaturedActivitiesSection returns `null` until activities load — the existing HomePage tests already mock `api.getFeaturedActivities`; reuse that. If not, mock it to resolve one activity.)

- [ ] **Step 2: Run to verify FAIL**

Run: `npm test -- --watchAll=false HomePage`
Expected: FAIL — old title, TrustBar/HowItWorks precede activities.

- [ ] **Step 3: Implement**

`src/pages/HomePage.js`:

```jsx
<title>Trivlu — The Easiest Prague Stag Do. All Sorted For You.</title>
<meta name="description"
      content="Your mates vote in 10 minutes. We deliver the perfect Prague stag do weekend — activities, booking and logistics all sorted for you."/>
...
<h1 className="hero-title">The Easiest Prague Stag Do. All Sorted For You.</h1>
...
<FeaturedActivitiesSection/>
<TrustBar/>
<HowItWorksSection onStartVote={openVoteSetup}/>
<ReviewsSection onStartVote={openVoteSetup}/>
<ContactCtaSection/>
```

- [ ] **Step 4: Run to verify PASS**

Run: `npm test -- --watchAll=false HomePage`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/pages/HomePage.js src/pages/HomePage.test.js
git commit -m "feat(home): Prague in hero title, activities directly under hero"
```

---

### Task 8: Extract `VoteDemoCard` shared component

**Files:**
- Create: `src/components/home/VoteDemoCard.js`, `src/components/home/VoteDemoCard.css`
- Modify: `src/pages/HomePage.js` (replace the inline `<aside className="vote-card">…` block, lines 38-60), `src/pages/HomePage.css` (move the `.vote-card` / `.vc-*` rules out)
- Test: `src/components/home/VoteDemoCard.test.js`

**Interfaces:**
- Produces: `<VoteDemoCard />` — presentational, no props, `aria-hidden="true"`, renders exactly today's hero markup (`.vote-card`, `.vc-head`, `.vc-row`…). Consumed by HomePage hero and Task 9's HowItWorks step.

- [ ] **Step 1: Write the failing test**

```js
import {render} from '@testing-library/react';
import VoteDemoCard from './VoteDemoCard';

test('renders the four demo vote rows', () => {
    const {container} = render(<VoteDemoCard/>);
    expect(container.querySelectorAll('.vc-row')).toHaveLength(4);
    expect(container.textContent).toContain('Bar Crawl');
    expect(container.querySelector('.vote-card').getAttribute('aria-hidden')).toBe('true');
});
```

- [ ] **Step 2: Run to verify FAIL, then implement**

Run: `npm test -- --watchAll=false VoteDemoCard` → FAIL (missing module).

`src/components/home/VoteDemoCard.js` — move the exact JSX from `HomePage.js:38-60` (the `<aside className="vote-card">…</aside>` block) into:

```jsx
import './VoteDemoCard.css';

// Static vote-tally showcase used in the hero and the How It Works section.
const ROWS = [
    {icon: 'ph-beer-stein', name: 'Bar Crawl', num: 8, pct: 89, fill: 'var(--purple-ll)'},
    {icon: 'ph-steering-wheel', name: 'Karting', num: 6, pct: 67, fill: 'var(--purple-l)'},
    {icon: 'ph-target', name: 'Shooting', num: 5, pct: 56, fill: 'var(--purple-l)'},
    {icon: 'ph-boat', name: 'Tiki Boat', num: 4, pct: 44, fill: 'var(--purple-l)'},
];

function VoteDemoCard() {
    return (
        <aside className="vote-card" aria-hidden="true">
            {/* …identical inner JSX to the current HomePage block… */}
        </aside>
    );
}

export default VoteDemoCard;
```

Move every `.vote-card`, `.vc-*` rule from `HomePage.css` (they start at ~line 169) into `VoteDemoCard.css` unchanged. In `HomePage.js`, replace the aside with `<VoteDemoCard/>` and import it. Keep any hero-scoped positioning overrides (e.g. `.hero .vote-card { … }`) in `HomePage.css`.

- [ ] **Step 3: Run to verify PASS**

Run: `npm test -- --watchAll=false VoteDemoCard HomePage`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add src/components/home/VoteDemoCard.js src/components/home/VoteDemoCard.css src/components/home/VoteDemoCard.test.js src/pages/HomePage.js src/pages/HomePage.css
git commit -m "refactor(home): extract VoteDemoCard from the hero"
```

---

### Task 9: How It Works visuals — Tinder moment, VoteDemoCard step, limo, local assets

**Files:**
- Create: `src/components/home/TinderMomentCard.js`, `src/components/home/TinderMomentCard.css`, `src/assets/home/` (4 images)
- Modify: `src/components/home/HowItWorksSection.js`, `src/components/home/HowItWorksSection.css`
- Test: `src/components/home/HowItWorksSection.test.js`

**Interfaces:**
- Consumes: `VoteDemoCard` (Task 8).
- Produces: `<TinderMomentCard image={src} alt="" />` — full-block photo with a swipe-choice overlay.

- [ ] **Step 1: Gather the assets (manual step, no test)**

1. Fetch the activities catalog from the running backend (`myhive-backend` locally, or prod): `curl -s "$API/activities" | jq -r '.[] | "\(.name) \(.imageUrl)"'` (adjust to the API shape used by `api.getActivities`). Find the **Steak & Tits** activity image and a **limousine** activity image; download to `src/assets/home/steak-and-tits.jpg` and `src/assets/home/limo.jpg`.
2. Download the two CDN screenshots that remain (steps 1 and 4 at `HowItWorksSection.js:8` and `:26`) to `src/assets/home/step-style.png` and `src/assets/home/step-review.jpg`.
3. **If either catalog photo can't be found, STOP and ask the user for the two photos** — do not substitute stock imagery.
4. Compress anything over ~300 KB (e.g. `sips -Z 1200` on macOS).

- [ ] **Step 2: Write the failing tests**

Update `src/components/home/HowItWorksSection.test.js`:

```js
test('renders the tinder moment, the vote demo card, and no CDN images', () => {
    render(<HowItWorksSection onStartVote={jest.fn()}/>);
    expect(document.querySelector('.tinder-moment')).toBeInTheDocument();
    expect(document.querySelector('.vote-card')).toBeInTheDocument();
    for (const img of document.querySelectorAll('img')) {
        expect(img.src).not.toContain('cdn.jsdelivr.net');
    }
});
```

- [ ] **Step 3: Run to verify FAIL**

Run: `npm test -- --watchAll=false HowItWorksSection`
Expected: FAIL — CDN URLs present, no `.tinder-moment`, no `.vote-card`.

- [ ] **Step 4: Implement `TinderMomentCard`**

`src/components/home/TinderMomentCard.js`:

```jsx
import './TinderMomentCard.css';

// Full-block "swipe moment": photo styled as the top card of a swipe deck,
// with a visible LIKE stamp and like/skip controls so the choice mechanic
// reads instantly.
function TinderMomentCard({image, alt = ''}) {
    return (
        <div className="tinder-moment" aria-hidden="true">
            <div className="tinder-moment-stack"/>
            <div className="tinder-moment-card">
                <img src={image} alt={alt} loading="lazy"/>
                <span className="tinder-moment-stamp">LIKE</span>
                <div className="tinder-moment-actions">
                    <span className="tinder-moment-btn tinder-moment-btn--skip">
                        <i className="ph ph-x"/>
                    </span>
                    <span className="tinder-moment-btn tinder-moment-btn--like">
                        <i className="ph ph-heart"/>
                    </span>
                </div>
            </div>
        </div>
    );
}

export default TinderMomentCard;
```

`src/components/home/TinderMomentCard.css`:

```css
.tinder-moment {
    position: relative;
    width: 100%;
    height: 100%;
    min-height: 220px;
}

/* Edge of the "next card" peeking out behind the top card */
.tinder-moment-stack {
    position: absolute;
    inset: 12px 4px 4px;
    border-radius: 16px;
    background: rgba(255, 255, 255, 0.08);
    transform: rotate(3deg);
}

.tinder-moment-card {
    position: absolute;
    inset: 0;
    border-radius: 16px;
    overflow: hidden;
    transform: rotate(-2deg);
    box-shadow: 0 10px 24px rgba(0, 0, 0, 0.35);
}

.tinder-moment-card img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
}

.tinder-moment-stamp {
    position: absolute;
    top: 14px;
    left: 14px;
    padding: 2px 10px;
    border: 3px solid #4ade80;
    border-radius: 8px;
    color: #4ade80;
    font-weight: 800;
    font-size: 1.1rem;
    letter-spacing: 0.08em;
    transform: rotate(-12deg);
}

.tinder-moment-actions {
    position: absolute;
    bottom: 10px;
    left: 0;
    right: 0;
    display: flex;
    justify-content: center;
    gap: 16px;
}

.tinder-moment-btn {
    width: 40px;
    height: 40px;
    border-radius: 50%;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    font-size: 1.25rem;
    background: rgba(0, 0, 0, 0.55);
    backdrop-filter: blur(4px);
}

.tinder-moment-btn--skip { color: #f87171; }
.tinder-moment-btn--like { color: #4ade80; }
```

- [ ] **Step 5: Rework `HowItWorksSection`**

```jsx
import VoteDemoCard from './VoteDemoCard';
import TinderMomentCard from './TinderMomentCard';
import stepStyleImg from '../../assets/home/step-style.png';
import steakTitsImg from '../../assets/home/steak-and-tits.jpg';
import limoImg from '../../assets/home/limo.jpg';

const STEPS = [
    {
        title: 'Define your stag style',
        text: 'Wild or classy, chill or adrenaline',
        img: stepStyleImg,
        objectPosition: 'top',
    },
    {
        title: 'Handpick the shortlist',
        text: 'Pick what the group gets to vote on',
        visual: <TinderMomentCard image={steakTitsImg}/>,
    },
    {
        title: 'Send the vote link',
        text: 'Your mates pick their favourites',
        visual: <VoteDemoCard/>,
    },
    {
        title: 'Review & confirm',
        text: 'Add, remove or tweak before you book',
        img: limoImg,
        objectPosition: 'center',
    },
];
```

Render body: `visual` wins over `img`:

```jsx
<div className="step-img">
    {step.visual
        ? step.visual
        : <img src={step.img} alt="" loading="lazy" style={{objectPosition: step.objectPosition}}/>}
</div>
```

In `HowItWorksSection.css`, make sure `.step-img` children can fill the block (`.step-img > * { width: 100%; height: 100%; }` if the existing rules only target `img`), and constrain the embedded `.vote-card` (`.step-img .vote-card { margin: 12px; }` — eyeball at Step 7).

Note: before the swap, load the current step-2 CDN image once in a browser to confirm it is the swipe screenshot (spec: "confirm visually before replacing"). If the swipe screenshot is actually another step, put the `TinderMomentCard` on that step instead and keep that step's old image for step 2.

- [ ] **Step 6: Run to verify PASS**

Run: `npm test -- --watchAll=false HowItWorksSection HomePage`
Expected: PASS. (CRA transforms image imports to stubs in Jest automatically.)

- [ ] **Step 7: Visual check**

Run `npm start`, open `http://localhost:3000`, check the section desktop + 390 px wide: tinder card fills its block with a readable LIKE stamp; vote card fits its step; limo photo crops sensibly.

- [ ] **Step 8: Commit**

```bash
git add src/components/home/TinderMomentCard.js src/components/home/TinderMomentCard.css src/components/home/HowItWorksSection.js src/components/home/HowItWorksSection.css src/components/home/HowItWorksSection.test.js src/assets/home
git commit -m "feat(home): tinder moment, vote demo card and limo visuals in How It Works"
```

---

### Task 10: WhatsApp widget

**Files:**
- Create: `src/components/WhatsAppWidget.js`, `src/components/WhatsAppWidget.css`
- Modify: `src/components/Layout.js` (mount after `<Footer/>`)
- Test: `src/components/WhatsAppWidget.test.js`

**Interfaces:**
- Consumes: `WHATSAPP_URL` from `services/config.js`, `pushEvent`.
- Produces: `<WhatsAppWidget/>` — fixed FAB, hidden on the participant swipe route (`/vote/:shareToken/activities`). Layout is public-only (`AdminApp` is a separate tree), so mounting there satisfies "not on admin".

- [ ] **Step 1: Write the failing tests**

```js
import {render, screen, fireEvent} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import WhatsAppWidget from './WhatsAppWidget';
import {WHATSAPP_URL} from '../services/config';

function renderAt(path) {
    return render(<MemoryRouter initialEntries={[path]}><WhatsAppWidget/></MemoryRouter>);
}

test('renders a WhatsApp link and fires the analytics event on click', () => {
    window.dataLayer = [];
    renderAt('/');
    const link = screen.getByRole('link', {name: /chat with us on whatsapp/i});
    expect(link).toHaveAttribute('href', WHATSAPP_URL);
    expect(link).toHaveAttribute('target', '_blank');
    fireEvent.click(link);
    expect(window.dataLayer).toContainEqual(expect.objectContaining({
        event: 'cta_click', cta_label: 'whatsapp_widget', page: '/',
    }));
});

test('hidden on the participant swipe page', () => {
    renderAt('/vote/tok123/activities');
    expect(screen.queryByRole('link', {name: /whatsapp/i})).toBeNull();
});
```

- [ ] **Step 2: Run to verify FAIL, then implement**

Run: `npm test -- --watchAll=false WhatsAppWidget` → FAIL (missing module).

`src/components/WhatsAppWidget.js`:

```jsx
import {useLocation} from 'react-router-dom';
import {WHATSAPP_URL} from '../services/config';
import {pushEvent} from '../utils/analytics';
import './WhatsAppWidget.css';

// Floating "chat with us" FAB. Hidden on the participant swipe page, where a
// fixed control would sit on top of the swipe buttons.
function WhatsAppWidget() {
    const {pathname} = useLocation();
    if (/^\/vote\/[^/]+\/activities$/.test(pathname)) {
        return null;
    }
    return (
        <a
            className="whatsapp-widget"
            href={WHATSAPP_URL}
            target="_blank"
            rel="noopener noreferrer"
            aria-label="Chat with us on WhatsApp"
            onClick={() => pushEvent('cta_click', {cta_label: 'whatsapp_widget', page: pathname})}
        >
            <i className="ph ph-whatsapp-logo" aria-hidden="true"/>
        </a>
    );
}

export default WhatsAppWidget;
```

`src/components/WhatsAppWidget.css`:

```css
.whatsapp-widget {
    position: fixed;
    right: 16px;
    bottom: 16px;
    z-index: 90; /* under .app-modal overlays (check their z-index in AppModal css) */
    width: 52px;
    height: 52px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    background: #25d366;
    color: #fff;
    font-size: 1.75rem;
    box-shadow: 0 6px 16px rgba(0, 0, 0, 0.35);
}

.whatsapp-widget:hover { color: #fff; opacity: 0.92; }

/* Sits above the sticky mobile CTA (Task 11) */
@media (max-width: 767px) {
    .homepage-has-sticky-cta .whatsapp-widget { bottom: 76px; }
}
```

Mount in `Layout.js` right after `<Footer/>`: `<WhatsAppWidget/>` (+ import). Verify the modal overlay z-index in `AppModal`'s CSS is higher; adjust 90 if needed.

- [ ] **Step 3: Run to verify PASS**

Run: `npm test -- --watchAll=false WhatsAppWidget`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add src/components/WhatsAppWidget.js src/components/WhatsAppWidget.css src/components/WhatsAppWidget.test.js src/components/Layout.js
git commit -m "feat(contact): floating WhatsApp widget"
```

---

### Task 11: Sticky mobile CTA on the homepage (feature-flagged, OFF by default)

**Files:**
- Create: `src/components/home/StickyVoteCta.js`, `src/components/home/StickyVoteCta.css`
- Modify: `src/pages/HomePage.js`, `src/services/config.js`
- Test: `src/components/home/StickyVoteCta.test.js`, `src/pages/HomePage.test.js`

**Interfaces:**
- Consumes: `openVoteSetup` from `useStartGroupVote` (passed as prop).
- Produces:
  - `STICKY_VOTE_CTA_ENABLED = false` in `src/services/config.js` (same pattern as `DESTINATION_PICKER_ENABLED`) — **ships OFF**; the user decides later whether to enable it.
  - `<StickyVoteCta onStartVote={fn} heroSelector=".hero-cta-group" hidden={bool} />` — fixed bottom bar, mobile-only (CSS `display:none` ≥768px), appears once the hero CTA scrolls out of view (IntersectionObserver), `hidden` prop suppresses it while the setup modal is open. Adds the class `homepage-has-sticky-cta` to `document.body` while visible (consumed by Task 10's CSS offset); removes it on unmount/hide.
  - HomePage renders it only when the flag is on:
    ```jsx
    {STICKY_VOTE_CTA_ENABLED && <StickyVoteCta onStartVote={openVoteSetup} hidden={voteSetupOpen}/>}
    ```
  - Extra HomePage test (flag is a compile-time constant, so assert the default): with the shipped config, no `.sticky-vote-cta` is rendered:
    ```js
    test('sticky CTA is feature-flagged off by default', () => {
        renderHomePage();
        expect(document.querySelector('.sticky-vote-cta')).toBeNull();
    });
    ```

- [ ] **Step 1: Write the failing tests**

JSDOM has no IntersectionObserver — stub it:

```js
let ioCallback;
beforeEach(() => {
    window.IntersectionObserver = jest.fn((cb) => {
        ioCallback = cb;
        return {observe: jest.fn(), disconnect: jest.fn()};
    });
});

function renderCta(props = {}) {
    return render(
        <div>
            <div className="hero-cta-group"/>
            <StickyVoteCta onStartVote={jest.fn()} {...props}/>
        </div>
    );
}

test('hidden until the hero CTA leaves the viewport, then shows and marks the body', () => {
    renderCta();
    expect(screen.queryByRole('button', {name: /start group vote/i})).toBeNull();
    act(() => ioCallback([{isIntersecting: false}]));
    expect(screen.getByRole('button', {name: /start group vote/i})).toBeInTheDocument();
    expect(document.body.classList.contains('homepage-has-sticky-cta')).toBe(true);
});

test('click fires analytics + onStartVote; hidden prop suppresses it', () => {
    window.dataLayer = [];
    const onStartVote = jest.fn();
    const {rerender} = renderCta({onStartVote});
    act(() => ioCallback([{isIntersecting: false}]));
    fireEvent.click(screen.getByRole('button', {name: /start group vote/i}));
    expect(onStartVote).toHaveBeenCalled();
    expect(window.dataLayer).toContainEqual(expect.objectContaining({
        event: 'cta_click', cta_label: 'Start Group Vote', block: 'sticky_mobile',
    }));
    rerender(<div><div className="hero-cta-group"/><StickyVoteCta onStartVote={onStartVote} hidden/></div>);
    expect(screen.queryByRole('button', {name: /start group vote/i})).toBeNull();
    expect(document.body.classList.contains('homepage-has-sticky-cta')).toBe(false);
});
```

- [ ] **Step 2: Run to verify FAIL, then implement**

Run: `npm test -- --watchAll=false StickyVoteCta` → FAIL (missing module).

`src/components/home/StickyVoteCta.js`:

```jsx
import {useEffect, useState} from 'react';
import {pushEvent} from '../../utils/analytics';
import './StickyVoteCta.css';

// Mobile-only bottom bar that repeats the hero CTA once it scrolls away.
function StickyVoteCta({onStartVote, heroSelector = '.hero-cta-group', hidden = false}) {
    const [heroGone, setHeroGone] = useState(false);

    useEffect(() => {
        const hero = document.querySelector(heroSelector);
        if (!hero || !window.IntersectionObserver) {
            return undefined;
        }
        const observer = new IntersectionObserver(
            (entries) => setHeroGone(!entries[0].isIntersecting)
        );
        observer.observe(hero);
        return () => observer.disconnect();
    }, [heroSelector]);

    const visible = heroGone && !hidden;

    useEffect(() => {
        document.body.classList.toggle('homepage-has-sticky-cta', visible);
        return () => document.body.classList.remove('homepage-has-sticky-cta');
    }, [visible]);

    if (!visible) {
        return null;
    }
    return (
        <div className="sticky-vote-cta">
            <button
                className="btn btn--primary btn--full-width"
                onClick={() => {
                    pushEvent('cta_click', {cta_label: 'Start Group Vote', block: 'sticky_mobile'});
                    onStartVote();
                }}
            >
                <i className="ph ph-check-square" aria-hidden="true"/> Start Group Vote
            </button>
        </div>
    );
}

export default StickyVoteCta;
```

`src/components/home/StickyVoteCta.css`:

```css
.sticky-vote-cta {
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 80;
    padding: 10px 16px calc(10px + env(safe-area-inset-bottom));
    background: linear-gradient(to top, rgba(10, 10, 14, 0.95), rgba(10, 10, 14, 0.75));
    backdrop-filter: blur(6px);
}

@media (min-width: 768px) {
    .sticky-vote-cta { display: none; }
}
```

In `src/services/config.js`, next to `DESTINATION_PICKER_ENABLED`:

```js
// Sticky mobile "Start Group Vote" bar on the homepage. Ships dark — visual
// risk; flip to true to trial it.
export const STICKY_VOTE_CTA_ENABLED = false;
```

In `HomePage.js`, before the closing `</div>` (import the flag from `../services/config`):

```jsx
{STICKY_VOTE_CTA_ENABLED && <StickyVoteCta onStartVote={openVoteSetup} hidden={voteSetupOpen}/>}
```

- [ ] **Step 3: Run to verify PASS**

Run: `npm test -- --watchAll=false StickyVoteCta HomePage`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add src/components/home/StickyVoteCta.js src/components/home/StickyVoteCta.css src/components/home/StickyVoteCta.test.js src/pages/HomePage.js src/pages/HomePage.test.js src/services/config.js
git commit -m "feat(home): sticky mobile vote CTA behind STICKY_VOTE_CTA_ENABLED (off)"
```

---

### Task 12: Endowed quiz progress bar

**Files:**
- Modify: `src/pages/vote/QuizPage.js` (line 102), `src/pages/vote/QuizPage.css`
- Test: `src/pages/vote/QuizPage.test.js`

**Interfaces:**
- Consumes: nothing new. Organizer detection already exists (`isOrganizer = !shareToken`).

- [ ] **Step 1: Write the failing tests**

Add to `src/pages/vote/QuizPage.test.js` (its existing setup mocks `voteApi.getPublicQuizForDestination` with N questions — reuse it):

```js
test('organizer sees an endowed progress bar (setup counts as a done step)', async () => {
    renderOrganizerQuiz(); // existing helper; assume 4 questions mocked
    await screen.findByText('1 / 4');
    const fill = document.querySelector('.quiz-progress-fill');
    // 1 completed (setup) of 5 total steps = 20%
    expect(fill.style.width).toBe('20%');
});

test('participant progress starts at zero', async () => {
    renderParticipantQuiz();
    await screen.findByText('1 / 4');
    expect(document.querySelector('.quiz-progress-fill').style.width).toBe('0%');
});
```

- [ ] **Step 2: Run to verify FAIL, then implement**

Run: `npm test -- --watchAll=false QuizPage` → FAIL (no `.quiz-progress-fill`).

In `QuizPage.js`, replace line 102:

```jsx
      {(() => {
        // Endowed progress: the organizer already completed the setup step, so
        // their bar starts pre-filled — started journeys get finished more.
        const endow = isOrganizer ? 1 : 0;
        const total = quiz.questions.length + endow;
        const done = stepIndex + endow;
        return (
          <div className="quiz-progress">
            <div className="quiz-progress-track">
              <div className="quiz-progress-fill" style={{width: `${Math.round((done / total) * 100)}%`}}/>
            </div>
            <span className="quiz-progress-label">{stepIndex + 1} / {quiz.questions.length}</span>
          </div>
        );
      })()}
```

`QuizPage.css` — style next to the existing `.quiz-progress` rule (keep its typography for `.quiz-progress-label`):

```css
.quiz-progress-track {
    height: 6px;
    border-radius: 3px;
    background: rgba(255, 255, 255, 0.12);
    overflow: hidden;
    margin-bottom: 6px;
}

.quiz-progress-fill {
    height: 100%;
    border-radius: 3px;
    background: var(--primary);
    transition: width 250ms ease;
}
```

- [ ] **Step 3: Run to verify PASS**

Run: `npm test -- --watchAll=false QuizPage`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add src/pages/vote/QuizPage.js src/pages/vote/QuizPage.css src/pages/vote/QuizPage.test.js
git commit -m "feat(vote): endowed progress bar in the quiz"
```

---

### Task 13: Mobile alignment pass — destination page

**Files:**
- Modify: `src/pages/DestinationPage.css` (and `src/components/TripBuilder.css` / `src/components/ActivityCard.css` only if the misalignment lives there)

No unit tests — this is a visual task with concrete acceptance criteria.

- [ ] **Step 1: Reproduce**

`npm start`, open `http://localhost:3000/destination/prague` in devtools at 390×844 (iPhone 12/13). Screenshot the Activities tab: hero header, category chips, activity cards.

- [ ] **Step 2: Fix to these acceptance criteria**

1. No horizontal scroll anywhere on the page at 320-430 px widths.
2. One horizontal gutter rhythm: every block (header, chips, cards, price rows) aligns to the same left/right padding (16 px).
3. Category chips: equal heights, consistent gap (8 px), wrapping without ragged edge-overflow; tap targets ≥ 40 px tall.
4. Activity cards: full-width, equal vertical spacing, image corners and paddings consistent between cards; price + "Add to trip" row aligned on one baseline.
5. The Miro mobile screenshot (Prague page) is the reference for what "aligned" means.

Make the smallest CSS changes that satisfy these — media-query overrides in `DestinationPage.css` preferred over restructuring markup.

- [ ] **Step 3: Verify**

Re-screenshot at 320 px, 390 px, 430 px. Run the full test suite once to catch accidental breakage: `npm test -- --watchAll=false`.
Expected: all green.

- [ ] **Step 4: Commit**

```bash
git add src/pages/DestinationPage.css src/components/TripBuilder.css src/components/ActivityCard.css
git commit -m "fix(mobile): align destination page chips, cards and gutters"
```

---

### Task 14: Full-suite regression + flow smoke test

**Files:** none new.

- [ ] **Step 1: Full test run**

Run: `npm test -- --watchAll=false`
Expected: all suites green. Fix anything still pinning removed behaviour (email in setup modal, one-click quiz vote, old hero title, CDN images).

- [ ] **Step 2: Manual smoke of the two funnels**

`npm start` + backend running:

1. **Vote funnel:** Home → Start Group Vote → small modal (travelers + dates popover, no email; close it, reopen — values persist) → quiz (progress bar pre-filled) → curate swipe → trip builder → "Let your mates vote" → modal asks email with new microcopy → typing email creates lead (check network tab `POST /leads`) → Create vote → waiting page. Verify a QUIZ session was created (no `myhive-trip-vote-session` key).
2. **Browse funnel:** Home → activities grid (directly under hero) → add an activity → small setup modal appears → Trip Builder → Complete Booking → checkout form shows consent note → typing a valid email fires `POST /leads` after ~2 s → submit booking succeeds.
3. WhatsApp FAB present on home/destination, absent on `/vote/<token>/activities`. Sticky CTA must NOT appear (flag ships off); optionally flip `STICKY_VOTE_CTA_ENABLED` locally to check it appears after scrolling past the hero with the FAB offset above it, then flip it back.
4. `window.dataLayer` contains `modal_abandoned` after closing either modal without submitting.

- [ ] **Step 3: Commit any smoke-test fixes**

```bash
git add -A src
git commit -m "fix(leads): smoke-test fixes for the lead-capture batch"
```

(Skip if nothing changed.)
