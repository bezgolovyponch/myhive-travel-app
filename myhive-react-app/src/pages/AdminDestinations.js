import {Fragment, useCallback, useEffect, useRef, useState} from 'react';
import {Alert, Badge, Button, Card, Col, Form, Modal, Row, Spinner} from 'react-bootstrap';
import {truncateText} from '../utils/format';
import {useAdminCrud} from '../hooks/useAdminCrud';
import {required, slugFormat} from '../utils/validators';
import AdminTable from '../components/AdminTable';
import DeleteConfirmModal from '../components/DeleteConfirmModal';
import ImageUploadField from '../components/ImageUploadField';
import api from '../services/api';
import {useAdminApi} from '../hooks/useAdminApi';
import AdminDestinationQuiz from './admin/AdminDestinationQuiz';
import SaveErrorAlert from '../components/admin/SaveErrorAlert';

const EMPTY_FORM = {
    name: '',
    slug: '',
    description: '',
    country: '',
    city: '',
    imageUrl: '',
    rating: '',
};

const COLUMNS = [
    {key: 'name', label: 'Name'},
    {key: 'slug', label: 'Slug'},
    {key: 'country', label: 'Country'},
    {key: 'city', label: 'City'},
    {key: 'rating', label: 'Rating'},
    {key: 'activities', label: 'Activities'},
];

