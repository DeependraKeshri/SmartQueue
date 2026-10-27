import React, { useCallback, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api, errMsg, newKey } from '../api.js';
import { useAuth } from '../auth.jsx';
import { ConnectionBanner, Field, Msg, useForm, useLoad } from '../components/ui.jsx';
import { useStomp } from '../realtime.js';

export default function JoinQueue() {
  const { queuePublicId } = useParams();
  const { user } = useAuth();
  const nav = useNavigate();
  const key = useRef(newKey());            // one idempotency key per join intent (safe against double-clicks / retries)
  const [f, , bind] = useForm({ guestName: '', guestPhone: '' });
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);
  const { data: board, setData, error } = useLoad(() => api.get(`/api/public/queues/${queuePublicId}/board`).then((r) => r.data), [queuePublicId]);
  const refetch = useCallback(() => api.get(`/api/public/queues/${queuePublicId}/board`).then((r) => setData(r.data)).catch(() => {}), [queuePublicId, setData]);
  const status = useStomp([`/topic/public/queues/${queuePublicId}`], (b) => setData(b), { onReconnect: refetch });

  const done = (entry) => {
    const list = JSON.parse(localStorage.getItem('sq_guest_entries') || '[]');
    if (!user) localStorage.setItem('sq_guest_entries', JSON.stringify([entry.publicId, ...list].slice(0, 10)));
    nav(`/status/${entry.publicId}`);
  };
  const joinAsCustomer = async () => {
    setErr(''); setBusy(true);
    try { done((await api.post(`/api/customer/queues/${queuePublicId}/join`, null, { headers: { 'Idempotency-Key': key.current } })).data.entry); }
    catch (x) { setErr(errMsg(x)); key.current = newKey(); } finally { setBusy(false); }
  };
  const joinAsGuest = async (e) => {
    e.preventDefault(); setErr(''); setBusy(true);
    try { done((await api.post(`/api/public/queues/${queuePublicId}/join`, { guestName: f.guestName, guestPhone: f.guestPhone || null }, { headers: { 'Idempotency-Key': key.current } })).data.entry); }
    catch (x) { setErr(errMsg(x)); key.current = newKey(); } finally { setBusy(false); }
  };
  if (error) return <div className="container"><Msg error={error} /></div>;
  if (!board) return <div className="container">Loading…</div>;
  const canJoin = board.status === 'ACTIVE' && board.businessOpen;
  return (
    <div className="container narrow">
      <ConnectionBanner status={status} />
      <div className="card">
        <h2>{board.queueName}</h2>
        <p className="muted">{board.businessName}</p>
        <p className="big">{board.waitingCount} waiting</p>
        {board.estimatedWaitMinutes != null && <p>Estimated wait for a new arrival: about <b>{board.estimatedWaitMinutes} min</b></p>}
        {!board.businessOpen && <Msg error="This business is currently closed." />}
        {board.status !== 'ACTIVE' && <Msg error={`This queue is ${board.status.toLowerCase()}.`} />}
      </div>
      {canJoin && (
        <div className="card">
          <Msg error={err} />
          {user?.role === 'CUSTOMER' ? (
            <button onClick={joinAsCustomer} disabled={busy}>{busy ? 'Joining…' : 'Join the queue'}</button>
          ) : user ? (
            <p className="muted">Staff accounts cannot join queues as customers.</p>
          ) : (
            <form onSubmit={joinAsGuest}>
              <h3>Join as guest</h3>
              <Field label="Your name"><input required maxLength={120} {...bind('guestName')} /></Field>
              <Field label="Phone (optional)"><input {...bind('guestPhone')} /></Field>
              <p><button disabled={busy}>{busy ? 'Joining…' : 'Get my token'}</button></p>
              <p className="muted">or <Link to="/login" state={{ from: `/join/${queuePublicId}` }}>sign in</Link> for notifications and history.</p>
            </form>
          )}
        </div>
      )}
    </div>
  );
}
