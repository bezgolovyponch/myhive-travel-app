import {useCallback, useEffect, useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {useAuth} from '../context/AuthContext';
import adminApi from '../services/adminApi';
import {Alert, Badge, Button, Card, Col, Form, Modal, Row, Spinner, Table} from 'react-bootstrap';

const EMPTY_FORM = {
    name: '',
    description: '',
    price: '',
    duration: '',
    category: '',
    imageUrl: '',
    destinationId: '',
};

function AdminActivities() {
    const {logout} = useAuth();
    const navigate = useNavigate();
    const [activities, setActivities] = useState([]);
    const [destinations, setDestinations] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [showModal, setShowModal] = useState(false);
    const [editing, setEditing] = useState(null);
    const [form, setForm] = useState(EMPTY_FORM);
    const [saving, setSaving] = useState(false);
    const [deleteId, setDeleteId] = useState(null);
    const [filterDestination, setFilterDestination] = useState('');

    const handleAuthError = useCallback((err) => {
        if (err.message === 'Unauthorized') {
            logout();
            navigate('/admin/login', {replace: true});
            return true;
        }
        return false;
    }, [logout, navigate]);

    const fetchData = useCallback(async () => {
        try {
            setLoading(true);
            setError('');
            const [activitiesData, destinationsData] = await Promise.all([
                adminApi.getActivities(),
                adminApi.getDestinations(),
            ]);
            setActivities(activitiesData);
            setDestinations(destinationsData);
        } catch (err) {
            if (handleAuthError(err)) return;
            setError(err.message || 'Failed to load data');
        } finally {
            setLoading(false);
        }
    }, [handleAuthError]);

    useEffect(() => {
        fetchData();
    }, [fetchData]);

    const openCreate = () => {
        setEditing(null);
        setForm(EMPTY_FORM);
        setShowModal(true);
    };

    const openEdit = (activity) => {
        setEditing(activity);
        setForm({
            name: activity.name || '',
            description: activity.description || '',
            price: activity.price ?? '',
            duration: activity.duration ?? '',
            category: activity.category || '',
            imageUrl: activity.imageUrl || '',
            destinationId: activity.destinationId || '',
        });
        setShowModal(true);
    };

    const handleSave = async () => {
        try {
            setSaving(true);
            setError('');
            const payload = {
                ...form,
                price: form.price !== '' ? Number(form.price) : null,
                duration: form.duration !== '' ? Number(form.duration) : null,
            };
            if (editing) {
                await adminApi.updateActivity(editing.id, payload);
            } else {
                await adminApi.createActivity(payload);
            }
            setShowModal(false);
            await fetchData();
        } catch (err) {
            if (handleAuthError(err)) return;
            setError(err.message || 'Failed to save activity');
        } finally {
            setSaving(false);
        }
    };

    const handleDelete = async () => {
        try {
            setSaving(true);
            setError('');
            await adminApi.deleteActivity(deleteId);
            setDeleteId(null);
            await fetchData();
        } catch (err) {
            if (handleAuthError(err)) return;
            setError(err.message || 'Failed to delete activity');
        } finally {
            setSaving(false);
        }
    };

    const formatAmount = (amount) => {
        if (amount == null) return '—';
        return `€${Number(amount).toFixed(2)}`;
    };

    const filteredActivities = activities.filter(
        a => !filterDestination || a.destinationId === filterDestination
    );

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
                    <Button variant="primary" size="sm" onClick={openCreate}>+ Add Activity</Button>
                </div>
            </div>

            {error && (
                <Alert variant="danger" dismissible onClose={() => setError('')}>{error}</Alert>
            )}

            <Card className="border-0 shadow-sm bg-white">
                <Card.Header className="bg-white border-bottom">
                    <div className="d-flex align-items-center justify-content-between">
                        <h6 className="fw-semibold mb-0 text-dark">
                            {filteredActivities.length} {filteredActivities.length === 1 ? 'activity' : 'activities'}
                        </h6>
                        <Form.Select
                            size="sm"
                            style={{width: 'auto'}}
                            value={filterDestination}
                            onChange={e => setFilterDestination(e.target.value)}
                        >
                            <option value="">All Destinations</option>
                            {destinations.map(d => (
                                <option key={d.id} value={d.id}>{d.name}</option>
                            ))}
                        </Form.Select>
                    </div>
                </Card.Header>
                <Card.Body className="p-0">
                    {filteredActivities.length === 0 ? (
                        <p className="text-muted text-center py-5">No activities found.</p>
                    ) : (
                        <Table responsive hover className="mb-0 align-middle">
                            <thead className="table-light">
                            <tr>
                                <th className="small text-muted text-uppercase">Name</th>
                                <th className="small text-muted text-uppercase">Destination</th>
                                <th className="small text-muted text-uppercase">Category</th>
                                <th className="small text-muted text-uppercase">Price</th>
                                <th className="small text-muted text-uppercase">Duration</th>
                                <th className="small text-muted text-uppercase text-end">Actions</th>
                            </tr>
                            </thead>
                            <tbody>
                            {filteredActivities.map((activity) => (
                                <tr key={activity.id}>
                                    <td>
                                        <div className="small fw-semibold">{activity.name}</div>
                                        {activity.description && (
                                            <div className="text-muted" style={{fontSize: '0.75rem'}}>
                                                {activity.description.length > 60
                                                    ? activity.description.substring(0, 60) + '...'
                                                    : activity.description}
                                            </div>
                                        )}
                                    </td>
                                    <td className="small">{activity.destinationName || '—'}</td>
                                    <td>
                                        {activity.category ? (
                                            <Badge bg="light" text="dark" className="border">
                                                {activity.category}
                                            </Badge>
                                        ) : '—'}
                                    </td>
                                    <td className="small fw-semibold">{formatAmount(activity.price)}</td>
                                    <td className="small">
                                        {activity.duration ? `${activity.duration} min` : '—'}
                                    </td>
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
                        {editing ? `Edit — ${form.name || editing.name}` : (form.name ? `New — ${form.name}` : 'New Activity')}
                    </Modal.Title>
                </Modal.Header>
                <Modal.Body data-bs-theme="dark">
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
                                <Form.Label className="small fw-semibold text-white">Price (€)</Form.Label>
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
                            <Form.Label className="small fw-semibold text-white">Category</Form.Label>
                            <Form.Control
                                value={form.category}
                                onChange={e => setForm({...form, category: e.target.value})}
                                placeholder="e.g. Adventure, Culture, Food"
                            />
                        </Form.Group>
                        <Form.Group className="mb-3">
                            <Form.Label className="small fw-semibold text-white">Image URL</Form.Label>
                            <Form.Control
                                value={form.imageUrl}
                                onChange={e => setForm({...form, imageUrl: e.target.value})}
                                placeholder="https://..."
                            />
                        </Form.Group>
                    </Form>
                </Modal.Body>
                <Modal.Footer>
                    <Button variant="outline-secondary" size="sm" onClick={() => setShowModal(false)}>
                        Cancel
                    </Button>
                    <Button variant="primary" size="sm" onClick={handleSave}
                            disabled={saving || !form.name || !form.destinationId || !form.price}>
                        {saving ? <Spinner animation="border" size="sm"/> : (editing ? 'Save Changes' : 'Create')}
                    </Button>
                </Modal.Footer>
            </Modal>

            {/* Delete Confirmation Modal */}
            <Modal show={!!deleteId} onHide={() => setDeleteId(null)} centered size="sm">
                <Modal.Body className="text-center py-4">
                    <div className="fw-semibold mb-2">Delete this activity?</div>
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

export default AdminActivities;
