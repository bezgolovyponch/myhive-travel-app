# AppContext Split Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the single `AppContext` with three focused contexts (`CatalogContext`, `TripContext`, `DestinationModalContext`) and localize the dead chat cluster into `ChatPanel`, with no change in app behavior.

**Architecture:** Tasks 1–4 add the three contexts (each: raw `Context` + `initialState` + `reducer` + `XProvider` + `useX()` hook) and an `AppProviders` composition — all additive, the old `AppContext` keeps working. Task 5 is one atomic cutover: repoint `App.js`, migrate all 16 consumers to the new hooks, localize chat, re-wrap the 5 integration tests, and delete `AppContext.js` + `AppContext.test.js`. Action-type strings are unchanged, so consumer dispatch calls don't change — only which hook supplies `state`/`dispatch`.

**Tech Stack:** React 19, Create React App (react-scripts 5), Jest + React Testing Library. Run all commands from `myhive-react-app/`. Spec: `docs/superpowers/specs/2026-06-15-appcontext-split-design.md`.

**Conventions:** no wildcard imports; commit per task; run the suite with `CI=true npx react-scripts test --watchAll=false`; build with `CI=true npm run build`. Commit messages use a Bash heredoc (`git commit -F - <<'EOF'`) and avoid double quotes.

**Why Task 5 is atomic:** the integration tests hand-wrap a provider around a component tree that spans several consumers, and a tree can't have some components on the old context and some on the new without every needed provider present. Migrating everything in one commit avoids a half-migrated tree. Tasks 1–4 make T5 purely mechanical.

---

## File Structure

- `src/context/CatalogContext.js` — Create. destinations/loading/error + on-mount fetch.
- `src/context/CatalogContext.test.js` — Create. Reducer unit tests.
- `src/context/TripContext.js` — Create. Trip data + trip modal flags + localStorage.
- `src/context/TripContext.test.js` — Create. Reducer unit tests (ported from AppContext.test.js).
- `src/context/DestinationModalContext.js` — Create. Coming-soon modal.
- `src/context/DestinationModalContext.test.js` — Create. Reducer unit tests.
- `src/context/AppProviders.js` — Create. Composes the three providers.
- `src/App.js` — Modify (T5). Use `AppProviders`.
- 16 consumer files — Modify (T5). Swap `useContext(AppContext)` for the focused hook(s).
- `src/components/ChatPanel.js` — Modify (T5). Localize chat into a local reducer.
- 5 integration test files — Modify (T5). Re-wrap with the focused providers.
- `src/context/AppContext.js` — Delete (T5).
- `src/context/AppContext.test.js` — Delete (T5; coverage moved to TripContext.test.js).

---

### Task 1: CatalogContext

**Files:**
- Create: `src/context/CatalogContext.js`
- Test: `src/context/CatalogContext.test.js`

- [ ] **Step 1: Write the failing test** — `src/context/CatalogContext.test.js`:

```js
import {initialState, reducer} from './CatalogContext';

describe('catalog reducer', () => {
    it('SET_DESTINATIONS sets destinations and clears loading', () => {
        const destinations = [{id: 'd1', name: 'Prague'}];
        const state = reducer(initialState, {type: 'SET_DESTINATIONS', destinations});
        expect(state.destinations).toEqual(destinations);
        expect(state.loading).toBe(false);
    });

    it('SET_ERROR sets the error and clears loading', () => {
        const state = reducer(initialState, {type: 'SET_ERROR', error: 'boom'});
        expect(state.error).toBe('boom');
        expect(state.loading).toBe(false);
    });

    it('SET_LOADING toggles loading', () => {
        const state = reducer({...initialState, loading: false}, {type: 'SET_LOADING', loading: true});
        expect(state.loading).toBe(true);
    });

    it('defaults: loading true, empty destinations, no error', () => {
        expect(initialState.loading).toBe(true);
        expect(initialState.destinations).toEqual([]);
        expect(initialState.error).toBeNull();
    });
});
```

- [ ] **Step 2: Run to verify failure**

