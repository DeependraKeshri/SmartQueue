import React from 'react';

export function Msg({ error, ok }) {
  if (error) return <div className="error" role="alert">{error}</div>;
  if (ok) return <div className="success">{ok}</div>;
  return null;
}

export function ConnectionBanner({ status }) {
  if (status === 'connected') return null;
  return <div className="banner">{status === 'connecting' ? 'Connecting to live updates…' : 'Connection lost – reconnecting. Data shown may be out of date.'}</div>;
}

export const Badge = ({ v }) => <span className={`badge ${v}`}>{String(v).replace('_', ' ')}</span>;

export function Field({ label, children }) {
  return (<div><label>{label}</label>{children}</div>);
}

/** Tiny controlled-form helper. */
export function useForm(initial) {
  const [v, setV] = React.useState(initial);
  const bind = (k, type) => ({
    value: v[k] ?? '',
    onChange: (e) => setV({ ...v, [k]: type === 'check' ? e.target.checked : e.target.value })
  });
  const check = (k) => ({ checked: !!v[k], onChange: (e) => setV({ ...v, [k]: e.target.checked }) });
  return [v, setV, bind, check];
}

/** Loads data once and exposes reload. */
export function useLoad(fn, deps = []) {
  const [data, setData] = React.useState(null);
  const [error, setError] = React.useState('');
  const [loading, setLoading] = React.useState(true);
  const load = React.useCallback(async () => {
    try { setError(''); setData(await fn()); } catch (e) { setError(e?.response?.data?.message || e.message); } finally { setLoading(false); }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  React.useEffect(() => { load(); }, [load]);
  return { data, error, loading, reload: load, setData };
}
