# Package Entity — Design Spec

**Date:** 2026-04-22
**Status:** Approved for implementation planning

## Goal

Add a new `Package` entity that lets admins bundle multiple `Activity` records into a single bookable product (a "ready-made tour") sold at a discount. Packages are first-class browseable items on the public site with their own SEO-indexed pages, and bookable as a unit through the existing booking flow.

## Decisions Summary

| Topic | Decision |
|---|---|
| Customer use | Buys the package as a unit (a ready-made tour) |
| Pricing | Sum of activity prices minus a **percentage** discount |
| Destination scope | Each package belongs to **one** destination (like Activity) |
| Public visibility | Own slug + own public page + sitemap entry |
| Activity ordering | Position-based ordering (`position INT`); no "day N" field yet |
| Booking integration | Hybrid (variant C): one `BookingItem` per activity, all linked by `package_id`; discount snapshot stored on the item |
| Activity deletion | **Forbidden** if used in any package — backend returns 409 |

## 1. Domain Model

### `packages` table

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | `GenerationType.AUTO` |
| `destination_id` | UUID FK NOT NULL | → `destinations.id` |
| `slug` | VARCHAR(300) UNIQUE | Auto-generated via Slugify + ICU4J; admin-overridable |
| `name` | VARCHAR NOT NULL | |
| `description` | TEXT | |
| `image_url` | VARCHAR(500) | Cloudflare R2 |
| `includes` | TEXT | |
| `duration` | INTEGER | Hours; admin-only field, not surfaced publicly |
| `discount_pct` | DECIMAL(5,2) NOT NULL | e.g. `15.00` = −15% |
| `created_at` | TIMESTAMP | `@CreationTimestamp` |

### `package_categories` (m2m, mirrors `activity_categories`)

| Column | Type |
|---|---|
| `package_id` | UUID FK |
| `category_id` | UUID FK |
| PK | (`package_id`, `category_id`) |

### `package_activities` (ordered list)

| Column | Type | Notes |
|---|---|---|
| `package_id` | UUID FK | |
| `activity_id` | UUID FK | |
| `position` | INTEGER NOT NULL | Stored as `INT` so we can later add a "day N" semantic without schema change |
| PK | (`package_id`, `activity_id`) | |

**Constraint:** All activities in a package must belong to the same destination as the package itself (validated in service layer).

### Computed price

Not stored. Calculated in `PackageService` on the fly:

```
finalPrice = sum(activity.price) * (1 - discountPct / 100)
```

Rounded to 2 decimal places, `RoundingMode.HALF_UP`.

### Slug generation

Same approach as `Activity` — `SlugUtils` (Slugify + ICU4J transliteration). Admin can override via the form.

### Activity deletion guard

`DELETE /admin/activities/{id}` checks `package_activities` — if any rows reference the activity, returns:

```http
409 Conflict
{ "packageNames": ["Honeymoon Bali", "Adventure Java"] }
```

The frontend renders a toast: *"Cannot delete: used in packages: …"*.

## 2. REST API

### Public endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/packages` | List with optional filters: `destinationSlug`, `categorySlug` |
| `GET` | `/packages/{id}` | Get by UUID |
| `GET` | `/packages/slug/{slug}` | Get by slug — used by public page |

### Admin endpoints (`/admin/packages`, JWT, ADMIN/MANAGER)

| Method | Path | Description |
|---|---|---|
| `GET` | `/admin/packages` | Paginated list |
| `POST` | `/admin/packages` | Create |
| `PUT` | `/admin/packages/{id}` | Update (all fields + ordered activity IDs + categories) |
| `DELETE` | `/admin/packages/{id}` | Delete |
| `POST` | `/admin/packages/{id}/image` | Image upload to R2 |

### Modifications to existing endpoints

- `GET /destinations/slug/{slug}` response gains a `packages[]` array (alongside existing `activities[]`).
- `DELETE /admin/activities/{id}` may now return `409 Conflict` per the deletion guard above.
- `SitemapController` adds `/destination/{destSlug}/package/{slug}` for every package.

### `PackageResponse` DTO (public)

Includes:
- All package fields (except `duration` — admin-only)
- Embedded ordered `activities[]` (id, slug, name, price, imageUrl, duration)
- Embedded `categories[]` (id, slug, name)
- Computed price fields:
  - `originalPrice` (sum of activity prices)
  - `discountedPrice` (after applying `discountPct`)
  - `savings` (= `originalPrice − discountedPrice`)
- `destination` (id, slug, name)

Frontend should not recalculate — always use server-computed values.

### `AdminPackageResponse` DTO

Same as `PackageResponse` plus `duration` and `discountPct`.

## 3. Booking Integration (variant C — hybrid)

### `booking_items` schema additions

| Column | Type | Notes |
|---|---|---|
| `package_id` | UUID NULL FK → `packages.id` | NULL for standalone activities |
| `package_discount_pct` | DECIMAL(5,2) NULL | Snapshot at booking time so future discount changes don't mutate old bookings |

### Booking semantics

- Booking a package creates **N `BookingItem` rows** (one per activity in the package).
- Each row has `activity_id` set + `package_id` set + `package_discount_pct` set + `price` snapshot of current activity price.
- Discount is **not** stored as a separate row. It's computed at display time by grouping items by `(booking_id, package_id)`.

