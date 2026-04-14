import {Alert, Badge, Button, Card, Form, Modal, Spinner} from 'react-bootstrap';
import {truncateText} from '../utils/format';
import {useAdminCrud} from '../hooks/useAdminCrud';
import AdminTable from '../components/AdminTable';
import DeleteConfirmModal from '../components/DeleteConfirmModal';
import ImageUploadField from '../components/ImageUploadField';

const EMPTY_FORM = {
    title: '',
    slug: '',
    excerpt: '',
    content: '',
    category: '',
    imageUrl: '',
    date: '',
};

const COLUMNS = [
    {key: 'title', label: 'Title'},
    {key: 'slug', label: 'Slug'},
    {key: 'category', label: 'Category'},
    {key: 'date', label: 'Date'},
];

function AdminBlog() {
    const {
        items: posts, loading, error, setError, page, setPage,
        totalPages, totalElements, showModal, setShowModal, editing,
        form, setForm, saving, uploading, setUploading, deleteId, setDeleteId,
        fetchData, openCreate: baseOpenCreate, openEdit, handleSave, handleDelete, adminApi,
    } = useAdminCrud({
        emptyForm: EMPTY_FORM,
        fetchFn: (api, page, size) => api.getBlogPostsPaged(page, size),
        createFn: (api, payload) => api.createBlogPost(payload),
        updateFn: (api, id, payload) => api.updateBlogPost(id, payload),
        deleteFn: (api, id) => api.deleteBlogPost(id),
        mapItemToForm: (post) => ({
            title: post.title || '',
            slug: post.slug || '',
            excerpt: post.excerpt || '',
            content: post.content || '',
            category: post.category || '',
            imageUrl: post.imageUrl || '',
            date: post.date || new Date().toISOString().split('T')[0],
        }),
    });

    const openCreate = () => {
        baseOpenCreate();
        setForm(prev => ({...prev, date: new Date().toISOString().split('T')[0]}));
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

            <Card className="border-0 shadow-sm">
                <Card.Header className="border-bottom">
                    <h6 className="fw-semibold mb-0">
                        {totalElements} {totalElements === 1 ? 'post' : 'posts'}
                    </h6>
                </Card.Header>
                <Card.Body className="p-0">
                    <AdminTable
                        columns={COLUMNS}
                        items={posts}
                        page={page}
                        totalPages={totalPages}
                        onPageChange={setPage}
                        emptyMessage="No blog posts yet."
                        renderRow={(post) => (
                            <tr key={post.id}>
                                <td>
                                    <div className="small fw-semibold">{post.title}</div>
                                    {post.excerpt && (
                                        <div className="text-muted" style={{fontSize: '0.75rem'}}>
                                            {truncateText(post.excerpt, 80)}
                                        </div>
                                    )}
                                </td>
                                <td className="small text-muted">{post.slug || '—'}</td>
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
                        )}
                    />
                </Card.Body>
            </Card>

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
                            <Form.Label className="small fw-semibold text-white">Slug</Form.Label>
                            <Form.Control
                                value={form.slug}
                                onChange={e => setForm({...form, slug: e.target.value})}
                                placeholder="Leave blank to auto-generate from title"
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
                            disabled={saving || uploading || !form.title || !form.content}>
                        {saving ? <Spinner animation="border" size="sm"/> : (editing ? 'Save Changes' : 'Publish')}
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

export default AdminBlog;
