import {Table} from 'react-bootstrap';
import Pagination from './Pagination';

function AdminTable({columns, items, renderRow, page, totalPages, onPageChange, emptyMessage = 'No items found.'}) {
    if (!items || items.length === 0) {
        return <p className="text-muted text-center py-5">{emptyMessage}</p>;
    }

    return (
        <>
            <Table responsive hover className="mb-0 align-middle">
                <thead>
                <tr>
                    {columns.map(col => (
                        <th key={col.key} className="small text-muted text-uppercase">{col.label}</th>
                    ))}
                    <th className="small text-muted text-uppercase text-end">Actions</th>
                </tr>
                </thead>
                <tbody>
                {items.map(item => renderRow(item))}
                </tbody>
            </Table>
            <Pagination page={page} totalPages={totalPages} onPageChange={onPageChange}/>
        </>
    );
}

export default AdminTable;
