import React from 'react';

/** Last line of defence: a rendering error shows a friendly page instead of a blank screen. */
export default class ErrorBoundary extends React.Component {
  constructor(props) { super(props); this.state = { failed: false }; }
  static getDerivedStateFromError() { return { failed: true }; }
  componentDidCatch(error) { console.error('UI error:', error.message); }
  render() {
    if (!this.state.failed) return this.props.children;
    return (
      <div className="container narrow"><div className="card">
        <h2>Something went wrong</h2>
        <p className="muted">The page failed to display. Your data is safe. Try reloading.</p>
        <button onClick={() => window.location.assign('/')}>Back to start</button>
      </div></div>
    );
  }
}
