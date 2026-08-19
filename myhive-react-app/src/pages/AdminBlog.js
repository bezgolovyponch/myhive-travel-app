import {useCallback} from 'react';
import {Alert, Badge, Button, Card, Form, Modal, Spinner} from 'react-bootstrap';
import {truncateText} from '../utils/format';
import {useAdminCrud} from '../hooks/useAdminCrud';
import {required, slugFormat} from '../utils/validators';
import AdminTable from '../components/AdminTable';
import DeleteConfirmModal from '../components/DeleteConfirmModal';
import ImageUploadField from '../components/ImageUploadField';
import MarkdownContent from '../components/MarkdownContent';
import SaveErrorAlert from '../components/admin/SaveErrorAlert';

const EMPTY_FORM = {
    title: '',
    slug: '',
    excerpt: '',
    content: '',
    category: '',
    imageUrl: '',
    date: '',
    seoIndexable: false,
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
        form, setForm, saving, saveError, setSaveError, fieldErrors, updateField, uploading, deleteId, setDeleteId,
        handleImageUpload, handleImageUploadError,
        fetchData, openCreate: baseOpenCreate, openEdit, handleSave, handleDelete,
    } = useAdminCrud({
        emptyForm: EMPTY_FORM,
        fetchFn: useCallback((api, page, size) => api.getBlogPostsPaged(page, size), []),
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
            seoIndexable: !!post.seoIndexable,
        }),
        validate: (form) => {
            const errors = {};
            const title = required(form.title);
            if (title) errors.title = title;
            const content = required(form.content);
            if (content) errors.content = content;
            const slug = slugFormat(form.slug);
            if (slug) errors.slug = slug;
            return errors;
        },
    });

    const openCreate = () => {
        baseOpenCreate();
        setForm(prev => ({...prev, date: new Date().toISOString().split('T')[0]}));
    };

    // Spinner only on the initial load — page changes keep the table mounted.
    if (loading && posts.length === 0) {
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
                    <SaveErrorAlert error={saveError} onClose={() => setSaveError('')}/>
                    <Form>
                        <Form.Group className="mb-3">
                            <Form.Label className="small fw-semibold text-white">Title</Form.Label>
                            <Form.Control
                                value={form.title}
                                onChange={e => updateField('title', e.target.value)}
                                isInvalid={!!fieldErrors.title}
                                placeholder="Blog post title"
                            />
                            <Form.Control.Feedback type="invalid">{fieldErrors.title}</Form.Control.Feedback>
                        </Form.Group>
                        <Form.Group className="mb-3">
                            <Form.Label className="small fw-semibold text-white">Slug</Form.Label>
                            <Form.Control
                                value={form.slug}
                                onChange={e => updateField('slug', e.target.value)}
                                isInvalid={!!fieldErrors.slug}
                                placeholder="Leave blank to auto-generate from title"
                            />
                            <Form.Control.Feedback type="invalid">{fieldErrors.slug}</Form.Control.Feedback>
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
                                onChange={e => updateField('content', e.target.value)}
                                isInvalid={!!fieldErrors.content}
                                placeholder="Markdown supported: ## headings, [links](/destination/prague), lists, tables. Blank line = new paragraph."
                            />
                            <Form.Control.Feedback type="invalid">{fieldErrors.content}</Form.Control.Feedback>
                        </Form.Group>
                        {form.content && (
                            <div className="border rounded p-3 mb-3">
                                <div className="text-secondary small mb-2">Preview (rendered exactly as on the site)</div>
                                <MarkdownContent>{form.content}</MarkdownContent>
                            </div>
                        )}
                        <Form.Group className="mb-3">
                            <Form.Check
                                type="switch"
                                id="blog-seo-indexable"
                                label="Indexable by Google (SEO-ready content)"
                                className="text-white"
                                checked={!!form.seoIndexable}
                                onChange={e => updateField('seoIndexable', e.target.checked)}
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
