import {useState} from 'react';
import {Navigate, useNavigate} from 'react-router-dom';
import {useAuth} from '../context/AuthContext';
import {Alert, Button, Card, Container, Form, Spinner} from 'react-bootstrap';

function AdminLogin() {
    const {login, isAuthenticated, loading} = useAuth();
    const navigate = useNavigate();
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [submitting, setSubmitting] = useState(false);

    if (loading) {
        return (
            <div className="d-flex align-items-center justify-content-center vh-100 bg-light">
                <Spinner animation="border" variant="primary"/>
            </div>
        );
    }

    if (isAuthenticated) {
        return <Navigate to="/admin" replace/>;
    }

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');
        setSubmitting(true);

        try {
            await login(email, password);
            navigate('/admin', {replace: true});
        } catch (err) {
            setError(err.message || 'Login failed');
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <div className="d-flex align-items-center justify-content-center vh-100 bg-light">
            <Container style={{maxWidth: 420}}>
                <Card className="shadow-sm border-0">
                    <Card.Body className="p-4">
                        <div className="text-center mb-4">
                            <h3 className="fw-bold mb-1">MyHive Admin</h3>
                            <p className="text-muted small mb-0">Sign in to manage bookings</p>
                        </div>
                        {error && <Alert variant="danger" className="py-2 small">{error}</Alert>}
                        <Form onSubmit={handleSubmit}>
                            <Form.Group className="mb-3" controlId="email">
                                <Form.Label className="small fw-semibold">Email</Form.Label>
                                <Form.Control
                                    type="email"
                                    value={email}
                                    onChange={(e) => setEmail(e.target.value)}
                                    placeholder="admin@myhive.com"
                                    required
                                    autoFocus
                                />
                            </Form.Group>
                            <Form.Group className="mb-3" controlId="password">
                                <Form.Label className="small fw-semibold">Password</Form.Label>
                                <Form.Control
                                    type="password"
                                    value={password}
                                    onChange={(e) => setPassword(e.target.value)}
                                    placeholder="Enter password"
                                    required
                                />
                            </Form.Group>
                            <Button
                                type="submit"
                                variant="primary"
                                className="w-100"
                                disabled={submitting}
                            >
                                {submitting ? (
                                    <>
                                        <Spinner size="sm" animation="border" className="me-2"/>
                                        Signing in...
                                    </>
                                ) : 'Sign In'}
                            </Button>
                        </Form>
                    </Card.Body>
                </Card>
            </Container>
        </div>
    );
}

export default AdminLogin;
