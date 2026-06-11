import {useCallback, useEffect, useState} from 'react';
import {Alert, Button, Card, Col, Form, Modal, Row, Spinner} from 'react-bootstrap';
import {formatAmount} from '../utils/format';
import {toggleArrayItem} from '../utils/toggleArrayItem';
import {useAdminCrud} from '../hooks/useAdminCrud';
import AdminTable from '../components/AdminTable';
import DeleteConfirmModal from '../components/DeleteConfirmModal';
import ImageUploadField from '../components/ImageUploadField';
import PackageActivityPicker from '../components/admin/PackageActivityPicker';

const EMPTY_FORM = {
    name: '',
    slug: '',
    description: '',
    destinationId: '',
    discountPct: '',
    duration: '',
    categoryIds: [],
    imageUrl: '',
    includes: '',
    activities: [],
};

const COLUMNS = [
    {key: 'name', label: 'Name'},
    {key: 'slug', label: 'Slug'},
    {key: 'destination', label: 'Destination'},
    {key: 'activityCount', label: '# Activities'},
    {key: 'originalPrice', label: 'Original Price'},
    {key: 'discountedPrice', label: 'Discounted Price'},
    {key: 'discountPct', label: 'Discount %'},
];

function AdminPackages() {
    const [destinations, setDestinations] = useState([]);
    const [categories, setCategories] = useState([]);
    const [destinationActivities, setDestinationActivities] = useState([]);

    const {
        items: packages, loading, error, setError, page, setPage,
        totalPages, totalElements, showModal, setShowModal, editing,
        form, setForm, saving, saveError, setSaveError, uploading, setUploading, deleteId, setDeleteId,
        fetchData, openCreate, openEdit, handleSave, handleDelete, adminApi,
    } = useAdminCrud({
        emptyForm: EMPTY_FORM,
        fetchFn: useCallback((api, page, size) => api.getPackagesPaged(page, size), []),
        createFn: (api, payload) => api.createPackage(payload),
        updateFn: (api, id, payload) => api.updatePackage(id, payload),
        deleteFn: (api, id) => api.deletePackage(id),
        mapItemToForm: (p) => ({
            name: p.name || '',
            slug: p.slug || '',
            description: p.description || '',
            destinationId: p.destinationId || '',
            discountPct: p.discountPct ?? '',
            duration: p.duration ?? '',
            categoryIds: p.categoryIds || (p.categories || []).map(c => c.id),
            imageUrl: p.imageUrl || '',
            includes: p.includes || '',
            activities: (p.activities || []).map(a => ({
                activityId: a.activityId,
                position: a.position,
                name: a.name,
                price: a.price,
                imageUrl: a.imageUrl,
                duration: a.duration,
            })),
        }),
        buildPayload: (form) => ({
            ...form,
            discountPct: form.discountPct !== '' ? Number(form.discountPct) : null,
            duration: form.duration !== '' ? Number(form.duration) : null,
            activities: form.activities.map((a, i) => ({activityId: a.activityId, position: i})),
        }),
    });

    useEffect(() => {
        adminApi.getDestinations().then(setDestinations).catch(() => {});
        adminApi.getCategories().then(setCategories).catch(() => {});
    }, [adminApi]);

    useEffect(() => {
        if (!form.destinationId) {
            setDestinationActivities([]);
            return;
        }
        adminApi.getActivities()
            .then(all => setDestinationActivities(all.filter(a => a.destinationId === form.destinationId)))
            .catch(() => setDestinationActivities([]));
    }, [adminApi, form.destinationId]);

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
    };

    const toggleCategory = (categoryId) =>
        setForm({...form, categoryIds: toggleArrayItem(form.categoryIds || [], categoryId)});

    const activitiesTotal = form.activities.reduce((s, a) => s + Number(a.price || 0), 0);
    const discountPct = Number(form.discountPct) || 0;

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
                <h4 className="fw-bold mb-0">Packages</h4>
                <div className="d-flex gap-2">
                    <Button variant="outline-secondary" size="sm" onClick={fetchData}>Refresh</Button>
                    <Button variant="primary" size="sm" onClick={openCreate}>+ Add Package</Button>
                </div>
            </div>

            {error && (
                <Alert variant="danger" dismissible onClose={() => setError('')}>{error}</Alert>
            )}

            <Card className="border-0 shadow-sm">
                <Card.Header className="border-bottom">
                    <h6 className="fw-semibold mb-0">
                        {totalElements} {totalElements === 1 ? 'package' : 'packages'}
                    </h6>
                </Card.Header>
                <Card.Body className="p-0">
                    <AdminTable
                        columns={COLUMNS}
                        items={packages}
                        page={page}
                        totalPages={totalPages}
                        onPageChange={setPage}
                        emptyMessage="No packages found."
                        renderRow={(pkg) => (
                                <tr key={pkg.id}>
                                    <td>
                                        <div className="small fw-semibold">{pkg.name}</div>
                                    </td>
                                    <td className="small text-muted">{pkg.slug || '—'}</td>
                                    <td className="small">{pkg.destinationName || '—'}</td>
                                    <td className="small">{(pkg.activities || []).length}</td>
                                    <td className="small">{formatAmount(pkg.originalPrice)}</td>
                                    <td className="small fw-semibold">{formatAmount(pkg.discountedPrice)}</td>
                                    <td className="small">{pkg.discountPct != null ? `${pkg.discountPct}%` : '—'}</td>
                                    <td className="text-end">
                                        <Button variant="outline-primary" size="sm" className="me-1"
                                                onClick={() => openEdit(pkg)}>
                                            Edit
                                        </Button>
                                        <Button variant="outline-danger" size="sm"
                                                onClick={() => setDeleteId(pkg.id)}>
                                            Delete
                                        </Button>
                                    </td>
                                </tr>
                        )}
                    />
                </Card.Body>
            </Card>

            <Modal show={showModal} onHide={() => setShowModal(false)} centered size="lg">
                <Modal.Header closeButton className="text-white" data-bs-theme="dark">
                    <Modal.Title className="fs-5">
                        {editing ? `Edit — ${form.name || editing.name}` : (form.name ? `New — ${form.name}` : 'New Package')}
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
                                onChange={e => handleDestinationChange(e.target.value)}
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
                                placeholder="Package name"
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
                                <Form.Label className="small fw-semibold text-white">Discount %</Form.Label>
                                <Form.Control
                                    type="number"
                                    step="0.01"
                                    min="0"
                                    max="100"
                                    value={form.discountPct}
                                    onChange={e => setForm({...form, discountPct: e.target.value})}
                                    placeholder="0.00"
                                />
                            </Col>
                            <Col sm={6}>
                                <Form.Label className="small fw-semibold text-white">Duration (hours)</Form.Label>
                                <Form.Control
                                    type="number"
                                    min="0"
                                    value={form.duration}
                                    onChange={e => setForm({...form, duration: e.target.value})}
                                    placeholder="e.g. 8"
                                />
                            </Col>
                        </Row>
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
                                        id={`pkg-category-${c.id}`}
                                        label={c.name}
                                        checked={(form.categoryIds || []).includes(c.id)}
                                        onChange={() => toggleCategory(c.id)}
                                    />
                                ))}
                            </div>
                        </Form.Group>
                        <Form.Group className="mb-3">
                            <Form.Label className="small fw-semibold text-white">Activities</Form.Label>
                            <PackageActivityPicker
                                value={form.activities}
                                onChange={(activities) => setForm({...form, activities})}
                                availableActivities={destinationActivities}
                                disabled={!form.destinationId}
                            />
                            {form.activities.length > 0 && (
                                <div className="text-muted small mt-2">
                                    Activities total: {formatAmount(activitiesTotal)}<br/>
                                    Discount ({discountPct}%): −{formatAmount(activitiesTotal * discountPct / 100)}<br/>
                                    <strong>Final price: {formatAmount(activitiesTotal * (100 - discountPct) / 100)}</strong>
                                </div>
                            )}
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
                    <Button
                        variant="primary"
                        size="sm"
                        onClick={handleSave}
                        disabled={saving || uploading || !form.name || !form.destinationId || form.discountPct === '' || form.activities.length === 0}
                    >
                        {saving ? <Spinner animation="border" size="sm"/> : (editing ? 'Save Changes' : 'Create')}
                    </Button>
                </Modal.Footer>
            </Modal>

            <DeleteConfirmModal
                show={!!deleteId}
                onHide={() => setDeleteId(null)}
                onConfirm={handleDelete}
                saving={saving}
            />
        </>
    );
}

export default AdminPackages;
