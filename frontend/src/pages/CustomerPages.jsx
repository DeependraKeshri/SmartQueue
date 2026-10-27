import React, { useCallback, useEffect, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { api, errMsg, etaText, fmtTime, newKey } from '../api.js';
import { Badge, Field, Msg, useLoad } from '../components/ui.jsx';
import { useStomp } from '../realtime.js';

export function CustomerDashboard() {
  const { data, error, reload } = useLoad(() => api.get('/api/customer/entries').then((r) => r.data), []);
  useStomp(['/user/queue/notifications'], () => reload(), { auth: true, onReconnect: reload });
  useEffect(() => { const t = setInterval(reload, 20000); return () => clearInterval(t); }, [reload]);
  const active = (data || []).filter((e) => ['WAITING', 'CALLED', 'SERVING'].includes(e.status));
  const past = (data || []).filter((e) => !['WAITING', 'CALLED', 'SERVING'].includes(e.status));
  return (
    <div className="container">
      <Msg error={error} />
      <h2>My queues</h2>
      {active.length === 0 && <div className="card muted">You are not in any queue. <Link to="/businesses">Find a business</Link></div>}
      <div className="grid">
        {active.map((e) => (
          <div className="card" key={e.id}>
            <div className="row" style={{ justifyContent: 'space-between' }}><b>{e.businessName}</b><Badge v={e.status} /></div>
            <div className="token" style={{ fontSize: '2.2rem' }}>{e.tokenLabel}</div>
            <p className="muted">{e.serviceName}</p>
            {e.status === 'WAITING' && <p>Position {e.position} · {etaText(e.eta)}</p>}
            {e.status === 'CALLED' && <p style={{ color: 'var(--ok)' }}><b>Go to {e.counterName || 'the counter'}</b></p>}
            <Link to={`/status/${e.publicId}`}><button>Live status</button></Link>
          </div>
        ))}
      </div>
      <h3>History</h3>
      <div className="card">
        <table><thead><tr><th>When</th><th>Business</th><th>Service</th><th>Token</th><th>Status</th></tr></thead>
          <tbody>{past.map((e) => (<tr key={e.id}><td>{fmtTime(e.joinedAt)}</td><td>{e.businessName}</td><td>{e.serviceName}</td><td>{e.tokenLabel}</td><td><Badge v={e.status} /></td></tr>))}</tbody></table>
        {past.length === 0 && <p className="muted">No history yet.</p>}
      </div>
    </div>
  );
}

function today(offset = 0) {
  const d = new Date(); d.setDate(d.getDate() + offset);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function SlotPicker({ serviceId, tz, onPick, picked }) {
  const [date, setDate] = useState(today(1));
  const { data, error } = useLoad(() => api.get(`/api/public/services/${serviceId}/slots`, { params: { date } }).then((r) => r.data), [serviceId, date]);
  return (
    <div>
      <Field label="Date"><input type="date" min={today(0)} value={date} onChange={(e) => setDate(e.target.value)} style={{ maxWidth: 200 }} /></Field>
      <Msg error={error} />
      <div className="row" style={{ marginTop: 10 }}>
        {(data || []).map((s) => (
          <button key={s.startAt} disabled={s.remaining <= 0} className={`slot ${picked === s.startAt ? 'sel' : ''}`} onClick={() => onPick(s.startAt)}>
            {new Date(s.startAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', timeZone: tz || undefined })}
          </button>
        ))}
      </div>
      {data && data.length === 0 && <p className="muted">No slots on this date (closed, holiday, past or outside booking window).</p>}
    </div>
  );
}

export function BookAppointment() {
  const { publicId } = useParams();
  const [sp] = useSearchParams();
  const { data: b } = useLoad(() => api.get(`/api/public/businesses/${publicId}`).then((r) => r.data), [publicId]);
  const [serviceId, setServiceId] = useState(sp.get('serviceId') ? Number(sp.get('serviceId')) : null);
  const [slot, setSlot] = useState(null);
  const [key, setKey] = useState(newKey());
  const [err, setErr] = useState('');
  const [ok, setOk] = useState('');
  if (!b) return <div className="container">Loading…</div>;
  const services = b.services.filter((s) => s.appointmentsEnabled);
  const book = async () => {
    setErr(''); setOk('');
    try {
      await api.post('/api/customer/appointments', { serviceId, startAt: slot }, { headers: { 'Idempotency-Key': key } });
      setOk('Appointment booked!'); setSlot(null); setKey(newKey());
    } catch (x) { setErr(errMsg(x)); setKey(newKey()); }
  };
  return (
    <div className="container narrow">
      <div className="card">
        <h2>Book at {b.name}</h2>
        <Field label="Service">
          <select value={serviceId || ''} onChange={(e) => { setServiceId(Number(e.target.value)); setSlot(null); }}>
            <option value="">Select…</option>{services.map((s) => <option key={s.id} value={s.id}>{s.name} ({s.estimatedDurationMinutes} min)</option>)}
          </select></Field>
        {serviceId && <SlotPicker serviceId={serviceId} tz={b.timezone} picked={slot} onPick={setSlot} />}
        <Msg error={err} ok={ok} />
        <p><button disabled={!slot} onClick={book}>Confirm booking</button> <Link to="/appointments">My appointments</Link></p>
      </div>
    </div>
  );
}

export function MyAppointments() {
  const { data, error, reload } = useLoad(() => api.get('/api/customer/appointments').then((r) => r.data), []);
  const [msg, setMsg] = useState({});
  const [resched, setResched] = useState(null);
  const [slot, setSlot] = useState(null);
  const act = async (a, path, body) => {
    try { await api.post(`/api/customer/appointments/${a.id}/${path}`, body || {}); setMsg({ ok: 'Done' }); setResched(null); reload(); }
    catch (x) { setMsg({ error: errMsg(x) }); }
  };
  return (
    <div className="container">
      <h2>My appointments</h2>
      <Msg error={error || msg.error} ok={msg.ok} />
      {(data || []).map((a) => (
        <div className="card" key={a.id}>
          <div className="row" style={{ justifyContent: 'space-between' }}><b>{a.businessName} – {a.serviceName}</b><Badge v={a.status} /></div>
          <p>{fmtTime(a.startAt, a.timezone)}</p>
          <div className="row">
            {['BOOKED', 'CONFIRMED'].includes(a.status) && <button onClick={() => act(a, 'check-in')}>I&apos;ve arrived (check in)</button>}
            {['BOOKED', 'CONFIRMED'].includes(a.status) && <button className="secondary" onClick={() => { setResched(resched === a.id ? null : a.id); setSlot(null); }}>Reschedule</button>}
            {['BOOKED', 'CONFIRMED', 'CHECKED_IN'].includes(a.status) && <button className="danger" onClick={() => window.confirm('Cancel this appointment?') && act(a, 'cancel')}>Cancel</button>}
            {a.queueEntryPublicId && a.status === 'CHECKED_IN' && <Link to={`/status/${a.queueEntryPublicId}`}>Live queue status</Link>}
          </div>
          {resched === a.id && (<div style={{ marginTop: 10 }}>
            <SlotPicker serviceId={a.serviceId} tz={a.timezone} picked={slot} onPick={setSlot} />
            <p><button disabled={!slot} onClick={() => act(a, 'reschedule', { startAt: slot })}>Move appointment</button></p></div>)}
        </div>
      ))}
      {data && data.length === 0 && <div className="card muted">No appointments yet. <Link to="/businesses">Find a business</Link></div>}
    </div>
  );
}

export function Notifications() {
  const { data, error, reload } = useLoad(() => api.get('/api/notifications', { params: { size: 50 } }).then((r) => r.data), []);
  const { data: prefs, setData: setPrefs } = useLoad(() => api.get('/api/notifications/preferences').then((r) => r.data), []);
  const [msg, setMsg] = useState('');
  const savePrefs = async (p) => { setPrefs(p); try { await api.put('/api/notifications/preferences', p); setMsg('Preferences saved'); } catch (x) { setMsg(errMsg(x)); } };
  const readAll = async () => { await api.post('/api/notifications/read-all'); reload(); };
  const askPush = async () => { if ('Notification' in window) await Notification.requestPermission(); setMsg('Browser permission: ' + (Notification?.permission || 'unsupported')); };
  return (
    <div className="container">
      <h2>Notifications</h2>
      <Msg error={error} ok={msg} />
      {prefs && (
        <div className="card">
          <h3>Preferences</h3>
          <div className="row">
            <label><input type="checkbox" checked={prefs.inAppEnabled} onChange={(e) => savePrefs({ ...prefs, inAppEnabled: e.target.checked })} />In-app</label>
            <label><input type="checkbox" checked={prefs.emailEnabled} onChange={(e) => savePrefs({ ...prefs, emailEnabled: e.target.checked })} />Email</label>
            <label><input type="checkbox" checked={prefs.pushEnabled} onChange={(e) => savePrefs({ ...prefs, pushEnabled: e.target.checked })} />Push</label>
            <button className="secondary" onClick={askPush}>Enable browser alerts</button>
          </div>
        </div>
      )}
      <div className="card">
        <div className="row" style={{ justifyContent: 'space-between' }}><h3>Inbox</h3><button className="secondary" onClick={readAll}>Mark all read</button></div>
        {(data?.items || []).map((n) => (
          <div key={n.id} style={{ padding: '8px 0', borderBottom: '1px solid var(--line)', opacity: n.readAt ? 0.6 : 1 }}>
            <b>{n.title}</b> <span className="muted">{fmtTime(n.createdAt)}</span><div>{n.message}</div>
          </div>
        ))}
        {data && data.items.length === 0 && <p className="muted">Nothing yet.</p>}
      </div>
    </div>
  );
}
