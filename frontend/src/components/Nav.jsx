import React, { useCallback, useEffect, useState } from 'react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import { useStomp } from '../realtime.js';

function Bell() {
  const [unread, setUnread] = useState(0);
  const [toast, setToast] = useState(null);
  const refresh = useCallback(() => api.get('/api/notifications/unread-count').then((r) => setUnread(r.data.unread)).catch(() => {}), []);
  useEffect(() => { refresh(); }, [refresh]);
  useStomp(['/user/queue/notifications'], (n) => {
    setUnread((u) => u + 1);
    setToast(n);
    setTimeout(() => setToast(null), 6000);
    if ('Notification' in window && Notification.permission === 'granted') {
      try { new Notification(n.title, { body: n.message }); } catch { /* ignore */ }
    }
  }, { auth: true, onReconnect: refresh });
  return (
    <>
      <Link to="/notifications" className="bell" title="Notifications">🔔{unread > 0 && <span className="dot">{unread}</span>}</Link>
      {toast && <div className="toast"><b>{toast.title}</b><div>{toast.message}</div></div>}
    </>
  );
}

export default function Nav() {
  const { user, logout } = useAuth();
  const nav = useNavigate();
  const staff = user && ['STAFF', 'MANAGER', 'BUSINESS_ADMIN'].includes(user.role);
  const mgr = user && ['MANAGER', 'BUSINESS_ADMIN'].includes(user.role);
  return (
    <div className="nav">
      <Link to="/" className="brand">SmartQueue</Link>
      <NavLink to="/businesses">Find a business</NavLink>
      {user?.role === 'CUSTOMER' && <><NavLink to="/me">My queues</NavLink><NavLink to="/appointments">Appointments</NavLink></>}
      {staff && <NavLink to="/staff">Counter</NavLink>}
      {mgr && <NavLink to="/admin">Admin</NavLink>}
      {user?.role === 'DISPLAY' && <NavLink to="/display">Display</NavLink>}
      {user?.role === 'PLATFORM_ADMIN' && <NavLink to="/platform">Platform</NavLink>}
      <span className="spacer" />
      {user ? (
        <>
          {user.role !== 'PLATFORM_ADMIN' && user.role !== 'DISPLAY' && <Bell />}
          <span className="muted">{user.fullName}{user.businessName ? ` · ${user.businessName}` : ''}</span>
          <button className="secondary" onClick={() => { logout(); nav('/'); }}>Sign out</button>
        </>
      ) : (
        <>
          <NavLink to="/login">Sign in</NavLink>
          <NavLink to="/business/login">Business</NavLink>
          <Link to="/register"><button>Sign up</button></Link>
        </>
      )}
    </div>
  );
}
