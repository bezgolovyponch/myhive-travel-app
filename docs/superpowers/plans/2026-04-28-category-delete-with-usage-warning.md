# Category Delete with Usage Warning — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the blocking category delete behavior with an informative warning modal that shows affected activities and packages by name, then cascade-removes the category from all join tables on confirm.

**Architecture:** A new `GET /admin/categories/{id}/usage` endpoint returns names of affected entities. The frontend fetches usage on Delete click, shows `CategoryDeleteModal` if there are associations, and the backend `deleteCategory` now disassociates from join tables transactionally before deleting.

**Tech Stack:** Spring Boot 4 / Java 25 / JPA (backend), React 19 / Bootstrap 5 (frontend)

---

### Task 1: Add `findByCategoriesId` to repositories

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/repository/ActivityRepository.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/repository/PackageRepository.java`

- [ ] **Step 1: Add method to ActivityRepository**

Replace the file content — add one line after `findByCategoriesSlug`:

```java
List<Activity> findByCategoriesId(UUID categoryId);
```

Full updated `ActivityRepository.java`:
```java
package com.myhive.backend.repository;

import com.myhive.backend.entity.Activity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {

    Optional<Activity> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Activity> findByDestinationId(UUID destinationId);

    List<Activity> findByCategoriesSlug(String categorySlug);

    List<Activity> findByCategoriesId(UUID categoryId);

    List<Activity> findByDestinationIdAndCategoriesSlug(UUID destinationId, String categorySlug);

    Page<Activity> findByDestinationId(UUID destinationId, Pageable pageable);

    Page<Activity> findByDestinationIdAndCategoriesSlug(UUID destinationId, String categorySlug, Pageable pageable);
}
```

- [ ] **Step 2: Add method to PackageRepository**

Add one method after `findByCategoriesSlug`:

```java
List<Package> findByCategoriesId(UUID categoryId);
```

Full updated `PackageRepository.java`:
```java
package com.myhive.backend.repository;

import com.myhive.backend.entity.Package;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PackageRepository extends JpaRepository<Package, UUID> {

    Optional<Package> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Package> findByDestinationId(UUID destinationId);

    List<Package> findByCategoriesSlug(String categorySlug);

    List<Package> findByCategoriesId(UUID categoryId);

    List<Package> findByDestinationIdAndCategoriesSlug(UUID destinationId, String categorySlug);

    @Query("SELECT p.name FROM Package p JOIN p.packageActivities pa WHERE pa.activity.id = :activityId")
    List<String> findPackageNamesByActivityId(@Param("activityId") UUID activityId);
}
```

- [ ] **Step 3: Verify compilation**

```bash
cd myhive-backend && ./gradlew compileJava
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/repository/ActivityRepository.java \
        myhive-backend/src/main/java/com/myhive/backend/repository/PackageRepository.java
git commit -m "feat: add findByCategoriesId to Activity and Package repositories"
```

---

### Task 2: Create `CategoryUsageDTO` and update `CategoryService`

**Files:**
- Create: `myhive-backend/src/main/java/com/myhive/backend/dto/CategoryUsageDTO.java`
- Modify: `myhive-backend/src/main/java/com/myhive/backend/service/CategoryService.java`
- Modify: `myhive-backend/src/test/java/com/myhive/backend/service/CategoryServiceTest.java`

- [ ] **Step 1: Write failing tests first**

Open `myhive-backend/src/test/java/com/myhive/backend/service/CategoryServiceTest.java`.

Add mocks for the two new repositories at the top of the class (after `categoryRepository`):

```java
@Mock
private ActivityRepository activityRepository;

@Mock
private PackageRepository packageRepository;
```

Add import for both repositories:
```java
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.PackageRepository;
```

Add these new test methods at the end of the class (before closing `}`):

```java
@Test
void getCategoryUsage_withActivityAndPackage_returnsNames() {
    Category category = TestDataFactory.category();
    Destination dest = TestDataFactory.destination();
    Activity activity = TestDataFactory.activity(dest);
    activity.setName("Hiking Tour");
    com.myhive.backend.entity.Package pkg = new com.myhive.backend.entity.Package();
    pkg.setName("Explorer Pack");

    when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
    when(activityRepository.findByCategoriesId(category.getId())).thenReturn(List.of(activity));
    when(packageRepository.findByCategoriesId(category.getId())).thenReturn(List.of(pkg));

    CategoryUsageDTO result = categoryService.getCategoryUsage(category.getId());

    assertThat(result.getActivityNames()).containsExactly("Hiking Tour");
    assertThat(result.getPackageNames()).containsExactly("Explorer Pack");
}