Run: `CI=true npx react-scripts test --watchAll=false --testPathPattern CatalogContext`
Expected: FAIL — `Cannot find module './CatalogContext'`.

- [ ] **Step 3: Create `src/context/CatalogContext.js`:**

```jsx
import {createContext, useContext, useEffect, useReducer} from 'react';
import api from '../services/api';

export const CatalogContext = createContext();

export const initialState = {
    destinations: [],
    loading: true,
    error: null,
};

export function reducer(state, action) {
    switch (action.type) {
        case 'SET_DESTINATIONS':
            return {...state, destinations: action.destinations, loading: false};
        case 'SET_ERROR':
            return {...state, error: action.error, loading: false};
        case 'SET_LOADING':
            return {...state, loading: action.loading};
        default:
            return state;
    }
}

export function CatalogProvider({children}) {
    const [state, dispatch] = useReducer(reducer, initialState);

    // Only destinations are needed app-wide (header breadcrumbs, vote setup,
    // home page). Activities are fetched per destination by their consumers.
    useEffect(() => {
        const fetchData = async () => {
            try {
                dispatch({type: 'SET_LOADING', loading: true});
                const destinations = await api.getDestinations();
                dispatch({type: 'SET_DESTINATIONS', destinations});
            } catch (error) {
                console.error('Error fetching data:', error);
                dispatch({type: 'SET_ERROR', error: error.message});
            }
        };
        fetchData();
    }, []);

    return (
        <CatalogContext.Provider value={{state, dispatch}}>
            {children}
        </CatalogContext.Provider>
    );
}

export function useCatalog() {
    const context = useContext(CatalogContext);
    if (context === undefined) {
        throw new Error('useCatalog must be used within a CatalogProvider');
    }
    return context;
}
```

- [ ] **Step 4: Run to verify pass**

Run: `CI=true npx react-scripts test --watchAll=false --testPathPattern CatalogContext`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/context/CatalogContext.js src/context/CatalogContext.test.js
git commit -F - <<'EOF'
feat: CatalogContext (destinations) extracted from AppContext

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
```

---

### Task 2: TripContext

**Files:**
- Create: `src/context/TripContext.js`
- Test: `src/context/TripContext.test.js`

- [ ] **Step 1: Write the failing test** — create `src/context/TripContext.test.js` with the full body of the current `src/context/AppContext.test.js`, changing ONLY the import line to:

```js
import {initialState, reducer} from './TripContext';
```

(Everything else in that file is trip-cluster reducer tests and trip `initialState` defaults — they apply unchanged to TripContext. Copy the file verbatim except that import.)

- [ ] **Step 2: Run to verify failure**

Run: `CI=true npx react-scripts test --watchAll=false --testPathPattern TripContext`
Expected: FAIL — `Cannot find module './TripContext'`.

- [ ] **Step 3: Create `src/context/TripContext.js`:**

```jsx
import {createContext, useContext, useEffect, useReducer} from 'react';

export const TripContext = createContext();

export const initialState = {
    tripItems: [],
    tripTravelers: 1,
    tripStartDate: '',
    tripEndDate: '',
    tripBudget: null,
    tripSetupModalOpen: false,
    tripBuilderModalOpen: false,
};

