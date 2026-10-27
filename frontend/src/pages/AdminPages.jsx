import React, { useState } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { api, errMsg, fmtTime } from '../api.js';
import { useAuth } from '../auth.jsx';
import { useStomp } from '../realtime.js';
import { Badge, Field, Msg, useForm, useLoad } from '../components/ui.jsx';

const TABS = [['analytics', 'Analytics'], ['appointments', 'Appointments'], ['queues', 'Queues'], ['services', 'Services'], ['counters', 'Counters'], ['staff', 'Staff'],
  ['hours', 'Hours'], ['policy', 'Queue policy'], ['profile', 'Business'], ['audit', 'Audit log']];

export function AdminLayout() {
  return (
    <div className="container">
      <div className="tabs">{TABS.map(([p, l]) => <NavLink key={p} to={`/admin/${p}`} className={({ isActive }) => (isActive ? 'active' : '')}>{l}</NavLink>)}</div>
      <Outlet />
    </div>
  );
}

const iso = (d) => d.toISOString().slice(0, 10);

// ---------------------------------------------------------------- analytics + forecast
function Bars({ rows, label, value, max }) {
  const m = max || Math.max(1, ...rows.map(value));
  return rows.map((r) => (
    <div className="barrow" key={label(r)}><span className="lbl">{label(r)}</span>
      <div className="bar" style={{ width: `${(value(r) / m) * 100}%`, maxWidth: '70%' }} /><span>{value(r)}</span></div>
  ));
}

