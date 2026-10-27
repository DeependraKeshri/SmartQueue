import axios from 'axios';

export const api = axios.create({ baseURL: import.meta.env.VITE_API_URL || '', timeout: 15000 });

api.interceptors.request.use((cfg) => {
  const token = localStorage.getItem('sq_token');
  if (token) cfg.headers.Authorization = `Bearer ${token}`;
  return cfg;
});

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

api.interceptors.response.use(
  (r) => r,
  async (err) => {
    // Temporary network/server failure: retry safe (GET) requests twice with backoff. Mutations are never auto-retried
    // here; they carry Idempotency-Key headers and are retried explicitly by the user.
    const cfg = err.config;
    const transient = !err.response || err.response.status >= 502;
    if (cfg && cfg.method === 'get' && transient && (cfg.__retries || 0) < 2) {
      cfg.__retries = (cfg.__retries || 0) + 1;
      await sleep(500 * cfg.__retries);
      return api(cfg);
    }
    // An expired/invalid token on an authenticated session logs the user out.
    if (err.response?.status === 401 && localStorage.getItem('sq_token')) {
      localStorage.removeItem('sq_token');
      localStorage.removeItem('sq_user');
      window.dispatchEvent(new Event('sq-logout'));
    }
    return Promise.reject(err);
  }
);

export function errMsg(e) {
  const d = e?.response?.data;
  if (d?.fieldErrors) return Object.values(d.fieldErrors).join(', ');
  if (d?.message) return d.message;
  if (e?.code === 'ERR_NETWORK') return 'Cannot reach the server. Check your connection.';
  return e?.message || 'Something went wrong';
}

/** Idempotency key: generated once per user intent, reused on retries. */
export const newKey = () => (crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`);

export const fmtTime = (iso, tz) => {
  if (!iso) return '';
  try {
    return new Date(iso).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short', timeZone: tz || undefined });
  } catch {
    return new Date(iso).toLocaleString();
  }
};

export const fmtClock = (iso) => (iso ? new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '');

export function etaText(eta) {
  if (!eta) return '';
  if (!eta.businessOpen) return 'Closed right now';
  if (eta.estimatedWaitMinutes === 0) return 'You are next';
  return `${eta.waitMinutesMin}–${eta.waitMinutesMax} min (about ${eta.estimatedWaitMinutes})`;
}