export function reducer(state, action) {
    switch (action.type) {
        case 'ADD_TO_TRIP':
            if (!state.tripItems.some(item => item.id === action.activity.id)) {
                const isFirstItem = state.tripItems.length === 0;
                return {
                    ...state,
                    tripItems: [...state.tripItems, action.activity],
                    tripSetupModalOpen: isFirstItem && !action.silent,
                    tripBuilderModalOpen: action.silent ? state.tripBuilderModalOpen : (!isFirstItem)
                };
            }
            return state;
        case 'REMOVE_FROM_TRIP':
            return {...state, tripItems: state.tripItems.filter(item => item.id !== action.activityId)};
        case 'OPEN_TRIP_BUILDER_MODAL':
            return {...state, tripBuilderModalOpen: true};
        case 'CLOSE_TRIP_BUILDER_MODAL':
            return {...state, tripBuilderModalOpen: false};
        case 'SET_TRIP_ITEMS':
            return {...state, tripItems: action.tripItems};
        case 'SET_TRIP_SETUP':
            return {
                ...state,
                tripTravelers: action.travelers,
                tripStartDate: action.startDate,
                tripEndDate: action.endDate,
                tripSetupModalOpen: false,
                tripBuilderModalOpen: true
            };
        case 'UPDATE_TRIP_TRAVELERS':
            return {...state, tripTravelers: action.travelers};
        case 'UPDATE_TRIP_DATES':
            return {...state, tripStartDate: action.startDate, tripEndDate: action.endDate};
        case 'UPDATE_TRIP_BUDGET':
            return {...state, tripBudget: action.budget};
        case 'CLOSE_TRIP_SETUP_MODAL':
            return {...state, tripSetupModalOpen: false};
        case 'CANCEL_TRIP_SETUP':
            return {...state, tripItems: [], tripBudget: null, tripSetupModalOpen: false};
        case 'ADD_PACKAGE_TO_TRIP': {
            const pkg = action.pkg;
            const newItems = pkg.activities.map(a => ({
                id: a.activityId,
                name: a.name,
                price: a.price,
                imageUrl: a.imageUrl,
                duration: a.duration,
                destinationSlug: pkg.destinationSlug,
                packageId: pkg.id,
                packageName: pkg.name,
                packageDiscountPct: pkg.discountPct,
            }));
            // Remove any standalone copies of activities now part of this package.
            const without = state.tripItems.filter(i => !newItems.some(n => n.id === i.id));
            const isFirstAdd = state.tripItems.length === 0;
            return {
                ...state,
                tripItems: [...without, ...newItems],
                tripSetupModalOpen: isFirstAdd,
                tripBuilderModalOpen: !isFirstAdd || state.tripBuilderModalOpen,
            };
        }
        case 'REMOVE_PACKAGE_FROM_TRIP':
            return {...state, tripItems: state.tripItems.filter(i => i.packageId !== action.packageId)};
        default:
            return state;
    }
}

export function TripProvider({children}) {
    const [state, dispatch] = useReducer(reducer, initialState, (init) => {
        let {tripItems, tripTravelers, tripStartDate, tripEndDate, tripBudget} = init;
        try {
            const saved = localStorage.getItem('myhive-trip-items');
            if (saved) {
                tripItems = JSON.parse(saved);
            }
        } catch (e) { /* ignore corrupt storage */ }
        try {
            const saved = localStorage.getItem('myhive-trip-setup');
            if (saved) {
                const setup = JSON.parse(saved);
                tripTravelers = setup.travelers || 1;
                tripStartDate = setup.startDate || '';
                tripEndDate = setup.endDate || '';
                tripBudget = setup.budget ?? null;
            }
        } catch (e) { /* ignore corrupt storage */ }
        return {...init, tripItems, tripTravelers, tripStartDate, tripEndDate, tripBudget};
    });

    useEffect(() => {
        localStorage.setItem('myhive-trip-items', JSON.stringify(state.tripItems));
    }, [state.tripItems]);

    useEffect(() => {
        localStorage.setItem('myhive-trip-setup', JSON.stringify({
            travelers: state.tripTravelers,
            startDate: state.tripStartDate,
            endDate: state.tripEndDate,
            budget: state.tripBudget
        }));
    }, [state.tripTravelers, state.tripStartDate, state.tripEndDate, state.tripBudget]);

    return (
        <TripContext.Provider value={{state, dispatch}}>
            {children}
        </TripContext.Provider>
    );
}

export function useTrip() {
    const context = useContext(TripContext);
    if (context === undefined) {
        throw new Error('useTrip must be used within a TripProvider');
    }
    return context;
}
```

- [ ] **Step 4: Run to verify pass**

Run: `CI=true npx react-scripts test --watchAll=false --testPathPattern TripContext`
Expected: PASS (all ported trip tests).

- [ ] **Step 5: Commit**

```bash
git add src/context/TripContext.js src/context/TripContext.test.js
git commit -F - <<'EOF'
feat: TripContext (trip data + trip modal flags + persistence) extracted