export function AdminAnalytics() {
  const [period, setPeriod] = useState('daily');
  const [date, setDate] = useState(iso(new Date()));
  const { data: a, error, loading } = useLoad(() => api.get(`/api/analytics/${period}`, { params: { date } }).then((r) => r.data), [period, date]);
  const { data: fc } = useLoad(() => api.get('/api/insights/demand', { params: { date: iso(new Date(Date.now() + 86400000)) } }).then((r) => r.data), []);
  const Stat = ({ l, v }) => <div className="card"><div className="muted">{l}</div><div className="big">{v}</div></div>;
  return (
    <>
      <div className="card row">
        <Field label="Period"><select value={period} onChange={(e) => setPeriod(e.target.value)}><option value="daily">Day</option><option value="weekly">Week</option><option value="monthly">Month</option></select></Field>
        <Field label="Date"><input type="date" value={date} onChange={(e) => setDate(e.target.value)} /></Field>
        {a && <span className="muted">{a.from} → {a.to} ({a.timezone})</span>}
      </div>
      <Msg error={error} />
      {loading && <p>Loading…</p>}
      {a && (<>
        <div className="grid">
          <Stat l="Customers" v={a.totalCustomers} /><Stat l="Completed" v={`${a.completed} (${a.completionRatePercent}%)`} />
          <Stat l="Cancelled" v={`${a.cancelled} (${a.cancellationRatePercent}%)`} /><Stat l="No-shows" v={`${a.noShows} (${a.noShowRatePercent}%)`} />
          <Stat l="Avg wait" v={`${a.avgWaitMinutes} min`} /><Stat l="Median wait" v={`${a.medianWaitMinutes} min`} />
          <Stat l="Max wait" v={`${a.maxWaitMinutes} min`} /><Stat l="Avg service" v={`${a.avgServiceMinutes} min`} />
          <Stat l="Appointments" v={`${a.appointmentsBooked} booked · ${a.appointmentsCompleted} done · ${a.appointmentsNoShow} no-show`} />
        </div>
        <div className="grid">
          <div className="card"><h3>Customers per hour (avg/day)</h3><Bars rows={a.customersPerHour} label={(r) => `${String(r.hour).padStart(2, '0')}:00`} value={(r) => r.avgCustomers} />
            {a.customersPerHour.length === 0 && <p className="muted">No data.</p>}
            {a.peakHours.length > 0 && <p className="muted">Peak: {a.peakHours.map((h) => `${h.hour}:00`).join(', ')}</p>}</div>
          <div className="card"><h3>Busiest days</h3><Bars rows={a.busiestDays} label={(r) => r.dayOfWeek.slice(0, 3)} value={(r) => r.totalCustomers} /></div>
          <div className="card"><h3>Queue length over time</h3><Bars rows={a.queueLength.slice(-24)} label={(r) => new Date(r.time).getHours() + 'h'} value={(r) => r.avgLength} /></div>
        </div>
        <div className="card"><h3>Services</h3><table><thead><tr><th>Service</th><th>Served</th><th>Avg service (min)</th><th>Avg wait (min)</th></tr></thead>
          <tbody>{a.services.map((s) => <tr key={s.serviceId}><td>{s.serviceName}</td><td>{s.served}</td><td>{s.avgServiceMinutes}</td><td>{s.avgWaitMinutes}</td></tr>)}</tbody></table></div>
        <div className="grid">
          <div className="card"><h3>Counter utilization</h3><table><thead><tr><th>Counter</th><th>Served</th><th>Busy (min)</th><th>Utilization</th></tr></thead>
            <tbody>{a.counters.map((c) => <tr key={c.counterId}><td>{c.counterName}</td><td>{c.served}</td><td>{c.busyMinutes}</td><td>{c.utilizationPercent}%</td></tr>)}</tbody></table></div>
          <div className="card"><h3>Staff</h3><table><thead><tr><th>Staff</th><th>Served</th><th>Avg (min)</th></tr></thead>
            <tbody>{a.staff.map((s) => <tr key={s.staffId}><td>{s.staffName}</td><td>{s.served}</td><td>{s.avgServiceMinutes}</td></tr>)}</tbody></table></div>
        </div>
        <p className="muted">{Object.values(a.notes).join(' ')}</p>
      </>)}
      {fc && (
        <div className="card">
          <h3>Tomorrow&apos;s demand forecast</h3>
          {!fc.sufficientData ? <p className="muted">{fc.message}</p> : (<>
            <p className="muted">Method: {fc.method} · based on {fc.historyDays} day(s) · {fc.activeCounters} active counter(s){fc.message ? ` · ${fc.message}` : ''}</p>
            <table><thead><tr><th>Hour</th><th>Expected arrivals</th><th>Capacity/h</th><th>Utilization</th><th>Exp. queue</th><th>Counters needed</th></tr></thead>
              <tbody>{fc.hours.map((h) => <tr key={h.hour}><td>{h.hour}:00</td><td>{h.expectedArrivals}</td><td>{h.capacityPerHour}</td><td>{h.utilizationPercent}%</td><td>{h.expectedQueueLength}</td><td>{h.recommendedCounters}</td></tr>)}</tbody></table>
            {fc.recommendations.map((r, i) => <div key={i} className={r.severity === 'HIGH' ? 'error' : 'success'}>{r.message}</div>)}
            <p className="muted">Recommendations are suggestions only – nothing is changed automatically.</p></>)}
        </div>)}
    </>
  );
}

// ---------------------------------------------------------------- appointments
export function AdminAppointments() {
  const [date, setDate] = useState(iso(new Date()));
  const { data, error, reload } = useLoad(() => api.get('/api/appointments', { params: { date } }).then((r) => r.data), [date]);
  const [msg, setMsg] = useState({});
  const { user } = useAuth();
  useStomp([`/topic/business/${user.businessId}/appointments`], () => reload(), { auth: true, onReconnect: reload });
  const act = async (a, p) => { try { await api.post(`/api/appointments/${a.id}/${p}`, p === 'cancel' ? {} : undefined); setMsg({}); reload(); } catch (x) { setMsg({ error: errMsg(x) }); } };
  return (
    <div className="card">
      <div className="row"><h3>Appointments</h3><input type="date" value={date} onChange={(e) => setDate(e.target.value)} style={{ maxWidth: 180 }} /></div>
      <Msg error={error || msg.error} />
      <table><thead><tr><th>Time</th><th>Customer</th><th>Service</th><th>Status</th><th /></tr></thead>
        <tbody>{(data || []).map((a) => (<tr key={a.id}><td>{fmtTime(a.startAt, a.timezone)}</td><td>{a.customerName}</td><td>{a.serviceName}</td><td><Badge v={a.status} /></td>
          <td className="row">
            {a.status === 'BOOKED' && <button onClick={() => act(a, 'confirm')}>Confirm</button>}
            {['BOOKED', 'CONFIRMED'].includes(a.status) && <button className="secondary" onClick={() => act(a, 'check-in')}>Check in</button>}
            {['BOOKED', 'CONFIRMED'].includes(a.status) && <button className="warn" onClick={() => act(a, 'no-show')}>No-show</button>}
            {['BOOKED', 'CONFIRMED', 'CHECKED_IN'].includes(a.status) && <button className="danger" onClick={() => window.confirm('Cancel appointment?') && act(a, 'cancel')}>Cancel</button>}
          </td></tr>))}</tbody></table>
      {data && data.length === 0 && <p className="muted">No appointments on this date.</p>}
    </div>
  );
}