@Test
void getCategoryUsage_noAssociations_returnsEmptyLists() {
    Category category = TestDataFactory.category();
    when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
    when(activityRepository.findByCategoriesId(category.getId())).thenReturn(List.of());
    when(packageRepository.findByCategoriesId(category.getId())).thenReturn(List.of());

    CategoryUsageDTO result = categoryService.getCategoryUsage(category.getId());

    assertThat(result.getActivityNames()).isEmpty();
    assertThat(result.getPackageNames()).isEmpty();
}

@Test
void deleteCategory_withActivities_removesFromActivitiesAndDeletes() {
    Category category = TestDataFactory.category();
    Destination dest = TestDataFactory.destination();
    Activity activity = TestDataFactory.activity(dest);
    activity.setCategories(new HashSet<>(Set.of(category)));

    when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
    when(activityRepository.findByCategoriesId(category.getId())).thenReturn(List.of(activity));
    when(packageRepository.findByCategoriesId(category.getId())).thenReturn(List.of());

    categoryService.deleteCategory(category.getId());

    assertThat(activity.getCategories()).doesNotContain(category);
    verify(activityRepository).save(activity);
    verify(categoryRepository).deleteById(category.getId());
}

@Test
void deleteCategory_withPackages_removesFromPackagesAndDeletes() {
    Category category = TestDataFactory.category();
    com.myhive.backend.entity.Package pkg = new com.myhive.backend.entity.Package();
    pkg.setCategories(new HashSet<>(Set.of(category)));

    when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
    when(activityRepository.findByCategoriesId(category.getId())).thenReturn(List.of());
    when(packageRepository.findByCategoriesId(category.getId())).thenReturn(List.of(pkg));

    categoryService.deleteCategory(category.getId());

    assertThat(pkg.getCategories()).doesNotContain(category);
    verify(packageRepository).save(pkg);
    verify(categoryRepository).deleteById(category.getId());
}
```

Also update existing `deleteCategory_withActivities_throwsBadRequest` test — rename it and change its assertion since `deleteCategory` no longer throws:

Remove the old test entirely and replace with the new `deleteCategory_withActivities_removesFromActivitiesAndDeletes` above (already done). Delete the old one:

```java
// DELETE this test — behavior has changed:
// void deleteCategory_withActivities_throwsBadRequest()
```

Also add import for `Set` and `HashSet` if not present:
```java
import java.util.Set;
```

Add import for `CategoryUsageDTO`:
```java
import com.myhive.backend.dto.CategoryUsageDTO;
```

Add import for `Activity`:
```java
import com.myhive.backend.entity.Activity;
```
(Already present — check before adding.)

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd myhive-backend && ./gradlew test --tests '*CategoryServiceTest'
```
Expected: FAIL — `CategoryUsageDTO` not found, `getCategoryUsage` not found, `activityRepository` / `packageRepository` not injected.

- [ ] **Step 3: Create `CategoryUsageDTO`**

Create file `myhive-backend/src/main/java/com/myhive/backend/dto/CategoryUsageDTO.java`:

```java
package com.myhive.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class CategoryUsageDTO {

    private final List<String> activityNames;
    private final List<String> packageNames;
}
```

- [ ] **Step 4: Update `CategoryService`**

Replace the full content of `CategoryService.java`:

```java
package com.myhive.backend.service;

import com.myhive.backend.dto.CategoryDTO;
import com.myhive.backend.dto.CategoryUsageDTO;
import com.myhive.backend.entity.Activity;
import com.myhive.backend.entity.Category;
import com.myhive.backend.entity.Package;
import com.myhive.backend.exception.BadRequestException;
import com.myhive.backend.exception.ResourceNotFoundException;
import com.myhive.backend.repository.ActivityRepository;
import com.myhive.backend.repository.CategoryRepository;
import com.myhive.backend.repository.PackageRepository;
import com.myhive.backend.util.SlugUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ActivityRepository activityRepository;
    private final PackageRepository packageRepository;

    public List<CategoryDTO> getAllCategories() {
        return categoryRepository.findAll().stream()
                .sorted(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::convertToDTO)
                .toList();
    }

    public Page<CategoryDTO> getCategoriesPaged(Pageable pageable) {
        return categoryRepository.findAll(pageable)
                .map(this::convertToDTO);
    }

    public CategoryDTO getCategoryById(UUID id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
        return convertToDTO(category);
    }

    public CategoryDTO getCategoryBySlug(String slug) {
        Category category = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        return convertToDTO(category);
    }

    public CategoryUsageDTO getCategoryUsage(UUID id) {
        categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
        List<String> activityNames = activityRepository.findByCategoriesId(id).stream()
                .map(Activity::getName)
                .toList();
        List<String> packageNames = packageRepository.findByCategoriesId(id).stream()
                .map(Package::getName)
                .toList();
        return new CategoryUsageDTO(activityNames, packageNames);
    }

    @Transactional
    public CategoryDTO createCategory(CategoryDTO dto) {
        if (categoryRepository.existsByNameIgnoreCase(dto.getName())) {
            throw new BadRequestException("Category with name '" + dto.getName() + "' already exists");
        }
        Category category = new Category();
        category.setName(dto.getName());
        category.setSlug(SlugUtils.resolveSlug(dto.getSlug(), dto.getName(), categoryRepository::existsBySlug));
        try {
            return convertToDTO(categoryRepository.save(category));
        } catch (DataIntegrityViolationException e) {
            category.setSlug(SlugUtils.resolveSlug(dto.getSlug(), dto.getName(), categoryRepository::existsBySlug));
            return convertToDTO(categoryRepository.save(category));
        }
    }

    @Transactional
    public CategoryDTO updateCategory(UUID id, CategoryDTO dto) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));

        if (!category.getName().equalsIgnoreCase(dto.getName())
                && categoryRepository.existsByNameIgnoreCase(dto.getName())) {
            throw new BadRequestException("Category with name '" + dto.getName() + "' already exists");
        }

        boolean updateSlug = SlugUtils.needsUpdate(dto.getSlug(), category.getSlug(), dto.getName(), category.getName());
        category.setName(dto.getName());
        if (updateSlug) {
            category.setSlug(SlugUtils.resolveForUpdate(dto.getSlug(), dto.getName(), category.getSlug(),
                    slug -> categoryRepository.findBySlug(slug)
                            .filter(c -> !c.getId().equals(id))
                            .isPresent()));
        }
        return convertToDTO(categoryRepository.save(category));
    }

    @Transactional
    public void deleteCategory(UUID id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
        for (Activity activity : activityRepository.findByCategoriesId(id)) {
            activity.getCategories().remove(category);
            activityRepository.save(activity);
        }
        for (Package pkg : packageRepository.findByCategoriesId(id)) {
            pkg.getCategories().remove(category);
            packageRepository.save(pkg);
        }
        categoryRepository.deleteById(id);
    }

    private CategoryDTO convertToDTO(Category category) {
        CategoryDTO dto = new CategoryDTO();
        dto.setId(category.getId());
        dto.setName(category.getName());
        dto.setSlug(category.getSlug());
        return dto;
    }
}
```

- [ ] **Step 5: Run tests**

```bash
cd myhive-backend && ./gradlew test --tests '*CategoryServiceTest'
```
Expected: all tests PASS including the new ones. The old `deleteCategory_withActivities_throwsBadRequest` test should be gone (deleted in step 1).

- [ ] **Step 6: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/dto/CategoryUsageDTO.java \
        myhive-backend/src/main/java/com/myhive/backend/service/CategoryService.java \
        myhive-backend/src/test/java/com/myhive/backend/service/CategoryServiceTest.java
git commit -m "feat: add getCategoryUsage and cascade deleteCategory in CategoryService"
```

---

### Task 3: Add usage endpoint to AdminController and integration tests

**Files:**
- Modify: `myhive-backend/src/main/java/com/myhive/backend/controller/AdminController.java`
- Modify: `myhive-backend/src/test/java/com/myhive/backend/controller/AdminControllerIntegrationTest.java`

- [ ] **Step 1: Write failing integration tests**

Open `AdminControllerIntegrationTest.java`. Add import:
```java
import com.myhive.backend.entity.Package;
import com.myhive.backend.repository.PackageRepository;
import java.math.BigDecimal;
```
(check before adding — `BigDecimal` may already be imported)

Add `PackageRepository` autowired field:
```java
@Autowired
private PackageRepository packageRepository;
```

Add a `categoryId` field alongside existing `destinationId` and `activityId`:
```java
private UUID categoryId;
```

In `setUp()`, create and save a category and assign it to the existing activity. Add after saving the activity:
```java
com.myhive.backend.entity.Category category = new com.myhive.backend.entity.Category();
category.setName("Test Category");
category.setSlug("test-category");
category = categoryRepository.save(category);
categoryId = category.getId();

