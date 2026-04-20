# Activities CSV Import/Export — Design Spec

**Date:** 2026-04-20
**Scope:** Admin-only bulk editing of existing activities via CSV round-trip (export → edit (e.g., with AI) → import).
**Out of scope for v1:** creating new activities via import, deleting activities via import, undo/rollback of a completed import.

## Goals

- Admin exports the full activities table as a CSV file.
- Admin edits specific text/numeric fields (typically using an AI assistant).
- Admin imports the edited CSV; the system updates matched rows safely.
- The design must make it hard to accidentally corrupt the production database.

## Non-goals

- Not a general-purpose data migration tool.
- Not a replacement for the per-row admin UI — it supplements bulk text edits.
- No new-row creation, no deletion via import (done through the existing UI).

## Scope of changes

**Mode:** update-only. Rows are matched to existing activities by `id` (UUID). Missing or unknown `id` is an error.

**Mutable fields (written on apply):**
- `name`
- `description`
- `price`
- `duration`
- `category_slugs` (many-to-many; full replacement, not merge)
- `includes`

**Read-only fields (present in export for context; ignored on apply, warning if changed):**
- `id`
- `slug`
- `destination_slug`
- `image_url`

`id` is the matching key and must never be changed.

## CSV format

### Columns (fixed set, read by header name — order-independent)

```
id,slug,destination_slug,name,description,price,duration,category_slugs,image_url,includes
```

### Conventions

- Encoding: export emits UTF-8 with BOM (Excel-friendly). Import accepts UTF-8 with or without BOM.
- Delimiter: `,` (RFC 4180). Multi-line text and embedded commas handled via standard double-quote escaping; literal `"` inside a field escaped as `""`.
- Line endings: both `\n` and `\r\n` accepted on import.
- `category_slugs`: `;`-separated slugs (e.g. `beach;wellness;family`). An empty cell means "remove all categories".
- `price`: decimal with `.` separator, up to 2 decimal places. Comma decimal (`1,50`) is rejected — avoids locale ambiguity.
- `duration`: integer (minutes) or empty.

### CSV injection defense

On export, any cell whose first character is one of `=`, `+`, `-`, `@`, `\t`, `\r` is prefixed with a single quote (`'`). This prevents cells being interpreted as formulas in Excel/Sheets. Import does not strip these — the cell is treated as literal text.

## API

All endpoints sit under `AdminController` and require role `ADMIN`.

| Method | Endpoint | Body | Response |
|---|---|---|---|
| `GET` | `/admin/activities/export` | — | `text/csv` attachment, filename `activities-YYYY-MM-DD.csv` |
| `POST` | `/admin/activities/import/preview` | `multipart/form-data`, field `file` | `ImportPreviewDTO` (no DB writes) |
| `POST` | `/admin/activities/import/apply` | `{ "token": "<uuid>" }` | `ImportResultDTO` |

### `ImportPreviewDTO`

```java
record ImportPreviewDTO(
    String token,                       // null if errors exist; otherwise one-time UUID, TTL 10 min
    int totalRows,
    int rowsToUpdate,
    int rowsUnchanged,
    int rowsWithErrors,
    int rowsWithWarnings,
    List<RowDiff> changes,              // only rows with real changes
    List<RowError> errors,
    List<RowWarning> warnings
)

record RowDiff(
    int csvRowNumber,                   // 1-based, including header
    UUID activityId,
    String activityName,
    Map<String, FieldChange> fieldChanges   // field -> { old, new }
)

record RowError(int csvRowNumber, String code, String message, String field)
record RowWarning(int csvRowNumber, String code, String message, String field)
```

Error/warning codes enumerated below.

### `ImportResultDTO`

```java
record ImportResultDTO(
    int rowsUpdated,
    Instant appliedAt
)
```

Failure returns standard error response with a specific code (e.g., `TOKEN_EXPIRED`, `STATE_CHANGED`).

## Validation rules (applied on preview AND re-applied on apply)

### File level

| Code | Condition |
|---|---|
| `EMPTY_FILE` | 0 bytes or no data rows |
| `FILE_TOO_LARGE` | > 5 MB |
| `TOO_MANY_ROWS` | > 10,000 data rows |
| `INVALID_ENCODING` | Not valid UTF-8 |
| `MISSING_COLUMNS` | Any required column missing from header |
| `UNKNOWN_COLUMNS` | *Warning only.* Extra columns present — ignored |

### Row level (errors — block apply)

| Code | Condition |
|---|---|
| `MISSING_ID` | `id` cell is blank |
| `INVALID_UUID` | `id` is not a valid UUID |
| `ROW_NOT_FOUND` | No activity with this `id` in DB |
| `DUPLICATE_ID` | Same `id` appears on multiple rows |
| `NAME_REQUIRED` | `name` is blank |
| `FIELD_TOO_LONG` | `name` > 255, `description` > 10,000, `includes` > 10,000 |
| `INVALID_DECIMAL` | `price` uses comma, non-numeric, more than 2 decimals, or negative |
| `PRICE_REQUIRED` | `price` blank |
| `INVALID_INTEGER` | `duration` non-empty and not a non-negative integer |
| `UNKNOWN_CATEGORY` | One or more `category_slugs` don't exist in DB |

### Row level (warnings — do not block apply)

| Code | Condition |
|---|---|
| `READ_ONLY_FIELD_CHANGED` | `slug`, `destination_slug`, or `image_url` differs from DB value. Value is ignored on apply |

### Semantic annotations

- `NO_CHANGES` — all mutable fields equal to DB. Not shown in `changes`, counted in `rowsUnchanged`. Read-only warnings are orthogonal: a row can be `NO_CHANGES` *and* have a `READ_ONLY_FIELD_CHANGED` warning (the warning is still listed, but the row does not appear in the diff table).

