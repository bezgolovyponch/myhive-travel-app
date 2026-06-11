import {useCallback, useEffect, useState} from 'react';
import {Alert, Badge, Button, Card, Col, Form, Modal, Row, Spinner} from 'react-bootstrap';
import {formatAmount, truncateText} from '../utils/format';
import {toggleArrayItem} from '../utils/toggleArrayItem';
import {useAdminCrud} from '../hooks/useAdminCrud';
import {useAuthErrorHandler} from '../hooks/useAuthErrorHandler';
import AdminTable from '../components/AdminTable';
import DeleteConfirmModal from '../components/DeleteConfirmModal';
import ImageUploadField from '../components/ImageUploadField';
import ImportActivitiesModal from '../components/admin/ImportActivitiesModal';

const EMPTY_FORM = {
    name: '',
    slug: '',
    description: '',
    price: '',
    duration: '',
    featuredWeight: 0,
    featured: false,
    categoryIds: [],
    imageUrl: '',
    includes: '',
    destinationId: '',
};

const COLUMNS = [
    {key: 'name', label: 'Name'},
    {key: 'slug', label: 'Slug'},
    {key: 'destination', label: 'Destination'},
    {key: 'categories', label: 'Categories'},
    {key: 'price', label: 'Price'},
    {key: 'duration', label: 'Duration'},
    {key: 'featuredWeight', label: 'Weight'},
    {key: 'featured', label: 'Featured'},
];

