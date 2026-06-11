# Frontend Review Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the user-facing bugs and systemic weaknesses found in the 2026-06-11 frontend code review of `myhive-react-app`.

**Architecture:** Extract three shared units (trip-pricing selector, `useFetchBySlug` hook, `saveError` channel in `useAdminCrud`) and apply them to the components that currently carry copy-pasted, drifted logic. Then fix isolated bugs (vote-status polling, modal validation, auth robustness) and clean up dead code/deps/bundle.

**Tech Stack:** React 19, Create React App (react-scripts 5), react-router 7, react-bootstrap, Jest + React Testing Library (`@testing-library/user-event` v13 — sync API).

**Conventions (from CLAUDE.md):** no wildcard imports; braces always; K&R style. Frontend tests run with `npm test -- --watchAll=false <pattern>` from `myhive-react-app/`. All paths below are relative to `myhive-react-app/` unless stated otherwise.

---

### Task 1: Shared trip-pricing selector (fixes the discount-mismatch bug)

The package-grouping + discounted-total logic is duplicated byte-for-byte in `TripBuilder.js:172-200` and `TripBuilderDropdown.js:17-46`, and a third *divergent* copy in `ContactForm.js:84-90` ignores package discounts entirely — the booking modal shows a higher total than the itinerary next to it.

**Files:**
- Create: `src/utils/tripPricing.js`
- Create: `src/utils/tripPricing.test.js`
- Modify: `src/components/TripBuilder.js:172-200`
- Modify: `src/components/TripBuilderDropdown.js:17-46`
- Modify: `src/components/ContactForm.js:84-90,106`

- [ ] **Step 1: Write the failing test**

Create `src/utils/tripPricing.test.js`:

```js
import {computeTripTotal, groupTripItems} from './tripPricing';

describe('groupTripItems', () => {
    test('splits standalone items from package groups', () => {
        const items = [
            {id: 'a1', price: 100},
            {id: 'a2', price: 50, packageId: 'p1', packageName: 'Combo', packageDiscountPct: 10},
            {id: 'a3', price: 30, packageId: 'p1', packageName: 'Combo', packageDiscountPct: 10},
        ];
        const {standalone, groups} = groupTripItems(items);
        expect(standalone).toHaveLength(1);
        expect(groups).toHaveLength(1);
        expect(groups[0].packageName).toBe('Combo');
        expect(groups[0].packageDiscountPct).toBe(10);
        expect(groups[0].items).toHaveLength(2);
    });
});

describe('computeTripTotal', () => {
    test('multiplies standalone prices by travelers', () => {
        const expectedTotal = 300;
        expect(computeTripTotal([{id: 'a1', price: 100}], 3)).toBe(expectedTotal);
    });

    test('applies package discount to grouped items', () => {
        const items = [
            {id: 'a2', price: 50, packageId: 'p1', packageDiscountPct: 10},
            {id: 'a3', price: 30, packageId: 'p1', packageDiscountPct: 10},
        ];
        // (50 + 30) × 2 travelers = 160, minus 10% = 144
        const expectedTotal = 144;
        expect(computeTripTotal(items, 2)).toBe(expectedTotal);
    });

    test('mixed cart: standalone at full price plus discounted package', () => {
        const items = [
            {id: 'a1', price: 100},
            {id: 'a2', price: 50, packageId: 'p1', packageDiscountPct: 20},
        ];
        // 100 + 50 × 0.8 = 140
        const expectedTotal = 140;
        expect(computeTripTotal(items, 1)).toBe(expectedTotal);
    });

    test('non-numeric prices count as zero', () => {
        expect(computeTripTotal([{id: 'a1', price: null}], 2)).toBe(0);
    });

    test('rounds to cents', () => {
        const items = [{id: 'a1', price: 33.335, packageId: 'p1', packageDiscountPct: 10}];
        expect(computeTripTotal(items, 1)).toBe(30);
    });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run (from `myhive-react-app/`): `npm test -- --watchAll=false tripPricing`
Expected: FAIL — `Cannot find module './tripPricing'`

- [ ] **Step 3: Implement `src/utils/tripPricing.js`**

```js
export function groupTripItems(tripItems) {
    const standalone = tripItems.filter(i => !i.packageId);
    const packageGroups = tripItems.reduce((acc, item) => {
        if (!item.packageId) {
            return acc;
        }
        if (!acc[item.packageId]) {
            acc[item.packageId] = {
                packageId: item.packageId,
                packageName: item.packageName,
                packageDiscountPct: Number(item.packageDiscountPct) || 0,
                destinationSlug: item.destinationSlug,
                items: [],
            };
        }
        acc[item.packageId].items.push(item);
        return acc;
    }, {});
    return {standalone, groups: Object.values(packageGroups)};
}