activity.getCategories().add(category);
activityRepository.save(activity);
```

You'll need to also autowire `CategoryRepository`:
```java
@Autowired
private com.myhive.backend.repository.CategoryRepository categoryRepository;
```

Now add the new test methods at the end of the class (before closing `}`):

```java
@Test
void getCategoryUsage_withActivity_returnsActivityName() throws Exception {
    String expectedActivityName = "Eiffel Tour";

    mockMvc.perform(get("/admin/categories/" + categoryId + "/usage")
                    .with(adminJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activityNames[0]", is(expectedActivityName)))
            .andExpect(jsonPath("$.packageNames").isEmpty());
}

@Test
void getCategoryUsage_nonexistent_returns404() throws Exception {
    mockMvc.perform(get("/admin/categories/" + UUID.randomUUID() + "/usage")
                    .with(adminJwt()))
            .andExpect(status().isNotFound());
}

@Test
void deleteCategory_withActivity_removesFromActivityAndDeletes() throws Exception {
    mockMvc.perform(delete("/admin/categories/" + categoryId)
                    .with(adminJwt()))
            .andExpect(status().isNoContent());

    mockMvc.perform(get("/admin/categories/" + categoryId + "/usage")
                    .with(adminJwt()))
            .andExpect(status().isNotFound());
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd myhive-backend && ./gradlew test --tests '*AdminControllerIntegrationTest'
```
Expected: FAIL — `/admin/categories/{id}/usage` endpoint not found (404).

- [ ] **Step 3: Add endpoint to AdminController**

Add import at the top of `AdminController.java`:
```java
import com.myhive.backend.dto.CategoryUsageDTO;
```

Add the new endpoint after the existing `deleteCategory` mapping (around line 214):

```java
@GetMapping("/categories/{id}/usage")
public ResponseEntity<CategoryUsageDTO> getCategoryUsage(@PathVariable UUID id) {
    return ResponseEntity.ok(categoryService.getCategoryUsage(id));
}
```

- [ ] **Step 4: Run tests**

```bash
cd myhive-backend && ./gradlew test --tests '*AdminControllerIntegrationTest'
```
Expected: all tests PASS.

- [ ] **Step 5: Run full test suite**

```bash
cd myhive-backend && ./gradlew test
```
Expected: BUILD SUCCESSFUL, no failures.

- [ ] **Step 6: Commit**

```bash
git add myhive-backend/src/main/java/com/myhive/backend/controller/AdminController.java \
        myhive-backend/src/test/java/com/myhive/backend/controller/AdminControllerIntegrationTest.java
git commit -m "feat: add GET /admin/categories/{id}/usage endpoint"
```

---

### Task 4: Frontend — add `getCategoryUsage` to adminApi

**Files:**
- Modify: `myhive-react-app/src/services/adminApi.js`

- [ ] **Step 1: Add the method**

Find the `deleteCategory` method in `adminApi.js`. Add `getCategoryUsage` directly before it:

```js
async getCategoryUsage(id) {
    const response = await fetch(`${API_BASE_URL}/admin/categories/${id}/usage`, {
        headers: await authHeaders(),
    });
    await handleError(response, 'Failed to fetch category usage');
    return response.json();
},
```

- [ ] **Step 2: Verify no syntax errors**

```bash
cd myhive-react-app && node --input-type=module < /dev/null || npx react-scripts build 2>&1 | head -20
```

Or simply start the dev server briefly:
```bash
cd myhive-react-app && npm start &
sleep 5 && kill %1
```
Expected: compiles without errors.

- [ ] **Step 3: Commit**

```bash
git add myhive-react-app/src/services/adminApi.js
git commit -m "feat: add getCategoryUsage to adminApi"
```

---

### Task 5: Create `CategoryDeleteModal` component

**Files:**
- Create: `myhive-react-app/src/components/admin/CategoryDeleteModal.js`

- [ ] **Step 1: Create the component**

```jsx
import {Badge, Button, Modal, Spinner} from 'react-bootstrap';

const MAX_VISIBLE = 5;

function NameList({names, label}) {
    if (names.length === 0) {
        return null;
    }
    const visible = names.slice(0, MAX_VISIBLE);
    const remainder = names.length - MAX_VISIBLE;
    return (
        <div className="mb-2">
            <div className="small text-muted mb-1">{label} ({names.length})</div>
            <div className="d-flex flex-wrap gap-1">
                {visible.map(name => (
                    <Badge key={name} bg="secondary">{name}</Badge>
                ))}
                {remainder > 0 && (
                    <Badge bg="secondary">...and {remainder} more</Badge>
                )}
            </div>
        </div>
    );
}

function CategoryDeleteModal({show, onHide, onConfirm, saving, categoryName, usage}) {
    const hasUsage = usage && (usage.activityNames.length > 0 || usage.packageNames.length > 0);

    return (
        <Modal show={show} onHide={onHide} centered>
            <Modal.Body className="py-4 px-4">
                <div className="fw-semibold mb-2">Delete "{categoryName}"?</div>
                {hasUsage ? (
                    <>
                        <div className="text-muted small mb-3">
                            This category will be removed from:
                        </div>
                        <NameList names={usage.activityNames} label="Activities"/>
                        <NameList names={usage.packageNames} label="Packages"/>
                    </>
                ) : (
                    <div className="text-muted small mb-3">This action cannot be undone.</div>
                )}
                <div className="d-flex justify-content-end gap-2 mt-3">
                    <Button variant="outline-secondary" size="sm" onClick={onHide}>
                        Cancel
                    </Button>
                    <Button variant="danger" size="sm" onClick={onConfirm} disabled={saving}>
                        {saving ? <Spinner animation="border" size="sm"/> : 'Delete anyway'}
                    </Button>
                </div>
            </Modal.Body>
        </Modal>
    );
}

export default CategoryDeleteModal;
```

- [ ] **Step 2: Commit**

```bash
git add myhive-react-app/src/components/admin/CategoryDeleteModal.js
git commit -m "feat: add CategoryDeleteModal component with usage warning"
```

---

### Task 6: Update `AdminCategories.js` to use new delete flow

**Files:**
- Modify: `myhive-react-app/src/pages/AdminCategories.js`

- [ ] **Step 1: Replace the full file content**

```jsx
import {useState} from 'react';
import {Alert, Button, Card, Form, Modal, Spinner} from 'react-bootstrap';
import {useAdminCrud} from '../hooks/useAdminCrud';
import AdminTable from '../components/AdminTable';
import CategoryDeleteModal from '../components/admin/CategoryDeleteModal';

const EMPTY_FORM = {
    name: '',
    slug: '',
};

const COLUMNS = [
    {key: 'name', label: 'Name'},
    {key: 'slug', label: 'Slug'},
];

function AdminCategories() {
    const [deleteTarget, setDeleteTarget] = useState(null);
    const [usage, setUsage] = useState(null);
    const [loadingUsage, setLoadingUsage] = useState(false);
    const [deleting, setDeleting] = useState(false);

    const {
        items: categories, loading, error, setError, page, setPage,
        totalPages, totalElements, showModal, setShowModal, editing,
        form, setForm, saving, fetchData, openCreate, openEdit, handleSave, adminApi,
    } = useAdminCrud({
        emptyForm: EMPTY_FORM,
        fetchFn: (api, page, size) => api.getCategoriesPaged(page, size),
        createFn: (api, payload) => api.createCategory(payload),
        updateFn: (api, id, payload) => api.updateCategory(id, payload),
        deleteFn: (api, id) => api.deleteCategory(id),
        mapItemToForm: (c) => ({
            name: c.name || '',
            slug: c.slug || '',
        }),
    });

    const handleDeleteClick = async (category) => {
        setLoadingUsage(true);
        setError('');
        try {
            const result = await adminApi.getCategoryUsage(category.id);
            setUsage(result);
            setDeleteTarget(category);
        } catch (e) {
            setError(e.message || 'Failed to load category usage');
        } finally {
            setLoadingUsage(false);
        }
    };

    const handleDeleteConfirm = async () => {
        setDeleting(true);
        setError('');
        try {
            await adminApi.deleteCategory(deleteTarget.id);
            setDeleteTarget(null);
            setUsage(null);
            await fetchData();
        } catch (e) {
            setError(e.message || 'Failed to delete category');
        } finally {
            setDeleting(false);
        }
    };

    const handleDeleteHide = () => {
        setDeleteTarget(null);
        setUsage(null);
    };

    if (loading) {
        return (
            <div className="d-flex justify-content-center py-5">
                <Spinner animation="border" variant="primary"/>
            </div>
        );
    }

    return (
        <>
            <div className="d-flex align-items-center justify-content-between mb-4">
                <h4 className="fw-bold mb-0">Categories</h4>
                <div className="d-flex gap-2">
                    <Button variant="outline-secondary" size="sm" onClick={fetchData}>Refresh</Button>
                    <Button variant="primary" size="sm" onClick={openCreate}>+ Add Category</Button>
                </div>
            </div>

            {error && (
                <Alert variant="danger" dismissible onClose={() => setError('')}>{error}</Alert>
            )}

            <Card className="border-0 shadow-sm">
                <Card.Header className="border-bottom">
                    <h6 className="fw-semibold mb-0">
                        {totalElements} {totalElements === 1 ? 'category' : 'categories'}
                    </h6>
                </Card.Header>
                <Card.Body className="p-0">
                    <AdminTable
                        columns={COLUMNS}
                        items={categories}
                        page={page}
                        totalPages={totalPages}
                        onPageChange={setPage}
                        emptyMessage="No categories found."
                        renderRow={(category) => (
                            <tr key={category.id}>
                                <td className="small fw-semibold">{category.name}</td>
                                <td className="small text-muted">{category.slug || '—'}</td>
                                <td className="text-end">
                                    <Button variant="outline-primary" size="sm" className="me-1"
                                            onClick={() => openEdit(category)}>
                                        Edit
                                    </Button>
                                    <Button
                                        variant="outline-danger"
                                        size="sm"
                                        disabled={loadingUsage}
                                        onClick={() => handleDeleteClick(category)}
                                    >
                                        {loadingUsage ? <Spinner animation="border" size="sm"/> : 'Delete'}
                                    </Button>
                                </td>
                            </tr>
                        )}
                    />
                </Card.Body>
            </Card>

            <Modal show={showModal} onHide={() => setShowModal(false)} centered>
                <Modal.Header closeButton className="text-white" data-bs-theme="dark">
                    <Modal.Title className="fs-5">
                        {editing ? `Edit — ${form.name || editing.name}` : (form.name ? `New — ${form.name}` : 'New Category')}
                    </Modal.Title>
                </Modal.Header>
                <Modal.Body data-bs-theme="dark">
                    <Form>
                        <Form.Group className="mb-3">
                            <Form.Label className="small fw-semibold text-white">Name</Form.Label>
                            <Form.Control
                                value={form.name}
                                onChange={e => setForm({...form, name: e.target.value})}
                                placeholder="e.g. Nightlife, Adventure, Culture"
                            />
                        </Form.Group>
                        <Form.Group className="mb-3">
                            <Form.Label className="small fw-semibold text-white">Slug</Form.Label>
                            <Form.Control
                                value={form.slug}
                                onChange={e => setForm({...form, slug: e.target.value})}
                                placeholder="Leave blank to auto-generate from name"
                            />
                        </Form.Group>
                    </Form>
                </Modal.Body>
                <Modal.Footer>
                    <Button variant="outline-secondary" size="sm" onClick={() => setShowModal(false)}>
                        Cancel
                    </Button>
                    <Button variant="primary" size="sm" onClick={handleSave}
                            disabled={saving || !form.name}>
                        {saving ? <Spinner animation="border" size="sm"/> : (editing ? 'Save Changes' : 'Create')}
                    </Button>
                </Modal.Footer>
            </Modal>

            <CategoryDeleteModal
                show={!!deleteTarget}
                onHide={handleDeleteHide}
                onConfirm={handleDeleteConfirm}
                saving={deleting}
                categoryName={deleteTarget?.name || ''}
                usage={usage}
            />
        </>
    );
}

export default AdminCategories;
```

- [ ] **Step 2: Start the dev server and verify manually**

```bash
cd myhive-react-app && npm start
```

Test these scenarios in the browser at `http://localhost:3000` (log in as admin):
1. Click Delete on a category with no associations → modal opens, shows "This action cannot be undone.", "Delete anyway" button works
2. Click Delete on a category used in activities → modal shows activity names as badges
3. Click Delete on a category used in packages → modal shows package names as badges
4. Cancel → modal closes, nothing deleted
5. Confirm → category deleted, list refreshes, deleted category is gone

- [ ] **Step 3: Commit**

```bash
git add myhive-react-app/src/pages/AdminCategories.js
git commit -m "feat: replace blocking category delete with usage warning modal"
```
