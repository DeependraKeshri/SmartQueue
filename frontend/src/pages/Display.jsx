import React, { useCallback, useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';
import { ConnectionBanner } from '../components/ui.jsx';
import { useStomp } from '../realtime.js';

function Board({ b }) {
  return (
    <div style={{ marginBottom: 32 }}>
      <h1>{b.businessName} – {b.queueName}</h1>
      {!b.businessOpen && <h2>Closed</h2>}
      {b.status === 'PAUSED' && <h2>Queue paused</h2>}
      <div className="serving">
        {b.nowServing.length === 0 && <div className="tile"><div className="next">Waiting for next customer…</div></div>}
        {b.nowServing.map((s) => (
          <div className="tile" key={s.token}><div className="t">{s.token}</div>
            {s.counter && <div>→ {s.counter}</div>}{s.displayName && <div>{s.displayName}</div>}</div>
        ))}
      </div>
      {b.nextUp.length > 0 && <div className="next">Next: {b.nextUp.map((n) => n.token + (n.displayName ? ` (${n.displayName})` : '')).join('  ·  ')}</div>}
      <div className="next">{b.waitingCount} waiting{b.estimatedWaitMinutes != null && b.showWait ? ` · about ${b.estimatedWaitMinutes} min` : ''}</div>
    </div>
  );
}

/** Public waiting-room screen. /display/:queuePublicId (no login) or /display (DISPLAY account: all queues of its business). */
export default function Display() {
  const { queuePublicId } = useParams();
  const { user } = useAuth();
  const [boards, setBoards] = useState([]);
  const load = useCallback(async () => {
    try {
      if (queuePublicId) setBoards([(await api.get(`/api/public/queues/${queuePublicId}/board`)).data]);
      else setBoards((await api.get('/api/display/boards')).data);
    } catch { /* keep last known state on screen */ }
  }, [queuePublicId]);
  useEffect(() => { load(); const t = setInterval(load, 30000); return () => clearInterval(t); }, [load]);

  const dests = queuePublicId ? [`/topic/public/queues/${queuePublicId}`]
    : (user?.businessId ? [`/topic/business/${user.businessId}/queues`] : []);
  const status = useStomp(dests, (msg) => {
    if (queuePublicId) setBoards([msg]); else load();
  }, { auth: !queuePublicId, onReconnect: load });
  return (
    <div className="display">
      <ConnectionBanner status={status} />
      {boards.map((b) => <Board key={b.queuePublicId} b={b} />)}
      {boards.length === 0 && <h1>SmartQueue</h1>}
    </div>
  );
}