function AdminDestinations() {
    const [allCategories, setAllCategories] = useState([]);
    const [selectedCategoryIds, setSelectedCategoryIds] = useState([]);
    const [quizDestinationId, setQuizDestinationId] = useState(null);
    const selectedCategoryIdsRef = useRef([]);
    // Latest-wins guard: opening Edit on A then quickly on B must not let A's
    // slow category fetch overwrite B's selection (and get saved onto B).
    const editSeqRef = useRef(0);
    const categoriesAdminApi = useAdminApi();

    const updateCategorySelection = (ids) => {
        selectedCategoryIdsRef.current = ids;
        setSelectedCategoryIds(ids);
    };

    const toggleCategory = (id) => {
        const next = selectedCategoryIds.includes(id)
            ? selectedCategoryIds.filter(cid => cid !== id)
            : [...selectedCategoryIds, id];
        updateCategorySelection(next);
    };

    const {
        items: destinations, loading, error, setError, page, setPage,
        totalPages, totalElements, showModal, setShowModal, editing,
        form, setForm, saving, saveError, setSaveError, fieldErrors, updateField, uploading, deleteId, setDeleteId,
        handleImageUpload, handleImageUploadError,
        fetchData, openCreate: baseOpenCreate, openEdit: baseOpenEdit, handleSave, handleDelete,
    } = useAdminCrud({
        emptyForm: EMPTY_FORM,
        fetchFn: useCallback((adminApiInstance, page, size) => adminApiInstance.getDestinationsPaged(page, size), []),
        createFn: (adminApiInstance, payload) => adminApiInstance.createDestination(payload),
        updateFn: (adminApiInstance, id, payload) => Promise.all([
            adminApiInstance.updateDestination(id, payload),
            adminApiInstance.updateDestinationCategories(id, selectedCategoryIdsRef.current),
        ]),
        deleteFn: (adminApiInstance, id) => adminApiInstance.deleteDestination(id),
        mapItemToForm: (d) => ({
            name: d.name || '',
            slug: d.slug || '',
            description: d.description || '',
            country: d.country || '',
            city: d.city || '',
            imageUrl: d.imageUrl || '',
            rating: d.rating ?? '',
        }),
        validate: (form) => {
            const errors = {};
            const name = required(form.name);
            if (name) errors.name = name;
            const slug = slugFormat(form.slug);
            if (slug) errors.slug = slug;
            return errors;
        },
        buildPayload: (form) => ({
            ...form,
            rating: form.rating !== '' ? Number(form.rating) : null,
        }),
    });

    useEffect(() => {
        categoriesAdminApi.getCategories().then(setAllCategories).catch(() => {});
    }, [categoriesAdminApi]);

    const openCreate = () => {
        updateCategorySelection([]);
        baseOpenCreate();
    };

    const openEdit = async (destination) => {
        const seq = ++editSeqRef.current;
        updateCategorySelection([]);
        baseOpenEdit(destination);
        try {
            const dest = await api.getDestination(destination.id);
            if (seq !== editSeqRef.current) {
                return;
            }
            updateCategorySelection((dest.assignedCategories || []).map(c => c.id));
        } catch {
            // leave empty on fetch failure
        }
    };

    // Spinner only on the initial load — page changes keep the table mounted.
    if (loading && destinations.length === 0) {
        return (
            <div className="d-flex justify-content-center py-5">
                <Spinner animation="border" variant="primary"/>
            </div>
        );
    }

    return (
        <>
            <div className="d-flex align-items-center justify-content-between mb-4">
                <h4 className="fw-bold mb-0">Destinations</h4>
                <div className="d-flex gap-2">
                    <Button variant="outline-secondary" size="sm" onClick={fetchData}>Refresh</Button>
                    <Button variant="primary" size="sm" onClick={openCreate}>+ Add Destination</Button>
                </div>
            </div>

            {error && (
                <Alert variant="danger" dismissible onClose={() => setError('')}>{error}</Alert>
            )}

            <Card className="border-0 shadow-sm">
                <Card.Header className="border-bottom">
                    <h6 className="fw-semibold mb-0">
                        {totalElements} {totalElements === 1 ? 'destination' : 'destinations'}
                    </h6>
                </Card.Header>
                <Card.Body className="p-0">
                    <AdminTable
                        columns={COLUMNS}
                        items={destinations}
                        page={page}
                        totalPages={totalPages}
                        onPageChange={setPage}
                        emptyMessage="No destinations found."
                        renderRow={(destination) => (
                            <Fragment key={destination.id}>
                                <tr>
                                    <td>
                                        <div className="d-flex align-items-center gap-2">
                                            {destination.imageUrl && (
                                                <img
                                                    src={destination.imageUrl}
                                                    alt={destination.name}
                                                    style={{width: 40, height: 40, borderRadius: 6, objectFit: 'cover'}}
                                                />
                                            )}
                                            <div>
                                                <div className="small fw-semibold">{destination.name}</div>
                                                {destination.description && (
                                                    <div className="text-muted" style={{fontSize: '0.75rem'}}>
                                                        {truncateText(destination.description)}
                                                    </div>
                                                )}
                                            </div>
                                        </div>
                                    </td>
                                    <td className="small text-muted">{destination.slug || '—'}</td>
                                    <td className="small">{destination.country || '—'}</td>
                                    <td className="small">{destination.city || '—'}</td>
                                    <td>
                                        {destination.rating ? (
                                            <Badge bg="light" text="dark" className="border">
                                                {destination.rating}
                                            </Badge>
                                        ) : '—'}
                                    </td>
                                    <td className="small">{destination.activityCount}</td>
                                    <td className="text-end">
                                        <Button variant="outline-primary" size="sm" className="me-1"
                                                onClick={() => openEdit(destination)}>
                                            Edit
                                        </Button>
                                        <Button variant="outline-secondary" size="sm" className="me-1"
                                                onClick={() => setQuizDestinationId(
                                                    quizDestinationId === destination.id ? null : destination.id
                                                )}>
                                            {quizDestinationId === destination.id ? 'Hide quiz' : 'Edit quiz'}
                                        </Button>
                                        <Button variant="outline-danger" size="sm"
                                                onClick={() => setDeleteId(destination.id)}>
                                            Delete
                                        </Button>
                                    </td>
                                </tr>
                                {quizDestinationId === destination.id && (
                                    <tr>
                                        <td colSpan={COLUMNS.length + 1} style={{padding: 0, background: '#f8f9fa'}}>
                                            <AdminDestinationQuiz destinationId={destination.id}/>
                                        </td>
                                    </tr>
                                )}
                            </Fragment>
                        )}
                    />
                </Card.Body>
            </Card>

            <Modal show={showModal} onHide={() => setShowModal(false)} centered>
                <Modal.Header closeButton className="text-white" data-bs-theme="dark">
                    <Modal.Title className="fs-5">
                        {editing ? `Edit — ${form.name || editing.name}` : (form.name ? `New — ${form.name}` : 'New Destination')}
                    </Modal.Title>
                </Modal.Header>
                <Modal.Body data-bs-theme="dark">
                    <SaveErrorAlert error={saveError} onClose={() => setSaveError('')}/>
                    <Form>
                        <Form.Group className="mb-3">
                            <Form.Label className="small fw-semibold text-white">Name</Form.Label>
                            <Form.Control
                                value={form.name}
                                onChange={e => updateField('name', e.target.value)}
                                isInvalid={!!fieldErrors.name}
                                placeholder="Destination name"
                            />
                            <Form.Control.Feedback type="invalid">{fieldErrors.name}</Form.Control.Feedback>
                        </Form.Group>
                        <Form.Group className="mb-3">
                            <Form.Label className="small fw-semibold text-white">Slug</Form.Label>
                            <Form.Control
                                value={form.slug}
                                onChange={e => updateField('slug', e.target.value)}
                                isInvalid={!!fieldErrors.slug}
                                placeholder="Leave blank to auto-generate from name"
                            />
                            <Form.Control.Feedback type="invalid">{fieldErrors.slug}</Form.Control.Feedback>
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
                                <Form.Label className="small fw-semibold text-white">Country</Form.Label>
                                <Form.Control
                                    value={form.country}
                                    onChange={e => setForm({...form, country: e.target.value})}
                                    placeholder="e.g. Czech Republic"
                                />
                            </Col>
                            <Col sm={6}>
                                <Form.Label className="small fw-semibold text-white">City</Form.Label>
                                <Form.Control
                                    value={form.city}
                                    onChange={e => setForm({...form, city: e.target.value})}
                                    placeholder="e.g. Prague"
                                />
                            </Col>
                        </Row>
                        <Form.Group className="mb-3">
                            <Form.Label className="small fw-semibold text-white">Rating</Form.Label>
                            <Form.Control
                                type="number"
                                step="0.01"
                                min="0"
                                max="5"
                                value={form.rating}
                                onChange={e => setForm({...form, rating: e.target.value})}
                                placeholder="e.g. 4.75"
                            />
                        </Form.Group>
                        {editing && allCategories.length > 0 && (
                            <Form.Group className="mb-3">
                                <Form.Label className="small fw-semibold text-white">Categories</Form.Label>
                                <div
                                    style={{
                                        maxHeight: 180,
                                        overflowY: 'auto',
                                        border: '1px solid #444',
                                        borderRadius: 4,
                                        padding: '8px 12px',
                                    }}
                                >
                                    {allCategories.map(cat => (
                                        <Form.Check
                                            key={cat.id}
                                            type="checkbox"
                                            id={`cat-${cat.id}`}
                                            label={cat.name}
                                            checked={selectedCategoryIds.includes(cat.id)}
                                            onChange={() => toggleCategory(cat.id)}
                                            className="mb-1"
                                        />
                                    ))}
                                </div>
                                <Form.Text className="text-muted" style={{fontSize: '0.75rem'}}>
                                    If none selected, categories are derived automatically from activities.
                                </Form.Text>
                            </Form.Group>
                        )}
                        <ImageUploadField
                            imageUrl={form.imageUrl}
                            uploading={uploading}
                            onUpload={handleImageUpload}
                            onError={handleImageUploadError}
                        />
                    </Form>
                </Modal.Body>
                <Modal.Footer>
                    <Button variant="outline-secondary" size="sm" onClick={() => setShowModal(false)}>
                        Cancel
                    </Button>
                    <Button variant="primary" size="sm" onClick={handleSave}
                            disabled={saving || uploading}>
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

export default AdminDestinations;
