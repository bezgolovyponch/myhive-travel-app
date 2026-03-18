import {useCallback, useEffect, useState} from 'react';
import adminApi from '../services/adminApi';
import {useAuth} from '../context/AuthContext';
import {useNavigate} from 'react-router-dom';
import {Alert, Badge, Button, Card, Col, Row, Spinner, Table} from 'react-bootstrap';

const STATUS_VARIANTS = {
    PAID: 'success',
    CONFIRMED: 'info',
    PENDING: 'warning',
    CANCELLED: 'danger',
};

function AdminDashboard() {
    const {logout} = useAuth();
    const navigate = useNavigate();
    const [stats, setStats] = useState(null);
    const [bookings, setBookings] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    const fetchData = useCallback(async () => {
        try {
            setLoading(true);
            setError('');
            const [statsData, bookingsData] = await Promise.all([
                adminApi.getBookingStats(),
                adminApi.getBookings(),
            ]);
            setStats(statsData);
            setBookings(bookingsData);
        } catch (err) {
            if (err.message === 'Unauthorized') {
                logout();
                navigate('/admin/login', {replace: true});
                return;
            }
            setError(err.message || 'Failed to load data');
        } finally {
            setLoading(false);
        }
    }, [logout, navigate]);

    useEffect(() => {
        fetchData();
    }, [fetchData]);

    const formatDate = (dateStr) => {
        if (!dateStr) return '—';
        return new Date(dateStr).toLocaleDateString('en-GB', {
            day: '2-digit', month: 'short', year: 'numeric',
        });
    };

    const formatAmount = (amount) => {
        if (amount == null) return '—';
        return `€${Number(amount).toFixed(2)}`;
    };

    if (loading) {
        return (
            <div className="d-flex justify-content-center py-5">
                <Spinner animation="border" variant="primary"/>
            </div>
        );
    }

    if (error) {
        return (
            <Alert variant="danger" className="d-flex align-items-center justify-content-between">
                <span>{error}</span>
                <Button variant="outline-danger" size="sm" onClick={fetchData}>Retry</Button>
            </Alert>
        );
    }

    return (
        <>
            <div className="d-flex align-items-center justify-content-between mb-4">
                <h4 className="fw-bold mb-0">Dashboard</h4>
                <Button variant="outline-secondary" size="sm" onClick={fetchData}>
                    Refresh
                </Button>
            </div>

            {stats && (
                <Row className="g-3 mb-4">
                    {[
                        {label: 'Total Bookings', value: stats.total, color: '#0d6efd'},
                        {label: 'Pending', value: stats.pending, color: '#ffc107'},
                        {label: 'Confirmed', value: stats.confirmed, color: '#0dcaf0'},
                        {label: 'Paid', value: stats.paid, color: '#198754'},
                    ].map(({label, value, color}) => (
                        <Col xs={6} md={3} key={label}>
                            <Card className="shadow-sm h-100 bg-white" style={{
                                borderLeft: `4px solid ${color}`,
                                borderTop: 'none',
                                borderRight: 'none',
                                borderBottom: 'none'
                            }}>
                                <Card.Body className="py-3">
                                    <div className="fs-2 fw-bold text-dark">{value}</div>
                                    <div className="text-muted small">{label}</div>
                                </Card.Body>
                            </Card>
                        </Col>
                    ))}
                </Row>
            )}

            <Card className="border-0 shadow-sm bg-white">
                <Card.Header className="bg-white border-bottom">
                    <h6 className="fw-semibold mb-0 text-dark">Bookings</h6>
                </Card.Header>
                <Card.Body className="p-0">
                    {bookings.length === 0 ? (
                        <p className="text-muted text-center py-5">No bookings found.</p>
                    ) : (
                        <Table responsive hover className="mb-0 align-middle">
                            <thead className="table-light">
                            <tr>
                                <th className="small text-muted text-uppercase">Customer</th>
                                <th className="small text-muted text-uppercase">Amount</th>
                                <th className="small text-muted text-uppercase">Status</th>
                                <th className="small text-muted text-uppercase">Travel Dates</th>
                                <th className="small text-muted text-uppercase">Travelers</th>
                                <th className="small text-muted text-uppercase">Created</th>
                            </tr>
                            </thead>
                            <tbody>
                            {bookings.map((booking) => (
                                <tr key={booking.id} onClick={() => navigate(`/admin/bookings/${booking.id}`)}
                                    style={{cursor: 'pointer'}}>
                                    <td>
                                        <div className="small fw-semibold">{booking.customerName || '—'}</div>
                                        <div className="text-muted"
                                             style={{fontSize: '0.75rem'}}>{booking.userEmail}</div>
                                    </td>
                                    <td className="small fw-semibold">{formatAmount(booking.totalAmount)}</td>
                                    <td>
                                        <Badge bg={STATUS_VARIANTS[booking.status?.toUpperCase()] || 'secondary'}>
                                            {booking.status}
                                        </Badge>
                                    </td>
                                    <td className="small">
                                        {booking.startDate && booking.endDate
                                            ? `${formatDate(booking.startDate)} — ${formatDate(booking.endDate)}`
                                            : '—'}
                                    </td>
                                    <td className="small">{booking.numberOfTravelers || '—'}</td>
                                    <td className="small">{formatDate(booking.createdAt)}</td>
                                </tr>
                            ))}
                            </tbody>
                        </Table>
                    )}
                </Card.Body>
            </Card>
        </>
    );
}

export default AdminDashboard;