## Preview → apply flow

1. Admin POSTs CSV to `/preview`.
2. Server parses, validates, computes diff vs current DB state.
3. If validation has **zero errors** (warnings are OK):
   - Generate a UUID token.
   - Store the parsed rows in an in-memory cache (e.g., Caffeine) keyed by token, TTL 10 minutes.
   - Return `ImportPreviewDTO` with the token populated.
4. If there are any errors: return `ImportPreviewDTO` with `token = null` and the error list. The UI disables Apply. Admin must fix the CSV and re-upload.
5. Admin reviews diff. If OK, clicks Apply.
6. UI POSTs `{ "token": "<uuid>" }` to `/apply`.
7. Server:
   - Looks up the token in cache. Missing/expired/reused → `TOKEN_EXPIRED` or `TOKEN_NOT_FOUND`.
   - Invalidates the token **immediately** (one-shot use).
   - Opens a single `@Transactional` block.
   - **Re-validates every row against current DB state.** If any row now fails (e.g., activity deleted, category deleted between preview and apply, field constraints still hold), rolls back and returns `STATE_CHANGED` with details.
   - Applies updates to all mutable fields.
   - Returns `ImportResultDTO`.
8. Any thrown exception during the transaction rolls back everything (strict all-or-nothing). Warnings never block apply.

## UI (React, `AdminActivities.js`)

Two new buttons in the header next to "+ Add Activity":

- **Export CSV** — downloads the file.
- **Import CSV** — opens `ImportActivitiesModal`.

### Modal steps

1. **Upload.** File input + Preview button.
2. **Review.** Stats line (`190 to update, 10 unchanged, 0 errors`). If errors exist: red error block with `[row, code, message]` list; Apply disabled. If no errors: table of diffs (`row | activity | field | before → after`), Apply enabled.
3. **Result.** Green success (`Updated 190 activities`) or red failure with message. Close button refreshes the admin table.

During apply, the button shows a spinner and is disabled.

## Edge cases (explicitly handled)

| Case | Resolution |
|---|---|
| Empty `category_slugs` cell | Remove all categories from that activity |
| Non-empty `category_slugs` | Full replacement of category set (not merge) |
| Row without `id` | `MISSING_ID` error |
| Duplicate `id` in file | `DUPLICATE_ID` error with all row numbers |
| `\r\n` vs `\n` line endings | Both accepted |
| UTF-8 BOM | Stripped on import |
| Formula in cell (`=CMD(...)`) | Export prefixes `'`; import treats as literal text |
| Price with comma (`1,50`) | `INVALID_DECIMAL` error |
| Very long description | `FIELD_TOO_LONG` (limit 10,000) |
| Token expired between preview and apply | `TOKEN_EXPIRED` → admin re-uploads file |
| Token reused | `TOKEN_NOT_FOUND` (already consumed) |
| Two admins previewing simultaneously | Independent tokens; first apply wins, second may hit `STATE_CHANGED` on re-validation |
| Category deleted between preview and apply | Re-validation catches it → `STATE_CHANGED`, rollback |
| Activity deleted between preview and apply | Re-validation catches it → `STATE_CHANGED`, rollback |
| Round-trip (export → no edits → import) | `rowsToUpdate=0`, `rowsUnchanged=N`; UI shows "No changes to apply", Apply disabled |
| 0-byte file | `EMPTY_FILE` error |

## Known limitations (v1, accepted)

- **No optimistic locking.** `Activity` has no `@Version` / `updatedAt` field. If admin A changes an activity through the single-row UI *after* admin B starts a preview but *before* admin B applies, B's apply will silently overwrite A's change. Mitigation: the window is minutes, and the admin workflow is typically single-user. Adding `@Version` is tracked as a follow-up.
- **No import-level undo.** Rollback = export before, edit, re-import. Document this in admin-facing help text.
- **In-memory preview cache is not cluster-safe.** Not a problem today (single Render instance), but note it for future scale-out.

## Security

- All three endpoints require `ROLE_ADMIN`. `MANAGER` is not sufficient.
- File size capped before parsing (`MultipartConfigElement` limit + explicit check).
- Row count capped (10,000).
- Field length caps applied before any DB write.
- Preview tokens are random UUIDs, one-shot, TTL 10 minutes.
- CSV injection defense on export.

## Testing

### Backend (`myhive-backend/src/test/`)

**`ActivityImportExportServiceTest` (unit):**
- Export: BOM present, proper escaping of quotes/newlines/commas, formula injection prefixing.
- Preview: every error code and warning code has a test.
- Preview: round-trip without edits → all rows unchanged.
- Preview: read-only field changed → warning, not error.
- Preview: returns token stored in cache with TTL.
- Apply: valid token persists changes; expired/reused token rejected.
- Apply: state-changed cases (activity deleted, category deleted) → rollback.
- Apply: empty `category_slugs` removes all categories.
- Apply: simulated exception mid-transaction rolls back all rows.

**`AdminControllerImportExportTest` (@SpringBootTest, H2):**
- Export as ADMIN → 200 CSV; as MANAGER → 403; anonymous → 401.
- Preview/Apply authorization mirror.
- End-to-end round-trip: export → modify 3 rows in memory → preview → apply → assert DB state.

**`TestDataFactory`:** add `activitiesForImport(int n)` helper returning N activities with mixed categories.

### Frontend

Minimum: snapshot test that the modal opens and Apply is disabled with errors. Optional — can be deferred.

## Open questions / follow-ups

- Add `@Version` and/or `updatedAt` to `Activity` for proper optimistic locking.
- Extend to `Destination` and `Category` import/export if the pattern proves useful.
- Consider a "dry-run with diff" export that only includes changed rows for auditing.