// ---------------------------------------------------------------- services
export function AdminServices() {
  const { data, error, reload } = useLoad(() => api.get('/api/services').then((r) => r.data), []);
  const blank = { name: '', description: '', estimatedDurationMinutes: 10, slotIntervalMinutes: 15, tokenPrefix: '', active: true, queueEnabled: true, appointmentsEnabled: false, priorityAllowed: true };
  const [f, setF, bind, check] = useForm(blank);
  const [editing, setEditing] = useState(null);
  const [msg, setMsg] = useState({});
  const save = async (e) => {
    e.preventDefault(); setMsg({});
    const body = { ...f, estimatedDurationMinutes: Number(f.estimatedDurationMinutes), slotIntervalMinutes: Number(f.slotIntervalMinutes), tokenPrefix: f.tokenPrefix || null };
    try { editing ? await api.put(`/api/services/${editing}`, body) : await api.post('/api/services', body); setF(blank); setEditing(null); setMsg({ ok: 'Saved' }); reload(); }
    catch (x) { setMsg({ error: errMsg(x) }); }
  };
  const del = async (s) => { if (!window.confirm(`Delete ${s.name}? History is kept.`)) return; try { await api.delete(`/api/services/${s.id}`); reload(); } catch (x) { setMsg({ error: errMsg(x) }); } };
  return (
    <>
      <form className="card" onSubmit={save}>
        <h3>{editing ? 'Edit service' : 'New service'}</h3>
        <Msg error={error || msg.error} ok={msg.ok} />
        <div className="grid">
          <Field label="Name"><input required {...bind('name')} /></Field>
          <Field label="Description"><input {...bind('description')} /></Field>
          <Field label="Duration (min)"><input type="number" min="1" max="600" {...bind('estimatedDurationMinutes')} /></Field>
          <Field label="Slot interval (min)"><input type="number" min="5" max="240" {...bind('slotIntervalMinutes')} /></Field>
          <Field label="Token prefix (optional)"><input maxLength={5} {...bind('tokenPrefix')} /></Field>
        </div>
        <div className="row">
          <label><input type="checkbox" {...check('active')} />Active</label><label><input type="checkbox" {...check('queueEnabled')} />Live queue</label>
          <label><input type="checkbox" {...check('appointmentsEnabled')} />Appointments</label><label><input type="checkbox" {...check('priorityAllowed')} />Priority levels</label>
        </div>
        <p><button>{editing ? 'Save changes' : 'Create service'}</button> {editing && <button type="button" className="secondary" onClick={() => { setEditing(null); setF(blank); }}>Cancel</button>}</p>
      </form>
      <div className="card"><table><thead><tr><th>Service</th><th>Duration</th><th>Queue</th><th>Appts</th><th>QR</th><th /></tr></thead>
        <tbody>{(data || []).map((s) => (<tr key={s.id}><td><b>{s.name}</b> {!s.active && <Badge v="INACTIVE" />}</td><td>{s.estimatedDurationMinutes} min</td>
          <td>{s.queuePublicId ? <a href={`/display/${s.queuePublicId}`} target="_blank" rel="noreferrer">Display screen</a> : '-'}</td><td>{s.appointmentsEnabled ? 'Yes' : 'No'}</td>
          <td>{s.queuePublicId && <a href={`/api/public/qr/queues/${s.queuePublicId}.png?size=512`} target="_blank" rel="noreferrer">QR</a>}</td>
          <td className="row"><button className="secondary" onClick={() => { setEditing(s.id); setF({ ...s, tokenPrefix: s.tokenPrefix || '' }); window.scrollTo(0, 0); }}>Edit</button>
            <button className="danger" onClick={() => del(s)}>Delete</button></td></tr>))}</tbody></table></div>
    </>
  );
}

