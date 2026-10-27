import React from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { homeFor, useAuth } from './auth.jsx';
import Nav from './components/Nav.jsx';
import Landing from './pages/Landing.jsx';
import { Login, RegisterBusiness, RegisterCustomer } from './pages/AuthPages.jsx';
import { BusinessList, BusinessPage } from './pages/PublicPages.jsx';
import JoinQueue from './pages/JoinQueue.jsx';
import QueueStatus from './pages/QueueStatus.jsx';
import Display from './pages/Display.jsx';
import { BookAppointment, CustomerDashboard, MyAppointments, Notifications } from './pages/CustomerPages.jsx';
import StaffConsole from './pages/StaffConsole.jsx';
import { AdminLayout, AdminQueues, AdminAnalytics, AdminAppointments, AdminAudit, AdminCounters, AdminHours, AdminPolicy,
  AdminProfile, AdminServices, AdminStaff } from './pages/AdminPages.jsx';
import Platform from './pages/Platform.jsx';

function Protected({ roles, children }) {
  const { user } = useAuth();
  const loc = useLocation();
  if (!user) return <Navigate to="/login" state={{ from: loc.pathname }} replace />;
  if (roles && !roles.includes(user.role)) return <Navigate to={homeFor(user.role)} replace />;
  return children;
}

const STAFF = ['STAFF', 'MANAGER', 'BUSINESS_ADMIN'];
const MANAGERS = ['MANAGER', 'BUSINESS_ADMIN'];

export default function App() {
  const { pathname } = useLocation();
  const bare = pathname.startsWith('/display');
  return (
    <>
      {!bare && <Nav />}
      <Routes>
        <Route path="/" element={<Landing />} />
        <Route path="/login" element={<Login />} />
        <Route path="/business/login" element={<Login business />} />
        <Route path="/register" element={<RegisterCustomer />} />
        <Route path="/register/business" element={<RegisterBusiness />} />
        <Route path="/businesses" element={<BusinessList />} />
        <Route path="/b/:publicId" element={<BusinessPage />} />
        <Route path="/join/:queuePublicId" element={<JoinQueue />} />
        <Route path="/status/:entryPublicId" element={<QueueStatus />} />
        <Route path="/display/:queuePublicId" element={<Display />} />
        <Route path="/display" element={<Protected roles={['DISPLAY', ...STAFF]}><Display /></Protected>} />

        <Route path="/me" element={<Protected roles={['CUSTOMER']}><CustomerDashboard /></Protected>} />
        <Route path="/appointments" element={<Protected roles={['CUSTOMER']}><MyAppointments /></Protected>} />
        <Route path="/book/:publicId" element={<Protected roles={['CUSTOMER']}><BookAppointment /></Protected>} />
        <Route path="/notifications" element={<Protected><Notifications /></Protected>} />

        <Route path="/staff" element={<Protected roles={STAFF}><StaffConsole /></Protected>} />
        <Route path="/admin" element={<Protected roles={MANAGERS}><AdminLayout /></Protected>}>
          <Route index element={<Navigate to="analytics" replace />} />
          <Route path="analytics" element={<AdminAnalytics />} />
          <Route path="appointments" element={<AdminAppointments />} />
          <Route path="queues" element={<AdminQueues />} />
          <Route path="services" element={<AdminServices />} />
          <Route path="counters" element={<AdminCounters />} />
          <Route path="staff" element={<AdminStaff />} />
          <Route path="hours" element={<AdminHours />} />
          <Route path="policy" element={<AdminPolicy />} />
          <Route path="profile" element={<AdminProfile />} />
          <Route path="audit" element={<AdminAudit />} />
        </Route>
        <Route path="/platform" element={<Protected roles={['PLATFORM_ADMIN']}><Platform /></Protected>} />
        <Route path="*" element={<div className="container"><div className="card"><h2>Page not found</h2><a href="/">Go home</a></div></div>} />
      </Routes>
    </>
  );
}
