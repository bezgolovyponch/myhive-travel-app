import {useCallback, useState} from 'react';
import {Alert, Badge, Button, Form, Modal, Spinner, Table} from 'react-bootstrap';

const STEP_UPLOAD = 'upload';
const STEP_REVIEW = 'review';
const STEP_RESULT = 'result';

/** Backend CsvImportException codes after which the preview token is no longer usable. */
const TOKEN_ERRORS = new Set(['TOKEN_NOT_FOUND', 'TOKEN_EXPIRED']);

function fieldLabel(key) {
    return {
        name: 'Name',
        description: 'Description',
        price: 'Price',
        min_price: 'Group minimum',
        duration: 'Duration',
        includes: 'Includes',
        category_slugs: 'Categories',
        featured_weight: 'Featured weight',
    }[key] || key;
}

function formatValue(v) {
    if (v === null || v === undefined || v === '') {
        return <span className="text-muted fst-italic">empty</span>;
    }
    return String(v);
}

function pluralize(count, singular, plural = `${singular}s`) {
    return `${count} ${count === 1 ? singular : plural}`;
}

function applyLabel(preview) {
    const parts = [];
    if (preview.rowsToUpdate > 0) {
        parts.push(pluralize(preview.rowsToUpdate, 'update'));
    }
    if (preview.rowsToCreate > 0) {
        parts.push(`${preview.rowsToCreate} new`);
    }
    return parts.length ? `Apply ${parts.join(', ')}` : 'Apply';
}

function resultLabel(result) {
    const parts = [];
    if (result.rowsUpdated > 0) {
        parts.push(`Updated ${pluralize(result.rowsUpdated, 'activity', 'activities')}`);
    }
    if (result.rowsCreated > 0) {
        parts.push(`created ${pluralize(result.rowsCreated, 'activity', 'activities')}`);
    }
    if (!parts.length) {
        return 'Nothing to apply.';
    }
    const sentence = parts.join(', ');
    return `${sentence.charAt(0).toUpperCase()}${sentence.slice(1)}.`;
}

