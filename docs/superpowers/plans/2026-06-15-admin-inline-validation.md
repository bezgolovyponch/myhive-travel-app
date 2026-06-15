# Admin Inline Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the silent disabled-Save admin UX with explicit, inline, on-submit field validation across the five admin CRUD pages.

**Architecture:** A shared pure-function validators module (`src/utils/validators.js`) supplies `required` / `slugFormat` / `discountRange`. `useAdminCrud` gains a `validate` option, `fieldErrors` state, and `updateField`/`clearFieldError` helpers; `handleSave` runs `validate` first and blocks the submit (no API call) when it returns errors. Each page passes a `validate` built from the shared validators, wires `isInvalid` + `Form.Control.Feedback` on its validated fields, switches those fields' `onChange` to `updateField`, and drops the required-field conditions from its Save button.

**Tech Stack:** React 19, Create React App (react-scripts 5), react-bootstrap, Jest + React Testing Library. Run all commands from `myhive-react-app/`. Spec: `docs/superpowers/specs/2026-06-15-admin-inline-validation-design.md`.

**Conventions:** no wildcard imports; commit per task; run the suite with `CI=true npx react-scripts test --watchAll=false`; build with `CI=true npm run build`. Commit messages use a Bash heredoc (`git commit -F - <<'EOF'`) and avoid double quotes (the shell mangles them).

**Display contract (applies to every page task):** the error message is rendered as the *content* of `Form.Control.Feedback`, so it is only present in the DOM when `fieldErrors.X` is truthy; `isInvalid={!!fieldErrors.X}` drives the red styling. This is why typing into a field (which calls `updateField`/`clearFieldError`) makes the message disappear.

---

## File Structure

- `src/utils/validators.js` — Create. Pure validators: `required`, `slugFormat`, `discountRange`.
- `src/utils/validators.test.js` — Create. Unit tests for the three validators.
- `src/hooks/useAdminCrud.js` — Modify. Add `validate` option, `fieldErrors` state, `updateField`/`clearFieldError`; gate `handleSave`.
- `src/hooks/useAdminCrud.test.js` — Modify. Add validate-path + updateField tests.
- `src/pages/AdminCategories.js` — Modify. Wire validation (simplest page). Plus a page-level integration test.
- `src/pages/AdminCategories.test.js` — Create. Integration test for the inline-validation wiring.
- `src/pages/AdminBlog.js` — Modify. Wire validation.
- `src/pages/AdminDestinations.js` — Modify. Wire validation.
- `src/pages/AdminActivities.js` — Modify. Wire validation.
- `src/pages/AdminPackages.js` — Modify. Wire validation (discountPct range, activities count, custom destination handler).

---

### Task 1: Shared validators

**Files:**
- Create: `src/utils/validators.js`
- Test: `src/utils/validators.test.js`

- [ ] **Step 1: Write the failing tests**

Create `src/utils/validators.test.js`:

