import {Button} from 'react-bootstrap';

function Pagination({page, totalPages, onPageChange}) {
    if (totalPages <= 1) return null;

    return (
        <div className="d-flex align-items-center justify-content-center gap-2 py-3">
            <Button
                variant="outline-secondary"
                size="sm"
                disabled={page === 0}
                onClick={() => onPageChange(page - 1)}
            >
                Previous
            </Button>
            <span className="small text-muted">
                Page {page + 1} of {totalPages}
            </span>
            <Button
                variant="outline-secondary"
                size="sm"
                disabled={page >= totalPages - 1}
                onClick={() => onPageChange(page + 1)}
            >
                Next
            </Button>
        </div>
    );
}

export default Pagination;