Reducer tests ported from AppContext.test.js (trip cluster only).

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
```

---

### Task 3: DestinationModalContext

**Files:**
- Create: `src/context/DestinationModalContext.js`
- Test: `src/context/DestinationModalContext.test.js`

- [ ] **Step 1: Write the failing test** — `src/context/DestinationModalContext.test.js`:

```js
import {initialState, reducer} from './DestinationModalContext';

describe('destination modal reducer', () => {
    it('OPEN_DESTINATION_MODAL opens with the selected destination', () => {
        const destination = {id: 'd1', name: 'Prague'};
        const state = reducer(initialState, {type: 'OPEN_DESTINATION_MODAL', destination});
        expect(state.destinationModalOpen).toBe(true);
        expect(state.selectedDestination).toEqual(destination);
    });

    it('CLOSE_DESTINATION_MODAL clears open flag and selection', () => {
        const open = {destinationModalOpen: true, selectedDestination: {id: 'd1'}};
        const state = reducer(open, {type: 'CLOSE_DESTINATION_MODAL'});
        expect(state.destinationModalOpen).toBe(false);
        expect(state.selectedDestination).toBeNull();
    });

    it('defaults: closed with no selection', () => {
        expect(initialState.destinationModalOpen).toBe(false);
        expect(initialState.selectedDestination).toBeNull();
    });
});
```

- [ ] **Step 2: Run to verify failure**

Run: `CI=true npx react-scripts test --watchAll=false --testPathPattern DestinationModalContext`
Expected: FAIL — module not found.

- [ ] **Step 3: Create `src/context/DestinationModalContext.js`:**

```jsx
import {createContext, useContext, useReducer} from 'react';

export const DestinationModalContext = createContext();

export const initialState = {
    destinationModalOpen: false,
    selectedDestination: null,
};

export function reducer(state, action) {
    switch (action.type) {
        case 'OPEN_DESTINATION_MODAL':
            return {...state, destinationModalOpen: true, selectedDestination: action.destination};
        case 'CLOSE_DESTINATION_MODAL':
            return {...state, destinationModalOpen: false, selectedDestination: null};
        default:
            return state;
    }
}

export function DestinationModalProvider({children}) {
    const [state, dispatch] = useReducer(reducer, initialState);
    return (
        <DestinationModalContext.Provider value={{state, dispatch}}>
            {children}
        </DestinationModalContext.Provider>
    );
}

export function useDestinationModal() {
    const context = useContext(DestinationModalContext);
    if (context === undefined) {
        throw new Error('useDestinationModal must be used within a DestinationModalProvider');
    }
    return context;
}
```

- [ ] **Step 4: Run to verify pass**

Run: `CI=true npx react-scripts test --watchAll=false --testPathPattern DestinationModalContext`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/context/DestinationModalContext.js src/context/DestinationModalContext.test.js
git commit -F - <<'EOF'
feat: DestinationModalContext (coming-soon modal) extracted from AppContext

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
```

---

### Task 4: AppProviders composition

**Files:**
- Create: `src/context/AppProviders.js`

- [ ] **Step 1: Create `src/context/AppProviders.js`:**

```jsx
import {CatalogProvider} from './CatalogContext';
import {TripProvider} from './TripContext';
import {DestinationModalProvider} from './DestinationModalContext';

// Composes the three focused contexts that replace the former single
// AppContext. Order is arbitrary — the three are independent.
export function AppProviders({children}) {
    return (
        <CatalogProvider>
            <TripProvider>
                <DestinationModalProvider>
                    {children}
                </DestinationModalProvider>
            </TripProvider>
        </CatalogProvider>
    );
}
```

- [ ] **Step 2: Run the full suite (nothing wired yet, just confirm no breakage)**

Run: `CI=true npx react-scripts test --watchAll=false`
Expected: PASS (the file is additive; the app still uses AppContext).

- [ ] **Step 3: Commit**

```bash
git add src/context/AppProviders.js
git commit -F - <<'EOF'
feat: AppProviders composing Catalog, Trip and DestinationModal providers

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
```

