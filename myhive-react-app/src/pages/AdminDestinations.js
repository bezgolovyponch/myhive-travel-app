import {Alert, Badge, Button, Card, Col, Form, Modal, Row, Spinner} from 'react-bootstrap';
import {truncateText} from '../utils/format';
import {useAdminCrud} from '../hooks/useAdminCrud';
import AdminTable from '../components/AdminTable';
import DeleteConfirmModal from '../components/DeleteConfirmModal';
import ImageUploadField from '../components/ImageUploadField';

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
    const {
        items: destinations, loading, error, setError, page, setPage,
        totalPages, totalElements, showModal, setShowModal, editing,
        form, setForm, saving, uploading, setUploading, deleteId, setDeleteId,
        fetchData, openCreate, openEdit, handleSave, handleDelete, adminApi,
    } = useAdminCrud({
        emptyForm: EMPTY_FORM,
        fetchFn: (api, page, size) => api.getDestinationsPaged(page, size),
        createFn: (api, payload) => api.createDestination(payload),
        updateFn: (api, id, payload) => api.updateDestination(id, payload),
        deleteFn: (api, id) => api.deleteDestination(id),
        mapItemToForm: (d) => ({
            name: d.name || '',
            slug: d.slug || '',
            description: d.description || '',
            country: d.country || '',
            city: d.city || '',
            imageUrl: d.imageUrl || '',
            rating: d.rating ?? '',
        }),
        buildPayload: (form) => ({
            ...form,
            rating: form.rating !== '' ? Number(form.rating) : null,
        }),
    });

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
                            <tr key={destination.id}>
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
                                    <Button variant="outline-danger" size="sm"
                                            onClick={() => setDeleteId(destination.id)}>
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
                        {editing ? `Edit — ${form.name || editing.name}` : (form.name ? `New — ${form.name}` : 'New Destination')}
                    </Modal.Title>
                </Modal.Header>
                <Modal.Body data-bs-theme="dark">
                    <Form>
                        <Form.Group className="mb-3">
                            <Form.Label className="small fw-semibold text-white">Name</Form.Label>
                            <Form.Control
                                value={form.name}
                                onChange={e => setForm({...form, name: e.target.value})}
                                placeholder="Destination name"
                            />
                        </Form.Group>
                        {editing && form.slug && (
                            <Form.Group className="mb-3">
                                <Form.Label className="small fw-semibold text-white">Slug</Form.Label>
                                <Form.Control value={form.slug} readOnly plaintext className="text-muted small"/>
                            </Form.Group>
                        )}
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
                        <ImageUploadField
                            imageUrl={form.imageUrl}
                            uploading={uploading}
                            onUpload={async (file) => {
                                setUploading(true);
                                setError('');
                                try {
                                    const {url} = await adminApi.uploadImage(file);
                                    setForm(prev => ({...prev, imageUrl: url}));
                                } finally {
                                    setUploading(false);
                                }
                            }}
                            onError={(err) => {
                                setError(err.message || 'Failed to upload image');
                            }}
                        />
                    </Form>
                </Modal.Body>
                <Modal.Footer>
                    <Button variant="outline-secondary" size="sm" onClick={() => setShowModal(false)}>
                        Cancel
                    </Button>
                    <Button variant="primary" size="sm" onClick={handleSave}
                            disabled={saving || uploading || !form.name}>
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
