# Destination–Category Binding

**Date:** 2026-05-01

## Problem

Categories are global. On a destination page (e.g. Tenerife), all categories are shown regardless of relevance — including ones like "Czech beer" that belong to other destinations.

## Goal

Allow admins to explicitly bind categories to destinations. The destination page and TripBuilder only show categories relevant to the current destination.

## Approach

Explicit admin binding with auto-computed fallback.

- Admin assigns categories to a destination via the destination edit form.
- If no categories are explicitly assigned, the system falls back to auto-computing categories from the destination's activities.
- The same filtered list is used on both `DestinationPage` and `TripBuilder`.

---

## Database

New join table created automatically by Hibernate (`ddl-auto=update` in prod):

```
destination_categories
  destination_id  UUID  FK → destinations.id
  category_id     UUID  FK → categories.id
```

No manual SQL migration required.

---

## Backend

### Entity changes

**`Destination.java`** — new field:
```java
@ManyToMany(fetch = FetchType.LAZY)
@JoinTable(
    name = "destination_categories",
    joinColumns = @JoinColumn(name = "destination_id"),
    inverseJoinColumns = @JoinColumn(name = "category_id")
)
private List<Category> categories = new ArrayList<>();
```

**`Category.java`** — inverse side (ORM only, not exposed directly):
```java
@ManyToMany(mappedBy = "categories")
private List<Destination> destinations = new ArrayList<>();
```

### New public endpoint

```
GET /destinations/{id}/categories
```

**Logic in `DestinationService.getCategoriesForDestination(UUID id)`:**
1. If destination has explicitly assigned categories → return them sorted by name.
2. If list is empty → return categories derived from the destination's activities (auto-fallback), sorted by name.
3. If neither → return empty list.

Returns `List<CategoryDTO>`. No auth required.

### New admin endpoint

```
PUT /admin/destinations/{id}/categories
```

- Body: `List<UUID>` — full replacement of the assigned category list.
- Passing `[]` clears the explicit binding (reverts to fallback).
- Requires ADMIN role.

### DTO change

**`DestinationDTO`** — add read-only field:
```java
private List<CategoryDTO> assignedCategories;
```

Populated on `GET /destinations/{id}` so the admin form can pre-select currently assigned categories.

---

## Frontend

### `api.js`

New function:
```js
getCategoriesForDestination: (destinationId) =>
  axios.get(`${API_URL}/destinations/${destinationId}/categories`)
```

### `DestinationPage.js`

Replace `api.getCategories()` with `api.getCategoriesForDestination(destinationId)` for the category filter list. All other behaviour unchanged.

### `TripBuilder.js`

Replace `api.getCategories()` with `api.getCategoriesForDestination(destinationId)` using the destination from context. All other behaviour unchanged.

### `adminApi.js`

New function:
```js
updateDestinationCategories: (id, categoryIds) =>
  axios.put(`/admin/destinations/${id}/categories`, categoryIds)
```

### `AdminDestinations.js`

In the destination edit form:
- Load all categories via `adminApi.getCategories()`.
- Pre-select those present in `assignedCategories` from the destination DTO.
- On save: call `adminApi.updateDestinationCategories(id, selectedCategoryIds)` in addition to the existing save call.

---

## Tests

### `DestinationServiceTest`

- Returns explicitly assigned categories when present.
- Falls back to activity-derived categories when no explicit assignment.
- Returns empty list when destination has no assigned categories and no activities with categories.

### `DestinationControllerTest`

- `GET /destinations/{id}/categories` returns 200 with correct list.
- `PUT /admin/destinations/{id}/categories` requires ADMIN role.
- `PUT /admin/destinations/{id}/categories` with `[]` clears the assignment.

---

## Out of scope

- No changes to `GET /categories` (still returns all categories globally).
- No per-category UI for managing destination assignments (managed from the destination side only).
- No frontend unit test changes.
