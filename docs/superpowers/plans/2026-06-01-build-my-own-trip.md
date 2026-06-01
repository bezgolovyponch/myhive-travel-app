# "Build my own trip" Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Build my own trip" button to the curate finalize step that pushes the organizer's swiped picks straight into the Trip Builder (carrying over travelers, dates, and budget) without creating a vote session.

**Architecture:** Frontend-only. Budget becomes part of the persisted trip-state in `AppContext` (alongside travelers/dates), so the Trip Builder budget panel can render on the solo path with no vote session. `CuratePage` gains a handler that seeds setup context and dispatches the picks, then navigates to the destination's Trip Builder tab.

**Tech Stack:** React 19, React Context (`useReducer`), React Router, Jest + React Testing Library.

---

### Task 1: AppContext — budget in trip-state

**Files:**
- Modify: `myhive-react-app/src/context/AppContext.js`
- Test: `myhive-react-app/src/context/AppContext.test.js`

- [ ] **Step 1: Write the failing tests**

In `myhive-react-app/src/context/AppContext.test.js`, add a new `describe` block after the `UPDATE_TRIP_DATES` block (after line 81):

```js
    describe('UPDATE_TRIP_BUDGET', () => {
        it('updates only the budget', () => {
            const prev = {...initialState, tripTravelers: 3, tripStartDate: '2026-06-01'};
            const state = reducer(prev, {type: 'UPDATE_TRIP_BUDGET', budget: 3000});

            expect(state.tripBudget).toBe(3000);
            expect(state.tripTravelers).toBe(3);
            expect(state.tripStartDate).toBe('2026-06-01');
        });

        it('accepts null to clear the budget', () => {
            const prev = {...initialState, tripBudget: 3000};
            const state = reducer(prev, {type: 'UPDATE_TRIP_BUDGET', budget: null});

            expect(state.tripBudget).toBeNull();
        });
    });
```

In the `initialState defaults` test (currently lines 107-115), add this assertion inside the `it` body:

```js
        expect(initialState.tripBudget).toBeNull();
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `npm test -- --watchAll=false --testPathPattern=AppContext` (from `myhive-react-app/`)
Expected: FAIL — `UPDATE_TRIP_BUDGET` falls through to `default` so `tripBudget` is `undefined`, and `initialState.tripBudget` is `undefined` (not `null`).

- [ ] **Step 3: Add `tripBudget` to initialState**

In `myhive-react-app/src/context/AppContext.js`, add to `initialState` (after `tripEndDate: '',` on line 13):

```js
    tripBudget: null,
```

- [ ] **Step 4: Add the reducer case**

In the same file, add after the `UPDATE_TRIP_DATES` case (after line 81):

```js
        case 'UPDATE_TRIP_BUDGET':
            return {...state, tripBudget: action.budget};
```

- [ ] **Step 5: Persist + load budget in the localStorage `myhive-trip-setup` blob**

In the `useReducer` initializer, declare the local alongside the others (after `let tripEndDate = init.tripEndDate;`, line 134):

```js
        let tripBudget = init.tripBudget;
```

Inside the `myhive-trip-setup` parse block, after `tripEndDate = setup.endDate || '';` (line 148), add:

```js
                tripBudget = setup.budget ?? null;
```

Change the initializer return (line 153) from:

```js
        return {...init, tripItems, tripTravelers, tripStartDate, tripEndDate};
```

to:

```js
        return {...init, tripItems, tripTravelers, tripStartDate, tripEndDate, tripBudget};
```

Update the persist effect (lines 162-168) to include budget:

```js
    useEffect(() => {
        localStorage.setItem('myhive-trip-setup', JSON.stringify({
            travelers: state.tripTravelers,
            startDate: state.tripStartDate,
            endDate: state.tripEndDate,
            budget: state.tripBudget
        }));
    }, [state.tripTravelers, state.tripStartDate, state.tripEndDate, state.tripBudget]);
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `npm test -- --watchAll=false --testPathPattern=AppContext`
Expected: PASS (all reducer tests, including the two new ones and the updated defaults test).