// ---------------------------------------------------------------- counters
export function AdminCounters() {
  const { data, error, reload } = useLoad(() => api.get('/api/counters').then((r) => r.data), []);
  const { data: services } = useLoad(() => api.get('/api/services').then((r) => r.data), []);
  const { data: staff } = useLoad(() => api.get('/api/staff').then((r) => r.data), []);
  const blank = { name: '', assignedStaffId: '', active: true, serviceIds: [] };
  const [f, setF, bind, check] = useForm(blank);
  const [editing, setEditing] = useState(null);
  const [msg, setMsg] = useState({});
  const toggleSvc = (id) => setF({ ...f, serviceIds: f.serviceIds.includes(id) ? f.serviceIds.filter((x) => x !== id) : [...f.serviceIds, id] });
  const save = async (e) => {
    e.preventDefault(); setMsg({});
    const body = { name: f.name, active: f.active, assignedStaffId: f.assignedStaffId ? Number(f.assignedStaffId) : null, serviceIds: f.serviceIds };
    try { editing ? await api.put(`/api/counters/${editing}`, body) : await api.post('/api/counters', body); setF(blank); setEditing(null); reload(); }
    catch (x) { setMsg({ error: errMsg(x) }); }
  };
  const status = async (c, s) => { try { await api.patch(`/api/counters/${c.id}/status`, { status: s }); reload(); } catch (x) { setMsg({ error: errMsg(x) }); } };
  return (
    <>
      <form className="card" onSubmit={save}>
        <h3>{editing ? 'Edit counter' : 'New counter'}</h3>
        <Msg error={error || msg.error} />
        <div className="grid">
          <Field label="Name"><input required {...bind('name')} /></Field>
          <Field label="Assigned staff"><select {...bind('assignedStaffId')}><option value="">— none —</option>
            {(staff || []).filter((s) => ['STAFF', 'MANAGER'].includes(s.role) && s.active).map((s) => <option key={s.id} value={s.id}>{s.fullName}</option>)}</select></Field>
        </div>
        <label>Services served (none selected = all)</label>
        <div className="row">{(services || []).map((s) => <label key={s.id}><input type="checkbox" checked={f.serviceIds.includes(s.id)} onChange={() => toggleSvc(s.id)} />{s.name}</label>)}</div>
        <div className="row"><label><input type="checkbox" {...check('active')} />Active</label></div>
        <p><button>{editing ? 'Save' : 'Create counter'}</button> {editing && <button type="button" className="secondary" onClick={() => { setEditing(null); setF(blank); }}>Cancel</button>}</p>
      </form>
      <div className="card"><table><thead><tr><th>Counter</th><th>Status</th><th>Staff</th><th>Services</th><th /></tr></thead>
        <tbody>{(data || []).map((c) => (<tr key={c.id}><td>{c.name}</td><td><Badge v={c.active ? c.status : 'INACTIVE'} /></td><td>{c.assignedStaffName || '-'}</td><td>{c.serviceNames.join(', ') || 'All'}</td>
          <td className="row"><button className="secondary" onClick={() => { setEditing(c.id); setF({ name: c.name, active: c.active, assignedStaffId: c.assignedStaffId || '', serviceIds: c.serviceIds }); }}>Edit</button>
            {['OPEN', 'ON_BREAK', 'CLOSED'].filter((s) => s !== c.status).map((s) => <button key={s} className="secondary" onClick={() => status(c, s)}>{s}</button>)}</td></tr>))}</tbody></table></div>
    </>
  );
}

