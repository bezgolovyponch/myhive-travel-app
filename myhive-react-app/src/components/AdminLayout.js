import {NavLink, Outlet} from 'react-router-dom';
import {useAuth} from '../context/AuthContext';
import {Button, Container, Nav, Navbar} from 'react-bootstrap';

function AdminLayout() {
    const {user, logout} = useAuth();

    return (
        <div className="min-vh-100" style={{background: 'var(--bg)', color: 'var(--text)'}}>
            <Navbar className="border-bottom shadow-sm" sticky="top" style={{background: 'var(--surface)'}}
                    data-bs-theme="dark">
                <Container>
                    <Navbar.Brand as={NavLink} to="/admin" className="fw-bold text-primary">
                        Trivlu Admin
                    </Navbar.Brand>
                    <Nav className="me-auto">
                        {user?.roles?.includes('ADMIN') && (
                            <Nav.Link as={NavLink} to="/admin" end>
                                Bookings
                            </Nav.Link>
                        )}
                        <Nav.Link as={NavLink} to="/admin/destinations">
                            Destinations
                        </Nav.Link>
                        <Nav.Link as={NavLink} to="/admin/activities">
                            Activities
                        </Nav.Link>
                        <Nav.Link as={NavLink} to="/admin/categories">
                            Categories
                        </Nav.Link>
                        <Nav.Link as={NavLink} to="/admin/blog">
                            Blog
                        </Nav.Link>
                    </Nav>
                    <Nav className="d-flex align-items-center gap-3">
                        <span className="text-muted small d-none d-md-inline">{user?.email}</span>
                        <Button variant="outline-secondary" size="sm" onClick={logout}>
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
