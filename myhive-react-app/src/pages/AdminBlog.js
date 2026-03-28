import {useCallback, useEffect, useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {useAuth} from '../context/AuthContext';
import adminApi from '../services/adminApi';
import {Alert, Badge, Button, Card, Form, Modal, Spinner, Table} from 'react-bootstrap';

const EMPTY_FORM = {
    title: '',
    excerpt: '',
    content: '',
    category: '',
    imageUrl: '',
    date: new Date().toISOString().split('T')[0],
};

function AdminBlog() {
    const {logout} = useAuth();
    const navigate = useNavigate();
    const [posts, setPosts] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [showModal, setShowModal] = useState(false);
    const [editing, setEditing] = useState(null);
    const [form, setForm] = useState(EMPTY_FORM);
    const [saving, setSaving] = useState(false);
    const [deleteId, setDeleteId] = useState(null);
    const [uploading, setUploading] = useState(false);

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
            const data = await adminApi.getBlogPosts();
            setPosts(data);
        } catch (err) {
            if (handleAuthError(err)) return;
            setError(err.message || 'Failed to load blog posts');
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

    const openEdit = (post) => {
        setEditing(post);
        setForm({
            title: post.title || '',
            excerpt: post.excerpt || '',
            content: post.content || '',
            category: post.category || '',
            imageUrl: post.imageUrl || '',
            date: post.date || new Date().toISOString().split('T')[0],
        });
        setShowModal(true);
    };

    const handleSave = async () => {
        try {
            setSaving(true);
            setError('');
            if (editing) {
                await adminApi.updateBlogPost(editing.id, form);
            } else {
                await adminApi.createBlogPost(form);
            }
            setShowModal(false);
            await fetchData();
        } catch (err) {
            if (handleAuthError(err)) return;
            setError(err.message || 'Failed to save blog post');
        } finally {
            setSaving(false);
        }
    };

    const handleDelete = async () => {
        try {
            setSaving(true);
            setError('');
            await adminApi.deleteBlogPost(deleteId);
            setDeleteId(null);
            await fetchData();
        } catch (err) {
            if (handleAuthError(err)) return;
            setError(err.message || 'Failed to delete blog post');
        } finally {
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
                <h4 className="fw-bold mb-0">Blog Posts</h4>
                <div className="d-flex gap-2">
                    <Button variant="outline-secondary" size="sm" onClick={fetchData}>Refresh</Button>
                    <Button variant="primary" size="sm" onClick={openCreate}>+ Add Post</Button>
                </div>
            </div>

            {error && (
                <Alert variant="danger" dismissible onClose={() => setError('')}>{error}</Alert>
            )}

            <Card className="border-0 shadow-sm bg-white">
                <Card.Header className="bg-white border-bottom">
                    <h6 className="fw-semibold mb-0 text-dark">
                        {posts.length} {posts.length === 1 ? 'post' : 'posts'}
                    </h6>
                </Card.Header>
                <Card.Body className="p-0">
                    {posts.length === 0 ? (
                        <p className="text-muted text-center py-5">No blog posts yet.</p>
                    ) : (
                        <Table responsive hover className="mb-0 align-middle">
                            <thead className="table-light">
                            <tr>
                                <th className="small text-muted text-uppercase">Title</th>
                                <th className="small text-muted text-uppercase">Category</th>
                                <th className="small text-muted text-uppercase">Date</th>
                                <th className="small text-muted text-uppercase text-end">Actions</th>
                            </tr>
                            </thead>
                            <tbody>
                            {posts.map((post) => (
                                <tr key={post.id}>
                                    <td>
                                        <div className="small fw-semibold">{post.title}</div>
                                        {post.excerpt && (
                                            <div className="text-muted" style={{fontSize: '0.75rem'}}>
                                                {post.excerpt.length > 80
                                                    ? post.excerpt.substring(0, 80) + '...'
                                                    : post.excerpt}
                                            </div>
                                        )}
                                    </td>
                                    <td>
                                        {post.category ? (
                                            <Badge bg="light" text="dark" className="border">
                                                {post.category}
                                            </Badge>
                                        ) : '—'}
                                    </td>
                                    <td className="small">{post.date || '—'}</td>
                                    <td className="text-end">
                                        <Button variant="outline-primary" size="sm" className="me-1"
                                                onClick={() => openEdit(post)}>
                                            Edit
                                        </Button>
                                        <Button variant="outline-danger" size="sm"
                                                onClick={() => setDeleteId(post.id)}>
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
            <Modal show={showModal} onHide={() => setShowModal(false)} centered size="lg">
                <Modal.Header closeButton className="text-white" data-bs-theme="dark">
                    <Modal.Title className="fs-5">
                        {editing ? `Edit — ${form.title || editing.title}` : (form.title ? `New — ${form.title}` : 'New Blog Post')}
                    </Modal.Title>
                </Modal.Header>
                <Modal.Body data-bs-theme="dark">
                    <Form>
                        <Form.Group className="mb-3">
                            <Form.Label className="small fw-semibold text-white">Title</Form.Label>
                            <Form.Control
                                value={form.title}
                                onChange={e => setForm({...form, title: e.target.value})}
                                placeholder="Blog post title"
                            />
                        </Form.Group>
                        <Form.Group className="mb-3">
                            <Form.Label className="small fw-semibold text-white">Excerpt</Form.Label>
                            <Form.Control
                                as="textarea"
                                rows={2}
                                value={form.excerpt}
                                onChange={e => setForm({...form, excerpt: e.target.value})}
                                placeholder="Short summary for the blog card"
                            />
                        </Form.Group>
                        <Form.Group className="mb-3">
                            <Form.Label className="small fw-semibold text-white">Content</Form.Label>
                            <Form.Control
                                as="textarea"
                                rows={10}
                                value={form.content}
                                onChange={e => setForm({...form, content: e.target.value})}
                                placeholder="Full blog post content. Use blank lines to separate paragraphs."
                            />
                        </Form.Group>
                        <Form.Group className="mb-3">
                            <Form.Label className="small fw-semibold text-white">Category</Form.Label>
                            <Form.Control
                                value={form.category}
                                onChange={e => setForm({...form, category: e.target.value})}
                                placeholder="e.g. Destinations, Tips, Technology"
                            />
                        </Form.Group>
                        <Form.Group className="mb-3">
                            <Form.Label className="small fw-semibold text-white">Publish Date</Form.Label>
                            <Form.Control
                                type="date"
                                value={form.date}
                                onChange={e => setForm({...form, date: e.target.value})}
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
                            disabled={saving || uploading || !form.title || !form.content}>
                        {saving ? <Spinner animation="border" size="sm"/> : (editing ? 'Save Changes' : 'Publish')}
                    </Button>
                </Modal.Footer>
            </Modal>

            {/* Delete Confirmation Modal */}
            <Modal show={!!deleteId} onHide={() => setDeleteId(null)} centered size="sm">
                <Modal.Body className="text-center py-4">
                    <div className="fw-semibold mb-2">Delete this blog post?</div>
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

export default AdminBlog;
