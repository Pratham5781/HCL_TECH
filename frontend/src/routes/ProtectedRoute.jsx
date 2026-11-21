import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../store/AuthContext';
const loginPaths = { ADMIN: '/admin/login', OPERATOR: '/operator/login', HELP_SUPPORT: '/support/login', CUSTOMER: '/customer/login' };
const portalPaths = { admin: '/admin/login', operator: '/operator/login', support: '/support/login', customer: '/customer/login' };
export function ProtectedRoute() {
  const { user } = useAuth(); const location = useLocation();
  return user ? <Outlet /> : <Navigate to={portalPaths[location.pathname.split('/')[1]] || '/customer/login'} replace state={{ from: location }} />;
}
export function RoleRoute({ role }) {
  const { user } = useAuth(); const location = useLocation();
  if (!user) return <Navigate to={loginPaths[role]} replace />;
  return user.role === role ? <Outlet /> : <Navigate to={loginPaths[role]} replace state={{ from: location }} />;
}