```js
import {required, slugFormat, discountRange} from './validators';

describe('required', () => {
    it('returns a message for null/undefined/empty/whitespace', () => {
        expect(required(null)).toBeTruthy();
        expect(required(undefined)).toBeTruthy();
        expect(required('')).toBeTruthy();
        expect(required('   ')).toBeTruthy();
    });

    it('returns undefined for non-empty values, including 0 and "0"', () => {
        expect(required('Bali')).toBeUndefined();
        expect(required('0')).toBeUndefined();
        expect(required(0)).toBeUndefined();
    });

    it('supports a custom message', () => {
        expect(required('', 'Name is required.')).toBe('Name is required.');
    });
});

describe('slugFormat', () => {
    it('returns undefined when blank (slug is optional)', () => {
        expect(slugFormat('')).toBeUndefined();
        expect(slugFormat(null)).toBeUndefined();
        expect(slugFormat(undefined)).toBeUndefined();
    });

    it('accepts lowercase alphanumeric with hyphens', () => {
        expect(slugFormat('bali-beach-2')).toBeUndefined();
    });

    it('rejects spaces, uppercase and other characters', () => {
        expect(slugFormat('Bali Beach')).toBeTruthy();
        expect(slugFormat('Bali')).toBeTruthy();
        expect(slugFormat('bali_beach')).toBeTruthy();
    });
});

describe('discountRange', () => {
    it('accepts 0 through 100 inclusive (and decimals)', () => {
        expect(discountRange(0)).toBeUndefined();
        expect(discountRange('0')).toBeUndefined();
        expect(discountRange(100)).toBeUndefined();
        expect(discountRange(15.5)).toBeUndefined();
    });

    it('rejects out-of-range and non-numeric values', () => {
        expect(discountRange(-1)).toBeTruthy();
        expect(discountRange(101)).toBeTruthy();
        expect(discountRange('abc')).toBeTruthy();
    });
});
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `CI=true npx react-scripts test --watchAll=false --testPathPattern validators`
Expected: FAIL — `Cannot find module './validators'`.

- [ ] **Step 3: Create the validators**

Create `src/utils/validators.js`:

```js
// Pure field validators for admin forms. Each returns an error message string
// when invalid, or undefined when valid.

export function required(value, message = 'This field is required.') {
    if (value === null || value === undefined) return message;
    if (typeof value === 'string' && value.trim() === '') return message;
    return undefined;
}

export function slugFormat(value, message = 'Use lowercase letters, numbers and hyphens only.') {
    // Slug is optional (auto-generated when blank); only validate when present.
    if (value === null || value === undefined || value === '') return undefined;
    return /^[a-z0-9-]+$/.test(value) ? undefined : message;
}

