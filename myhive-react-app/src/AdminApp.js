import {Navigate, Route, Routes} from 'react-router-dom';
import {AuthProvider, useAuth} from './context/AuthContext';
import AdminLayout from './components/AdminLayout';
import AdminLogin from './pages/AdminLogin';
import AdminDashboard from './pages/AdminDashboard';
import AdminBookingDetail from './pages/AdminBookingDetail';
import AdminActivities from './pages/AdminActivities';
import AdminPackages from './pages/AdminPackages';
import AdminCategories from './pages/AdminCategories';
import AdminDestinations from './pages/AdminDestinations';
import AdminBlog from './pages/AdminBlog';
import ProtectedRoute from './components/ProtectedRoute';

function AdminIndex() {
    const {user} = useAuth();
    if (user?.roles?.includes('ADMIN') || user?.roles?.includes('MANAGER')) {
        return <AdminDashboard/>;
    }
    return <Navigate to="/admin/activities" replace/>;
}

function AdminApp() {
    return (
        <AuthProvider>
            <Routes>
                <Route path="login" element={<AdminLogin/>}/>
                <Route path="*" element={
                    <ProtectedRoute>
                        <AdminLayout/>
                    </ProtectedRoute>
                }>
                    <Route index element={<AdminIndex/>}/>
                    <Route path="bookings/:id"
                           element={<ProtectedRoute requiredRole={['ADMIN', 'MANAGER']}><AdminBookingDetail/></ProtectedRoute>}/>
                    <Route path="activities" element={<AdminActivities/>}/>
                    <Route path="packages" element={<AdminPackages/>}/>
                    <Route path="categories" element={<AdminCategories/>}/>
                    <Route path="destinations" element={<AdminDestinations/>}/>
                    <Route path="blog" element={<AdminBlog/>}/>
                </Route>
            </Routes>
        </AuthProvider>
    );
}

export default AdminApp;