**Invariant:** All items in the same booking that share a `package_id` MUST have the same `package_discount_pct`. Enforced by the booking-creation service (single snapshot per package per booking). No DB constraint — service-layer responsibility.

### Total calculation

```
total = sum(items where package_id IS NULL)
      + Σ over groups grouped by package_id:
          sum(group.price) * (1 - group.package_discount_pct / 100)
```

### Email / invoice / admin booking-detail rendering

Items grouped by `package_id`; standalone items shown separately.

```
Honeymoon Bali Package
  • Uluwatu sunset tour     $80.00
  • Cooking class           $50.00
  • Spa day                $120.00
  Subtotal:                $250.00
  Package discount (−15%): −$37.50
  Total:                   $212.50

Standalone activities
  • Diving                  $90.00

Booking total:             $302.50
```

### Edge cases

- **Activity removed between preview and confirm:** Already prevented by deletion guard. Belt-and-suspenders: confirm step revalidates, returns 409 if missing.
- **Activity price changed between preview and confirm:** Recalculate, return updated total to client, require explicit re-confirm.

## 4. Admin Frontend

### `AdminPackages` page

List view, modeled on `AdminActivities`:

- **Table columns:** image, name, destination, activity count, original / discounted price, discount %, actions (Edit, Delete)
- **Filters:** by destination, by category, search by name
- **"+ Create Package"** button opens the form

### Package form (create / edit)

| Field | UI | Required |
|---|---|---|
| `name` | text input | ✓ |
| `slug` | text input (auto-fills from name; editable) | |
| `description` | textarea | |
| `destination` | select | ✓ |
| `image` | R2 upload widget | |
| `includes` | textarea | |
| `duration` | number input (hours) | |
| `discountPct` | number input (0–100) | ✓ |
| `categories` | multi-select | |
| `activities` | **ordered drag-and-drop list** (see below) | ≥1 required |

**Activity picker component:**
- Lists current package activities in `position` order
- Drag-and-drop to reorder (library choice deferred to implementation — `@dnd-kit/core` recommended since `react-beautiful-dnd` is unmaintained)
- Each row: thumbnail, name, current price, ✕ remove button
- "+ Add activity" opens a modal with destination-filtered activity search
- Changing the form's `destination` while activities are present → confirm dialog: *"This will clear the activity list. Continue?"*

**Live price preview** below the form:
```
Activities total:  $250.00
Discount (15%):   −$37.50
Final price:       $212.50
```

### `AdminLayout`

Add navigation entry **"Packages"** between "Activities" and "Categories".

### `AdminActivities` change

Catch 409 from delete and show toast: *"Cannot delete: used in packages: {names}"*.

## 5. Public Frontend

### New route

`/destination/:destSlug/package/:slug` → new page **`PackageDetailPage`** (modeled on `ActivityDetailPage`).

**Page contents:**
- Hero with cover image + name
- Description
- **"What's included"** — activity cards in `position` order: image, name, duration, price; each card links to its own activity page
- **"Includes"** text block
- **Sticky price card** (right column on desktop):
  ```
  Original price:  $250.00
  You save:         $37.50  ← green badge
  Package price:   $212.50
  [ Add to trip ]
  ```
- Breadcrumbs: Home › {Destination} › {Package name}

### `DestinationPage` change

Add **"Packages"** section above the existing **"Activities"** section: horizontal-scroll cards (image, name, original/discounted price, "Save $X" badge). Section hidden when destination has zero packages.

### `TripBuilder` change

- New **"Add package"** button → modal listing packages for the active destination
- Selecting a package adds **all** its activities to the trip with a visual "Part of: {Package name}" tag
- These activities cannot be removed individually — only the whole package can be removed (single ✕ button on the group)

### SEO

- `<title>`: `{Package name} — {Destination} Package | Trivlu`
- Meta description: first 160 chars of `description`
- Canonical: `https://trivlu.com/destination/{destSlug}/package/{slug}`
- Sitemap entry added (see API section)

## 6. Out of Scope (YAGNI for this iteration)

- Featured packages on `HomePage`
- "Day N" semantic field on `package_activities` (will arrive together with multi-day TripBuilder)
- CSV import/export for packages
- Dedicated package filter on global search / homepage

## 7. Testing Requirements

Per CLAUDE.md: backend unit tests for all new and changed code.

**Backend test coverage:**
- `PackageService` — CRUD, slug generation, price calculation, destination-mismatch validation
- `PackageController` — public + admin endpoints, auth boundaries, 404/409 cases
- `ActivityService.delete` — 409 when used in a package
- `BookingService` — package booking creates N items with correct snapshots; total calculation with packages + standalone items; price-changed-between-preview-and-confirm flow
- `SitemapController` — package URLs included
- Repository: `PackageRepository.findBySlug`, filtering by destination/category

**Test data:** extend `TestDataFactory` with `package(...)` helpers.

## 8. Migration Notes

- Dev (H2 + `create-drop`) and prod (Postgres + `update`) — both pick up new tables/columns automatically. No manual migration script needed.
- Existing bookings remain valid: new `package_id` / `package_discount_pct` columns are NULL for all pre-existing rows, which the calculation logic treats as standalone items.