// ---------------------------------------------------------------- staff
export function AdminStaff() {
  const { user } = useAuth();
  const { data, error, reload } = useLoad(() => api.get('/api/staff').then((r) => r.data), []);
  const blank = { fullName: '', email: '', phone: '', password: '', role: 'STAFF' };
  const [f, setF, bind] = useForm(blank);
  const [msg, setMsg] = useState({});
  const create = async (e) => {
    e.preventDefault(); setMsg({});
    try { await api.post('/api/staff', { ...f, phone: f.phone || null, serviceIds: [] }); setF(blank); setMsg({ ok: 'Account created' }); reload(); } catch (x) { setMsg({ error: errMsg(x) }); }
  };
  const toggle = async (u) => { try { await api.put(`/api/staff/${u.id}`, { active: !u.active }); reload(); } catch (x) { setMsg({ error: errMsg(x) }); } };
  return (
    <>
      <form className="card" onSubmit={create}>
        <h3>Add team member</h3><Msg error={error || msg.error} ok={msg.ok} />
        <div className="grid">
          <Field label="Full name"><input required {...bind('fullName')} /></Field><Field label="Email"><input type="email" required {...bind('email')} /></Field>
          <Field label="Phone"><input {...bind('phone')} /></Field><Field label="Temporary password"><input type="password" required minLength={8} autoComplete="new-password" {...bind('password')} /></Field>
          <Field label="Role"><select {...bind('role')}><option value="STAFF">Staff</option>{user.role === 'BUSINESS_ADMIN' && <option value="MANAGER">Manager</option>}<option value="DISPLAY">Display screen</option></select></Field>
        </div>
        <p><button>Create account</button></p>
      </form>
      <div className="card"><table><thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Active</th><th /></tr></thead>
        <tbody>{(data || []).map((u) => (<tr key={u.id}><td>{u.fullName}</td><td>{u.email}</td><td>{u.role}</td><td>{u.active ? 'Yes' : 'No'}</td>
          <td>{u.role !== 'BUSINESS_ADMIN' && <button className={u.active ? 'danger' : 'ok'} onClick={() => toggle(u)}>{u.active ? 'Deactivate' : 'Activate'}</button>}</td></tr>))}</tbody></table></div>
    </>
  );
}

// ---------------------------------------------------------------- hours / breaks / holidays
const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
export function AdminHours() {
  const { data, error, reload, setData } = useLoad(() => api.get('/api/business/schedule').then((r) => r.data), []);
  const [msg, setMsg] = useState({});
  const [brk, setBrk] = useState({ dayOfWeek: '', startTime: '12:00', endTime: '13:00', label: 'Lunch' });
  const [hol, setHol] = useState({ date: '', name: '' });
  if (!data) return <Msg error={error} />;
  const rows = DAYS.map((d) => data.hours.find((h) => h.dayOfWeek === d) || { dayOfWeek: d, openTime: '09:00:00', closeTime: '17:00:00', closed: true });
  const upd = (d, patch) => setData({ ...data, hours: rows.map((r) => (r.dayOfWeek === d ? { ...r, ...patch } : r)) });
  const t5 = (t) => (t ? t.slice(0, 5) : '');
  const saveHours = async () => {
    try { await api.put('/api/business/hours', { days: rows.map((r) => ({ ...r, openTime: r.closed ? null : t5(r.openTime) + ':00', closeTime: r.closed ? null : t5(r.closeTime) + ':00' })) }); setMsg({ ok: 'Hours saved' }); reload(); }
    catch (x) { setMsg({ error: errMsg(x) }); }
  };
  const addBreak = async () => { try { await api.post('/api/business/breaks', { ...brk, dayOfWeek: brk.dayOfWeek || null, startTime: brk.startTime + ':00', endTime: brk.endTime + ':00' }); reload(); } catch (x) { setMsg({ error: errMsg(x) }); } };
  const addHoliday = async () => { try { await api.post('/api/business/holidays', hol); setHol({ date: '', name: '' }); reload(); } catch (x) { setMsg({ error: errMsg(x) }); } };
  const del = async (kind, id) => { await api.delete(`/api/business/${kind}/${id}`); reload(); };
  return (
    <>
      <div className="card"><h3>Opening hours ({data.timezone}) <span className={`badge ${data.openNow ? 'OPEN' : 'CLOSED'}`}>{data.openNow ? 'open now' : 'closed now'}</span></h3>
        <Msg error={msg.error} ok={msg.ok} />
        <table><tbody>{rows.map((r) => (<tr key={r.dayOfWeek}><td>{r.dayOfWeek}</td>
          <td><label><input type="checkbox" checked={r.closed} onChange={(e) => upd(r.dayOfWeek, { closed: e.target.checked })} />Closed</label></td>
          <td><input type="time" disabled={r.closed} value={t5(r.openTime)} onChange={(e) => upd(r.dayOfWeek, { openTime: e.target.value })} /></td>
          <td><input type="time" disabled={r.closed} value={t5(r.closeTime)} onChange={(e) => upd(r.dayOfWeek, { closeTime: e.target.value })} /></td></tr>))}</tbody></table>
        <p><button onClick={saveHours}>Save hours</button></p></div>
      <div className="grid">
        <div className="card"><h3>Breaks</h3>
          {data.breaks.map((b) => <div className="row" key={b.id}>{b.dayOfWeek || 'Every day'} {t5(b.startTime)}–{t5(b.endTime)} {b.label}<button className="danger" onClick={() => del('breaks', b.id)}>Remove</button></div>)}
          <div className="row"><select value={brk.dayOfWeek} onChange={(e) => setBrk({ ...brk, dayOfWeek: e.target.value })}><option value="">Every day</option>{DAYS.map((d) => <option key={d}>{d}</option>)}</select>
            <input type="time" value={brk.startTime} onChange={(e) => setBrk({ ...brk, startTime: e.target.value })} /><input type="time" value={brk.endTime} onChange={(e) => setBrk({ ...brk, endTime: e.target.value })} />
            <input placeholder="Label" value={brk.label} onChange={(e) => setBrk({ ...brk, label: e.target.value })} /><button onClick={addBreak}>Add</button></div></div>
        <div className="card"><h3>Holidays</h3>
          {data.holidays.map((h) => <div className="row" key={h.id}>{h.date} {h.name}<button className="danger" onClick={() => del('holidays', h.id)}>Remove</button></div>)}
          <div className="row"><input type="date" value={hol.date} onChange={(e) => setHol({ ...hol, date: e.target.value })} /><input placeholder="Name" value={hol.name} onChange={(e) => setHol({ ...hol, name: e.target.value })} /><button onClick={addHoliday} disabled={!hol.date}>Add</button></div></div>
      </div>
    </>
  );
}

