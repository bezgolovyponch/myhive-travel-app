import 'bootstrap/dist/css/bootstrap.min.css';
import './styles/global.css';
import {BrowserRouter as Router, Navigate, Route, Routes} from 'react-router-dom';
import {AppProvider} from './context/AppContext';
import {AuthProvider, useAuth} from './context/AuthContext';
import Layout from './components/Layout';
import AdminLayout from './components/AdminLayout';
import AdminLogin from './pages/AdminLogin';
import AdminDashboard from './pages/AdminDashboard';
import AdminBookingDetail from './pages/AdminBookingDetail';
import AdminActivities from './pages/AdminActivities';
import AdminBlog from './pages/AdminBlog';
import ProtectedRoute from './components/ProtectedRoute';

function AdminIndex() {
    const {user} = useAuth();
    if (user?.roles?.includes('ADMIN')) {
        return <AdminDashboard/>;
    }
    return <Navigate to="/admin/activities" replace/>;
}

function AdminRoutes() {
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
                         element={<ProtectedRoute requiredRole="ADMIN"><AdminBookingDetail/></ProtectedRoute>}/>
          <Route path="activities" element={<AdminActivities/>}/>
                  <Route path="blog" element={<AdminBlog/>}/>
        </Route>
          </Routes>
      </AuthProvider>
  );
}

function App() {
    return (
        <Router>
            <Routes>
                {/* Admin routes — single AuthProvider instance */}
                <Route path="/admin/*" element={<AdminRoutes/>}/>

        {/* Public routes — existing app */}
        <Route path="/*" element={
          <AppProvider>
            <Layout/>
          </AppProvider>
        }/>
      </Routes>
    </Router>
  );
}

export default App;
