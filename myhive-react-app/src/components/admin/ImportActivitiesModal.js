import {useCallback, useState} from 'react';
import {Alert, Badge, Button, Form, Modal, Spinner, Table} from 'react-bootstrap';

const STEP_UPLOAD = 'upload';
const STEP_REVIEW = 'review';
const STEP_RESULT = 'result';

function fieldLabel(key) {
    return {
        name: 'Name',
        description: 'Description',
        price: 'Price',
        duration: 'Duration',
        includes: 'Includes',
        category_slugs: 'Categories',
    }[key] || key;
}

function formatValue(v) {
    if (v === null || v === undefined || v === '') {
        return <span className="text-muted fst-italic">empty</span>;
    }
    return String(v);
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
            setError(e.message || 'Failed to apply');
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
        && preview.rowsToUpdate > 0;

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
                            Upload the CSV you exported (and possibly edited). Only existing activities matched by
                            <code className="mx-1">id</code> will be updated. Read-only fields
                            (<code>slug</code>, <code>destination_slug</code>, <code>image_url</code>) are ignored.
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
                        <div className="d-flex gap-3 mb-3">
                            <Badge bg="primary">{preview.rowsToUpdate} to update</Badge>
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
                    </>
                )}

                {step === STEP_RESULT && result && (
                    <Alert variant="success" className="mb-0">
                        Updated {result.rowsUpdated} {result.rowsUpdated === 1 ? 'activity' : 'activities'}.
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
                            {loading ? <Spinner animation="border" size="sm"/>
                                : `Apply ${preview.rowsToUpdate} change${preview.rowsToUpdate === 1 ? '' : 's'}`}
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
