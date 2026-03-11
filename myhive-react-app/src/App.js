import 'bootstrap/dist/css/bootstrap.min.css';
import './App.css';
import {HashRouter as Router, Route, Routes} from 'react-router-dom';
import {AppProvider} from './context/AppContext';
import {AuthProvider} from './context/AuthContext';
import Layout from './components/Layout';
import AdminLayout from './components/AdminLayout';
import AdminLogin from './pages/AdminLogin';
import AdminDashboard from './pages/AdminDashboard';
import AdminBookingDetail from './pages/AdminBookingDetail';
import ProtectedRoute from './components/ProtectedRoute';

function App() {
  return (
    <Router>
      <Routes>
        {/* Admin routes — separate layout, own auth context */}
        <Route path="/admin/login" element={
          <AuthProvider>
            <AdminLogin/>
          </AuthProvider>
        }/>
        <Route path="/admin" element={
          <AuthProvider>
            <ProtectedRoute>
              <AdminLayout/>
            </ProtectedRoute>
          </AuthProvider>
        }>
          <Route index element={<AdminDashboard/>}/>
          <Route path="bookings/:id" element={<AdminBookingDetail/>}/>
        </Route>

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