function ImportActivitiesModal({show, onHide, adminApi, onImported}) {
    const [step, setStep] = useState(STEP_UPLOAD);
    const [file, setFile] = useState(null);
    const [preview, setPreview] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [result, setResult] = useState(null);

    const reset = useCallback(() => {
        setStep(STEP_UPLOAD);
        setFile(null);
        setPreview(null);
        setError('');
        setResult(null);
    }, []);

    const handleClose = () => {
        reset();
        onHide();
    };

    const handlePreview = async () => {
        if (!file) {
            return;
        }
        setLoading(true);
        setError('');
        try {
            const p = await adminApi.previewActivityImport(file);
            setPreview(p);
            setStep(STEP_REVIEW);
        } catch (e) {
            setError(e.message || 'Failed to preview');
        } finally {
            setLoading(false);
        }
    };

    const handleApply = async () => {
        setLoading(true);
        setError('');
        try {
            const r = await adminApi.applyActivityImport(preview.token);
            setResult(r);
            setStep(STEP_RESULT);
        } catch (e) {
            const message = e.message || 'Failed to apply';
            if (TOKEN_ERRORS.has(e.body?.error)) {
                // The preview token is gone (expired, or already applied): a second click can
                // only fail the same way, so disable Apply and point at the way out.
                setPreview((p) => ({...p, token: null}));
                setError(`${message}. Go Back and preview the file again.`);
            } else {
                // Image download failures keep the token valid: fixing the image host and
                // pressing Apply again is a legitimate retry.
                setError(message);
            }
        } finally {
            setLoading(false);
        }
    };

    const handleFinish = () => {
        if (onImported) {
            onImported();
        }
        handleClose();
    };

    const canApply = preview
        && preview.token
        && preview.rowsWithErrors === 0
        && (preview.rowsToUpdate > 0 || preview.rowsToCreate > 0);

    return (
        <Modal show={show} onHide={handleClose} size="lg" centered>
            <Modal.Header closeButton>
                <Modal.Title className="fs-5">Import activities from CSV</Modal.Title>
            </Modal.Header>
            <Modal.Body>
                {error && <Alert variant="danger" dismissible onClose={() => setError('')}>{error}</Alert>}

                {step === STEP_UPLOAD && (
                    <>
                        <p className="small text-muted">
                            Upload the CSV you exported (and possibly edited). Rows matched by
                            <code className="mx-1">id</code> are updated; for them
                            <code className="mx-1">slug</code>, <code>destination_slug</code> and
                            <code className="mx-1">image_url</code> are read-only.
                        </p>
                        <p className="small text-muted">
                            Rows with an empty <code>id</code> create new activities:
                            <code className="mx-1">destination_slug</code> is required, an empty
                            <code className="mx-1">slug</code> is generated from the name, and
                            <code className="mx-1">image_url</code> may point to any public image
                            (it is downloaded and stored in our image storage).
                        </p>
                        <Form.Control
                            type="file"
                            accept=".csv,text/csv"
                            onChange={(e) => setFile(e.target.files?.[0] || null)}
                        />
                    </>
                )}

                {step === STEP_REVIEW && preview && (
                    <>
                        <div className="d-flex flex-wrap gap-3 mb-3">
                            <Badge bg="primary">{preview.rowsToUpdate} to update</Badge>
                            <Badge bg="success">{preview.rowsToCreate} to create</Badge>
                            <Badge bg="secondary">{preview.rowsUnchanged} unchanged</Badge>
                            {preview.rowsWithWarnings > 0 && (
                                <Badge bg="warning" text="dark">{preview.rowsWithWarnings} warnings</Badge>
                            )}
                            {preview.rowsWithErrors > 0 && (
                                <Badge bg="danger">{preview.rowsWithErrors} errors</Badge>
                            )}
                        </div>

                        {preview.errors.length > 0 && (
                            <Alert variant="danger">
                                <strong>Fix these errors and re-upload:</strong>
                                <ul className="mb-0 small">
                                    {preview.errors.map((err, i) => (
                                        <li key={i}>
                                            Row {err.csvRowNumber} [{err.code}]
                                            {err.field ? ` (${err.field})` : ''}: {err.message}
                                        </li>
                                    ))}
                                </ul>
                            </Alert>
                        )}

                        {preview.warnings.length > 0 && (
                            <Alert variant="warning">
                                <ul className="mb-0 small">
                                    {preview.warnings.map((w, i) => (
                                        <li key={i}>
                                            Row {w.csvRowNumber} [{w.code}]
                                            {w.field ? ` (${w.field})` : ''}: {w.message}
                                        </li>
                                    ))}
                                </ul>
                            </Alert>
                        )}

                        {preview.changes.length > 0 && (
                            <div style={{maxHeight: 320, overflowY: 'auto'}}>
                                <Table size="sm" hover>
                                    <thead>
                                    <tr>
                                        <th>Row</th>
                                        <th>Activity</th>
                                        <th>Field</th>
                                        <th>Before</th>
                                        <th>After</th>
                                    </tr>
                                    </thead>
                                    <tbody>
                                    {preview.changes.flatMap((diff) =>
                                        Object.entries(diff.fieldChanges).map(([field, change]) => (
                                            <tr key={`${diff.activityId}-${field}`}>
                                                <td className="small">{diff.csvRowNumber}</td>
                                                <td className="small">{diff.activityName}</td>
                                                <td className="small fw-semibold">{fieldLabel(field)}</td>
                                                <td className="small text-muted">{formatValue(change.oldValue)}</td>
                                                <td className="small">{formatValue(change.newValue)}</td>
                                            </tr>
                                        ))
                                    )}
                                    </tbody>
                                </Table>
                            </div>
                        )}

                        {(preview.creates || []).length > 0 && (
                            <>
                                <h6 className="mt-3 mb-2">New activities</h6>
                                <div style={{maxHeight: 320, overflowY: 'auto'}}>
                                    <Table size="sm" hover>
                                        <thead>
                                        <tr>
                                            <th>Row</th>
                                            <th>Name</th>
                                            <th>Destination</th>
                                            <th>Slug</th>
                                            <th>Price</th>
                                            <th>Categories</th>
                                            <th>Image</th>
                                        </tr>
                                        </thead>
                                        <tbody>
                                        {preview.creates.map((c) => (
                                            <tr key={c.csvRowNumber}>
                                                <td className="small">{c.csvRowNumber}</td>
                                                <td className="small fw-semibold">{c.name}</td>
                                                <td className="small">{c.destinationSlug}</td>
                                                <td className="small">
                                                    {c.slug || <span className="text-muted fst-italic">auto</span>}
                                                </td>
                                                <td className="small">{formatValue(c.price)}</td>
                                                <td className="small">{(c.categorySlugs || []).join(', ') || formatValue('')}</td>
                                                <td className="small">
                                                    {c.imageUrl
                                                        ? <span title={c.imageUrl}>yes</span>
                                                        : <span className="text-muted fst-italic">none</span>}
                                                </td>
                                            </tr>
                                        ))}
                                        </tbody>
                                    </Table>
                                </div>
                            </>
                        )}
                    </>
                )}

                {step === STEP_RESULT && result && (
                    <Alert variant="success" className="mb-0">
                        {resultLabel(result)}
                    </Alert>
                )}
            </Modal.Body>
            <Modal.Footer>
                {step === STEP_UPLOAD && (
                    <>
                        <Button variant="outline-secondary" onClick={handleClose}>Cancel</Button>
                        <Button variant="primary" onClick={handlePreview} disabled={!file || loading}>
                            {loading ? <Spinner animation="border" size="sm"/> : 'Preview'}
                        </Button>
                    </>
                )}
                {step === STEP_REVIEW && (
                    <>
                        <Button variant="outline-secondary" onClick={reset}>Back</Button>
                        <Button variant="primary" onClick={handleApply} disabled={!canApply || loading}>
                            {loading ? <Spinner animation="border" size="sm"/> : applyLabel(preview)}
                        </Button>
                    </>
                )}
                {step === STEP_RESULT && (
                    <Button variant="primary" onClick={handleFinish}>Close</Button>
                )}
            </Modal.Footer>
        </Modal>
    );
}

export default ImportActivitiesModal;
