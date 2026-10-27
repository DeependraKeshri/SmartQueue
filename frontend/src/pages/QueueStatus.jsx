import React, { useCallback, useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { api, errMsg, etaText, fmtClock } from '../api.js';
import { Badge, ConnectionBanner, Msg, useLoad } from '../components/ui.jsx';
import { useStomp } from '../realtime.js';

export default function QueueStatus() {
  const { entryPublicId } = useParams();
  const { data: e, setData, error } = useLoad(() => api.get(`/api/public/entries/${entryPublicId}`).then((r) => r.data), [entryPublicId]);
  const [err, setErr] = useState('');
  const refetch = useCallback(() => api.get(`/api/public/entries/${entryPublicId}`).then((r) => setData(r.data)).catch(() => {}), [entryPublicId, setData]);
  const status = useStomp([`/topic/public/entries/${entryPublicId}`], (u) => setData(u), { onReconnect: refetch });

  // Alert (browser notification + vibration) when the customer is called
  useEffect(() => {
    if (e?.status === 'CALLED') {
      if ('Notification' in window && Notification.permission === 'granted') {
        try { new Notification('It is your turn!', { body: `Token ${e.tokenLabel} – please go to ${e.counterName || 'the counter'}` }); } catch { /* ignore */ }
      }
      if (navigator.vibrate) navigator.vibrate([300, 150, 300]);
    }
  }, [e?.status, e?.tokenLabel, e?.counterName]);

  const cancel = async () => {
    if (!window.confirm('Leave the queue?')) return;
    try { setData((await api.post(`/api/public/entries/${entryPublicId}/cancel`)).data); } catch (x) { setErr(errMsg(x)); }
  };
  if (error) return <div className="container"><Msg error={error} /></div>;
  if (!e) return <div className="container">Loading…</div>;
  const active = ['WAITING', 'CALLED', 'SERVING'].includes(e.status);
  return (
    <div className="container narrow">
      <ConnectionBanner status={status} />
      <div className="card" style={{ textAlign: 'center' }}>
        <p className="muted">{e.businessName} · {e.serviceName}</p>
        <div className="token">{e.tokenLabel}</div>
        <p><Badge v={e.status} /></p>
        {e.status === 'WAITING' && (<>
          <p className="big">Position {e.position}</p>
          <p>{e.peopleAhead} ahead of you</p>
          <p>{etaText(e.eta)}</p>
          {e.eta?.estimatedTurnTime && e.eta.businessOpen && <p className="muted">Expected around {fmtClock(e.eta.estimatedTurnTime)}</p>}
          {e.eta && <p className="muted">Confidence: {e.eta.confidence.toLowerCase()}</p>}
          {e.eta?.note && <Msg error={e.eta.note} />}
        </>)}
        {e.status === 'CALLED' && <p className="big" style={{ color: 'var(--ok)' }}>It&apos;s your turn! Go to {e.counterName || 'the counter'}.</p>}
        {e.status === 'SERVING' && <p className="big">You are being served{e.counterName ? ` at ${e.counterName}` : ''}.</p>}
        {e.status === 'SKIPPED' && <p>You were skipped. Please speak to the staff.</p>}
        {e.status === 'NO_SHOW' && <p>Marked as no-show.</p>}
        {e.status === 'COMPLETED' && <p>Thank you!</p>}
        <Msg error={err} />
        <div className="row" style={{ justifyContent: 'center' }}>
          {active && e.status !== 'SERVING' && <button className="danger" onClick={cancel}>Leave queue</button>}
          {'Notification' in window && Notification.permission === 'default' && <button className="secondary" onClick={() => Notification.requestPermission()}>Enable alerts</button>}
        </div>
      </div>
    </div>
  );
}
