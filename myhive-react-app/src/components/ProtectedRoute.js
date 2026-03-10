import {Navigate} from 'react-router-dom';
import {useAuth} from '../context/AuthContext';
import {Spinner} from 'react-bootstrap';

function ProtectedRoute({children}) {
    const {isAuthenticated, loading} = useAuth();

    if (loading) {
        return (
            <div className="d-flex align-items-center justify-content-center vh-100 bg-light">
                <Spinner animation="border" variant="primary"/>
            </div>
        );
    }

    if (!isAuthenticated) {
        return <Navigate to="/admin/login" replace/>;
    }

    return children;
}

export default ProtectedRoute;