// ---------------------------------------------------------------- queue policy
const POLICY_FIELDS = [['emergencyWeight', 'Emergency weight'], ['appointmentWeight', 'Appointment weight'], ['priorityWeight', 'Priority weight'], ['normalWeight', 'Normal weight'],
  ['agingPointsPerMinute', 'Aging points per minute waited'], ['maxWaitMinutes', 'Max wait before guaranteed service (min, 0=off)'], ['fairnessEveryN', 'Serve a normal customer after N priority calls (0=off)'],
  ['appointmentEarlyWindowMinutes', 'Appointment early window (min)'], ['appointmentLateGraceMinutes', 'Appointment late grace (min)'], ['noShowGraceMinutes', 'No-show grace (min)'],
  ['maxRecalls', 'Max recalls'], ['approachingThreshold', '"Turn approaching" at position']];
export function AdminPolicy() {
  const { data, error, setData } = useLoad(() => api.get('/api/business/policy').then((r) => r.data), []);
  const [msg, setMsg] = useState({});
  if (!data) return <Msg error={error} />;
  const num = (k) => ({ type: 'number', step: k === 'agingPointsPerMinute' ? '0.1' : '1', value: data[k], onChange: (e) => setData({ ...data, [k]: Number(e.target.value) }) });
  const save = async (e) => { e.preventDefault(); try { setData((await api.put('/api/business/policy', data)).data); setMsg({ ok: 'Policy saved' }); } catch (x) { setMsg({ error: errMsg(x) }); } };
  return (
    <form className="card" onSubmit={save}>
      <h3>Queue scheduling policy</h3><p className="muted">Score = level weight + aging × minutes waited. Emergencies always go first; the max-wait guard and fairness rule prevent starvation.</p>
      <Msg error={msg.error} ok={msg.ok} />
      <div className="grid">{POLICY_FIELDS.map(([k, l]) => <Field key={k} label={l}><input {...num(k)} /></Field>)}
        <Field label="Appointment policy"><select value={data.appointmentPolicy} onChange={(e) => setData({ ...data, appointmentPolicy: e.target.value })}><option value="APPOINTMENT_PRIORITY">Appointments get priority in their window</option><option value="FIFO">First come, first served</option></select></Field></div>
      <div className="row"><label><input type="checkbox" checked={data.emergencyBypassesFairness} onChange={(e) => setData({ ...data, emergencyBypassesFairness: e.target.checked })} />Emergencies bypass fairness counter</label>
        <label><input type="checkbox" checked={data.autoNoShow} onChange={(e) => setData({ ...data, autoNoShow: e.target.checked })} />Auto no-show</label></div>
      <p><button>Save policy</button> <span className="muted">(business administrator only)</span></p>
    </form>
  );
}

