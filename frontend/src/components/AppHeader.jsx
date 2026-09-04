import { useState } from 'react';

export default function AppHeader({ onGlobalSearch }) {
  const [query, setQuery] = useState('');
  const [notice, setNotice] = useState(false);

  function handleSubmit(e) {
    e.preventDefault();
    const trimmed = query.trim();
    if (!trimmed) return;
    onGlobalSearch(trimmed);
    setQuery('');
  }

  return (
    <header className="app-header">
      <div className="app-header__row">
        <div className="app-header__brand">
          <span className="app-header__mark" aria-hidden="true">◆</span>
          <span className="app-header__wordmark">Smart Watchlist</span>
        </div>

        <nav className="app-header__nav" aria-label="Primary">
          <span className="app-header__nav-item app-header__nav-item--active">Watchlist</span>
        </nav>

        <form className="app-header__search" onSubmit={handleSubmit} role="search">
          <span className="app-header__search-icon" aria-hidden="true">⌕</span>
          <input
            type="text"
            placeholder="Search stocks, funds…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            aria-label="Search stocks, funds"
          />
        </form>

        <div className="app-header__utility">
          <button
            type="button"
            className="app-header__icon-btn"
            title="Notifications"
            onClick={() => setNotice(true)}
          >
            🔔
          </button>
          <span className="app-header__avatar" title="Demo user (id 1) — no authentication in this build">
            D
          </span>
        </div>
      </div>

      {notice && (
        <div className="app-header__toast" role="status">
          No notifications — this demo doesn't send any.
          <button type="button" onClick={() => setNotice(false)} aria-label="Dismiss">
            ×
          </button>
        </div>
      )}
    </header>
  );
}