export function computeTripTotal(tripItems, travelers) {
    const {standalone, groups} = groupTripItems(tripItems);
    let total = 0;
    standalone.forEach(it => {
        total += (Number(it.price) || 0) * travelers;
    });
    groups.forEach(g => {
        const sub = g.items.reduce((s, it) => s + (Number(it.price) || 0) * travelers, 0);
        total += sub * (100 - g.packageDiscountPct) / 100;
    });
    return Math.round(total * 100) / 100;
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm test -- --watchAll=false tripPricing`
Expected: PASS (5 tests)

- [ ] **Step 5: Use the selector in `TripBuilder.js`**

Add to imports: `import {computeTripTotal, groupTripItems} from '../utils/tripPricing';`

Replace lines 172-200 (from `const standalone = state.tripItems.filter(...)` through the `totalPrice` IIFE) with:

```js
  const {standalone, groups: groupsArray} = groupTripItems(state.tripItems);
  const totalPrice = computeTripTotal(state.tripItems, travelers);
```

The rest of the component already uses `standalone`, `groupsArray`, `totalPrice` — no other changes.

- [ ] **Step 6: Use the selector in `TripBuilderDropdown.js`**

Add to imports: `import {computeTripTotal, groupTripItems} from '../utils/tripPricing';`

Replace lines 17-46 (same duplicated block) with:

```js
    const {standalone, groups: groupsArray} = groupTripItems(state.tripItems);
    const totalPrice = computeTripTotal(state.tripItems, travelers);
```

- [ ] **Step 7: Fix `ContactForm.js` to use the discounted total**

Add to imports: `import {computeTripTotal} from '../utils/tripPricing';`

Delete the `calculateTotalPrice` function (lines 84-90) and change line 106 from:

```jsx
<p><strong>Estimated Total:</strong> €{calculateTotalPrice().toFixed(2)}</p>
```

to:

```jsx
<p><strong>Estimated Total:</strong> €{computeTripTotal(tripData.tripItems, Number(formData.numberOfTravelers) || 1).toFixed(2)}</p>
```

- [ ] **Step 8: Run the full test suite**

Run: `npm test -- --watchAll=false`
Expected: PASS (all suites — TripSetupModal/AppContext tests must not regress)

- [ ] **Step 9: Commit**

```bash
git add myhive-react-app/src/utils/tripPricing.js myhive-react-app/src/utils/tripPricing.test.js myhive-react-app/src/components/TripBuilder.js myhive-react-app/src/components/TripBuilderDropdown.js myhive-react-app/src/components/ContactForm.js
git commit -m "fix: apply package discounts in booking-modal total via shared tripPricing util"
```

---

### Task 2: Admin save errors visible inside the modal

`useAdminCrud.handleSave` writes failures into the page-level `error`, but the page-level `Alert` renders *behind* the open modal backdrop — a failed Create/Save looks like a silent no-op. Introduce a separate `saveError` rendered inside each modal.

**Files:**
- Modify: `src/hooks/useAdminCrud.js`
- Create: `src/hooks/useAdminCrud.test.js`
- Modify: `src/pages/AdminActivities.js` (Modal.Body at line 224, ImageUploadField at 336-352)
- Modify: `src/pages/AdminDestinations.js` (same pattern, modal ~line 198)
- Modify: `src/pages/AdminPackages.js` (same pattern, modal ~line 173)
- Modify: `src/pages/AdminBlog.js` (same pattern, modal ~line 125)
- Modify: `src/pages/AdminCategories.js` (same pattern, modal ~line 133; no image upload)

- [ ] **Step 1: Write the failing hook test**

Create `src/hooks/useAdminCrud.test.js`:

```js
import {act, renderHook, waitFor} from '@testing-library/react';
import {useAdminCrud} from './useAdminCrud';

jest.mock('./useAdminApi', () => ({useAdminApi: () => ({})}));
jest.mock('./useAuthErrorHandler', () => ({useAuthErrorHandler: () => () => false}));

function renderCrud(overrides = {}) {
    return renderHook(() => useAdminCrud({
        emptyForm: {name: ''},
        fetchFn: jest.fn().mockResolvedValue({content: [], totalPages: 0, totalElements: 0}),
        createFn: jest.fn(),
        updateFn: jest.fn(),
        deleteFn: jest.fn(),
        mapItemToForm: (item) => item,
        ...overrides,
    }));
}

test('failed save sets saveError and keeps the modal open', async () => {
    const expectedMessage = 'Slug already exists';
    const {result} = renderCrud({
        createFn: jest.fn().mockRejectedValue(new Error(expectedMessage)),
    });
    await waitFor(() => expect(result.current.loading).toBe(false));

    act(() => result.current.openCreate());
    await act(() => result.current.handleSave());

    expect(result.current.saveError).toBe(expectedMessage);
    expect(result.current.showModal).toBe(true);
});

test('reopening the modal clears a previous saveError', async () => {
    const {result} = renderCrud({
        createFn: jest.fn().mockRejectedValue(new Error('boom')),
    });
    await waitFor(() => expect(result.current.loading).toBe(false));

    act(() => result.current.openCreate());
    await act(() => result.current.handleSave());
    expect(result.current.saveError).toBe('boom');

    act(() => result.current.openCreate());
    expect(result.current.saveError).toBe('');
});

test('successful save closes the modal and refetches', async () => {
    const fetchFn = jest.fn().mockResolvedValue({content: [], totalPages: 0, totalElements: 0});
    const createFn = jest.fn().mockResolvedValue({});
    const {result} = renderCrud({fetchFn, createFn});
    await waitFor(() => expect(result.current.loading).toBe(false));

    act(() => result.current.openCreate());
    await act(() => result.current.handleSave());

    expect(result.current.saveError).toBe('');
    expect(result.current.showModal).toBe(false);
    expect(fetchFn).toHaveBeenCalledTimes(2);
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm test -- --watchAll=false useAdminCrud`
Expected: FAIL — `result.current.saveError` is `undefined`

- [ ] **Step 3: Add `saveError` to the hook**

In `src/hooks/useAdminCrud.js`:

After line 28 (`const [saving, setSaving] = useState(false);`) add:

```js
    const [saveError, setSaveError] = useState('');
```

In `openCreate` and `openEdit`, add `setSaveError('');` as the first line of each.

In `handleSave`, replace `setError('');` with `setSaveError('');` and in the catch replace `setError(err.message || 'Failed to save.');` with `setSaveError(err.message || 'Failed to save.');`

In the returned object, after `saving,` add:

```js
        setSaving,
        saveError,
        setSaveError,
```

(`setSaving` is exported for Task 7's custom delete handlers.)

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm test -- --watchAll=false useAdminCrud`
Expected: PASS (3 tests)

- [ ] **Step 5: Render `saveError` inside the AdminActivities modal**

In `src/pages/AdminActivities.js`:

1. Add `saveError, setSaveError,` to the destructuring of `useAdminCrud(...)` (after `saving,`).
2. Immediately after `<Modal.Body data-bs-theme="dark">` (line 224), insert:

```jsx
                    {saveError && (
                        <Alert variant="danger" dismissible onClose={() => setSaveError('')}>{saveError}</Alert>
                    )}
```

3. In the `ImageUploadField` props (lines 336-352): replace `setError('');` with `setSaveError('');` in `onUpload`, and in `onError` replace `setError(err.message || 'Failed to upload image');` with `setSaveError(err.message || 'Failed to upload image');`

- [ ] **Step 6: Apply the same pattern to the other four admin pages**

For each of `src/pages/AdminDestinations.js`, `src/pages/AdminPackages.js`, `src/pages/AdminBlog.js`, `src/pages/AdminCategories.js` (Read each file first — structure mirrors AdminActivities):

1. Add `saveError, setSaveError,` to the `useAdminCrud(...)` destructuring.
2. Insert the identical `{saveError && (<Alert ...>...)}` block as the first child of the edit/create `<Modal.Body>`.
3. Where the page has an `ImageUploadField` (Destinations, Packages, Blog — Categories has none), switch its `onUpload`'s `setError('')` and `onError`'s `setError(...)` calls to `setSaveError`, exactly as in Step 5.
4. Verify `Alert` is already imported from `react-bootstrap` in each file (it is — used for the page-level error); add it to the import if missing.

- [ ] **Step 7: Run the full test suite**

Run: `npm test -- --watchAll=false`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add myhive-react-app/src/hooks/useAdminCrud.js myhive-react-app/src/hooks/useAdminCrud.test.js myhive-react-app/src/pages/AdminActivities.js myhive-react-app/src/pages/AdminDestinations.js myhive-react-app/src/pages/AdminPackages.js myhive-react-app/src/pages/AdminBlog.js myhive-react-app/src/pages/AdminCategories.js
git commit -m "fix: surface admin save/upload errors inside the modal instead of behind it"
```

---

### Task 3: `useFetchBySlug` hook — kills the stale-error bug and races in three detail pages

`ActivityDetailPage`, `PackageDetailPage`, `BlogPostPage` copy-paste the same fetch block that (a) never resets `error` on slug change — one failure poisons all later navigations, and (b) has no stale-response guard. (`api` methods don't use `this`, so passing `api.getActivityBySlug` by reference is safe — verified.)

**Files:**
- Create: `src/hooks/useFetchBySlug.js`
- Create: `src/hooks/useFetchBySlug.test.js`
- Modify: `src/pages/ActivityDetailPage.js:14-24`
- Modify: `src/pages/PackageDetailPage.js:14-24`
- Modify: `src/pages/BlogPostPage.js:10-19`

- [ ] **Step 1: Write the failing test**

Create `src/hooks/useFetchBySlug.test.js`:

```js
import {renderHook, waitFor} from '@testing-library/react';
import {useFetchBySlug} from './useFetchBySlug';

test('exposes data after a successful fetch', async () => {
    const fetchFn = jest.fn().mockResolvedValue({name: 'Prague'});
    const {result} = renderHook(() => useFetchBySlug(fetchFn, 'prague'));
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.data).toEqual({name: 'Prague'});
    expect(result.current.error).toBe(false);
});

test('resets error when slug changes after a failure', async () => {
    const fetchFn = jest.fn()
        .mockRejectedValueOnce(new Error('boom'))
        .mockResolvedValueOnce({name: 'Prague'});
    const {result, rerender} = renderHook(({slug}) => useFetchBySlug(fetchFn, slug), {
        initialProps: {slug: 'bad'},
    });
    await waitFor(() => expect(result.current.error).toBe(true));

    rerender({slug: 'prague'});
    await waitFor(() => expect(result.current.data).toEqual({name: 'Prague'}));
    expect(result.current.error).toBe(false);
});

test('ignores a stale response that resolves after the slug changed', async () => {
    let resolveFirst;
    const fetchFn = jest.fn()
        .mockImplementationOnce(() => new Promise(res => {
            resolveFirst = res;
        }))
        .mockResolvedValueOnce({name: 'new'});
    const {result, rerender} = renderHook(({slug}) => useFetchBySlug(fetchFn, slug), {
        initialProps: {slug: 'old'},
    });

    rerender({slug: 'new'});
    await waitFor(() => expect(result.current.data).toEqual({name: 'new'}));

    resolveFirst({name: 'old'});
    // Give the stale promise a chance to (incorrectly) apply state
    await new Promise(res => setTimeout(res, 0));
    expect(result.current.data).toEqual({name: 'new'});
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm test -- --watchAll=false useFetchBySlug`
Expected: FAIL — `Cannot find module './useFetchBySlug'`

- [ ] **Step 3: Implement `src/hooks/useFetchBySlug.js`**

```js
import {useEffect, useState} from 'react';

// Generic fetch-by-route-param hook: resets error on param change and ignores
// responses that resolve after the param has already moved on.
// fetchFn must be referentially stable (module-level api methods qualify).
export function useFetchBySlug(fetchFn, slug) {
    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(false);

    useEffect(() => {
        let cancelled = false;
        setLoading(true);
        setError(false);
        fetchFn(slug)
            .then(result => {
                if (!cancelled) {
                    setData(result);
                }
            })
            .catch(() => {
                if (!cancelled) {
                    setError(true);
                }
            })
            .finally(() => {
                if (!cancelled) {
                    setLoading(false);
                }
            });
        return () => {
            cancelled = true;
        };
    }, [fetchFn, slug]);

    return {data, loading, error};
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm test -- --watchAll=false useFetchBySlug`
Expected: PASS (3 tests)

- [ ] **Step 5: Apply to `ActivityDetailPage.js`**

Replace lines 14-24 (the three `useState` calls and the `useEffect`) with:

```js
    const {data: activity, loading, error} = useFetchBySlug(api.getActivityBySlug, slug);
```

Add import: `import {useFetchBySlug} from '../hooks/useFetchBySlug';`
Remove now-unused `useEffect`/`useState` from the react import if nothing else in the file uses them (check first — the file may have other state).

- [ ] **Step 6: Apply to `PackageDetailPage.js`**

Replace lines 14-24 with:

```js
    const {data: pkg, loading, error} = useFetchBySlug(api.getPackageBySlug, slug);
```

Add the same hook import; prune unused react imports.

- [ ] **Step 7: Apply to `BlogPostPage.js`**

Replace lines 10-19 with:

```js
    const {data: post, loading, error} = useFetchBySlug(api.getBlogPostBySlug, slug);
```

Add the same hook import; prune unused react imports.

- [ ] **Step 8: Run the full test suite and verify no unused-import warnings**

Run: `npm test -- --watchAll=false`
Expected: PASS, no `no-unused-vars` warnings for the three pages.

- [ ] **Step 9: Commit**

```bash
git add myhive-react-app/src/hooks/useFetchBySlug.js myhive-react-app/src/hooks/useFetchBySlug.test.js myhive-react-app/src/pages/ActivityDetailPage.js myhive-react-app/src/pages/PackageDetailPage.js myhive-react-app/src/pages/BlogPostPage.js
git commit -m "fix: reset error state and guard stale responses in detail pages via useFetchBySlug"
```

---

### Task 4: DestinationPage — scoped errors, no full-page blank on filter, race guard

A failed "Show More"/filter click currently replaces the loaded page with "Destination not found" (`DestinationPage.js:84-105`), filter clicks blank the whole page via the global `loading`, `error` is never reset on slug change, and overlapping paged fetches can interleave two filters' results.

**Files:**
- Modify: `src/pages/DestinationPage.js`
- Create: `src/pages/DestinationPage.test.js`

- [ ] **Step 1: Write the failing test**

Create `src/pages/DestinationPage.test.js` (mirror the mocking style of `src/pages/HomePage.test.js` — Read it first; adjust the `jest.mock` call if HomePage does it differently):

```js
import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {HelmetProvider} from 'react-helmet-async';
import {AppContext} from '../context/AppContext';
import DestinationPage from './DestinationPage';
import api from '../services/api';

jest.mock('../services/api');

function renderPage() {
    return render(
        <HelmetProvider>
            <AppContext.Provider value={{state: {tripItems: []}, dispatch: jest.fn()}}>
                <MemoryRouter initialEntries={['/destination/prague']}>
                    <Routes>
                        <Route path="/destination/:slug" element={<DestinationPage/>}/>
                    </Routes>
                </MemoryRouter>
            </AppContext.Provider>
        </HelmetProvider>
    );
}

test('a failed filter change shows an inline error, not "Destination not found"', async () => {
    api.getDestinationBySlug.mockResolvedValue({id: 'd1', name: 'Prague', slug: 'prague'});
    api.getCategoriesForDestination.mockResolvedValue([{slug: 'food', name: 'food'}]);
    api.getPackagesByDestination.mockResolvedValue([]);
    api.getActivitiesPaged
        .mockResolvedValueOnce({content: [], totalElements: 0, last: true})
        .mockRejectedValueOnce(new Error('network'));

    renderPage();
    expect(await screen.findByRole('heading', {name: 'Prague'})).toBeInTheDocument();

    userEvent.click(screen.getByRole('button', {name: 'Food'}));

    expect(await screen.findByText(/couldn't load activities/i)).toBeInTheDocument();
    // The page itself must survive the list failure
    expect(screen.getByRole('heading', {name: 'Prague'})).toBeInTheDocument();
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm test -- --watchAll=false DestinationPage`
Expected: FAIL — after the filter click the page renders "Destination not found", so the inline error text is never found.

- [ ] **Step 3: Rework the fetch logic in `DestinationPage.js`**

1. Add `useRef` to the react import (line 1).
2. Add two states and a sequence ref after line 33:

```js
    const [listError, setListError] = useState(false);
    const [filterLoading, setFilterLoading] = useState(false);
    const requestSeqRef = useRef(0);
```

3. Replace `fetchActivitiesPage` (lines 35-50) with a latest-wins version:

```js
    const fetchActivitiesPage = useCallback(async (destinationId, pageNum, categorySlug, reset = false) => {
        const seq = ++requestSeqRef.current;
        const categoryParam = categorySlug === 'all' ? null : categorySlug;
        const data = await api.getActivitiesPaged(destinationId, {
            page: pageNum,
            size: PAGE_SIZE,
            categorySlug: categoryParam
        });
        if (seq !== requestSeqRef.current) {
            // A newer filter/page request started while this one was in flight.
            return;
        }
        setTotalElements(data.totalElements);
        setHasMore(!data.last);
        setPage(pageNum);
        if (reset) {
            setActivities(data.content);
        } else {
            setActivities(prev => [...prev, ...data.content]);
        }
    }, []);
```

4. Replace the main effect (lines 52-76) with a cancellation-aware version that also resets the error flags:

```js
  useEffect(() => {
    let cancelled = false;
    const fetchDestinationData = async () => {
      try {
        setLoading(true);
        setError(false);
        setListError(false);
        setCurrentFilter('all');
        const destData = await api.getDestinationBySlug(slug);
        const categoriesData = await api.getCategoriesForDestination(destData.id);
        if (cancelled) {
          return;
        }
        setDestination(destData);
        setCategories(categoriesData);
        await fetchActivitiesPage(destData.id, 0, 'all', true);
        try {
          const pkgData = await api.getPackagesByDestination(destData.id);
          if (!cancelled) {
            setPackages(pkgData);
          }
        } catch {
          if (!cancelled) {
            setPackages([]);
          }
        }
      } catch {
        if (!cancelled) {
          setError(true);
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };

    fetchDestinationData();
    return () => {
      cancelled = true;
    };
  }, [slug, fetchActivitiesPage]);
```

5. Replace `handleFilterChange` and `handleLoadMore` (lines 84-105) so they use the scoped states:

```js
    const handleFilterChange = async (filter) => {
        setCurrentFilter(filter);
        setListError(false);
        try {
            setFilterLoading(true);
            await fetchActivitiesPage(destination.id, 0, filter, true);
        } catch {
            setListError(true);
        } finally {
            setFilterLoading(false);
        }
    };

    const handleLoadMore = async () => {
        setListError(false);
        try {
            setLoadingMore(true);
            await fetchActivitiesPage(destination.id, page + 1, currentFilter);
        } catch {
            setListError(true);
        } finally {
            setLoadingMore(false);
        }
    };
```

6. In the JSX, just above `<div className="activities-grid">` (line 198), insert:

```jsx
        {listError && (
            <p className="text-error">Couldn't load activities. Please try again.</p>
        )}
        {filterLoading && (
            <p>Loading activities...</p>
        )}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm test -- --watchAll=false DestinationPage`
Expected: PASS

- [ ] **Step 5: Run the full suite and commit**

Run: `npm test -- --watchAll=false` — expected PASS, then:

```bash
git add myhive-react-app/src/pages/DestinationPage.js myhive-react-app/src/pages/DestinationPage.test.js
git commit -m "fix: scope DestinationPage filter/pagination errors and guard against stale responses"
```

---

### Task 5: VoteWaitingPage — detect session completion while waiting + honest clipboard

Status is checked once on mount; the 30s poll only reads the participant count, so participants are never redirected when the organizer closes the session. `getSession` already returns `participantCount`, so poll it instead. Also: `handleCopy` claims success even when the clipboard write rejects.

**Files:**
- Modify: `src/pages/vote/VoteWaitingPage.js:37-48,64-71,95-99`
- Modify (likely): `src/pages/vote/VoteWaitingPage.test.js` — Read it first; update its `voteApi` mocks from `getParticipantCount` to `getSession` polling.

- [ ] **Step 1: Replace the mount-only status check and the count-poll with one polling effect**

Delete both the effect at lines 37-48 (`voteApi.getSession(shareToken)...`) and the effect at lines 64-71 (`getParticipantCount` poll). In their place:

```js
    // Poll the full session (it includes participantCount) so we also notice
    // the organizer closing the session / the 24h expiry while we wait.
    useEffect(() => {
        let cancelled = false;
        const load = (initial) => voteApi.getSession(shareToken)
            .then(s => {
                if (cancelled) {
                    return;
                }
                setSession(s);
                setParticipantCount(s.participantCount);
                if (s.status === 'COMPLETED') {
                    if (s.destinationSlug) {
                        navigate(`/destination/${s.destinationSlug}?tab=trip-builder&voteSession=${shareToken}`,
                            { replace: true });
                    } else {
                        navigate(`/vote/${shareToken}/result`, { replace: true });
                    }
                }
            })
            .catch(e => {
                if (cancelled) {
                    return;
                }
                if (initial) {
                    setSessionError(e.message);
                } else {
                    // Transient poll failure — keep the page up, try again next tick.
                    console.error('session poll error:', e);
                }
            });
        load(true);
        const id = setInterval(() => load(false), 30_000);
        return () => {
            cancelled = true;
            clearInterval(id);
        };
    }, [shareToken, navigate]);
```

- [ ] **Step 2: Fix `handleCopy` (lines 95-99)**

```js
    const handleCopy = () => {
        navigator.clipboard.writeText(shareUrl)
            .then(() => {
                setCopied(true);
                setTimeout(() => setCopied(false), 2000);
            })
            .catch(() => {
                // Clipboard unavailable (insecure context / permission denied) —
                // the readonly input above stays visible for manual copying.
            });
    };
```

- [ ] **Step 3: Update `VoteWaitingPage.test.js`**

Read the existing test. Replace `getParticipantCount` mocks with `getSession` returning `{status: 'OPEN', participantCount: N, destinationName: ..., expiresAt: ...}`. Add one new test: when a poll returns `status: 'COMPLETED'` with a `destinationSlug`, the page navigates to `/destination/<slug>?tab=trip-builder&voteSession=<token>` (assert via a `<Routes>` harness or a mocked `useNavigate`, matching whatever style the existing test file already uses).

- [ ] **Step 4: Run tests and commit**

Run: `npm test -- --watchAll=false VoteWaitingPage` — expected PASS, then `npm test -- --watchAll=false` — expected PASS.

```bash
git add myhive-react-app/src/pages/vote/VoteWaitingPage.js myhive-react-app/src/pages/vote/VoteWaitingPage.test.js
git commit -m "fix: redirect vote-waiting participants when the session completes; honest copy feedback"
```

---

### Task 6: TripSetupModal — make validation actually run

The footer buttons live outside the `<form>` and call `handleConfirm` via `onClick`, so `required`/`type="email"` never fire — `"x"` passes as the email that receives vote results. An invalid budget silently no-ops.

**Files:**
- Modify: `src/components/TripSetupModal.js`
- Modify (check): `src/components/TripSetupModal.test.js` — confirm-button interactions still work via submit.

- [ ] **Step 1: Wire the footer button to the form**

1. Add state after line 15: `const [budgetError, setBudgetError] = useState('');` and add `setBudgetError('');` to the reset effect (lines 19-28).
2. Add a submit handler next to `handleConfirm`:

```js
    const handleSubmit = (e) => {
        e.preventDefault();
        handleConfirm();
    };
```

3. Change the `<form>` tag (line 82) to:

```jsx
                    <form id="trip-setup-form" className="contact-form" onSubmit={handleSubmit}>
```

4. Change the footer confirm button (lines 169-175) to a real submit of that form, which makes the browser run constraint validation (email format, `min` on budget) before `handleConfirm`:

```jsx
                    <button
                        type="submit"
                        form="trip-setup-form"
                        className="btn btn--primary"
                        disabled={isVoteMode && !voteFormValid}
                    >
                        {isVoteMode ? 'Continue to Categories' : 'Confirm'}
                    </button>
```

(Remove the `onClick={handleConfirm}`.)

- [ ] **Step 2: Give the silent budget rejection a message**

In `handleConfirm`, replace the silent `return` (lines 48-51) with:

```js
            const budgetValue = budget.trim() === '' ? null : Number(budget);
            if (budgetValue !== null && (!Number.isFinite(budgetValue) || budgetValue <= 0)) {
                setBudgetError('Budget must be a positive number.');
                return;
            }
            setBudgetError('');
```

In the budget `form-group` (after the closing `</div>` of the relative-position wrapper, line 162), add:

```jsx
                                {budgetError && <p className="text-error">{budgetError}</p>}
```

Also clear it on input: change the budget `onChange` to:

```jsx
                                        onChange={e => {
                                            setBudget(e.target.value);
                                            setBudgetError('');
                                        }}
```

- [ ] **Step 3: Run tests**

Run: `npm test -- --watchAll=false TripSetupModal`
Expected: PASS. If the existing test clicks the confirm button, jsdom fires form submission via the `form` attribute — if a test fails on requestSubmit support, adapt the test to `fireEvent.submit` on the form.

- [ ] **Step 4: Commit**

```bash
git add myhive-react-app/src/components/TripSetupModal.js myhive-react-app/src/components/TripSetupModal.test.js
git commit -m "fix: run native validation in TripSetupModal and surface budget errors"
```

---

### Task 7: Auth robustness — status-based 401 handling, stable getAccessToken, custom deletes routed

Three related fixes: (a) `useAuthErrorHandler` matches `err.message === 'Unauthorized'` — brittle string coupling (`adminApi.handleError` already sets `err.status`, verified at `adminApi.js:33-37`); (b) `getAccessToken` is memoized on `auth.user?.access_token`, so every silent token renewal recreates the admin API and refetches every open admin list; (c) the custom delete handlers in AdminActivities/AdminCategories bypass the 401 logout and don't set `saving`.

**Files:**
- Modify: `src/hooks/useAuthErrorHandler.js`
- Modify: `src/context/AuthContext.js:1,33`
- Modify: `src/pages/AdminActivities.js:80-94`
- Modify: `src/pages/AdminCategories.js:39-66` (Read the file first)

- [ ] **Step 1: Status-based auth error detection**

Replace the callback body in `src/hooks/useAuthErrorHandler.js`:

```js
    return useCallback((err) => {
        if (err.status === 401 || err.status === 403) {
            logout();
            return true;
        }
        return false;
    }, [logout]);
```

- [ ] **Step 2: Stable `getAccessToken` in `AuthContext.js`**

Change line 1 to add `useRef`:

```js
import {createContext, useCallback, useContext, useMemo, useRef} from 'react';
```

Replace line 33 with:

```js
    // Stable identity: silent token renewal must not recreate consumers
    // (useAdminApi memoizes on this), so read the freshest user via a ref.
    const userRef = useRef(auth.user);
    userRef.current = auth.user;
    const getAccessToken = useCallback(async () => userRef.current?.access_token, []);
```

- [ ] **Step 3: Route AdminActivities' custom delete through the auth handler**

In `src/pages/AdminActivities.js`:

1. Add import: `import {useAuthErrorHandler} from '../hooks/useAuthErrorHandler';`
2. Inside the component add: `const handleAuthError = useAuthErrorHandler();`
3. Add `setSaving,` to the `useAdminCrud` destructuring (exported in Task 2).
4. Replace `customHandleDelete` (lines 80-94):

```js
    const customHandleDelete = async () => {
        try {
            setSaving(true);
            setError('');
            await adminApi.deleteActivity(deleteId);
            await fetchData();
        } catch (e) {
            if (handleAuthError(e)) {
                return;
            }
            if (e?.status === 409 && Array.isArray(e?.body?.packageNames)) {
                setError(`Cannot delete: used in packages: ${e.body.packageNames.join(', ')}`);
            } else {
                setError(e.message || 'Failed to delete activity');
            }
        } finally {
            setDeleteId(null);
            setSaving(false);
        }
    };
```

- [ ] **Step 4: Same treatment in `AdminCategories.js`**

Read the file; in `handleDeleteClick`/`handleDeleteConfirm` (lines ~39-66) add an `if (handleAuthError(err)) return;` guard before the local error handling, importing/instantiating `useAuthErrorHandler` the same way as Step 3.

- [ ] **Step 5: Run tests and commit**

Run: `npm test -- --watchAll=false` — `useAdminCrud.test.js` mocks `useAuthErrorHandler`, so it must still pass.

```bash
git add myhive-react-app/src/hooks/useAuthErrorHandler.js myhive-react-app/src/context/AuthContext.js myhive-react-app/src/pages/AdminActivities.js myhive-react-app/src/pages/AdminCategories.js
git commit -m "fix: status-based 401 logout, stable getAccessToken, auth-aware custom deletes"
```

---

### Task 8: Cleanup — accidental deps, dead NAVIGATE state, admin bundle splitting, token encoding

**Files:**
- Modify: `myhive-react-app/package.json` (via npm)
- Modify: `src/context/AppContext.js:9,38-39`
- Modify: `src/components/DestinationCard.js:15-23`
- Create: `src/AdminApp.js`
- Modify: `src/App.js`
- Modify: `src/services/voteApi.js`

- [ ] **Step 1: Remove the accidental `npm` and `start` packages**

Run (from `myhive-react-app/`): `npm uninstall npm start`
Expected: both lines disappear from `package.json` dependencies. Then `npm test -- --watchAll=false` still passes (the `start` *script* is unrelated to the `start` package).

- [ ] **Step 2: Delete dead `NAVIGATE`/`currentPath` state**

1. In `src/context/AppContext.js`: remove `currentPath: '/',` (line 9) and the `case 'NAVIGATE': ...` (lines 38-39).
2. In `src/components/DestinationCard.js`: remove the `dispatch({type: 'NAVIGATE', path});` line in `handleClick` (line 18) and inline `path`:

```js
  const handleClick = () => {
    if (hasActivities) {
      navigate(`/destination/${destination.slug || destination.id}`);
    } else {
      dispatch({type: 'OPEN_DESTINATION_MODAL', destination});
    }
  };
```

(`dispatch` is still used by the else-branch — keep the context import.)
3. Grep for `'NAVIGATE'` and `currentPath` across `src/` to confirm zero remaining references.

- [ ] **Step 3: Split the admin app out of the public bundle**

1. Create `src/AdminApp.js` by moving `AdminIndex` and the `AdminRoutes` body out of `App.js` verbatim:

```js
import {Navigate, Route, Routes} from 'react-router-dom';
import {AuthProvider, useAuth} from './context/AuthContext';
import AdminLayout from './components/AdminLayout';
import AdminLogin from './pages/AdminLogin';
import AdminDashboard from './pages/AdminDashboard';
import AdminBookingDetail from './pages/AdminBookingDetail';
import AdminActivities from './pages/AdminActivities';
import AdminPackages from './pages/AdminPackages';
import AdminCategories from './pages/AdminCategories';
import AdminDestinations from './pages/AdminDestinations';
import AdminBlog from './pages/AdminBlog';
import ProtectedRoute from './components/ProtectedRoute';

function AdminIndex() {
    const {user} = useAuth();
    if (user?.roles?.includes('ADMIN')) {
        return <AdminDashboard/>;
    }
    return <Navigate to="/admin/activities" replace/>;
}

function AdminApp() {
    return (
        <AuthProvider>
            <Routes>
                <Route path="login" element={<AdminLogin/>}/>
                <Route path="*" element={
                    <ProtectedRoute>
                        <AdminLayout/>
                    </ProtectedRoute>
                }>
                    <Route index element={<AdminIndex/>}/>
                    <Route path="bookings/:id"
                           element={<ProtectedRoute requiredRole="ADMIN"><AdminBookingDetail/></ProtectedRoute>}/>
                    <Route path="activities" element={<AdminActivities/>}/>
                    <Route path="packages" element={<AdminPackages/>}/>
                    <Route path="categories" element={<AdminCategories/>}/>
                    <Route path="destinations" element={<AdminDestinations/>}/>
                    <Route path="blog" element={<AdminBlog/>}/>
                </Route>
            </Routes>
        </AuthProvider>
    );
}

export default AdminApp;
```

2. Replace `src/App.js` entirely with:

```js
import 'bootstrap/dist/css/bootstrap.min.css';
import './styles/global.css';
import {lazy, Suspense} from 'react';
import {BrowserRouter as Router, Route, Routes} from 'react-router-dom';
import {HelmetProvider} from 'react-helmet-async';
import {AppProvider} from './context/AppContext';
import Layout from './components/Layout';

// Admin pages + the OIDC client are heavy and irrelevant to public visitors —
// load that whole subtree only when /admin is actually opened.
const AdminApp = lazy(() => import('./AdminApp'));

function App() {
    return (
        <HelmetProvider>
            <Router>
                <Routes>
                    <Route path="/admin/*" element={
                        <Suspense fallback={<div style={{padding: '4rem', textAlign: 'center'}}>Loading…</div>}>
                            <AdminApp/>
                        </Suspense>
                    }/>
                    <Route path="/*" element={
                        <AppProvider>
                            <Layout/>
                        </AppProvider>
                    }/>
                </Routes>
            </Router>
        </HelmetProvider>
    );
}

export default App;
```

3. Run `npm run build` and confirm the output shows a separate chunk for the admin code (a second sizeable `*.chunk.js`) and the main bundle shrinks.

- [ ] **Step 4: Encode interpolated tokens in `voteApi.js`**

In `src/services/voteApi.js`, wrap every `${shareToken}` and `${managerToken}` interpolation with `encodeURIComponent(...)` (11 occurrences; `closeSession`'s `managerToken` at lines 99-100 is the important one — it arrives via a shared URL's query param). Example for `closeSession`:

```js
  async closeSession(shareToken, managerToken) {
    const base = `${API_BASE_URL}/vote/sessions/${encodeURIComponent(shareToken)}/close`;
    const url = managerToken
        ? `${base}?managerToken=${encodeURIComponent(managerToken)}`
        : base;
    const response = await fetch(url, { method: 'POST' });
    if (!response.ok && response.status !== 400) throw new Error('Failed to close session');
  },
```

- [ ] **Step 5: Full verification and commit**

Run: `npm test -- --watchAll=false` — expected PASS.
Run: `npm run build` — expected success with the admin chunk split out.

```bash
git add myhive-react-app/package.json myhive-react-app/package-lock.json myhive-react-app/src/context/AppContext.js myhive-react-app/src/components/DestinationCard.js myhive-react-app/src/AdminApp.js myhive-react-app/src/App.js myhive-react-app/src/services/voteApi.js
git commit -m "chore: drop accidental deps, dead NAVIGATE state; lazy-load admin bundle; encode vote tokens"
```

---

## Final verification (after all tasks)

- [ ] `npm test -- --watchAll=false` — all suites green.
- [ ] `npm run build` — production build succeeds.
- [ ] Manual smoke (dev server + backend): add a package to the trip → itinerary total and booking-modal "Estimated Total" now match; admin create with a duplicate slug shows the error inside the modal.
- [ ] Code review of the full diff (per CLAUDE.md workflow rule 1) before declaring done.

## Phase 2 backlog (intentionally out of scope — separate plan)

Not planned here; each is a candidate for a follow-up plan once Phase 1 is approved:

- Shared accessible modal wrapper (focus trap, Escape, `role="dialog"`) extracted from `ActivityPreviewModal`, adopted by TripSetupModal/ContactForm/SuccessModal/TripBuilderDropdown/Layout overlays; `aria-label`s for all icon-only buttons; keyboard access for ActivityCard/DestinationCard (wrap in `Link` like PackageCard).
- TripBuilder vote-session effect: depend on `searchParams.get('voteSession')` string, not the `searchParams` object; surface vote-result fetch failures instead of `.catch(() => {})`.
- AppContext split (stable dispatch context vs state) and dropping the eager global `GET /activities` on every page load.
- Admin polish: inline validation messages; `price === 0` edit bug (`!form.price` disables Save); per-row delete spinner in AdminCategories; AdminDestinations `openEdit` race + ref/state mirror; AdminPackages unpaginated activity fetch; shared image-upload handler; keep table rendered during pagination.
- ChatPanel: `onKeyPress` → `onKeyDown`, Send button, cleanup of `setTimeout`, un-hardcode Tenerife content (default destination is Prague).
- Vote pages: Helmet titles + `noindex`; move inline style blobs to CSS files.
- DestinationCard badge logic (`'Hot Deal'` for everything below 4.7 rating — product decision needed).
- `api.js`/`voteApi.js` error normalization (one `handleResponse` that preserves backend messages + status), `formatPrice` vs `formatAmount` consolidation.
- Tooling: migrate CRA → Vite; move testing libs to devDependencies; upgrade `@testing-library/user-event` to v14; add admin/auth test coverage.
