import {Button} from 'react-bootstrap';
import {useT} from '../i18n';

function Pagination({page, totalPages, onPageChange}) {
    const t = useT('cards');
    if (totalPages <= 1) return null;

    return (
        <div className="d-flex align-items-center justify-content-center gap-2 py-3">
            <Button
                variant="outline-secondary"
                size="sm"
                disabled={page === 0}
                onClick={() => onPageChange(page - 1)}
            >
                {t('previous')}
            </Button>
            <span className="small text-muted">
                {t('pageOf', {current: page + 1, total: totalPages})}
            </span>
            <Button
                variant="outline-secondary"
                size="sm"
                disabled={page >= totalPages - 1}
                onClick={() => onPageChange(page + 1)}
            >
                {t('next')}
            </Button>
        </div>
    );
}

export default Pagination;
