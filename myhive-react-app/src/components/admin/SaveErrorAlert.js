import {Alert} from 'react-bootstrap';

// Rendered inside admin modals: save/upload failures must appear above the
// form — the page-level alert is hidden behind the open modal's backdrop.
function SaveErrorAlert({error, onClose}) {
    if (!error) {
        return null;
    }
    return (
        <Alert variant="danger" dismissible onClose={onClose}>{error}</Alert>
    );
}

export default SaveErrorAlert;