function AdminActivities() {
    const [destinations, setDestinations] = useState([]);
    const [categories, setCategories] = useState([]);
    const [filterDestination, setFilterDestination] = useState('');
    const [showImportModal, setShowImportModal] = useState(false);
    const handleAuthError = useAuthErrorHandler();

    const fetchFn = useCallback(
        (api, page, size) => api.getActivitiesPaged(page, size, filterDestination || null),
        [filterDestination]
    );

    const {
        items: activities, loading, error, setError, page, setPage,
        totalPages, totalElements, showModal, setShowModal, editing,
        form, setForm, saving, setSaving, saveError, setSaveError, uploading, setUploading, deleteId, setDeleteId,
        fetchData, openCreate, openEdit, handleSave, adminApi,
    } = useAdminCrud({
        emptyForm: EMPTY_FORM,
        fetchFn,
        createFn: (api, payload) => api.createActivity(payload),
        updateFn: (api, id, payload) => api.updateActivity(id, payload),
        deleteFn: (api, id) => api.deleteActivity(id),
        mapItemToForm: (a) => ({
            name: a.name || '',
            slug: a.slug || '',
            description: a.description || '',
            price: a.price ?? '',
            duration: a.duration ?? '',
            featuredWeight: a.featuredWeight ?? 0,
            featured: a.featured ?? false,
            categoryIds: a.categoryIds || (a.categories || []).map(c => c.id),
            imageUrl: a.imageUrl || '',
            includes: a.includes || '',
            destinationId: a.destinationId || '',
        }),
        buildPayload: (form) => ({
            ...form,
            price: form.price !== '' ? Number(form.price) : null,
            duration: form.duration !== '' ? Number(form.duration) : null,
            featuredWeight: form.featuredWeight !== '' && form.featuredWeight !== null
                ? Number(form.featuredWeight) : 0,
        }),
    });

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

    const handleExport = async () => {
        setError('');
        try {
            await adminApi.exportActivitiesCsv(filterDestination || null);
        } catch (e) {
            setError(e.message || 'Failed to export activities');
        }
    };

    useEffect(() => {
        adminApi.getDestinations().then(setDestinations).catch(() => {
        });
        adminApi.getCategories().then(setCategories).catch(() => {
        });
    }, [adminApi]);

    const toggleCategory = (categoryId) =>
        setForm({...form, categoryIds: toggleArrayItem(form.categoryIds || [], categoryId)});

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
                <h4 className="fw-bold mb-0">Activities</h4>
                <div className="d-flex gap-2">
                    <Button variant="outline-secondary" size="sm" onClick={fetchData}>Refresh</Button>
                    <Button variant="outline-secondary" size="sm" onClick={handleExport}>
                        {filterDestination ? 'Export CSV (filtered)' : 'Export CSV'}
                    </Button>
                    <Button variant="outline-secondary" size="sm" onClick={() => setShowImportModal(true)}>
                        Import CSV
                    </Button>
                    <Button variant="primary" size="sm" onClick={openCreate}>+ Add Activity</Button>
                </div>
            </div>

            {error && (
                <Alert variant="danger" dismissible onClose={() => setError('')}>{error}</Alert>
            )}

            <Card className="border-0 shadow-sm">
                <Card.Header className="border-bottom">
                    <div className="d-flex align-items-center justify-content-between">
                        <h6 className="fw-semibold mb-0">
                            {totalElements} {totalElements === 1 ? 'activity' : 'activities'}
                        </h6>
                        <Form.Select
                            size="sm"
                            style={{width: 'auto'}}
                            value={filterDestination}
                            onChange={e => {
                                setFilterDestination(e.target.value);
                                setPage(0);
                            }}
                        >
                            <option value="">All Destinations</option>
                            {destinations.map(d => (
                                <option key={d.id} value={d.id}>{d.name}</option>
                            ))}
                        </Form.Select>
                    </div>
                </Card.Header>
                <Card.Body className="p-0">
                    <AdminTable
                        columns={COLUMNS}
                        items={activities}
                        page={page}
                        totalPages={totalPages}
                        onPageChange={setPage}
                        emptyMessage="No activities found."
                        renderRow={(activity) => (
                            <tr key={activity.id}>
                                <td>
                                    <div className="small fw-semibold">{activity.name}</div>
                                    {activity.description && (
                                        <div className="text-muted" style={{fontSize: '0.75rem'}}>
                                            {truncateText(activity.description)}
                                        </div>
                                    )}
                                </td>
                                <td className="small text-muted">{activity.slug || '—'}</td>
                                <td className="small">{activity.destinationName || '—'}</td>
                                <td>
                                    {activity.categories && activity.categories.length > 0 ? (
                                        <div className="d-flex flex-wrap gap-1">
                                            {activity.categories.map(c => (
                                                <Badge key={c.id} bg="light" text="dark" className="border">
                                                    {c.name}
                                                </Badge>
                                            ))}
                                        </div>
                                    ) : '—'}
                                </td>
                                <td className="small fw-semibold">{formatAmount(activity.price)}</td>
                                <td className="small">
                                    {activity.duration ? `${activity.duration} min` : '—'}
                                </td>
                                <td className="small">{activity.featuredWeight ?? 0}</td>
                                <td className="small">{activity.featured ? '✓' : '—'}</td>
                                <td className="text-end">
                                    <Button variant="outline-primary" size="sm" className="me-1"
                                            onClick={() => openEdit(activity)}>
                                        Edit
                                    </Button>
                                    <Button variant="outline-danger" size="sm"
                                            onClick={() => setDeleteId(activity.id)}>
                                        Delete
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
                        {editing ? `Edit — ${form.name || editing.name}` : (form.name ? `New — ${form.name}` : 'New Activity')}
                    </Modal.Title>
                </Modal.Header>
                <Modal.Body data-bs-theme="dark">
                    {saveError && (
                        <Alert variant="danger" dismissible onClose={() => setSaveError('')}>{saveError}</Alert>
                    )}
                    <Form>
                        <Form.Group className="mb-3">
                            <Form.Label className="small fw-semibold text-white">Destination</Form.Label>
                            <Form.Select
                                value={form.destinationId}
                                onChange={e => setForm({...form, destinationId: e.target.value})}
                            >
                                <option value="">Select destination...</option>
                                {destinations.map(d => (
                                    <option key={d.id} value={d.id}>{d.name}</option>
                                ))}
                            </Form.Select>
                        </Form.Group>
                        <Form.Group className="mb-3">
                            <Form.Label className="small fw-semibold text-white">Name</Form.Label>
                            <Form.Control
                                value={form.name}
                                onChange={e => setForm({...form, name: e.target.value})}
                                placeholder="Activity name"
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
                        <Form.Group className="mb-3">
                            <Form.Label className="small fw-semibold text-white">Description</Form.Label>
                            <Form.Control
                                as="textarea"
                                rows={3}
                                value={form.description}
                                onChange={e => setForm({...form, description: e.target.value})}
                                placeholder="Description"
                            />
                        </Form.Group>
                        <Row className="g-3 mb-3">
                            <Col sm={6}>
                                <Form.Label className="small fw-semibold text-white">Price per person (€)</Form.Label>
                                <Form.Control
                                    type="number"
                                    step="0.01"
                                    min="0"
                                    value={form.price}
                                    onChange={e => setForm({...form, price: e.target.value})}
                                    placeholder="0.00"
                                />
                            </Col>
                            <Col sm={6}>
                                <Form.Label className="small fw-semibold text-white">Duration (min)</Form.Label>
                                <Form.Control
                                    type="number"
                                    min="0"
                                    value={form.duration}
                                    onChange={e => setForm({...form, duration: e.target.value})}
                                    placeholder="60"
                                />
                            </Col>
                        </Row>
                        <Form.Group className="mb-3">
                            <Form.Label className="small fw-semibold text-white">Featured weight</Form.Label>
                            <Form.Control
                                type="number"
                                min="0"
                                value={form.featuredWeight}
                                onChange={e => setForm({...form, featuredWeight: e.target.value})}
                                placeholder="0"
                            />
                        </Form.Group>
                        <Form.Group className="mb-3">
                            <Form.Check
                                type="switch"
                                id="featured-on-homepage"
                                label="Featured on homepage"
                                className="text-white"
                                checked={!!form.featured}
                                onChange={e => setForm({...form, featured: e.target.checked})}
                            />
                        </Form.Group>
                        <Form.Group className="mb-3">
                            <Form.Label className="small fw-semibold text-white">Categories</Form.Label>
                            <div className="border rounded p-2 d-flex flex-column gap-1"
                                 style={{maxHeight: 200, overflowY: 'auto'}}>
                                {categories.length === 0 ? (
                                    <div className="text-muted small">No categories available. Create one in the
                                        Categories tab.</div>
                                ) : categories.map(c => (
                                    <Form.Check
                                        key={c.id}
                                        type="checkbox"
                                        id={`category-${c.id}`}
                                        label={c.name}
                                        checked={(form.categoryIds || []).includes(c.id)}
                                        onChange={() => toggleCategory(c.id)}
                                    />
                                ))}
                            </div>
                        </Form.Group>
                        <Form.Group className="mb-3">
                            <Form.Label className="small fw-semibold text-white">Includes</Form.Label>
                            <Form.Control
                                as="textarea"
                                rows={2}
                                value={form.includes}
                                onChange={e => setForm({...form, includes: e.target.value})}
                                placeholder="e.g. Transport, guide, lunch, tickets"
                            />
                        </Form.Group>
                        <ImageUploadField
                            imageUrl={form.imageUrl}
                            uploading={uploading}
                            onUpload={async (file) => {
                                setUploading(true);
                                setSaveError('');
                                try {
                                    const {url} = await adminApi.uploadImage(file);
                                    setForm(prev => ({...prev, imageUrl: url}));
                                } finally {
                                    setUploading(false);
                                }
                            }}
                            onError={(err) => {
                                setSaveError(err.message || 'Failed to upload image');
                            }}
                        />
                    </Form>
                </Modal.Body>
                <Modal.Footer>
                    <Button variant="outline-secondary" size="sm" onClick={() => setShowModal(false)}>
                        Cancel
                    </Button>
                    <Button variant="primary" size="sm" onClick={handleSave}
                            disabled={saving || uploading || !form.name || !form.destinationId || !form.price}>
                        {saving ? <Spinner animation="border" size="sm"/> : (editing ? 'Save Changes' : 'Create')}
                    </Button>
                </Modal.Footer>
            </Modal>

            <DeleteConfirmModal
                show={!!deleteId}
                onHide={() => setDeleteId(null)}
                onConfirm={customHandleDelete}
                saving={saving}
            />

            <ImportActivitiesModal
                show={showImportModal}
                onHide={() => setShowImportModal(false)}
                adminApi={adminApi}
                onImported={fetchData}
            />
        </>
    );
}

export default AdminActivities;