---

### Task 5: Atomic cutover

One commit: repoint `App.js`, migrate every consumer + ChatPanel, re-wrap the integration tests, delete the old context. Action-type strings are unchanged, so `dispatch({type: '...'})` calls stay; only the hook providing `state`/`dispatch` changes. After each consumer edit, if `useContext` is no longer used in that file, drop it from the React import.

**Files:** `src/App.js`; 16 consumers; `src/components/ChatPanel.js`; 5 test files; delete `src/context/AppContext.js` + `src/context/AppContext.test.js`.

- [ ] **Step 1: `src/App.js`**

Replace `import {AppProvider} from './context/AppContext';` with:
```js
import {AppProviders} from './context/AppProviders';
```
Replace the `<AppProvider><Layout/></AppProvider>` element with:
```jsx
                        <AppProviders>
                            <Layout/>
                        </AppProviders>
```

- [ ] **Step 2: Trip-only consumers** — in each file, change the import and the `useContext(AppContext)` line as below (drop `useContext` from the React import if now unused):

  - `src/components/ActivityCard.js`: import → `import {useTrip} from '../context/TripContext';`; line → `const {dispatch} = useTrip();`
  - `src/pages/ActivityDetailPage.js`: import → `import {useTrip} from '../context/TripContext';`; line → `const {state, dispatch} = useTrip();`
  - `src/pages/PackageDetailPage.js`: import → `import {useTrip} from '../context/TripContext';`; line → `const {dispatch} = useTrip();`
  - `src/components/TripBuilder.js`: import → `import {useTrip} from '../context/TripContext';`; line → `const {state, dispatch} = useTrip();`
  - `src/components/TripBuilderDropdown.js`: import → `import {useTrip} from '../context/TripContext';`; line → `const {state, dispatch} = useTrip();`
  - `src/pages/vote/CuratePage.js`: import → `import {useTrip} from '../../context/TripContext';`; line → `const {dispatch} = useTrip();`
  - `src/pages/vote/VoteResultPage.js`: import → `import {useTrip} from '../../context/TripContext';`; line → `const {state, dispatch} = useTrip();`

- [ ] **Step 3: DestinationModal-only consumers**

  - `src/components/Layout.js`: import → `import {useDestinationModal} from '../context/DestinationModalContext';`; line → `const {state, dispatch} = useDestinationModal();` (Layout reads `state.destinationModalOpen` / `state.selectedDestination` and dispatches `CLOSE_DESTINATION_MODAL` — all destination-modal.)
  - `src/components/DestinationCard.js`: import → `import {useDestinationModal} from '../context/DestinationModalContext';`; line → `const {dispatch} = useDestinationModal();`

- [ ] **Step 4: Catalog-only consumer**

  - `src/pages/DestinationPage.js`: import → `import {useCatalog} from '../context/CatalogContext';`; line → `const {state} = useCatalog();` (reads `state.destinations` only.)

- [ ] **Step 5: Dual-context consumers** — take two hooks; alias catalog state; change the catalog-field reads; keep trip reads/dispatch on the unaliased `state`/`dispatch`:

  - `src/components/Header.js`:
    - import → `import {useCatalog} from '../context/CatalogContext';` and `import {useTrip} from '../context/TripContext';`
    - line → `const {state: catalog} = useCatalog();` then `const {state, dispatch} = useTrip();`
    - change the single `state.destinations` read (in the `destination` lookup) to `catalog.destinations`. `state.tripBuilderModalOpen` and `state.tripItems` stay (trip).
  - `src/components/TripSetupModal.js`:
    - import → add `import {useCatalog} from '../context/CatalogContext';` and `import {useTrip} from '../context/TripContext';` (replacing the AppContext import)
    - line → `const {state: catalog} = useCatalog();` then `const {state, dispatch} = useTrip();`
    - change `state.destinations` → `catalog.destinations`, `state.loading` → `catalog.loading`, `state.error` → `catalog.error`. `state.tripSetupModalOpen` stays (trip).
  - `src/components/home/FeaturedActivitiesSection.js`:
    - import → `import {useCatalog} from '../../context/CatalogContext';` and `import {useTrip} from '../../context/TripContext';`
    - line → `const {state: catalog} = useCatalog();` then `const {state: trip} = useTrip();`
    - change `state.destinations` → `catalog.destinations`, `state.tripItems` → `trip.tripItems`.
  - `src/hooks/useStartGroupVote.js`:
    - import → `import {useCatalog} from '../context/CatalogContext';` and `import {useTrip} from '../context/TripContext';`
    - line → `const {state: catalog} = useCatalog();` then `const {state, dispatch} = useTrip();`
    - change `state.destinations` → `catalog.destinations`. `state.tripItems` and `dispatch` stay (trip).

