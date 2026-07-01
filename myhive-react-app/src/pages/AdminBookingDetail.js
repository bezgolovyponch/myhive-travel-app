import {useCallback, useEffect, useState} from 'react';
import {useNavigate, useParams} from 'react-router-dom';
import {useAdminApi} from '../hooks/useAdminApi';
import {useAuthErrorHandler} from '../hooks/useAuthErrorHandler';
import {Alert, Badge, Button, Card, Col, Row, Spinner, Table} from 'react-bootstrap';
import {formatAmount, formatDate, formatDateTime, STATUS_VARIANTS} from '../utils/format';
import {copyToClipboard} from '../utils/clipboard';

function AdminBookingDetail() {
    const adminApi = useAdminApi();
    const {id} = useParams();
    const handleAuthError = useAuthErrorHandler();
    const navigate = useNavigate();
    const [booking, setBooking] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [amount, setAmount] = useState('');
    const [creating, setCreating] = useState(false);
    const [linkError, setLinkError] = useState('');

    const fetchBooking = useCallback(async () => {
        try {
            setLoading(true);
            setError('');
            const data = await adminApi.getBookingById(id);
            setBooking(data);
        } catch (err) {
            if (handleAuthError(err)) return;
            setError(err.message || 'Failed to load booking');
        } finally {
            setLoading(false);
        }
    }, [adminApi, id, handleAuthError]);

    useEffect(() => {
        fetchBooking();
    }, [fetchBooking]);

    const balanceDue = Math.max(0, (booking?.totalAmount || 0) - (booking?.amountPaid || 0));

    useEffect(() => {
        if (booking) {
            setAmount(balanceDue > 0 ? String(balanceDue) : '');
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [booking?.id]);

    const handleCreateLink = async () => {
        const cents = Math.round(parseFloat(amount) * 100);
        if (!Number.isFinite(cents) || cents < 50) {
            setLinkError('Enter a valid amount (min €0.50)');
            return;
        }
        try {
            setCreating(true);
            setLinkError('');
            await adminApi.createBookingPaymentLink(id, cents);
            await fetchBooking();
        } catch (err) {
            if (handleAuthError(err)) return;
            setLinkError(err.message || 'Failed to create payment link');
        } finally {
            setCreating(false);
        }
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
                <div className="d-flex gap-2">
                    <Button variant="outline-danger" size="sm" onClick={fetchBooking}>Retry</Button>
                    <Button variant="outline-secondary" size="sm" onClick={() => navigate('/admin')}>Back</Button>
                </div>
            </Alert>
        );
    }

    if (!booking) return null;

    return (
        <>
            <div className="d-flex align-items-center justify-content-between mb-4">
                <div className="d-flex align-items-center gap-3">
                    <Button variant="outline-secondary" size="sm" onClick={() => navigate('/admin')}>
                        ← Back
                    </Button>
                    <h4 className="fw-bold mb-0">Booking Detail</h4>
                </div>
            </div>

            <Row className="g-3 mb-4">
                <Col md={8}>
                    <Card className="shadow-sm mb-3">
                        <Card.Header className="border-bottom">
                            <div className="d-flex align-items-center justify-content-between">
                                <h6 className="fw-semibold mb-0">Customer Information</h6>
                                <Badge bg={STATUS_VARIANTS[booking.status?.toUpperCase()] || 'secondary'}
                                       className="fs-6">
                                    {booking.status}
                                </Badge>
                            </div>
                        </Card.Header>
                        <Card.Body>
                            <Row className="g-3">
                                <Col sm={6}>
                                    <div className="text-muted small">Full Name</div>
                                    <div className="fw-semibold">{booking.customerName || '—'}</div>
                                </Col>
                                <Col sm={6}>
                                    <div className="text-muted small">Email</div>
                                    <div className="fw-semibold">{booking.userEmail}</div>
                                </Col>
                                <Col sm={6}>
                                    <div className="text-muted small">Phone</div>
                                    <div className="fw-semibold">{booking.phone || '—'}</div>
                                </Col>
                                <Col sm={6}>
                                    <div className="text-muted small">Number of Travelers</div>
                                    <div className="fw-semibold">{booking.numberOfTravelers || '—'}</div>
                                </Col>
                            </Row>
                        </Card.Body>
                    </Card>

                    <Card className="shadow-sm">
                        <Card.Header className="border-bottom">
                            <h6 className="fw-semibold mb-0">Booking Details</h6>
                        </Card.Header>
                        <Card.Body>
                            <Row className="g-3">
                                <Col sm={6}>
                                    <div className="text-muted small">Destination</div>
                                    <div className="fw-semibold">
                                        {(() => {
                                            const destinations = [...new Set(
                                                (booking.items || [])
                                                    .map(i => i.destinationName)
                                                    .filter(Boolean)
                                            )];
                                            return destinations.length > 0 ? destinations.join(', ') : '—';
                                        })()}
                                    </div>
                                </Col>
                                <Col sm={6}>
                                    <div className="text-muted small">Total Amount</div>
                                    <div className="fw-bold fs-5">{formatAmount(booking.totalAmount)}</div>
                                </Col>
                                <Col sm={6}>
                                    <div className="text-muted small">Travel Dates</div>
                                    <div className="fw-semibold">
                                        {booking.startDate && booking.endDate
                                            ? `${formatDate(booking.startDate)} — ${formatDate(booking.endDate)}`
                                            : '—'}
                                    </div>
                                </Col>
                                <Col sm={6}>
                                    <div className="text-muted small">Created</div>
                                    <div className="fw-semibold">{formatDateTime(booking.createdAt)}</div>
                                </Col>
                                <Col sm={6}>
                                    <div className="text-muted small">Paid At</div>
                                    <div className="fw-semibold">{formatDateTime(booking.paidAt)}</div>
                                </Col>
                                <Col sm={6}>
                                    <div className="text-muted small">Booking ID</div>
                                    <div className="fw-semibold">
                                        <code className="small">{booking.id}</code>
                                    </div>
                                </Col>
                                <Col sm={6}>
                                    <div className="text-muted small">Stripe Session</div>
                                    <div className="fw-semibold">
                                        {booking.stripeSessionId ?
                                            <code className="small">{booking.stripeSessionId}</code> : '—'}
                                    </div>
                                </Col>
                                {booking.notes && (
                                    <Col sm={12}>
                                        <div className="text-muted small">Notes</div>
                                        <div className="fw-semibold">{booking.notes}</div>
                                    </Col>
                                )}
                            </Row>
                        </Card.Body>
                    </Card>

                    <Card className="shadow-sm mt-3">
                        <Card.Header className="border-bottom">
                            <h6 className="fw-semibold mb-0">Marketing Attribution</h6>
                        </Card.Header>
                        <Card.Body>
                            <Row className="g-3">
                                <Col sm={6}>
                                    <div className="text-muted small">Trip ID</div>
                                    <div className="fw-semibold">
                                        {booking.tripId ? <code className="small">{booking.tripId}</code> : '—'}
                                    </div>
                                </Col>
                                <Col sm={6}>
                                    <div className="text-muted small">UTM Source</div>
                                    <div className="fw-semibold">{booking.utmSource || '—'}</div>
                                </Col>
                                <Col sm={6}>
                                    <div className="text-muted small">UTM Medium</div>
                                    <div className="fw-semibold">{booking.utmMedium || '—'}</div>
                                </Col>
                                <Col sm={6}>
                                    <div className="text-muted small">UTM Campaign</div>
                                    <div className="fw-semibold">{booking.utmCampaign || '—'}</div>
                                </Col>
                                <Col sm={6}>
                                    <div className="text-muted small">UTM Term</div>
                                    <div className="fw-semibold">{booking.utmTerm || '—'}</div>
                                </Col>
                                <Col sm={6}>
                                    <div className="text-muted small">UTM Content</div>
                                    <div className="fw-semibold">{booking.utmContent || '—'}</div>
                                </Col>
                                <Col sm={6}>
                                    <div className="text-muted small">Referral (ref)</div>
                                    <div className="fw-semibold">{booking.ref || '—'}</div>
                                </Col>
                                <Col sm={6}>
                                    <div className="text-muted small">gclid</div>
                                    <div className="fw-semibold">
                                        {booking.gclid ? <code className="small">{booking.gclid}</code> : '—'}
                                    </div>
                                </Col>
                                <Col sm={6}>
                                    <div className="text-muted small">fbclid</div>
                                    <div className="fw-semibold">
                                        {booking.fbclid ? <code className="small">{booking.fbclid}</code> : '—'}
                                    </div>
                                </Col>
                                <Col sm={12}>
                                    <div className="text-muted small">Referrer</div>
                                    <div className="fw-semibold text-break">{booking.referrer || '—'}</div>
                                </Col>
                            </Row>
                        </Card.Body>
                    </Card>

                    {booking.status?.toUpperCase() !== 'CANCELLED' && (
                        <Card className="shadow-sm mt-3">
                            <Card.Header className="border-bottom">
                                <h6 className="fw-semibold mb-0">Payment</h6>
                            </Card.Header>
                            <Card.Body>
                                <Row className="g-3 mb-3">
                                    <Col sm={4}>
                                        <div className="text-muted small">Total</div>
                                        <div className="fw-semibold">{formatAmount(booking.totalAmount)}</div>
                                    </Col>
                                    <Col sm={4}>
                                        <div className="text-muted small">Paid</div>
                                        <div className="fw-semibold">{formatAmount(booking.amountPaid || 0)}</div>
                                    </Col>
                                    <Col sm={4}>
                                        <div className="text-muted small">Balance due</div>
                                        <div className="fw-bold">{formatAmount(balanceDue)}</div>
                                    </Col>
                                </Row>
                                {linkError && <Alert variant="danger" className="py-2">{linkError}</Alert>}
                                <div className="d-flex gap-2 align-items-end">
                                    <div>
                                        <label htmlFor="pl-amount" className="text-muted small d-block">Amount (€)</label>
                                        <input id="pl-amount" type="number" step="0.01" min="0.5"
                                               className="form-control" style={{maxWidth: '10rem'}}
                                               value={amount} onChange={(e) => setAmount(e.target.value)}/>
                                    </div>
                                    <Button variant="primary" onClick={handleCreateLink} disabled={creating}>
                                        {creating ? 'Creating…' : 'Create payment link'}
                                    </Button>
                                </div>
                                {booking.paymentLinks?.length > 0 && (
                                    <Table responsive className="mt-3 mb-0 align-middle">
                                        <thead>
                                        <tr>
                                            <th className="small text-muted text-uppercase">Amount</th>
                                            <th className="small text-muted text-uppercase">Type</th>
                                            <th className="small text-muted text-uppercase">Status</th>
                                            <th className="small text-muted text-uppercase">Link</th>
                                        </tr>
                                        </thead>
                                        <tbody>
                                        {booking.paymentLinks.map((pl) => (
                                            <tr key={pl.id}>
                                                <td className="small">{formatAmount(pl.amount)}</td>
                                                <td className="small">{pl.type === 'DEPOSIT' ? 'Deposit' : pl.type === 'BALANCE' ? 'Balance' : pl.type}</td>
                                                <td><Badge bg={pl.paid ? 'success' : 'secondary'}>
                                                    {pl.paid ? 'Paid' : 'Unpaid'}</Badge></td>
                                                <td className="small">
                                                    {pl.paid ? <span className="text-muted">—</span> : (
                                                        <div className="d-flex align-items-center gap-2">
                                                            <span className="text-break">{pl.url}</span>
                                                            <Button size="sm" variant="outline-secondary"
                                                                    onClick={() => copyToClipboard(pl.url)}>
                                                                Copy</Button>
                                                        </div>
                                                    )}
                                                </td>
                                            </tr>
                                        ))}
                                        </tbody>
                                    </Table>
                                )}
                            </Card.Body>
                        </Card>
                    )}
                </Col>

                <Col md={4}>
                    <Card className="shadow-sm h-100">
                        <Card.Header className="border-bottom">
                            <h6 className="fw-semibold mb-0">Summary</h6>
                        </Card.Header>
                        <Card.Body className="d-flex flex-column justify-content-center">
                            <div className="text-center">
                                <div className="fs-1 fw-bold">{booking.items?.length || 0}</div>
                                <div className="text-muted">Activities</div>
                            </div>
                            <hr/>
                            <div className="text-center">
                                <div className="fs-4 fw-bold">{booking.numberOfTravelers || '—'}</div>
                                <div className="text-muted">Travelers</div>
                            </div>
                            <hr/>
                            <div className="text-center">
                                <div className="fs-4 fw-bold">{formatAmount(booking.totalAmount)}</div>
                                <div className="text-muted">Total</div>
                            </div>
                        </Card.Body>
                    </Card>
                </Col>
            </Row>

            <Card className="shadow-sm">
                <Card.Header className="border-bottom">
                    <h6 className="fw-semibold mb-0">Booking Items</h6>
                </Card.Header>
                <Card.Body className="p-0">
                    {(!booking.items || booking.items.length === 0) ? (
                        <p className="text-muted text-center py-4">No items in this booking.</p>
                    ) : (
                        <Table responsive hover className="mb-0 align-middle">
                            <thead className="">
                            <tr>
                                <th className="small text-muted text-uppercase">#</th>
                                <th className="small text-muted text-uppercase">Activity</th>
                                <th className="small text-muted text-uppercase">Destination</th>
                                <th className="small text-muted text-uppercase">Price</th>
                                <th className="small text-muted text-uppercase">Qty</th>
                                <th className="small text-muted text-uppercase">Subtotal</th>
                            </tr>
                            </thead>
                            <tbody>
                            {booking.items.map((item, idx) => (
                                <tr key={item.id || idx}>
                                    <td className="small text-muted">{idx + 1}</td>
                                    <td className="small fw-semibold">{item.activityName || '—'}</td>
                                    <td className="small">{item.destinationName || '—'}</td>
                                    <td className="small">{formatAmount(item.price)}</td>
                                    <td className="small">{item.quantity || 1}</td>
                                    <td className="small fw-semibold">
                                        {formatAmount((item.price || 0) * (item.quantity || 1))}
                                    </td>
                                </tr>
                            ))}
                            </tbody>
                            <tfoot className="">
                            <tr>
                                <td colSpan={5} className="text-end fw-semibold small">Total</td>
                                <td className="fw-bold">{formatAmount(booking.totalAmount)}</td>
                            </tr>
                            </tfoot>
                        </Table>
                    )}
                </Card.Body>
            </Card>
        </>
    );
}

export default AdminBookingDetail;
