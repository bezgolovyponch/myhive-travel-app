import {Button, Modal, Spinner} from 'react-bootstrap';

function DeleteConfirmModal({show, onHide, onConfirm, saving, message = 'This action cannot be undone.'}) {
    return (
        <Modal show={show} onHide={onHide} centered size="sm">
            <Modal.Body className="text-center py-4">
                <div className="fw-semibold mb-2">Are you sure?</div>
                <div className="text-muted small mb-3">{message}</div>
                <div className="d-flex justify-content-center gap-2">
                    <Button variant="outline-secondary" size="sm" onClick={onHide}>
                        Cancel
                    </Button>
                    <Button variant="danger" size="sm" onClick={onConfirm} disabled={saving}>
                        {saving ? <Spinner animation="border" size="sm"/> : 'Delete'}
                    </Button>
                </div>
            </Modal.Body>
        </Modal>
    );
}

export default DeleteConfirmModal;
