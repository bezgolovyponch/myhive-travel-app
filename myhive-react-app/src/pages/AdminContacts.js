import {useCallback, useEffect, useState} from 'react';
import {Alert, Badge, Button, Card, Form, Spinner, Table} from 'react-bootstrap';
import {useAdminApi} from '../hooks/useAdminApi';
import {useAuthErrorHandler} from '../hooks/useAuthErrorHandler';
import Pagination from '../components/Pagination';
import {formatDateTime} from '../utils/format';

const PAGE_SIZE = 20;
const SEARCH_DEBOUNCE_MS = 300;

const COLUMNS = ['Email', 'Name', 'Locale', 'Sources', 'First seen', 'Last seen', 'Touches', 'Status'];

function AdminContacts() {
    const adminApi = useAdminApi();
    const handleAuthError = useAuthErrorHandler();
    const [contacts, setContacts] = useState([]);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [search, setSearch] = useState('');
    const [query, setQuery] = useState('');
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [exporting, setExporting] = useState(false);

    // Debounce keystrokes into the query that actually hits the API.
    useEffect(() => {
        const timer = setTimeout(() => {
            setQuery(search.trim());
            setPage(0);
        }, SEARCH_DEBOUNCE_MS);
        return () => clearTimeout(timer);
    }, [search]);

    const fetchContacts = useCallback(async () => {
        try {
            setLoading(true);
            setError('');
            const data = await adminApi.getContactsPaged(page, PAGE_SIZE, query);
            setContacts(data.content || []);
            setTotalPages(data.totalPages || 0);
            setTotalElements(data.totalElements || 0);
        } catch (err) {
            if (handleAuthError(err)) return;
            setError(err.message || 'Failed to load contacts');
        } finally {
            setLoading(false);
        }
    }, [adminApi, handleAuthError, page, query]);

    useEffect(() => {
        fetchContacts();
    }, [fetchContacts]);

    const handleExport = async () => {
        try {
            setExporting(true);
            setError('');
            await adminApi.exportContactsCsv();
        } catch (err) {
            if (handleAuthError(err)) return;
            setError(err.message || 'Failed to export contacts');
        } finally {
            setExporting(false);
        }
    };

    return (
        <>
            <div className="d-flex align-items-center justify-content-between mb-4">
                <div>
                    <h4 className="fw-bold mb-0">Contacts</h4>
                    <span className="text-muted small">{totalElements} addresses</span>
                </div>
                <Button variant="outline-primary" size="sm" onClick={handleExport} disabled={exporting}>
                    {exporting ? 'Exporting…' : 'Export CSV'}
                </Button>
            </div>

            <Form.Control
                type="search"
                className="mb-3"
                placeholder="Search email or name"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
            />

            {error && (
                <Alert variant="danger" className="d-flex align-items-center justify-content-between">
                    <span>{error}</span>
                    <Button variant="outline-danger" size="sm" onClick={fetchContacts}>Retry</Button>
                </Alert>
            )}

            <Card className="shadow-sm">
                <Card.Body className="p-0">
                    {loading ? (
                        <div className="d-flex justify-content-center py-5">
                            <Spinner animation="border" variant="primary"/>
                        </div>
                    ) : contacts.length === 0 ? (
                        <p className="text-muted text-center py-5">
                            {query ? 'No contacts match your search.' : 'No contacts yet.'}
                        </p>
                    ) : (
                        <>
                            <Table responsive hover className="mb-0 align-middle">
                                <thead>
                                <tr>
                                    {COLUMNS.map(label => (
                                        <th key={label} className="small text-muted text-uppercase">{label}</th>
                                    ))}
                                </tr>
                                </thead>
                                <tbody>
                                {contacts.map(contact => (
                                    <ContactRow key={contact.id} contact={contact}/>
                                ))}
                                </tbody>
                            </Table>
                            <Pagination page={page} totalPages={totalPages} onPageChange={setPage}/>
                        </>
                    )}
                </Card.Body>
            </Card>
        </>
    );
}

function ContactRow({contact}) {
    const sources = contact.firstSource === contact.lastSource
        ? contact.firstSource
        : `${contact.firstSource} → ${contact.lastSource}`;
    return (
        <tr>
            <td><a href={`mailto:${contact.email}`}>{contact.email}</a></td>
            <td>{contact.name || <span className="text-muted">—</span>}</td>
            <td>{(contact.locale || 'en').toUpperCase()}</td>
            <td className="small">{sources}</td>
            <td className="small text-nowrap">{formatDateTime(contact.firstSeenAt)}</td>
            <td className="small text-nowrap">{formatDateTime(contact.lastSeenAt)}</td>
            <td>{contact.touchCount}</td>
            <td>
                {contact.unsubscribed
                    ? <Badge bg="secondary">Unsubscribed</Badge>
                    : <Badge bg="success">OK to contact</Badge>}
            </td>
        </tr>
    );
}

export default AdminContacts;