- [ ] **Step 7: Commit**

```bash
git add myhive-react-app/src/context/AppContext.js myhive-react-app/src/context/AppContext.test.js
git commit -m "feat: add tripBudget to AppContext trip-state with persistence"
```

---

### Task 2: TripBuilder — drive budget panel from state.tripBudget

**Files:**
- Modify: `myhive-react-app/src/components/TripBuilder.js`

No new test: the budget panel has no existing component test, and the source of truth change is covered functionally by Task 1 (reducer) + Task 3 (CuratePage dispatch). Verification is by reading the diff and the manual smoke check below.

- [ ] **Step 1: Feed the budget into state on the vote-session path**

In `myhive-react-app/src/components/TripBuilder.js`, inside the `voteApi.getResult(voteSession).then(result => { ... })` effect, after the existing dates dispatch block (after line 74, the closing `}` of the `if (result.startDate || result.endDate)` block), add:

```js
            dispatch({ type: 'UPDATE_TRIP_BUDGET', budget: result.budget ?? null });
```

This keeps the group path's budget bar behavior identical while routing it through the same state field the solo path uses.

- [ ] **Step 2: Render the budget panel from `state.tripBudget`**

Replace the budget block (lines 298-313), which currently reads:

```js
        {voteResult && voteResult.budget != null && (
            <div className="trip-vote-budget">
              <div className="trip-vote-budget-row">
                <span>Spent</span>
                <span>{formatPrice(totalPrice)}</span>
              </div>
              <div className="trip-vote-budget-row">
                <span>Budget</span>
                <span>{formatPrice(voteResult.budget)}</span>
              </div>
              <div className={`trip-vote-budget-row ${voteResult.budget - totalPrice < 0 ? 'trip-vote-budget-over' : ''}`}>
                <span>Remaining</span>
                <span>{formatPrice(voteResult.budget - totalPrice)}</span>
              </div>
            </div>
        )}
```

with:

```js
        {state.tripBudget != null && (
            <div className="trip-vote-budget">
              <div className="trip-vote-budget-row">
                <span>Spent</span>
                <span>{formatPrice(totalPrice)}</span>
              </div>
              <div className="trip-vote-budget-row">
                <span>Budget</span>
                <span>{formatPrice(state.tripBudget)}</span>
              </div>
              <div className={`trip-vote-budget-row ${state.tripBudget - totalPrice < 0 ? 'trip-vote-budget-over' : ''}`}>
                <span>Remaining</span>
                <span>{formatPrice(state.tripBudget - totalPrice)}</span>
              </div>
            </div>
        )}
```

(`state` is already destructured from `useContext(AppContext)` at the top of the component, line 14. `voteResult` remains used by the suggestions block below — do not remove it.)

- [ ] **Step 3: Run the existing test suite to confirm nothing regressed**

Run: `npm test -- --watchAll=false` (from `myhive-react-app/`)
Expected: PASS (no test targets the budget panel; this confirms no collateral breakage).

- [ ] **Step 4: Commit**

```bash
git add myhive-react-app/src/components/TripBuilder.js
git commit -m "feat: drive Trip Builder budget panel from state.tripBudget"
```

---

### Task 3: CuratePage — "Build my own trip" button + handler

**Files:**
- Modify: `myhive-react-app/src/pages/vote/CuratePage.js`
- Modify: `myhive-react-app/src/pages/vote/CuratePage.css`
- Test: `myhive-react-app/src/pages/vote/CuratePage.test.js`

- [ ] **Step 1: Update the test harness to provide AppContext, then write the failing tests**

In `myhive-react-app/src/pages/vote/CuratePage.test.js`:

Add the import after line 5 (`import voteApi ...`):

```js
import { AppContext } from '../../context/AppContext';
```

Add `slug` to the shared `setup.destination` (line 10) so navigation has a target:

```js
  destination: { id: 'dest1', slug: 'bali' },
```

Replace `renderWith` (lines 18-28) so the page is wrapped in an `AppContext.Provider` (default mock dispatch keeps existing tests working) and add a destination route:

```js
function renderWith(state, dispatch = jest.fn()) {
  return render(
    <AppContext.Provider value={{ state: { tripItems: [] }, dispatch }}>
      <MemoryRouter initialEntries={[{ pathname: '/vote/new/curate', state }]}>
        <Routes>
          <Route path="/vote/new/curate" element={<CuratePage />} />
          <Route path="/vote/:shareToken/waiting" element={<div>waiting page</div>} />
          <Route path="/destination/:slug" element={<div>destination page</div>} />
          <Route path="/" element={<div>home</div>} />
        </Routes>
      </MemoryRouter>
    </AppContext.Provider>
  );
}
```

Append two new tests at the end of the file:

```js
test('build my own trip seeds setup, adds picks, and navigates to trip builder', async () => {
  const dispatch = jest.fn();
  voteApi.buildPool.mockResolvedValue({
    pool: [
      { activityId: 'act1', name: 'Tank Driving', price: 150, imageUrl: null, slug: 'tank', destinationSlug: 'bali', categories: ['Extreme'] },
      { activityId: 'act2', name: 'Spa Day', price: 80, imageUrl: null, slug: 'spa', destinationSlug: 'bali', categories: ['Chillout'] },
    ],
  });

  renderWith({ setup, responses: [] }, dispatch);

  expect(await screen.findByLabelText('Like')).toBeInTheDocument();
  await userEvent.click(screen.getByLabelText('Like'));      // include act1
  await userEvent.click(screen.getByLabelText('Dislike'));   // skip act2

  expect(await screen.findByText(/Your voting list/i)).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: /Build my own trip/i }));

  expect(dispatch).toHaveBeenCalledWith({ type: 'UPDATE_TRIP_TRAVELERS', travelers: 2 });
  expect(dispatch).toHaveBeenCalledWith({ type: 'UPDATE_TRIP_DATES', startDate: '2026-08-01', endDate: '2026-08-10' });
  expect(dispatch).toHaveBeenCalledWith({ type: 'UPDATE_TRIP_BUDGET', budget: 3000 });

  const addCalls = dispatch.mock.calls.filter(c => c[0].type === 'ADD_TO_TRIP');
  expect(addCalls).toHaveLength(1);
  expect(addCalls[0][0].silent).toBe(true);
  expect(addCalls[0][0].activity).toMatchObject({ id: 'act1', name: 'Tank Driving', categories: [{ name: 'Extreme' }] });

  expect(await screen.findByText('destination page')).toBeInTheDocument();
});

test('build my own trip is disabled when nothing was picked', async () => {
  voteApi.buildPool.mockResolvedValue({
    pool: [
      { activityId: 'act1', name: 'Tank Driving', price: 150, imageUrl: null, slug: 'tank', destinationSlug: 'bali', categories: [] },
    ],
  });

  renderWith({ setup, responses: [] });

  expect(await screen.findByLabelText('Dislike')).toBeInTheDocument();
  await userEvent.click(screen.getByLabelText('Dislike'));   // skip everything

  expect(await screen.findByText(/Your voting list \(0\)/i)).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /Build my own trip/i })).toBeDisabled();
});
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `npm test -- --watchAll=false --testPathPattern=CuratePage`
Expected: FAIL — the "Build my own trip" button does not exist yet (`Unable to find role="button" name=/Build my own trip/`).

- [ ] **Step 3: Wire AppContext into CuratePage**

In `myhive-react-app/src/pages/vote/CuratePage.js`, change the React import (line 1) from:

```js
import { useEffect, useRef, useState } from 'react';
```

to:

```js
import { useContext, useEffect, useRef, useState } from 'react';
```

Add this import after line 5 (`import SwipeCard ...`):

```js
import { AppContext } from '../../context/AppContext';
```

Inside the component, after `const navigate = useNavigate();` (line 10), add:

```js
  const { dispatch } = useContext(AppContext);