- [ ] **Step 6: Localize chat — replace `src/components/ChatPanel.js` entirely with:**

```jsx
import {useEffect, useReducer, useRef, useState} from 'react';
import {useLocation} from 'react-router-dom';
import {useTrip} from '../context/TripContext';
import './ChatPanel.css';

const initialChatState = {
  chatOpen: false,
  chatMessages: [
    {sender: 'ai', text: 'Hi! I\'m your AI travel assistant. What type of trip are you looking for?'}
  ],
  autoEngaged: false,
};

function chatReducer(state, action) {
  switch (action.type) {
    case 'TOGGLE_CHAT':
      return {...state, chatOpen: !state.chatOpen};
    case 'SET_AUTO_ENGAGED':
      return {...state, autoEngaged: action.value};
    case 'ADD_CHAT_MESSAGE':
      return {...state, chatMessages: [...state.chatMessages, action.message]};
    default:
      return state;
  }
}

function ChatPanel() {
  const [state, dispatch] = useReducer(chatReducer, initialChatState);
  const {state: trip} = useTrip();
  const location = useLocation();
  const currentTab = new URLSearchParams(location.search).get('tab') || 'activities';
  const [inputValue, setInputValue] = useState('');
  const messagesEndRef = useRef(null);
  const replyTimeoutsRef = useRef(new Set());

  // Clear pending canned replies if the panel unmounts mid-conversation.
  useEffect(() => {
    const timeouts = replyTimeoutsRef.current;
    return () => timeouts.forEach(clearTimeout);
  }, []);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [state.chatMessages]);

  useEffect(() => {
    if (!state.chatOpen && !state.autoEngaged) {
      const timeout = setTimeout(() => {
        dispatch({
          type: 'ADD_CHAT_MESSAGE',
          message: {
            sender: 'ai',
            text: 'Planning a stag do? Tell me what your group is into and I\'ll point you to the right activities!'
          }
        });
        dispatch({type: 'SET_AUTO_ENGAGED', value: true});
      }, 3000);
      return () => clearTimeout(timeout);
    }
  }, [state.chatOpen, state.autoEngaged, dispatch]);

  const handleSendMessage = () => {
    if (inputValue.trim()) {
      dispatch({
        type: 'ADD_CHAT_MESSAGE',
        message: { sender: 'user', text: inputValue }
      });

      // Simulate AI response. Each message gets its own timeout — sending a
      // second message quickly must not cancel the first reply.
      const id = setTimeout(() => {
        replyTimeoutsRef.current.delete(id);
        dispatch({
          type: 'ADD_CHAT_MESSAGE',
          message: {
            sender: 'ai',
            text: getAIResponse(inputValue, currentTab)
          }
        });
      }, 1000);
      replyTimeoutsRef.current.add(id);

      setInputValue('');
    }
  };

  // Canned responses are destination-agnostic on purpose — the catalog
  // changes per destination, so naming specific activities here goes stale.
  const getAIResponse = (input, tab) => {
    const userMessageLower = input.toLowerCase();
    let aiResponse = "I can help you plan the perfect group getaway! What interests you most?";

    if (userMessageLower.includes('party') || userMessageLower.includes('nightlife') || userMessageLower.includes('club')) {
      aiResponse = "For epic nightlife, check the Nightlife category in Activities — bar crawls, club entries and boat parties are group favourites.";
    } else if (userMessageLower.includes('adventure') || userMessageLower.includes('active')) {
      aiResponse = "Adventure awaits! Browse the Adventure category for go-karting, water sports and adrenaline activities your group can do together.";
    } else if (userMessageLower.includes('beach') || userMessageLower.includes('relax') || userMessageLower.includes('spa')) {
      aiResponse = "Perfect for unwinding — look for spa, beach club and relaxed daytime activities to balance out the big nights.";
    } else if (userMessageLower.includes('package') || userMessageLower.includes('deal')) {
      aiResponse = "Our packages bundle several activities at a discount — check the Packages tab on the destination page and customize any of them in the Trip Builder!";
    } else if (userMessageLower.includes('suggest') || userMessageLower.includes('recommend')) {
      if (tab === 'packages') {
        aiResponse = "Packages are the easiest start — a ready-made mix of activities at a discount that you can still customize in the Trip Builder!";
      } else if (tab === 'trip-builder') {
        aiResponse = `You have ${trip.tripItems.length} activities selected. ${trip.tripItems.length < 2 ? 'Consider adding more for a full experience!' : 'This looks like a great balanced trip!'}`;
      } else {
        aiResponse = "Start with the featured activities on the home page — they're the most popular with groups. Add anything you like to the Trip Builder!";
      }
    } else if (userMessageLower.includes('price') || userMessageLower.includes('cost') || userMessageLower.includes('budget')) {
      aiResponse = "Prices are shown per person on every activity card, and packages give you a discount for booking several activities together. The Trip Builder totals everything for your group size.";
    } else if (userMessageLower.includes('group') || userMessageLower.includes('friends')) {
      aiResponse = "Everything here is built for groups! You can even start a group vote from the Trip Builder so the whole crew picks the activities together.";
    } else if (userMessageLower.includes('hi') || userMessageLower.includes('hello') || userMessageLower.includes('help')) {
      aiResponse = "Hello! I'm here to help you plan the perfect trip. Are you looking for adventure, nightlife, relaxation, or a mix of everything?";
    }

    return aiResponse;
  };

  return (
      <>
        <div className={`chat-panel ${state.chatOpen ? 'open' : ''}`}>
          <div className="chat-header">
            <h3 className="chat-title">AI Travel Assistant</h3>
            <button
                type="button"
                className="chat-close-btn"
                aria-label="Close chat"
                onClick={() => dispatch({type: 'TOGGLE_CHAT'})}
            >
              ×
            </button>
          </div>
          <div className="chat-messages">
            {state.chatMessages.map((msg, i) => (
                <div key={i} className={`chat-message ${msg.sender}`}>
                  <div className="chat-avatar">{msg.sender === 'ai' ? 'AI' : 'You'}</div>
                  <div className="chat-bubble">{msg.text}</div>
                </div>
            ))}
            <div ref={messagesEndRef}/>
          </div>
          <div className="chat-input-container">
            <input
                type="text"
                className="chat-input"
                aria-label="Chat message"
                value={inputValue}
                onChange={(e) => setInputValue(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleSendMessage()}
                placeholder="Tell me about your ideal trip..."
            />
            <button
                type="button"
                className="chat-send-btn"
                aria-label="Send message"
                onClick={handleSendMessage}
                disabled={!inputValue.trim()}
            >
              Send
            </button>
          </div>
        </div>
        <button
            type="button"
            className="chat-trigger-btn"
            aria-label="Open chat assistant"
            onClick={() => dispatch({type: 'TOGGLE_CHAT'})}
        >🤖</button>
      </>
  );
}

export default ChatPanel;
```

