# Admin Inline Validation — Design

**Date:** 2026-06-15
**Phase:** 4 (sub-project 2 of 4)
**Status:** Approved, ready for implementation plan

## Context

Phase 4 of the frontend cleanup is four independent sub-projects, done one at a
time (separate spec + plan each), in this order:

1. Price formatter consolidation — shipped 2026-06-15 (`f32aebf`).
2. **Admin inline validation** ← *this spec*
3. AppContext split
4. CRA → Vite migration (last, as a clean infra swap)

This spec covers only sub-project 2.

## Problem

The admin CRUD pages (`AdminActivities`, `AdminBlog`, `AdminCategories`,
`AdminDestinations`, `AdminPackages`) have no inline field-level validation.
Today validation is only:

- **Disabled Save button** while required fields are empty
  (e.g. `disabled={saving || uploading || !form.name || !form.destinationId || form.price === ''}`).
  The button silently disables — the admin is never told *which* field is
  missing or *why* they can't save.
- **Backend error after submit**, surfaced via `SaveErrorAlert` (e.g. a 400 for
  a malformed slug or an out-of-range discount).

No page uses react-bootstrap's `isInvalid` / `Form.Control.Feedback`
(grep: zero matches across `src/`).

## Goals

- Replace the silent disabled-Save with explicit, inline, per-field feedback.
- Catch the common backend-rejection cases on the client before submit.
- Keep the implementation DRY and consistent with the codebase's shared-hook
  architecture (`useAdminCrud`).

## Decisions (from brainstorming)

- **UX model:** Save is always enabled (except during save/upload). Validation
  runs **on submit**: a click on Save validates; if there are errors they render
  inline under the offending fields and the submit is blocked. Editing a field
  clears that field's error.
- **Rule scope (medium):** required fields + the two backend-rejection cases.
  Numeric ranges that HTML `min`/`max` already loosely guard
  (price/duration/featuredWeight ≥ 0, rating 0–5) are **left to HTML** and not
  validated inline. The exception is `discountPct`, whose 0–100 range is a real
  backend rejection and IS validated.

## Design

### 1. `useAdminCrud` gains validation support

`src/hooks/useAdminCrud.js`:

- New option `validate` — `(form) => ({ [field]: message })`. Returns an object
  of field → message for invalid fields; an empty object means valid.
- New state `const [fieldErrors, setFieldErrors] = useState({})`, exported.
- `handleSave` validates first:
  ```js
  const handleSave = async () => {
      if (validate) {
          const errs = validate(form);
          if (Object.keys(errs).length > 0) {
              setFieldErrors(errs);
              return;            // block submit; no setSaving, no API call
          }
      }
      setFieldErrors({});
      try {
          setSaving(true);
          setSaveError('');
          // ...unchanged: buildPayload, create/update, close, refetch
  ```
- `openCreate` and `openEdit` clear `fieldErrors` (alongside the existing
  `setSaveError('')`).
- New helper `updateField(name, value)` (returned from the hook):
  ```js
  const updateField = useCallback((name, value) => {
      setForm(prev => ({ ...prev, [name]: value }));
      setFieldErrors(prev => {
          if (!prev[name]) return prev;
          const next = { ...prev };
          delete next[name];
          return next;
      });
  }, []);
  ```
  Validated fields switch their `onChange` to `updateField('x', value)` so
  editing the field clears its error. Non-validated fields keep using `setForm`.
- The hook's return object adds `fieldErrors` and `updateField`.

No existing behavior changes when `validate` is not passed (backward compatible).

### 2. Shared validators — `src/utils/validators.js`

Pure functions; each returns an error message string or `undefined`:

```js
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

Composition pattern in each page: build a `validate` that assigns only defined
messages, e.g.

```js
const validate = (form) => {
    const errors = {};
    const name = required(form.name);
    if (name) errors.name = name;
    const slug = slugFormat(form.slug);
    if (slug) errors.slug = slug;
    return errors;
};
```

(`discountRange` is only used after a `required` check passes for `discountPct`;
the page assigns the first failing message per field.)

### 3. Per-page rules

| Page | Required | slug format | Other |
|------|----------|-------------|-------|
| AdminActivities | `name`, `destinationId`, `price` (empty string = missing; 0 is valid) | `slug` | — |
| AdminBlog | `title`, `content` | `slug` | — |
| AdminCategories | `name` | `slug` | — |
| AdminDestinations | `name` | `slug` | — |
| AdminPackages | `name`, `destinationId`, `discountPct` (empty string = missing; 0 is valid) | `slug` | `discountPct` 0–100; `activities.length >= 1` |

Notes:
- `price` / `discountPct` "required" mirrors the existing `=== ''` check (a
  value of `0` is valid and must pass). Use `required` with an explicit empty-string
  check, not falsy, so `0` passes.
- `activities` (Packages) is an array; its rule is `form.activities.length >= 1`
  with message "Add at least one activity."

### 4. Display

- Each validated `Form.Control` / `Form.Select` gets
  `isInvalid={!!fieldErrors.X}` and an immediately-following
  `<Form.Control.Feedback type="invalid">{fieldErrors.X}</Form.Control.Feedback>`.
  (react-bootstrap renders `.invalid-feedback` only when the sibling control has
  `isInvalid`, so the message shows exactly when set.)
- `activities` in Packages uses a custom picker, not a `Form.Control`; render its
  error as `{fieldErrors.activities && <div className="text-danger small mt-1">{fieldErrors.activities}</div>}`
  beneath the picker.
- `SaveErrorAlert` (backend errors) stays in `Modal.Body` and coexists with the
  inline field feedback.

### 5. Save button

Remove the required-field conditions from each Save button's `disabled` prop;
keep only `disabled={saving || uploading}`. The required checks now live in
`validate`, so the button is clickable and clicking surfaces the inline errors.

## Out of scope

- `AdminDestinationQuiz` — a bespoke inline editor that is **not** built on
  `useAdminCrud` and already has its own `validate()` + `Alert`. Left unchanged.
- Numeric range validation for price / duration / featuredWeight / rating —
  left to the existing HTML `min`/`max` attributes (per the chosen medium scope).
- Async/uniqueness checks (e.g. slug uniqueness) — the backend remains the
  authority; we only guard format and presence client-side.

## Testing

- **`src/utils/validators.test.js`** — unit-test each validator: `required`
  (null/undefined/empty/whitespace → message; non-empty and `0`/`false` →
  undefined), `slugFormat` (empty → ok; valid slug → ok; spaces/uppercase/
  special → message), `discountRange` (0 and 100 → ok; -1 / 101 / non-numeric →
  message).
- **`src/hooks/useAdminCrud.test.js`** — add: with a `validate` that returns
  errors, `handleSave` sets `fieldErrors`, does NOT call `createFn`/`updateFn`,
  and leaves the modal open; with valid input it proceeds and clears
  `fieldErrors`; `updateField` clears a specific field's error while leaving
  others.
- **One page-level test (`AdminCategories`)** — the simplest page (name required
  + slug format): clicking Save with an empty name shows the inline message and
  does not call the create API; typing into name clears the message; a bad slug
  shows its message. Reuses the existing admin mocking approach
  (mock `useAdminApi` / `useAuthErrorHandler`, as `useAdminCrud.test.js` does).
- Full suite green; `npm run build` clean (no eslint warnings).

## Risks

- **Low–medium.** The hook change is additive and backward-compatible. The main
  surface area is wiring `isInvalid`/`Feedback` across five pages and switching
  validated fields' `onChange` to `updateField` — mechanical but spread across
  files. The page-level test plus the hook/validator unit tests cover the logic;
  the per-field JSX wiring is declarative and low-risk.
