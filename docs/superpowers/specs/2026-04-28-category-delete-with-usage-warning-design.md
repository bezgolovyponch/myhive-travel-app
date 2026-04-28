# Category Delete with Usage Warning

**Date:** 2026-04-28  
**Status:** Approved

## Problem

Deleting a category that is assigned to activities or packages currently:
- Throws a 400 error if the category has activities (blocked)
- Throws a 500 DB error if the category has packages (unhandled FK violation)

The admin must manually unassign the category from every entity before deleting — cumbersome and error-prone.

## Solution

Replace the blocking behavior with an informational warning modal. When the admin clicks Delete on a category, the frontend fetches usage data and shows a modal listing affected activities and packages by name. The admin can then confirm and the backend cascade-removes the category from all join tables before deleting the record.

## Backend

### New DTO: `CategoryUsageDTO`

```java
List<String> activityNames;
List<String> packageNames;
```

### New endpoint

`GET /admin/categories/{id}/usage` → `CategoryUsageDTO`  
Auth: ADMIN role (matches existing category mutation endpoints).

### `CategoryService` changes

**New method `getCategoryUsage(UUID id)`:**
- Loads the category (throws 404 if not found)
- Calls `activityRepository.findByCategoriesId(id)` → maps to names
- Calls `packageRepository.findByCategoriesId(id)` → maps to names
- Returns `CategoryUsageDTO`

**Modified `deleteCategory(UUID id)`:**
- Remove the current blocking check for activities
- Load activities via `activityRepository.findByCategoriesId(id)`, remove category from each `activity.getCategories()`, save
- Load packages via `packageRepository.findByCategoriesId(id)`, remove category from each `package.getCategories()`, save
- Delete the category record
- Wrap in `@Transactional`

### Repository changes

**`ActivityRepository`:** add `List<Activity> findByCategoriesId(UUID categoryId)`  
**`PackageRepository`:** add `List<Package> findByCategoriesId(UUID categoryId)`

## Frontend

### `adminApi.js`

Add `getCategoryUsage(id)` → `GET /admin/categories/{id}/usage`

### New component: `CategoryDeleteModal`

Props: `show`, `onHide`, `onConfirm`, `saving`, `categoryName`, `usage` (`{ activityNames, packageNames }`)

**Layout:**
- Title: "Delete Category?"
- If usage is non-empty: warning text "This category will be removed from:"
  - "Activities (N)" section with names as `Badge bg="secondary"`
  - "Packages (N)" section with names as `Badge bg="secondary"`
  - If more than 5 names in either list: show first 5 + "...and N more"
- If no usage: simple "This action cannot be undone."
- Buttons: Cancel / Delete anyway (danger)

### `AdminCategories.js` changes

Replace the standard `handleDelete` + `DeleteConfirmModal` flow with:

1. State: `loadingUsage: boolean`, `usage: { activityNames, packageNames } | null`
2. On Delete click: set `deleteId`, call `getCategoryUsage(id)`, set `loadingUsage` during fetch
3. Show `CategoryDeleteModal` when `deleteId` is set and `usage` is loaded
4. On confirm: call `deleteCategory(id)`, refresh, reset state
5. All Delete buttons in the table are disabled while `loadingUsage` is true

## Error handling

- Usage fetch fails → show error alert, do not open modal
- Delete fails → show error alert inside modal (stay open)

## Testing

**Backend:**
- `getCategoryUsage` returns correct activity and package names
- `getCategoryUsage` returns empty lists when category has no associations
- `deleteCategory` removes category from activity join table and deletes
- `deleteCategory` removes category from package join table and deletes
- `deleteCategory` on category with no associations still deletes cleanly

**Frontend:** manual verification of modal appearance and flow for each case (no usage, activities only, packages only, both).