// ---------------------------------------------------------------- business profile
export function AdminProfile() {
  const { data, error, setData } = useLoad(() => api.get('/api/business').then((r) => r.data), []);
  const [msg, setMsg] = useState({});
  if (!data) return <Msg error={error} />;
  const set = (k) => ({ value: data[k] ?? '', onChange: (e) => setData({ ...data, [k]: e.target.value }) });
  const chk = (k) => ({ checked: !!data[k], onChange: (e) => setData({ ...data, [k]: e.target.checked }) });
  const save = async (e) => {
    e.preventDefault();
    try { setData((await api.patch('/api/business', { name: data.name, description: data.description, logoUrl: data.logoUrl, timezone: data.timezone, tokenPrefix: data.tokenPrefix,
      displayMode: data.displayMode, allowGuestJoin: data.allowGuestJoin, showWaitOnDisplay: data.showWaitOnDisplay, inAppNotificationsEnabled: data.inAppNotificationsEnabled,
      emailNotificationsEnabled: data.emailNotificationsEnabled, pushNotificationsEnabled: data.pushNotificationsEnabled, autoConfirmAppointments: data.autoConfirmAppointments,
      contactEmail: data.contactEmail || undefined, contactPhone: data.contactPhone || undefined })).data); setMsg({ ok: 'Saved' }); }
    catch (x) { setMsg({ error: errMsg(x) }); }
  };
  return (
    <form className="card" onSubmit={save}>
      <h3>Business settings</h3><Msg error={msg.error} ok={msg.ok} />
      <div className="grid">
        <Field label="Name"><input required {...set('name')} /></Field><Field label="Description"><input {...set('description')} /></Field>
        <Field label="Logo URL"><input {...set('logoUrl')} /></Field><Field label="Timezone"><input {...set('timezone')} /></Field>
        <Field label="Default token prefix (new queues)"><input maxLength={5} {...set('tokenPrefix')} /></Field>
        <Field label="Public display privacy"><select {...set('displayMode')}><option value="TOKEN_ONLY">Token only</option><option value="MASKED_NAME">Masked name (J*** D***)</option>
          <option value="FIRST_NAME_INITIAL">First name + initial</option><option value="ANONYMOUS_TOKEN">Anonymous (token only, no counter)</option></select></Field>
      </div>
      <div className="row">
        <label><input type="checkbox" {...chk('allowGuestJoin')} />Allow guests (QR) to join</label><label><input type="checkbox" {...chk('showWaitOnDisplay')} />Show wait time on screens</label>
        <label><input type="checkbox" {...chk('autoConfirmAppointments')} />Auto-confirm appointments</label>
      </div>
      <div className="row"><b>Notifications:</b><label><input type="checkbox" {...chk('inAppNotificationsEnabled')} />In-app</label><label><input type="checkbox" {...chk('emailNotificationsEnabled')} />Email</label>
        <label><input type="checkbox" {...chk('pushNotificationsEnabled')} />Push</label></div>
      <p><button>Save</button></p>
      <p className="muted">Business QR: <a href={`/api/public/qr/businesses/${data.publicId}.png?size=512`} target="_blank" rel="noreferrer">download</a> · Public page: <a href={`/b/${data.publicId}`}>/b/{data.publicId}</a></p>
    </form>
  );
}

