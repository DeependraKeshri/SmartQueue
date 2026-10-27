import React, { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { errMsg } from '../api.js';
import { homeFor, useAuth } from '../auth.jsx';
import { Field, Msg, useForm } from '../components/ui.jsx';

export function Login({ business }) {
  const { login } = useAuth();
  const nav = useNavigate();
  const loc = useLocation();
  const [f, , bind] = useForm({ email: '', password: '' });
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);
  const submit = async (e) => {
    e.preventDefault(); setErr(''); setBusy(true);
    try {
      const u = await login(f.email, f.password);
      nav(loc.state?.from || homeFor(u.role), { replace: true });
    } catch (x) { setErr(errMsg(x)); } finally { setBusy(false); }
  };
  return (
    <div className="container narrow">
      <form className="card" onSubmit={submit}>
        <h2>{business ? 'Business sign in' : 'Sign in'}</h2>
        <Msg error={err} />
        <Field label="Email"><input type="email" required autoComplete="username" {...bind('email')} /></Field>
        <Field label="Password"><input type="password" required autoComplete="current-password" {...bind('password')} /></Field>
        <p><button disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button></p>
        <p className="muted">{business ? <Link to="/register/business">Register a business</Link> : <>New here? <Link to="/register">Create an account</Link></>}</p>
      </form>
    </div>
  );
}

export function RegisterCustomer() {
  const { registerCustomer } = useAuth();
  const nav = useNavigate();
  const [f, , bind] = useForm({ fullName: '', email: '', phone: '', password: '' });
  const [err, setErr] = useState('');
  const submit = async (e) => {
    e.preventDefault(); setErr('');
    try { await registerCustomer({ ...f, phone: f.phone || null }); nav('/me'); } catch (x) { setErr(errMsg(x)); }
  };
  return (
    <div className="container narrow">
      <form className="card" onSubmit={submit}>
        <h2>Create your account</h2>
        <Msg error={err} />
        <Field label="Full name"><input required {...bind('fullName')} /></Field>
        <Field label="Email"><input type="email" required {...bind('email')} /></Field>
        <Field label="Phone (optional)"><input {...bind('phone')} /></Field>
        <Field label="Password (8+ chars, letter and digit)"><input type="password" required minLength={8} autoComplete="new-password" {...bind('password')} /></Field>
        <p><button>Create account</button></p>
      </form>
    </div>
  );
}

export function RegisterBusiness() {
  const { registerBusiness } = useAuth();
  const nav = useNavigate();
  const [f, , bind] = useForm({ businessName: '', timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC',
    tokenPrefix: 'A', adminFullName: '', email: '', phone: '', password: '' });
  const [err, setErr] = useState('');
  const submit = async (e) => {
    e.preventDefault(); setErr('');
    try { await registerBusiness({ ...f, phone: f.phone || null }); nav('/admin/services'); } catch (x) { setErr(errMsg(x)); }
  };
  return (
    <div className="container narrow">
      <form className="card" onSubmit={submit}>
        <h2>Register your business</h2>
        <Msg error={err} />
        <Field label="Business name"><input required {...bind('businessName')} /></Field>
        <Field label="Timezone (IANA, e.g. Asia/Kolkata)"><input required {...bind('timezone')} /></Field>
        <Field label="Token prefix (1-5 letters)"><input required maxLength={5} pattern="[A-Za-z]{1,5}" {...bind('tokenPrefix')} /></Field>
        <Field label="Your name"><input required {...bind('adminFullName')} /></Field>
        <Field label="Email"><input type="email" required {...bind('email')} /></Field>
        <Field label="Phone (optional)"><input {...bind('phone')} /></Field>
        <Field label="Password (8+ chars, letter and digit)"><input type="password" required minLength={8} autoComplete="new-password" {...bind('password')} /></Field>
        <p><button>Create business</button></p>
        <p className="muted">Default hours are Mon–Fri 09:00–17:00 – change them under Admin → Hours.</p>
      </form>
    </div>
  );
}
