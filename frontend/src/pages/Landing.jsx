import React from 'react';
import { Link } from 'react-router-dom';

export default function Landing() {
  return (
    <div className="container">
      <div className="card">
        <h1>Skip the line. Not the service.</h1>
        <p className="muted">Join queues remotely, see a live position and wait estimate, and book appointments – all in real time.</p>
        <div className="row">
          <Link to="/businesses"><button>Find a business</button></Link>
          <Link to="/register"><button className="secondary">Create customer account</button></Link>
          <Link to="/register/business"><button className="secondary">Register your business</button></Link>
        </div>
      </div>
      <div className="grid">
        <div className="card"><h3>Live position &amp; ETA</h3><p className="muted">Updates instantly as the queue moves. Estimates come from real service-time history.</p></div>
        <div className="card"><h3>Appointments</h3><p className="muted">Book, reschedule and check in. Appointments get fair priority in the live queue.</p></div>
        <div className="card"><h3>QR join</h3><p className="muted">Scan at the door – no account required.</p></div>
      </div>
    </div>
  );
}
