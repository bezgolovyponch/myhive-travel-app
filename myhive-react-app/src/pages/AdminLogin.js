import {Navigate} from 'react-router-dom';
import {useAuth} from '../context/AuthContext';
import {Button, Card, Container, Spinner} from 'react-bootstrap';

function AdminLogin() {
    const {login, isAuthenticated, loading} = useAuth();

    if (loading) {
        return (
            <div className="d-flex align-items-center justify-content-center vh-100">
                <Spinner animation="border" variant="primary"/>
            </div>
        );
    }

    if (isAuthenticated) {
        return <Navigate to="/admin" replace/>;
    }

    return (
        <div className="d-flex align-items-center justify-content-center vh-100">
            <Container style={{maxWidth: 420}}>
                <Card className="shadow-sm border-0">
                    <Card.Body className="p-4">
                        <div className="text-center mb-4">
                            <h3 className="fw-bold mb-1">MyHive Admin</h3>
                            <p className="text-muted small mb-0">Sign in to manage bookings</p>
                        </div>
                        <Button
                            variant="primary"
                            className="w-100"
                            onClick={() => login()}
                        >
                            Sign In
                        </Button>
                    </Card.Body>
                </Card>
            </Container>
        </div>
    );
}

export default AdminLogin;
