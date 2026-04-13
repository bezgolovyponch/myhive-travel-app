import {useCallback, useEffect, useState} from 'react';
import {useAdminApi} from '../hooks/useAdminApi';
import {useAuthErrorHandler} from '../hooks/useAuthErrorHandler';
import {Alert, Badge, Button, Card, Col, Form, Modal, Row, Spinner, Table} from 'react-bootstrap';

const EMPTY_FORM = {
    name: '',
    description: '',
    country: '',
    city: '',
    imageUrl: '',
    rating: '',
};

function AdminDestinations() {
    const adminApi = useAdminApi();
    const handleAuthError = useAuthErrorHandler();
    const [destinations, setDestinations] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [showModal, setShowModal] = useState(false);
    const [editing, setEditing] = useState(null);
    const [form, setForm] = useState(EMPTY_FORM);
    const [saving, setSaving] = useState(false);
    const [deleteId, setDeleteId] = useState(null);
    const [uploading, setUploading] = useState(false);

    const fetchData = useCallback(async () => {
        try {
            setLoading(true);
            setError('');
            const data = await adminApi.getDestinations();
            setDestinations(data);
        } catch (err) {
            if (handleAuthError(err)) return;
            setError(err.message || 'Failed to load destinations');
        } finally {
            setLoading(false);
        }
    }, [adminApi, handleAuthError]);

    useEffect(() => {
        fetchData();
    }, [fetchData]);

    const openCreate = () => {
        setEditing(null);
        setForm(EMPTY_FORM);
        setShowModal(true);
    };

    const openEdit = (destination) => {
        setEditing(destination);
        setForm({
            name: destination.name || '',
            description: destination.description || '',
            country: destination.country || '',
            city: destination.city || '',
            imageUrl: destination.imageUrl || '',
            rating: destination.rating ?? '',
        });
        setShowModal(true);
    };

    const handleSave = async () => {
        try {
            setSaving(true);
            setError('');
            const payload = {
                ...form,
                rating: form.rating !== '' ? Number(form.rating) : null,
            };
            if (editing) {
                await adminApi.updateDestination(editing.id, payload);
            } else {
                await adminApi.createDestination(payload);
            }
            setShowModal(false);
            await fetchData();
        } catch (err) {
            if (handleAuthError(err)) return;
            setError(err.message || 'Failed to save destination');
        } finally {
            setSaving(false);
        }
    };

    const handleDelete = async () => {
        try {
            setSaving(true);
            setError('');
            await adminApi.deleteDestination(deleteId);
            await fetchData();
        } catch (err) {
            if (handleAuthError(err)) return;
            setError(err.message || 'Failed to delete destination');
        } finally {
            setDeleteId(null);
            setSaving(false);
        }
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
                        {destinations.length} {destinations.length === 1 ? 'destination' : 'destinations'}
                    </h6>
                </Card.Header>
                <Card.Body className="p-0">
                    {destinations.length === 0 ? (
                        <p className="text-muted text-center py-5">No destinations found.</p>
                    ) : (
                        <Table responsive hover className="mb-0 align-middle">
                            <thead>
                            <tr>
                                <th className="small text-muted text-uppercase">Name</th>
                                <th className="small text-muted text-uppercase">Country</th>
                                <th className="small text-muted text-uppercase">City</th>
                                <th className="small text-muted text-uppercase">Rating</th>
                                <th className="small text-muted text-uppercase">Activities</th>
                                <th className="small text-muted text-uppercase text-end">Actions</th>
                            </tr>
                            </thead>
                            <tbody>
                            {destinations.map((destination) => (
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
                                                        {destination.description.length > 60
                                                            ? destination.description.substring(0, 60) + '...'
                                                            : destination.description}
                                                    </div>
                                                )}
                                            </div>
                                        </div>
                                    </td>
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
                            ))}
                            </tbody>
                        </Table>
                    )}
                </Card.Body>
            </Card>

            {/* Create / Edit Modal */}
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
                        <Form.Group className="mb-3">
                            <Form.Label className="small fw-semibold text-white">Image</Form.Label>
                            <Form.Control
                                type="file"
                                accept="image/*"
                                disabled={uploading}
                                onChange={async (e) => {
                                    const file = e.target.files[0];
                                    if (!file) return;
                                    try {
                                        setUploading(true);
                                        setError('');
                                        const {url} = await adminApi.uploadImage(file);
                                        setForm(prev => ({...prev, imageUrl: url}));
                                    } catch (err) {
                                        if (handleAuthError(err)) return;
                                        setError(err.message || 'Failed to upload image');
                                    } finally {
                                        setUploading(false);
                                    }
                                }}
                            />
                            {uploading && (
                                <div className="mt-2">
                                    <Spinner animation="border" size="sm"/> <span
                                    className="small text-muted">Uploading...</span>
                                </div>
                            )}
                            {form.imageUrl && !uploading && (
                                <div className="mt-2">
                                    <img
                                        src={form.imageUrl}
                                        alt="Preview"
                                        style={{maxHeight: 120, borderRadius: 6, objectFit: 'cover'}}
                                    />
                                    <div className="small text-muted mt-1 text-truncate" style={{maxWidth: 300}}>
                                        {form.imageUrl}
                                    </div>
                                </div>
                            )}
                        </Form.Group>
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

            {/* Delete Confirmation Modal */}
            <Modal show={!!deleteId} onHide={() => setDeleteId(null)} centered size="sm">
                <Modal.Body className="text-center py-4">
                    <div className="fw-semibold mb-2">Delete this destination?</div>
                    <div className="text-muted small mb-3">This action cannot be undone.</div>
                    <div className="d-flex justify-content-center gap-2">
                        <Button variant="outline-secondary" size="sm" onClick={() => setDeleteId(null)}>
                            Cancel
                        </Button>
                        <Button variant="danger" size="sm" onClick={handleDelete} disabled={saving}>
                            {saving ? <Spinner animation="border" size="sm"/> : 'Delete'}
                        </Button>
                    </div>
                </Modal.Body>
            </Modal>
        </>
    );
}

export default AdminDestinations;