// ---------------------------------------------------------------- audit log
export function AdminAudit() {
  const [action, setAction] = useState('');
  const [page, setPage] = useState(0);
  const { data, error } = useLoad(() => api.get('/api/audit-logs', { params: { action: action || undefined, page, size: 30 } }).then((r) => r.data), [action, page]);
  return (
    <div className="card">
      <div className="row"><h3>Audit log</h3><input placeholder="Filter by exact action, e.g. QUEUE_MANUAL_OVERRIDE" value={action} onChange={(e) => { setAction(e.target.value.trim()); setPage(0); }} style={{ maxWidth: 380 }} /></div>
      <Msg error={error} />
      <table><thead><tr><th>Time</th><th>Action</th><th>Resource</th><th>Actor</th><th>Details</th></tr></thead>
        <tbody>{(data?.items || []).map((a) => (<tr key={a.id}><td>{fmtTime(a.createdAt)}</td><td>{a.action}</td><td>{a.resourceType} {a.resourceId}</td><td>{a.actorRole}{a.actorId ? ` #${a.actorId}` : ''}</td><td className="muted">{a.metadata}</td></tr>))}</tbody></table>
      <div className="row"><button className="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>Prev</button>
        <span>Page {page + 1} of {Math.max(1, data?.totalPages || 1)}</span><button className="secondary" disabled={!data || page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Next</button></div>
    </div>
  );
}

// ---------------------------------------------------------------- queue configuration
export function AdminQueues() {
  const { data, error, reload } = useLoad(() => api.get('/api/queues').then((r) => r.data), []);
  const { data: services } = useLoad(() => api.get('/api/services').then((r) => r.data), []);
  const [msg, setMsg] = useState({});
  const [svc, setSvc] = useState('');
  const run = async (fn, ok) => { setMsg({}); try { const r = await fn(); setMsg({ ok: typeof ok === 'function' ? ok(r) : ok }); reload(); } catch (x) { setMsg({ error: errMsg(x) }); } };
  const setStatus = (q, status) => {
    let cancelWaiting = false;
    if (status === 'INACTIVE' && q.waitingCount > 0) {
      if (!window.confirm(`${q.waitingCount} customer(s) are waiting. Deactivating cancels them and notifies them. Continue?`)) return;
      cancelWaiting = true;
    }
    run(() => api.patch(`/api/queues/${q.id}/status`, { status, cancelWaiting }), 'Queue updated');
  };
  const delay = (q) => { const m = window.prompt('Announce a delay of how many minutes?', '15'); if (m) run(() => api.post(`/api/queues/${q.id}/announce-delay`, null, { params: { minutes: Number(m) } }), (r) => `Notified ${r.data.notified} waiting customer(s)`); };
  const withoutQueue = (services || []).filter((s) => !s.queueId);
  return (
    <>
      <div className="card"><h3>Queues</h3><Msg error={error || msg.error} ok={msg.ok} />
        <table><thead><tr><th>Queue</th><th>Prefix</th><th>Status</th><th>Waiting</th><th>At counter</th><th /></tr></thead>
          <tbody>{(data || []).map((q) => (<tr key={q.id}><td>{q.name}<div className="muted">{q.serviceName}</div></td><td>{q.tokenPrefix}</td><td><Badge v={q.status} /></td><td>{q.waitingCount}</td><td>{q.atCounterCount}</td>
            <td className="row">{['ACTIVE', 'PAUSED', 'INACTIVE'].filter((s) => s !== q.status).map((s) => <button key={s} className={s === 'INACTIVE' ? 'danger' : 'secondary'} onClick={() => setStatus(q, s)}>{s === 'ACTIVE' ? 'Activate' : s === 'PAUSED' ? 'Pause' : 'Deactivate'}</button>)}
              <button className="warn" onClick={() => delay(q)}>Announce delay</button><a href={`/display/${q.publicId}`} target="_blank" rel="noreferrer">Display</a></td></tr>))}</tbody></table></div>
      {withoutQueue.length > 0 && (<div className="card"><h3>Create a queue for a service</h3><div className="row">
        <select value={svc} onChange={(e) => setSvc(e.target.value)}><option value="">Select service…</option>{withoutQueue.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}</select>
        <button disabled={!svc} onClick={() => run(() => api.post('/api/queues', { serviceId: Number(svc) }), 'Queue created')}>Create queue</button></div></div>)}
    </>
  );
}
