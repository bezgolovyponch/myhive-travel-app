import {useCallback, useState} from 'react';
import {Alert, Button, Card, Form, Modal, Spinner} from 'react-bootstrap';
import {useAdminCrud} from '../hooks/useAdminCrud';
import {useAuthErrorHandler} from '../hooks/useAuthErrorHandler';
import AdminTable from '../components/AdminTable';
import CategoryDeleteModal from '../components/admin/CategoryDeleteModal';
import SaveErrorAlert from '../components/admin/SaveErrorAlert';

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
    // Track which row's usage lookup is running, so one click doesn't
    // spinner-disable every Delete button in the table.
    const [loadingUsageId, setLoadingUsageId] = useState(null);
    const [deleting, setDeleting] = useState(false);
    const handleAuthError = useAuthErrorHandler();

    const {
        items: categories, loading, error, setError, page, setPage,
        totalPages, totalElements, showModal, setShowModal, editing,
        form, setForm, saving, saveError, setSaveError, fetchData, openCreate, openEdit, handleSave, adminApi,
    } = useAdminCrud({
        emptyForm: EMPTY_FORM,
        fetchFn: useCallback((api, page, size) => api.getCategoriesPaged(page, size), []),
        createFn: (api, payload) => api.createCategory(payload),
        updateFn: (api, id, payload) => api.updateCategory(id, payload),
        deleteFn: (api, id) => api.deleteCategory(id),
        mapItemToForm: (c) => ({
            name: c.name || '',
            slug: c.slug || '',
        }),
    });

    const handleDeleteClick = async (category) => {
        setLoadingUsageId(category.id);
        setError('');
        try {
            const result = await adminApi.getCategoryUsage(category.id);
            setUsage(result);
            setDeleteTarget(category);
        } catch (e) {
            if (handleAuthError(e)) {
                return;
            }
            setError(e.message || 'Failed to load category usage');
        } finally {
            setLoadingUsageId(null);
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
            if (handleAuthError(e)) {
                return;
            }
            setError(e.message || 'Failed to delete category');
        } finally {
            setDeleting(false);
        }
    };

    const handleDeleteHide = () => {
        setDeleteTarget(null);
        setUsage(null);
    };

    // Spinner only on the initial load — page changes keep the table mounted.
    if (loading && categories.length === 0) {
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
                                    {/* All rows disable while one lookup runs (no concurrent
                                        lookups — the slower one would retarget the open modal),
                                        but only the clicked row shows the spinner. */}
                                    <Button
                                        variant="outline-danger"
                                        size="sm"
                                        disabled={loadingUsageId !== null}
                                        onClick={() => handleDeleteClick(category)}
                                    >
                                        {loadingUsageId === category.id ? <Spinner animation="border" size="sm"/> : 'Delete'}
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
                    <SaveErrorAlert error={saveError} onClose={() => setSaveError('')}/>
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
