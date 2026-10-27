import { Client } from '@stomp/stompjs';
import { useEffect, useRef, useState } from 'react';

function brokerUrl() {
  if (import.meta.env.VITE_WS_URL) return import.meta.env.VITE_WS_URL;
  const proto = window.location.protocol === 'https:' ? 'wss' : 'ws';
  return `${proto}://${window.location.host}/ws`;
}

/**
 * Subscribes to STOMP destinations with automatic reconnect (3s) and 10s heartbeats.
 *  - onMessage(payload, destination) is called for every message; stale messages (older `sequence`) are dropped.
 *  - onReconnect() is called after every RE-connection so the caller can re-fetch the REST snapshot
 *    (messages missed while offline are never replayed).
 * Returns the connection status: 'connecting' | 'connected' | 'reconnecting'.
 */
export function useStomp(destinations, onMessage, { auth = false, onReconnect } = {}) {
  const [status, setStatus] = useState('connecting');
  const handler = useRef(onMessage);
  const reconnect = useRef(onReconnect);
  handler.current = onMessage;
  reconnect.current = onReconnect;
  const key = JSON.stringify(destinations || []);

  useEffect(() => {
    const dests = JSON.parse(key);
    if (!dests.length) return undefined;
    const lastSeq = {};
    let connectedBefore = false;
    const token = auth ? localStorage.getItem('sq_token') : null;
    const client = new Client({
      brokerURL: brokerUrl(),
      connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        setStatus('connected');
        dests.forEach((d) => client.subscribe(d, (frame) => {
          let body;
          try { body = JSON.parse(frame.body); } catch { return; }
          if (body && body.sequence != null) {
            if (lastSeq[d] && body.sequence < lastSeq[d]) return;
            lastSeq[d] = body.sequence;
          }
          handler.current?.(body, d);
        }));
        if (connectedBefore) reconnect.current?.();
        connectedBefore = true;
      },
      onWebSocketClose: () => setStatus((s) => (s === 'connected' ? 'reconnecting' : s)),
      onStompError: () => setStatus('reconnecting')
    });
    client.activate();
    return () => { client.deactivate(); };
  }, [key, auth]);

  return status;
}