- [ ] **Step 7: Re-wrap the 5 integration tests** — replace the `AppContext` import and the single `<AppContext.Provider value={{state, dispatch}}>` wrapper with the focused provider(s) the rendered tree consumes, passing the SAME state object to each (the existing state objects are supersets, so each provider takes the whole object and each consumer reads its own fields):

  - `src/pages/DestinationPage.test.js`: import `CatalogContext` from `../context/CatalogContext` and `TripContext` from `../context/TripContext`. Wrap (inside `HelmetProvider`):
    ```jsx
    <CatalogContext.Provider value={{state: baseState, dispatch: jest.fn()}}>
      <TripContext.Provider value={{state: baseState, dispatch: jest.fn()}}>
        ...existing children (MemoryRouter etc.)...
      </TripContext.Provider>
    </CatalogContext.Provider>
    ```
  - `src/pages/HomePage.test.js`: import `CatalogContext`, `TripContext`. Replace the `AppContext.Provider` wrapper with nested `CatalogContext.Provider` + `TripContext.Provider`, both `value={{state, dispatch: jest.fn()}}`.
  - `src/pages/vote/CuratePage.test.js`: import `TripContext` from `../../context/TripContext`. CuratePage consumes only `useTrip`. In BOTH wrappers (the `renderWith` helper and the standalone `render` near the bottom), replace `<AppContext.Provider value={{ state: { tripItems: [] }, dispatch }}>` with `<TripContext.Provider value={{ state: { tripItems: [] }, dispatch }}>` (and the `jest.fn()` variant likewise).
  - `src/hooks/useStartGroupVote.test.js`: import `CatalogContext`, `TripContext`. Replace `<AppContext.Provider value={{ state, dispatch }}>` with nested `<CatalogContext.Provider value={{ state, dispatch }}><TripContext.Provider value={{ state, dispatch }}>...` (the harness `state` has both `tripItems` and `destinations`).
  - `src/components/TripSetupModal.test.js`: import `CatalogContext`, `TripContext`. Replace `<AppContext.Provider value={{ state, dispatch: jest.fn() }}>` with nested `<CatalogContext.Provider value={{ state, dispatch: jest.fn() }}><TripContext.Provider value={{ state, dispatch: jest.fn() }}>...`.

  If any test throws a `useX must be used within` error, the rendered tree also needs that provider — wrap it too (seeded with the same state object).

