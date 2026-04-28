import {Badge, Button, Modal, Spinner} from 'react-bootstrap';

const MAX_VISIBLE = 5;

function NameList({names, label}) {
    if (names.length === 0) {
        return null;
    }
    const visible = names.slice(0, MAX_VISIBLE);
    const remainder = names.length - MAX_VISIBLE;
    return (
        <div className="mb-2">
            <div className="small text-muted mb-1">{label} ({names.length})</div>
            <div className="d-flex flex-wrap gap-1">
                {visible.map(name => (
                    <Badge key={name} bg="secondary">{name}</Badge>
                ))}
                {remainder > 0 && (
                    <Badge bg="secondary">...and {remainder} more</Badge>
                )}
            </div>
        </div>
    );
}

function CategoryDeleteModal({show, onHide, onConfirm, saving, categoryName, usage}) {
    const hasUsage = usage && (usage.activityNames.length > 0 || usage.packageNames.length > 0);

    return (
        <Modal show={show} onHide={onHide} centered>
            <Modal.Body className="py-4 px-4">
                <div className="fw-semibold mb-2">Delete "{categoryName}"?</div>
                {hasUsage ? (
                    <>
                        <div className="text-muted small mb-3">
                            This category will be removed from:
                        </div>
                        <NameList names={usage.activityNames} label="Activities"/>
                        <NameList names={usage.packageNames} label="Packages"/>
                    </>
                ) : (
                    <div className="text-muted small mb-3">This action cannot be undone.</div>
                )}
                <div className="d-flex justify-content-end gap-2 mt-3">
                    <Button variant="outline-secondary" size="sm" onClick={onHide}>
                        Cancel
                    </Button>
                    <Button variant="danger" size="sm" onClick={onConfirm} disabled={saving}>
                        {saving ? <Spinner animation="border" size="sm"/> : 'Delete anyway'}
                    </Button>
                </div>
            </Modal.Body>
        </Modal>
    );
}

export default CategoryDeleteModal;