```

- [ ] **Step 4: Add the `handleBuildMyTrip` handler**

In `CuratePage.js`, add after `handleStartOver` (after line 68):

```js
  const handleBuildMyTrip = () => {
    const picked = pool.filter(a => pickedRef.current.includes(a.id));
    if (picked.length === 0) {
      return;
    }
    if (setup.travelers && setup.travelers > 0) {
      dispatch({ type: 'UPDATE_TRIP_TRAVELERS', travelers: setup.travelers });
    }
    dispatch({
      type: 'UPDATE_TRIP_DATES',
      startDate: setup.startDate ?? '',
      endDate: setup.endDate ?? '',
    });
    dispatch({ type: 'UPDATE_TRIP_BUDGET', budget: setup.budget ?? null });
    picked.forEach(a => {
      dispatch({
        type: 'ADD_TO_TRIP',
        silent: true,
        activity: {
          id: a.id,
          name: a.name,
          price: a.price,
          slug: a.slug,
          destinationSlug: a.destinationSlug,
          imageUrl: a.imageUrl,
          categories: (a.categories || []).map(name => ({ name })),
        },
      });
    });
    navigate(`/destination/${setup.destination.slug}?tab=trip-builder`);
  };
```

- [ ] **Step 5: Add the button to the finalize actions row**

In `CuratePage.js`, in the `curate-finalize-actions` block (lines 155-167), add the new button between the "Start over" and "Create & get link" buttons (i.e. after the closing `</button>` of `curate-finalize-reset`, line 158):

```js
          <button
            type="button"
            className="curate-finalize-build"
            disabled={pickedActivities.length === 0}
            onClick={handleBuildMyTrip}
          >
            Build my own trip
          </button>
```

- [ ] **Step 6: Add button styling**

In `myhive-react-app/src/pages/vote/CuratePage.css`, append:

```css
.curate-finalize-build {
  padding: 12px 24px;
  background: var(--primary, #32b8c6);
  color: #fff;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 600;
}

.curate-finalize-build:disabled {
  background: #555;
  cursor: not-allowed;
}
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `npm test -- --watchAll=false --testPathPattern=CuratePage`
Expected: PASS — all CuratePage tests, including the two new ones and the unchanged create/start-over/redirect/empty tests.

- [ ] **Step 8: Run the full frontend suite**

Run: `npm test -- --watchAll=false`
Expected: PASS (whole suite green).

- [ ] **Step 9: Commit**

```bash
git add myhive-react-app/src/pages/vote/CuratePage.js myhive-react-app/src/pages/vote/CuratePage.css myhive-react-app/src/pages/vote/CuratePage.test.js
git commit -m "feat: add 'Build my own trip' solo path on curate finalize step"
```

---

## Manual Smoke Check (after all tasks)

1. `cd myhive-react-app && npm start`, and run the backend (`cd myhive-backend && ./gradlew bootRun --args='--spring.profiles.active=dev'`).
2. Start a "Vote together" flow as organizer: set travelers/dates/budget, complete the quiz, swipe right on a few activities.
3. On the finalize screen, click **Build my own trip**.
4. Confirm: lands on `/destination/<slug>?tab=trip-builder`, the picked activities are in the itinerary, travelers/dates match setup, and the budget bar shows Spent / Budget / Remaining.
5. Confirm no vote session was created (no share link, no waiting page).

---

## Notes for the Implementer

- **Task order:** Task 1 first (defines `UPDATE_TRIP_BUDGET` and `tripBudget`). Tasks 2 and 3 both depend on Task 1 but touch different files and are independent of each other.
- **Do not** remove `voteResult` from `TripBuilder.js` — it still drives the "Group suggestions" block.
- The pool DTO (`VotePoolActivityDTO`) has no `description`/`includes`/`duration`; those default downstream and are intentionally omitted from the trip item mapping.
