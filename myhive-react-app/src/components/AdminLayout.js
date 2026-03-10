import {Outlet, useNavigate} from 'react-router-dom';
import {useAuth} from '../context/AuthContext';
import {Button, Container, Nav, Navbar} from 'react-bootstrap';

function AdminLayout() {
    const {user, logout} = useAuth();
    const navigate = useNavigate();

    const handleLogout = () => {
        logout();
        navigate('/admin/login', {replace: true});
    };

    return (
        <div className="min-vh-100 bg-light">
            <Navbar bg="white" className="border-bottom shadow-sm" sticky="top">
                <Container>
                    <Navbar.Brand
                        href="/admin"
                        onClick={(e) => {
                            e.preventDefault();
                            navigate('/admin');
                        }}
                        className="fw-bold text-primary"
                    >
                        MyHive Admin
                    </Navbar.Brand>
                    <Nav className="ms-auto d-flex align-items-center gap-3">
                        <span className="text-muted small d-none d-md-inline">{user?.email}</span>
                        <Button variant="outline-secondary" size="sm" onClick={handleLogout}>
                            Logout
                        </Button>
                    </Nav>
                </Container>
            </Navbar>
            <Container className="py-4">
                <Outlet/>
            </Container>
        </div>
    );
}

export default AdminLayout;
