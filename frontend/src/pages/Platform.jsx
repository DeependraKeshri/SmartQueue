import React, { useState } from 'react';
import { api, errMsg } from '../api.js';
import { Msg, useLoad } from '../components/ui.jsx';

export default function Platform() {
  const { data, error, reload } = useLoad(() => api.get('/api/platform/businesses').then((r) => r.data), []);
  const [err, setErr] = useState('');
  const toggle = async (b) => {
    try { await api.patch(`/api/platform/businesses/${b.id}/active`, null, { params: { active: !b.active } }); reload(); } catch (x) { setErr(errMsg(x)); }
  };
  return (
    <div className="container">
      <h2>Businesses</h2>
      <Msg error={error || err} />
      <div className="card"><table><thead><tr><th>Name</th><th>Slug</th><th>Timezone</th><th>Active</th><th /></tr></thead>
        <tbody>{(data || []).map((b) => (<tr key={b.id}><td>{b.name}</td><td>{b.slug}</td><td>{b.timezone}</td><td>{b.active ? 'Yes' : 'Suspended'}</td>
          <td><button className={b.active ? 'danger' : 'ok'} onClick={() => toggle(b)}>{b.active ? 'Suspend' : 'Activate'}</button></td></tr>))}</tbody></table></div>
    </div>
  );
}
