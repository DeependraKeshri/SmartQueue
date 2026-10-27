import React, { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { api } from './api.js';

const Ctx = createContext(null);
export const useAuth = () => useContext(Ctx);

export function homeFor(role) {
  switch (role) {
    case 'CUSTOMER': return '/me';
    case 'STAFF': return '/staff';
    case 'MANAGER':
    case 'BUSINESS_ADMIN': return '/staff';
    case 'DISPLAY': return '/display';
    case 'PLATFORM_ADMIN': return '/platform';
    default: return '/';
  }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    try { return JSON.parse(localStorage.getItem('sq_user')); } catch { return null; }
  });

  const store = useCallback((resp) => {
    localStorage.setItem('sq_token', resp.token);
    localStorage.setItem('sq_user', JSON.stringify(resp.user));
    setUser(resp.user);
    return resp.user;
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem('sq_token');
    localStorage.removeItem('sq_user');
    setUser(null);
  }, []);

  useEffect(() => {
    const h = () => setUser(null);
    window.addEventListener('sq-logout', h);
    return () => window.removeEventListener('sq-logout', h);
  }, []);

  // refresh the profile on load so role/business changes made by an admin are picked up
  useEffect(() => {
    if (localStorage.getItem('sq_token')) {
      api.get('/api/users/me').then((r) => { localStorage.setItem('sq_user', JSON.stringify(r.data)); setUser(r.data); }).catch(() => {});
    }
  }, []);

  const login = async (email, password) => store((await api.post('/api/auth/login', { email, password })).data);
  const registerCustomer = async (body) => store((await api.post('/api/auth/register/customer', body)).data);
  const registerBusiness = async (body) => store((await api.post('/api/auth/register/business', body)).data);

  return <Ctx.Provider value={{ user, login, logout, registerCustomer, registerBusiness }}>{children}</Ctx.Provider>;
}
