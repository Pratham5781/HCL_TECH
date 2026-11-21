import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import client from '../api/client';
const AuthContext = createContext(null);
export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    const role = localStorage.getItem('role');
    const email = localStorage.getItem('email');
    return role && email ? { role, email } : null;
  });
  useEffect(() => {
    const expire = () => { localStorage.clear(); setUser(null); };
    window.addEventListener('auth:expired', expire);
    return () => window.removeEventListener('auth:expired', expire);
  }, []);
  const login = async (portal, credentials) => {
    const { data } = await client.post(`/api/auth/${portal}/login`, credentials);
    const pair = data.data;
    localStorage.setItem('accessToken', pair.accessToken);
    localStorage.setItem('refreshToken', pair.refreshToken);
    localStorage.setItem('role', pair.role);
    localStorage.setItem('email', pair.email);
    const next = { role: pair.role, email: pair.email };
    setUser(next);
    return next;
  };
  const logout = async () => {
    const token = localStorage.getItem('refreshToken');
    try { if (token) await client.post('/api/auth/logout', { refreshToken: token }); } finally {
      localStorage.removeItem('accessToken'); localStorage.removeItem('refreshToken');
      localStorage.removeItem('role'); localStorage.removeItem('email'); setUser(null);
    }
  };
  const value = useMemo(() => ({ user, login, logout }), [user]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
export function useAuth() { return useContext(AuthContext); }

