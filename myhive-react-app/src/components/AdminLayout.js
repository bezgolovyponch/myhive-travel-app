import {Outlet, useLocation, useNavigate} from 'react-router-dom';
import {useAuth} from '../context/AuthContext';
import {Button, Container, Nav, Navbar} from 'react-bootstrap';

function AdminLayout() {
    const {user, logout} = useAuth();
    const navigate = useNavigate();
    const location = useLocation();

    const handleLogout = () => {
        logout();
        navigate('/admin/login', {replace: true});
    };

    return (
        <div className="min-vh-100" style={{background: 'var(--bg)', color: 'var(--text)'}}>
            <Navbar className="border-bottom shadow-sm" sticky="top" style={{background: 'var(--surface)'}}
                    data-bs-theme="dark">
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
                    <Nav className="me-auto">
                        <Nav.Link
                            active={location.pathname === '/admin'}
                            href="/admin"
                            onClick={(e) => {
                                e.preventDefault();
                                navigate('/admin');
                            }}
                        >
                            Bookings
                        </Nav.Link>
                        <Nav.Link
                            active={location.pathname.startsWith('/admin/activities')}
                            href="/admin/activities"
                            onClick={(e) => {
                                e.preventDefault();
                                navigate('/admin/activities');
                            }}
                        >
                            Activities
                        </Nav.Link>
                        <Nav.Link
                            active={location.pathname.startsWith('/admin/blog')}
                            href="/admin/blog"
                            onClick={(e) => {
                                e.preventDefault();
                                navigate('/admin/blog');
                            }}
                        >
                            Blog
                        </Nav.Link>
                    </Nav>
                    <Nav className="d-flex align-items-center gap-3">
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
