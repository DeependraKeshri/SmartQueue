
import React, { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api.js';
import { Msg, useLoad } from '../components/ui.jsx';

export function BusinessList() {
  const [q, setQ] = useState('');
  const [searchTerm, setSearchTerm] = useState('');
  const [suggestions, setSuggestions] = useState([]);
  const [suggestionLoading, setSuggestionLoading] = useState(false);
  const [showSuggestions, setShowSuggestions] = useState(false);

  const { data, error } = useLoad(
    () =>
      api
        .get('/api/public/businesses', {
          params: { q: searchTerm || undefined },
        })
        .then((response) => response.data),
    [searchTerm]
  );

  React.useEffect(() => {
    const query = q.trim();

    if (!query) {
      setSuggestions([]);
      setSuggestionLoading(false);
      return undefined;
    }

    let cancelled = false;

    const timer = setTimeout(async () => {
      setSuggestionLoading(true);

      try {
        const response = await api.get('/api/public/businesses', {
          params: { q: query },
        });

        if (!cancelled) {
          setSuggestions((response.data || []).slice(0, 6));
        }
      } catch {
        if (!cancelled) {
          setSuggestions([]);
        }
      } finally {
        if (!cancelled) {
          setSuggestionLoading(false);
        }
      }
    }, 250);

    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [q]);

  const submitSearch = (event) => {
    event.preventDefault();
    setShowSuggestions(false);
    setSearchTerm(q.trim());
  };

  const chooseSuggestion = (business) => {
    setQ(business.name);
    setSearchTerm(business.name);
    setSuggestions([]);
    setShowSuggestions(false);
  };

  return (
    <div className="container">
      <div className="card">
        <h2>Find a business</h2>

        <form onSubmit={submitSearch}>
          <div className="row" style={{ alignItems: 'flex-start' }}>
            <div
              style={{
                position: 'relative',
                width: '100%',
                maxWidth: 320,
              }}
            >
              <input
                style={{ width: '100%', boxSizing: 'border-box' }}
                placeholder="Search by name"
                value={q}
                autoComplete="off"
                aria-label="Search businesses by name"
                aria-autocomplete="list"
                aria-expanded={showSuggestions && suggestions.length > 0}
                onFocus={() => setShowSuggestions(true)}
                onBlur={() =>
                  setTimeout(() => setShowSuggestions(false), 150)
                }
                onChange={(event) => {
                  setQ(event.target.value);
                  setShowSuggestions(true);
                }}
              />

              {showSuggestions && q.trim() && (
                <div
                  role="listbox"
                  aria-label="Business suggestions"
                  style={{
                    position: 'absolute',
                    top: '100%',
                    left: 0,
                    right: 0,
                    zIndex: 20,
                    marginTop: 4,
                    background: 'var(--card, #fff)',
                    border: '1px solid var(--border, #ccc)',
                    borderRadius: 8,
                    boxShadow: '0 4px 12px rgba(0,0,0,0.12)',
                    overflow: 'hidden',
                  }}
                >
                  {suggestionLoading && (
                    <div className="muted" style={{ padding: 10 }}>
                      Searching…
                    </div>
                  )}

                  {!suggestionLoading &&
                    suggestions.map((business) => (
                      <button
                        type="button"
                        role="option"
                        aria-selected="false"
                        key={business.publicId}
                        onMouseDown={(event) => event.preventDefault()}
                        onClick={() => chooseSuggestion(business)}
                        style={{
                          display: 'block',
                          width: '100%',
                          textAlign: 'left',
                          border: 0,
                          borderRadius: 0,
                          padding: '10px 12px',
                          background: 'transparent',
                          color: 'inherit',
                          cursor: 'pointer',
                        }}
                      >
                        {business.name}
                      </button>
                    ))}

                  {!suggestionLoading && suggestions.length === 0 && (
                    <div className="muted" style={{ padding: 10 }}>
                      No matching businesses found.
                    </div>
                  )}
                </div>
              )}
            </div>

            <button type="submit">Search</button>
          </div>
        </form>
      </div>

      <Msg error={error} />

      <div className="grid">
        {(data || []).map((business) => (
          <div className="card" key={business.publicId}>
            <h3>{business.name}</h3>
            <p className="muted">{business.description}</p>

            <p>
              <span
                className={`badge ${business.openNow ? 'OPEN' : 'CLOSED'}`}
              >
                {business.openNow ? 'Open now' : 'Closed'}
              </span>
            </p>

            <Link to={`/b/${business.publicId}`}>
              <button>View</button>
            </Link>
          </div>
        ))}
      </div>

      {data && data.length === 0 && (
        <p className="muted">No businesses found.</p>
      )}
    </div>
  );
}

export function BusinessPage() {
  const { publicId } = useParams();

  const { data: business, error } = useLoad(
    () =>
      api
        .get(`/api/public/businesses/${publicId}`)
        .then((response) => response.data),
    [publicId]
  );

  if (error) {
    return (
      <div className="container">
        <Msg error={error} />
      </div>
    );
  }

  if (!business) {
    return <div className="container">Loading…</div>;
  }

  return (
    <div className="container">
      <div className="card">
        <div
          className="row"
          style={{ justifyContent: 'space-between' }}
        >
          <div>
            <h2>{business.name}</h2>
            <p className="muted">{business.description}</p>
            <span
              className={`badge ${business.openNow ? 'OPEN' : 'CLOSED'}`}
            >
              {business.openNow ? 'Open now' : 'Closed'}
            </span>
          </div>

          <img
            alt="QR code for this business"
            width={120}
            height={120}
            src={`/api/public/qr/businesses/${business.publicId}.png?size=240`}
          />
        </div>
      </div>

      <div className="grid">
        {business.services.map((service) => (
          <div className="card" key={service.id}>
            <h3>{service.name}</h3>

            <p className="muted">
              {service.description} · about{' '}
              {service.estimatedDurationMinutes} min
            </p>

            {service.queuePublicId && (
              <p>
                {service.waitingCount} waiting
                {service.estimatedWaitMinutes != null
                  ? ` · ~${service.estimatedWaitMinutes} min wait`
                  : ''}{' '}
                <span className={`badge ${service.queueStatus}`}>
                  {service.queueStatus}
                </span>
              </p>
            )}

            <div className="row">
              {service.queuePublicId && (
                <Link to={`/join/${service.queuePublicId}`}>
                  <button>Join queue</button>
                </Link>
              )}

              {service.appointmentsEnabled && (
                <Link
                  to={`/book/${business.publicId}?serviceId=${service.id}`}
                >
                  <button className="secondary">
                    Book appointment
                  </button>
                </Link>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