- [ ] **Step 8: Delete the old context + its test**

```bash
git rm src/context/AppContext.js src/context/AppContext.test.js
```

- [ ] **Step 9: Run the full suite**

Run: `CI=true npx react-scripts test --watchAll=false`
Expected: all suites PASS. (If a test errors with `useX must be used within a XProvider`, add the missing provider to that test's wrapper per Step 7.)

- [ ] **Step 10: Build — confirm no dangling imports / unused `useContext` lint**

Run: `CI=true npm run build`
Expected: `Compiled successfully.` with no eslint warnings (ignore browserslist/deprecation notices). A warning here usually means a leftover `useContext` import or a missed `AppContext` reference — fix and rebuild.

- [ ] **Step 11: Confirm no AppContext references remain**

Run: `git -C C:/Users/dijtb/IdeaProjects/myhive-travel-app grep -n "AppContext" -- myhive-react-app/src || echo "clean"`
Expected: `clean` (no matches).

- [ ] **Step 12: Commit**

```bash
git add -A
git commit -F - <<'EOF'
refactor: split AppContext into Catalog, Trip and DestinationModal contexts

Repoint App.js to AppProviders, migrate every consumer to the focused
useCatalog/useTrip/useDestinationModal hooks (action strings unchanged),
localize the dead chat cluster into ChatPanel's own reducer, re-wrap the
integration tests onto the focused providers, and delete AppContext.
Pure behavior-preserving refactor.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
```

---

## Final verification

- [ ] Full suite green: `CI=true npx react-scripts test --watchAll=false`.
- [ ] Clean build: `CI=true npm run build` → `Compiled successfully.`, no eslint warnings.
- [ ] `git grep AppContext -- myhive-react-app/src` returns nothing; `AppContext.js` and `AppContext.test.js` are gone.
- [ ] Multi-angle code review of the branch diff; fix findings.

## Out of scope (carry-over note)

No API redesign (kept `{state, dispatch}` + existing action strings). `ChatPanel` is localized but left in place (still unmounted; deleting dead components is a separate decision). The final Phase-4 sub-project (CRA → Vite) gets its own spec and plan.
