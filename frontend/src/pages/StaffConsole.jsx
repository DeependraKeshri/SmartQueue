import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { api, errMsg, etaText, fmtClock } from '../api.js';
import { useAuth } from '../auth.jsx';
import { Badge, ConnectionBanner, Field, Msg, useForm } from '../components/ui.jsx';
import { useStomp } from '../realtime.js';

export default function StaffConsole() {
  const { user } = useAuth();
  const isManager = ['MANAGER', 'BUSINESS_ADMIN'].includes(user.role);
  const [counters, setCounters] = useState([]);
  const [queues, setQueues] = useState([]);
  const [views, setViews] = useState([]);       // staff view of each queue
  const [counterId, setCounterId] = useState(null);
  const [msg, setMsg] = useState({});
  const [f, , bind] = useForm({ queueId: '', guestName: '', guestPhone: '', priority: 'NORMAL' });
  const timer = useRef(null);

  const loadAll = useCallback(async () => {
    try {
      const [c, q] = await Promise.all([api.get('/api/counters'), api.get('/api/queues')]);
      setCounters(c.data); setQueues(q.data);
      const v = await Promise.all(q.data.map((x) => api.get(`/api/queues/${x.id}`).then((r) => r.data)));
      setViews(v);
      setCounterId((cur) => cur || c.data.find((x) => x.assignedStaffId === user.id)?.id || c.data[0]?.id || null);
    } catch (x) { setMsg({ error: errMsg(x) }); }
  }, [user.id]);
  useEffect(() => { loadAll(); const t = setInterval(loadAll, 30000); return () => clearInterval(t); }, [loadAll]);

  // live updates: any queue event triggers a (debounced) refresh; reconnect also refreshes
  const status = useStomp([`/topic/business/${user.businessId}/queues`], () => {
    clearTimeout(timer.current); timer.current = setTimeout(loadAll, 250);
  }, { auth: true, onReconnect: loadAll });

  const counter = counters.find((c) => c.id === counterId);
  const atMyCounter = useMemo(() => views.flatMap((v) => v.atCounter).filter((e) => e.counterId === counterId), [views, counterId]);
  const current = atMyCounter[0];
  const waiting = useMemo(() => views.flatMap((v) => v.waiting), [views]);
  const skipped = useMemo(() => views.flatMap((v) => v.skipped), [views]);

  const run = async (fn, okText) => {
    setMsg({});
    try { await fn(); if (okText) setMsg({ ok: okText }); await loadAll(); } catch (x) { setMsg({ error: errMsg(x) }); loadAll(); }
  };
  const callNext = () => run(async () => {
    const r = await api.post('/api/queues/call-next', { counterId });
    if (r.status === 204) setMsg({ ok: 'Nobody is waiting.' });
  });
  const act = (id, path, body) => run(() => api.post(`/api/queue-entries/${id}/${path}`, body));
  const walkIn = (e) => { e.preventDefault(); run(async () => {
    await api.post(`/api/queues/${f.queueId || queues[0].id}/walk-ins`, { guestName: f.guestName, guestPhone: f.guestPhone || null, priority: f.priority });
  }, 'Walk-in added'); };
  const setCounterStatus = (s) => run(() => api.patch(`/api/counters/${counterId}/status`, { status: s }));
  const reason = (what) => window.prompt(`Reason for ${what} (required, audited):`);

  return (
    <div className="container">
      <ConnectionBanner status={status} />
      <Msg error={msg.error} ok={msg.ok} />
      <div className="card">
        <div className="row">
          <Field label="My counter">
            <select value={counterId || ''} onChange={(e) => setCounterId(Number(e.target.value))}>
              {counters.map((c) => <option key={c.id} value={c.id}>{c.name} ({c.status})</option>)}</select></Field>
          {counter && <Badge v={counter.status} />}
          {counter && ['OPEN', 'ON_BREAK', 'CLOSED'].filter((s) => s !== counter.status).map((s) => (
            <button key={s} className="secondary" onClick={() => setCounterStatus(s)}>{s === 'OPEN' ? 'Open counter' : s === 'ON_BREAK' ? 'Go on break' : 'Close counter'}</button>))}
        </div>
      </div>

      <div className="grid">
        <div className="card">
          <h3>Now at this counter</h3>
          {current ? (<>
            <div className="token" style={{ fontSize: '2.6rem' }}>{current.tokenLabel}</div>
            <p><b>{current.displayName}</b> {current.guestPhone && <span className="muted">· {current.guestPhone}</span>} <Badge v={current.status} /> <Badge v={current.priority} /></p>
            <div className="row">
              {current.status === 'CALLED' && <button onClick={() => act(current.id, 'start')}>Start serving</button>}
              <button className="ok" onClick={() => act(current.id, 'complete')}>Complete</button>
              {current.status === 'CALLED' && <>
                <button className="secondary" onClick={() => act(current.id, 'recall')}>Recall ({current.recallCount})</button>
                <button className="warn" onClick={() => act(current.id, 'skip')}>Skip</button>
                <button className="danger" onClick={() => act(current.id, 'no-show')}>No-show</button>
                <button className="secondary" onClick={() => act(current.id, 'move-to-end')}>Move to end</button></>}
            </div>
          </>) : (<>
            <p className="muted">No customer at this counter.</p>
            <button onClick={callNext} disabled={!counter || counter.status !== 'OPEN'}>Call next customer</button>
            {counter && counter.status !== 'OPEN' && <p className="muted">Open the counter first.</p>}
          </>)}
        </div>

        <div className="card">
          <h3>Add walk-in</h3>
          <form onSubmit={walkIn}>
            <Field label="Queue"><select {...bind('queueId')}>{queues.map((q) => <option key={q.id} value={q.id}>{q.name}</option>)}</select></Field>
            <Field label="Name"><input required {...bind('guestName')} /></Field>
            <Field label="Phone (optional)"><input {...bind('guestPhone')} /></Field>
            <Field label="Priority"><select {...bind('priority')}><option>NORMAL</option><option>PRIORITY</option><option>EMERGENCY</option></select></Field>
            <p><button disabled={queues.length === 0}>Add to queue</button></p>
          </form>
        </div>
      </div>

      <div className="card">
        <h3>Waiting ({waiting.length})</h3>
        <table><thead><tr><th>#</th><th>Token</th><th>Name</th><th>Priority</th><th>Source</th><th>Wait</th><th /></tr></thead>
          <tbody>{waiting.map((e) => (
            <tr key={e.id}><td>{e.position}</td><td><b>{e.tokenLabel}</b></td><td>{e.displayName}</td><td><Badge v={e.priority} /></td><td>{e.source}</td>
              <td>{etaText(e.eta)}{e.eta?.estimatedTurnTime ? ` (${fmtClock(e.eta.estimatedTurnTime)})` : ''}</td>
              <td className="row">
                {isManager && counterId && <button className="secondary" onClick={() => { const r = reason('calling out of order'); if (r) run(() => api.post(`/api/queue-entries/${e.id}/call`, { counterId, reason: r })); }}>Call now</button>}
                <select value={e.priority} onChange={(ev) => { const r = reason('priority change'); if (r) run(() => api.patch(`/api/queue-entries/${e.id}/priority`, { priority: ev.target.value, reason: r })); }}>
                  {['NORMAL', 'PRIORITY', 'EMERGENCY'].map((p) => <option key={p}>{p}</option>)}{e.priority === 'APPOINTMENT' && <option>APPOINTMENT</option>}</select>
                <button className="danger" onClick={() => window.confirm('Cancel this entry?') && act(e.id, 'cancel')}>Cancel</button></td></tr>))}</tbody></table>
        {waiting.length === 0 && <p className="muted">Queue is empty.</p>}
      </div>

      {skipped.length > 0 && (
        <div className="card"><h3>Skipped ({skipped.length})</h3>
          {skipped.map((e) => (<div className="row" key={e.id}><b>{e.tokenLabel}</b> {e.displayName}
            <button className="secondary" disabled={!counterId || !!current} onClick={() => act(e.id, 'recall', { counterId })}>Recall here</button>
            <button className="secondary" onClick={() => act(e.id, 'move-to-end')}>Back to queue (end)</button>
            <button className="danger" onClick={() => act(e.id, 'no-show')}>No-show</button></div>))}
        </div>)}
    </div>
  );
}