export function discountRange(value, message = 'Discount must be between 0 and 100.') {
    const n = Number(value);
    if (!Number.isFinite(n) || n < 0 || n > 100) return message;
    return undefined;
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `CI=true npx react-scripts test --watchAll=false --testPathPattern validators`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/utils/validators.js src/utils/validators.test.js
git commit -F - <<'EOF'
feat: shared admin form validators (required, slugFormat, discountRange)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
```

---

### Task 2: useAdminCrud validation support

**Files:**
- Modify: `src/hooks/useAdminCrud.js`
- Test: `src/hooks/useAdminCrud.test.js`

- [ ] **Step 1: Write the failing tests**

Append these tests to `src/hooks/useAdminCrud.test.js` (after the existing `successful save` test, before the existing delete tests is fine — placement does not matter):

```js
test('handleSave blocks and sets fieldErrors when validate returns errors', async () => {
    const createFn = jest.fn();
    const validate = jest.fn().mockReturnValue({name: 'This field is required.'});
    const {result} = renderCrud({createFn, validate});
    await waitFor(() => expect(result.current.loading).toBe(false));

    act(() => result.current.openCreate());
    await act(() => result.current.handleSave());

    expect(result.current.fieldErrors).toEqual({name: 'This field is required.'});
    expect(createFn).not.toHaveBeenCalled();
    expect(result.current.showModal).toBe(true);
});

test('handleSave proceeds and clears fieldErrors when validate passes', async () => {
    const fetchFn = jest.fn().mockResolvedValue({content: [], totalPages: 0, totalElements: 0});
    const createFn = jest.fn().mockResolvedValue({});
    const validate = jest.fn().mockReturnValue({});
    const {result} = renderCrud({fetchFn, createFn, validate});
    await waitFor(() => expect(result.current.loading).toBe(false));

    act(() => result.current.openCreate());
    await act(() => result.current.handleSave());

    expect(createFn).toHaveBeenCalledTimes(1);
    expect(result.current.fieldErrors).toEqual({});
    expect(result.current.showModal).toBe(false);
});

test('updateField updates the form and clears that field error only', async () => {
    const validate = jest.fn().mockReturnValue({name: 'This field is required.', slug: 'Bad slug.'});
    const {result} = renderCrud({validate});
    await waitFor(() => expect(result.current.loading).toBe(false));

    act(() => result.current.openCreate());
    await act(() => result.current.handleSave());
    expect(result.current.fieldErrors).toEqual({name: 'This field is required.', slug: 'Bad slug.'});

    act(() => result.current.updateField('name', 'Bali'));

    expect(result.current.form.name).toBe('Bali');
    expect(result.current.fieldErrors).toEqual({slug: 'Bad slug.'});
});
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `CI=true npx react-scripts test --watchAll=false --testPathPattern useAdminCrud`
Expected: FAIL — `result.current.fieldErrors`/`updateField` are undefined; `handleSave` calls `createFn` despite validate errors.

- [ ] **Step 3: Implement validation in the hook**

In `src/hooks/useAdminCrud.js`:

(a) Add `validate` to the destructured options (after `mapDeleteError`):

```js
                                 mapItemToForm,
                                 buildPayload = (form) => form,
                                 mapDeleteError,
                                 validate,
                                 pageSize = 10,
```

(b) Add `fieldErrors` state next to `saveError` (after line `const [saveError, setSaveError] = useState('');`):

```js
    // Per-field validation messages, keyed by form field name; rendered inline
    // under each control. Set on a blocked save, cleared as fields are edited.
    const [fieldErrors, setFieldErrors] = useState({});
```

(c) Add `clearFieldError` and `updateField` helpers (place them just before `handleSave`):

```js
    const clearFieldError = useCallback((name) => {
        setFieldErrors(prev => {
            if (!prev[name]) return prev;
            const next = {...prev};
            delete next[name];
            return next;
        });
    }, []);

    const updateField = useCallback((name, value) => {
        setForm(prev => ({...prev, [name]: value}));
        clearFieldError(name);
    }, [clearFieldError]);
```

(d) Gate `handleSave` — add the validation block at the very top of the function body, before `setSaving(true)`:

```js
    const handleSave = async () => {
        if (validate) {
            const errors = validate(form);
            if (Object.keys(errors).length > 0) {
                setFieldErrors(errors);
                return;
            }
        }
        setFieldErrors({});
        try {
            setSaving(true);
            setSaveError('');
            // ...rest unchanged
```

(e) Clear `fieldErrors` when opening either modal. In `openCreate` and `openEdit`, add `setFieldErrors({});` next to the existing `setSaveError('');`:

```js
    const openCreate = () => {
        setSaveError('');
        setFieldErrors({});
        setForm(emptyForm);
        setEditing(null);
        setShowModal(true);
    };

    const openEdit = (item) => {
        setSaveError('');
        setFieldErrors({});
        setForm(mapItemToForm(item));
        setEditing(item);
        setShowModal(true);
    };
```

(f) Export the new values in the returned object (add alongside `saveError`, `setSaveError`):

```js
        saveError,
        setSaveError,
        fieldErrors,
        updateField,
        clearFieldError,
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `CI=true npx react-scripts test --watchAll=false --testPathPattern useAdminCrud`
Expected: PASS (existing tests stay green; three new tests pass).

- [ ] **Step 5: Commit**

```bash
git add src/hooks/useAdminCrud.js src/hooks/useAdminCrud.test.js
git commit -F - <<'EOF'
feat: validate option, fieldErrors and updateField on useAdminCrud

handleSave runs an optional validate(form) first and blocks the submit
(no API call, modal stays open) when it returns field errors. updateField
sets a form field and clears its error; clearFieldError clears one field.
Backward compatible: no validate means no behavior change.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
```

---

### Task 3: AdminCategories validation + page integration test

**Files:**
- Modify: `src/pages/AdminCategories.js`
- Test: `src/pages/AdminCategories.test.js`

- [ ] **Step 1: Write the failing page test**

Create `src/pages/AdminCategories.test.js`:

```js
import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import AdminCategories from './AdminCategories';

const mockApi = {
    getCategoriesPaged: jest.fn(),
    createCategory: jest.fn(),
    updateCategory: jest.fn(),
    deleteCategory: jest.fn(),
    getCategoryUsage: jest.fn(),
};

jest.mock('../hooks/useAdminApi', () => ({useAdminApi: () => mockApi}));
jest.mock('../hooks/useAuthErrorHandler', () => ({useAuthErrorHandler: () => () => false}));

beforeEach(() => {
    // CRA's jest preset resets mocks before each test, so set implementations here.
    mockApi.getCategoriesPaged.mockResolvedValue({content: [], totalPages: 0, totalElements: 0});
    mockApi.createCategory.mockResolvedValue({});
});

async function openCreateModal(user) {
    render(<AdminCategories/>);
    await screen.findByText('Categories');
    await user.click(screen.getByRole('button', {name: '+ Add Category'}));
}

test('clicking Create with an empty name shows an inline error and does not call the API', async () => {
    const user = userEvent.setup();
    await openCreateModal(user);

    await user.click(screen.getByRole('button', {name: 'Create'}));

    expect(await screen.findByText('This field is required.')).toBeInTheDocument();
    expect(mockApi.createCategory).not.toHaveBeenCalled();
});

test('typing into the name field clears its error', async () => {
    const user = userEvent.setup();
    await openCreateModal(user);
    await user.click(screen.getByRole('button', {name: 'Create'}));
    expect(await screen.findByText('This field is required.')).toBeInTheDocument();

    await user.type(screen.getByPlaceholderText(/Nightlife/), 'Nightlife');

    expect(screen.queryByText('This field is required.')).not.toBeInTheDocument();
});

test('an invalid slug shows a format error and blocks create', async () => {
    const user = userEvent.setup();
    await openCreateModal(user);
    await user.type(screen.getByPlaceholderText(/Nightlife/), 'Nightlife');
    await user.type(screen.getByPlaceholderText(/auto-generate/), 'Bad Slug');

    await user.click(screen.getByRole('button', {name: 'Create'}));

    expect(await screen.findByText(/lowercase letters, numbers and hyphens/)).toBeInTheDocument();
    expect(mockApi.createCategory).not.toHaveBeenCalled();
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `CI=true npx react-scripts test --watchAll=false --testPathPattern AdminCategories`
Expected: FAIL — Save is currently disabled with an empty name (button click does nothing) and there is no inline error text; `createCategory` is not called but the `findByText` assertions fail.

- [ ] **Step 3: Wire validation into AdminCategories**

In `src/pages/AdminCategories.js`:

(a) Add the validators import after the `useAdminCrud` import (line 3):

```js
import {required, slugFormat} from '../utils/validators';
```

(b) Destructure `fieldErrors` and `updateField` from the hook (extend the existing destructure list — add them after `setSaveError`):

```js
        form, setForm, saving, saveError, setSaveError, fieldErrors, updateField, fetchData, openCreate, openEdit, handleSave, adminApi,
```

(c) Pass `validate` into the `useAdminCrud({...})` config (add after `mapItemToForm`):

```js
        validate: (form) => {
            const errors = {};
            const name = required(form.name);
            if (name) errors.name = name;
            const slug = slugFormat(form.slug);
            if (slug) errors.slug = slug;
            return errors;
        },
```

(d) Wire the Name control — replace its `Form.Control` with:

```jsx
                            <Form.Control
                                value={form.name}
                                onChange={e => updateField('name', e.target.value)}
                                isInvalid={!!fieldErrors.name}
                                placeholder="e.g. Nightlife, Adventure, Culture"
                            />
                            <Form.Control.Feedback type="invalid">{fieldErrors.name}</Form.Control.Feedback>
```

(e) Wire the Slug control — replace its `Form.Control` with:

```jsx
                            <Form.Control
                                value={form.slug}
                                onChange={e => updateField('slug', e.target.value)}
                                isInvalid={!!fieldErrors.slug}
                                placeholder="Leave blank to auto-generate from name"
                            />
                            <Form.Control.Feedback type="invalid">{fieldErrors.slug}</Form.Control.Feedback>
```

(f) Enable the Save button — change its `disabled` prop from `disabled={saving || !form.name}` to:

```jsx
                            disabled={saving}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `CI=true npx react-scripts test --watchAll=false --testPathPattern AdminCategories`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/pages/AdminCategories.js src/pages/AdminCategories.test.js
git commit -F - <<'EOF'
feat: inline validation on AdminCategories (name required, slug format)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
```

---

### Task 4: AdminBlog validation

**Files:**
- Modify: `src/pages/AdminBlog.js`

- [ ] **Step 1: Wire validation into AdminBlog**

(a) Add the validators import after the `useAdminCrud` import (line 4):

```js
import {required, slugFormat} from '../utils/validators';
```

(b) Destructure `fieldErrors` and `updateField` from the hook — add them after `setSaveError` in the existing destructure:

```js
        form, setForm, saving, saveError, setSaveError, fieldErrors, updateField, uploading, deleteId, setDeleteId,
```

(c) Pass `validate` into the `useAdminCrud({...})` config (add after `mapItemToForm`):

```js
        validate: (form) => {
            const errors = {};
            const title = required(form.title);
            if (title) errors.title = title;
            const content = required(form.content);
            if (content) errors.content = content;
            const slug = slugFormat(form.slug);
            if (slug) errors.slug = slug;
            return errors;
        },
```

(d) Wire the Title control — replace its `Form.Control` with:

```jsx
                            <Form.Control
                                value={form.title}
                                onChange={e => updateField('title', e.target.value)}
                                isInvalid={!!fieldErrors.title}
                                placeholder="Blog post title"
                            />
                            <Form.Control.Feedback type="invalid">{fieldErrors.title}</Form.Control.Feedback>
```

(e) Wire the Slug control — replace its `Form.Control` with:

```jsx
                            <Form.Control
                                value={form.slug}
                                onChange={e => updateField('slug', e.target.value)}
                                isInvalid={!!fieldErrors.slug}
                                placeholder="Leave blank to auto-generate from title"
                            />
                            <Form.Control.Feedback type="invalid">{fieldErrors.slug}</Form.Control.Feedback>
```

(f) Wire the Content control — replace its `Form.Control` with:

```jsx
                            <Form.Control
                                as="textarea"
                                rows={10}
                                value={form.content}
                                onChange={e => updateField('content', e.target.value)}
                                isInvalid={!!fieldErrors.content}
                                placeholder="Full blog post content. Use blank lines to separate paragraphs."
                            />
                            <Form.Control.Feedback type="invalid">{fieldErrors.content}</Form.Control.Feedback>
```

(g) Enable the Save button — change `disabled={saving || uploading || !form.title || !form.content}` to:

```jsx
                            disabled={saving || uploading}
```

- [ ] **Step 2: Run the suite**

Run: `CI=true npx react-scripts test --watchAll=false`
Expected: PASS (no test pins AdminBlog's old behavior).

- [ ] **Step 3: Commit**

```bash
git add src/pages/AdminBlog.js
git commit -F - <<'EOF'
feat: inline validation on AdminBlog (title and content required, slug format)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
```

---

### Task 5: AdminDestinations validation

**Files:**
- Modify: `src/pages/AdminDestinations.js`

- [ ] **Step 1: Wire validation into AdminDestinations**

(a) Add the validators import after the `useAdminCrud` import (line 4):

```js
import {required, slugFormat} from '../utils/validators';
```

(b) Destructure `fieldErrors` and `updateField` from the hook — add them after `setSaveError`:

```js
        form, setForm, saving, saveError, setSaveError, fieldErrors, updateField, uploading, deleteId, setDeleteId,
```

(c) Pass `validate` into the `useAdminCrud({...})` config (add after `mapItemToForm`, before `buildPayload`):

```js
        validate: (form) => {
            const errors = {};
            const name = required(form.name);
            if (name) errors.name = name;
            const slug = slugFormat(form.slug);
            if (slug) errors.slug = slug;
            return errors;
        },
```

(d) Wire the Name control — replace its `Form.Control` with:

```jsx
                            <Form.Control
                                value={form.name}
                                onChange={e => updateField('name', e.target.value)}
                                isInvalid={!!fieldErrors.name}
                                placeholder="Destination name"
                            />
                            <Form.Control.Feedback type="invalid">{fieldErrors.name}</Form.Control.Feedback>
```

(e) Wire the Slug control — replace its `Form.Control` with:

```jsx
                            <Form.Control
                                value={form.slug}
                                onChange={e => updateField('slug', e.target.value)}
                                isInvalid={!!fieldErrors.slug}
                                placeholder="Leave blank to auto-generate from name"
                            />
                            <Form.Control.Feedback type="invalid">{fieldErrors.slug}</Form.Control.Feedback>
```

(f) Enable the Save button — change `disabled={saving || uploading || !form.name}` to:

```jsx
                            disabled={saving || uploading}
```

- [ ] **Step 2: Run the suite**

Run: `CI=true npx react-scripts test --watchAll=false`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/pages/AdminDestinations.js
git commit -F - <<'EOF'
feat: inline validation on AdminDestinations (name required, slug format)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
```

---

### Task 6: AdminActivities validation

**Files:**
- Modify: `src/pages/AdminActivities.js`

- [ ] **Step 1: Wire validation into AdminActivities**

(a) Add the validators import after the `useAdminCrud` import:

```js
import {required, slugFormat} from '../utils/validators';
```

(b) Destructure `fieldErrors` and `updateField` from the hook — add them after `setSaveError` in the existing destructure (the line currently reads `form, setForm, saving, saveError, setSaveError, uploading, deleteId, setDeleteId,`):

```js
        form, setForm, saving, saveError, setSaveError, fieldErrors, updateField, uploading, deleteId, setDeleteId,
```

(c) Pass `validate` into the `useAdminCrud({...})` config (add after `mapDeleteError`'s value, before `mapItemToForm`):

```js
        validate: (form) => {
            const errors = {};
            const name = required(form.name);
            if (name) errors.name = name;
            const destinationId = required(form.destinationId, 'Select a destination.');
            if (destinationId) errors.destinationId = destinationId;
            const price = required(form.price, 'Price is required.');
            if (price) errors.price = price;
            const slug = slugFormat(form.slug);
            if (slug) errors.slug = slug;
            return errors;
        },
```

(d) Wire the Destination `Form.Select` — replace it with:

```jsx
                            <Form.Select
                                value={form.destinationId}
                                onChange={e => updateField('destinationId', e.target.value)}
                                isInvalid={!!fieldErrors.destinationId}
                            >
                                <option value="">Select destination...</option>
                                {destinations.map(d => (
                                    <option key={d.id} value={d.id}>{d.name}</option>
                                ))}
                            </Form.Select>
                            <Form.Control.Feedback type="invalid">{fieldErrors.destinationId}</Form.Control.Feedback>
```

(e) Wire the Name control — replace its `Form.Control` with:

```jsx
                            <Form.Control
                                value={form.name}
                                onChange={e => updateField('name', e.target.value)}
                                isInvalid={!!fieldErrors.name}
                                placeholder="Activity name"
                            />
                            <Form.Control.Feedback type="invalid">{fieldErrors.name}</Form.Control.Feedback>
```

(f) Wire the Slug control — replace its `Form.Control` with:

```jsx
                            <Form.Control
                                value={form.slug}
                                onChange={e => updateField('slug', e.target.value)}
                                isInvalid={!!fieldErrors.slug}
                                placeholder="Leave blank to auto-generate from name"
                            />
                            <Form.Control.Feedback type="invalid">{fieldErrors.slug}</Form.Control.Feedback>
```

(g) Wire the Price control — it lives in a `Col`. Replace its `Form.Control` with (keep the surrounding `Col`/`Form.Label`):

```jsx
                                <Form.Control
                                    type="number"
                                    step="0.01"
                                    min="0"
                                    value={form.price}
                                    onChange={e => updateField('price', e.target.value)}
                                    isInvalid={!!fieldErrors.price}
                                    placeholder="0.00"
                                />
                                <Form.Control.Feedback type="invalid">{fieldErrors.price}</Form.Control.Feedback>
```

(h) Enable the Save button — change `disabled={saving || uploading || !form.name || !form.destinationId || form.price === ''}` to:

```jsx
                            disabled={saving || uploading}
```

- [ ] **Step 2: Run the suite**

Run: `CI=true npx react-scripts test --watchAll=false`
Expected: PASS (the existing AdminActivities behavior — mapDeleteError, CSV, etc. — is unaffected; no test pins the disabled-Save condition).

- [ ] **Step 3: Commit**

```bash
git add src/pages/AdminActivities.js
git commit -F - <<'EOF'
feat: inline validation on AdminActivities (name, destination, price; slug format)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
```

---

### Task 7: AdminPackages validation

**Files:**
- Modify: `src/pages/AdminPackages.js`

This page has two special fields: `destinationId` (uses `handleDestinationChange`, which clears activities) and `activities` (a custom `PackageActivityPicker`). Both clear their error via `clearFieldError` rather than `updateField`.

- [ ] **Step 1: Wire validation into AdminPackages**

(a) Add the validators import after the `useAdminCrud` import (line 5):

```js
import {required, slugFormat, discountRange} from '../utils/validators';
```

(b) Destructure `fieldErrors`, `updateField` and `clearFieldError` from the hook — add them after `setSaveError`:

```js
        form, setForm, saving, saveError, setSaveError, fieldErrors, updateField, clearFieldError, uploading, deleteId, setDeleteId,
```

(c) Pass `validate` into the `useAdminCrud({...})` config (add after `mapItemToForm`, before `buildPayload`):

```js
        validate: (form) => {
            const errors = {};
            const name = required(form.name);
            if (name) errors.name = name;
            const destinationId = required(form.destinationId, 'Select a destination.');
            if (destinationId) errors.destinationId = destinationId;
            const discountRequired = required(form.discountPct, 'Discount is required.');
            if (discountRequired) {
                errors.discountPct = discountRequired;
            } else {
                const range = discountRange(form.discountPct);
                if (range) errors.discountPct = range;
            }
            if (form.activities.length === 0) {
                errors.activities = 'Add at least one activity.';
            }
            const slug = slugFormat(form.slug);
            if (slug) errors.slug = slug;
            return errors;
        },
```

(d) Make `handleDestinationChange` clear the destination error after each `setForm`. Replace the existing function with:

```js
    const handleDestinationChange = (newDestinationId) => {
        if (form.activities.length > 0) {
            const confirmed = window.confirm('Changing destination will clear the activity list. Continue?');
            if (!confirmed) {
                return;
            }
            setForm({...form, destinationId: newDestinationId, activities: []});
        } else {
            setForm({...form, destinationId: newDestinationId});
        }
        clearFieldError('destinationId');
    };
```

(e) Wire the Destination `Form.Select` — replace it with (note: `onChange` keeps `handleDestinationChange`):

```jsx
                            <Form.Select
                                value={form.destinationId}
                                onChange={e => handleDestinationChange(e.target.value)}
                                isInvalid={!!fieldErrors.destinationId}
                            >
                                <option value="">Select destination...</option>
                                {destinations.map(d => (
                                    <option key={d.id} value={d.id}>{d.name}</option>
                                ))}
                            </Form.Select>
                            <Form.Control.Feedback type="invalid">{fieldErrors.destinationId}</Form.Control.Feedback>
```

(f) Wire the Name control — replace its `Form.Control` with:

```jsx
                            <Form.Control
                                value={form.name}
                                onChange={e => updateField('name', e.target.value)}
                                isInvalid={!!fieldErrors.name}
                                placeholder="Package name"
                            />
                            <Form.Control.Feedback type="invalid">{fieldErrors.name}</Form.Control.Feedback>
```

(g) Wire the Slug control — replace its `Form.Control` with:

```jsx
                            <Form.Control
                                value={form.slug}
                                onChange={e => updateField('slug', e.target.value)}
                                isInvalid={!!fieldErrors.slug}
                                placeholder="Leave blank to auto-generate from name"
                            />
                            <Form.Control.Feedback type="invalid">{fieldErrors.slug}</Form.Control.Feedback>
```

(h) Wire the Discount % control (inside its `Col`) — replace its `Form.Control` with:

```jsx
                                <Form.Control
                                    type="number"
                                    step="0.01"
                                    min="0"
                                    max="100"
                                    value={form.discountPct}
                                    onChange={e => updateField('discountPct', e.target.value)}
                                    isInvalid={!!fieldErrors.discountPct}
                                    placeholder="0.00"
                                />
                                <Form.Control.Feedback type="invalid">{fieldErrors.discountPct}</Form.Control.Feedback>
```

(i) Wire the Activities picker — replace the `PackageActivityPicker` element's `onChange` and add an error line beneath it. The current block is:

```jsx
                            <PackageActivityPicker
                                value={form.activities}
                                onChange={(activities) => setForm({...form, activities})}
                                availableActivities={destinationActivities}
                                disabled={!form.destinationId}
                            />
```

Replace it with:

```jsx
                            <PackageActivityPicker
                                value={form.activities}
                                onChange={(activities) => {
                                    setForm({...form, activities});
                                    clearFieldError('activities');
                                }}
                                availableActivities={destinationActivities}
                                disabled={!form.destinationId}
                            />
                            {fieldErrors.activities && (
                                <div className="text-danger small mt-1">{fieldErrors.activities}</div>
                            )}
```

(j) Enable the Save button — change `disabled={saving || uploading || !form.name || !form.destinationId || form.discountPct === '' || form.activities.length === 0}` to:

```jsx
                        disabled={saving || uploading}
```

- [ ] **Step 2: Run the suite**

Run: `CI=true npx react-scripts test --watchAll=false`
Expected: PASS.

- [ ] **Step 3: Build to confirm no eslint warnings (unused imports, etc.)**

Run: `CI=true npm run build`
Expected: `Compiled successfully.` with no eslint warnings.

- [ ] **Step 4: Commit**

```bash
git add src/pages/AdminPackages.js
git commit -F - <<'EOF'
feat: inline validation on AdminPackages

Required name/destination/discount, discount 0-100, at least one activity,
and slug format. Save button no longer silently disables on these.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
```

---

## Final verification

- [ ] Full suite green: `CI=true npx react-scripts test --watchAll=false`.
- [ ] Clean build: `CI=true npm run build` → `Compiled successfully.`, no eslint warnings.
- [ ] Spot-check: across all five pages, the Save button's `disabled` is now only `saving` (Categories) or `saving || uploading` (the rest); no required-field conditions remain there.
- [ ] Multi-angle code review of the branch diff; fix findings.

## Out of scope (carry-over note)

`AdminDestinationQuiz` keeps its own `validate()` + `Alert` (not a `useAdminCrud` page). Numeric range checks for price/duration/featuredWeight/rating stay on the existing HTML `min`/`max`. The other two Phase-4 sub-projects (AppContext split, CRA → Vite) each get their own spec and plan.
