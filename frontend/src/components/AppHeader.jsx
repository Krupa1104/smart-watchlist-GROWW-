import { useEffect, useMemo, useRef, useState } from 'react';

const MAX_RESULTS = 8;

// Real instrument search: filters the already-loaded stock/fund list
// locally on every keystroke (no API call per keystroke — see
// App.jsx/listInstruments). Selecting a result hands it up to the parent
// (opens the add form prefilled) rather than adding anything itself —
// typing here must never mutate the watchlist on its own.
export default function AppHeader({ instruments = [], onSelectInstrument }) {
  const [query, setQuery] = useState('');
  const [open, setOpen] = useState(false);
  const [notice, setNotice] = useState(false);
  const containerRef = useRef(null);

  const results = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return [];
    return instruments
      .filter(
        (i) =>
          i.symbol.toLowerCase().includes(q) ||
          (i.name && i.name.toLowerCase().includes(q))
      )
      .slice(0, MAX_RESULTS);
  }, [query, instruments]);

  const showDropdown = open && query.trim().length > 0;

  // Closing on an outside click (rather than input onBlur) so clicking a
  // result itself doesn't get pre-empted by a blur firing first.
  useEffect(() => {
    function handleClickOutside(e) {
      if (containerRef.current && !containerRef.current.contains(e.target)) {
        setOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  function handleChange(e) {
    const value = e.target.value;
    setQuery(value);
    setOpen(value.trim().length > 0);
  }

  function handleSubmit(e) {
    e.preventDefault();
    if (results.length > 0) handleSelect(results[0]);
  }

  function handleSelect(instrument) {
    onSelectInstrument(instrument);
    setQuery('');
    setOpen(false);
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

        <div className="app-header__search-wrap" ref={containerRef}>
          <form className="app-header__search" onSubmit={handleSubmit} role="search">
            <span className="app-header__search-icon" aria-hidden="true">⌕</span>
            <input
              type="text"
              placeholder="Search stocks, funds…"
              value={query}
              onChange={handleChange}
              onFocus={() => setOpen(query.trim().length > 0)}
              aria-label="Search stocks, funds"
              role="combobox"
              aria-expanded={showDropdown}
              aria-controls="global-search-results"
              autoComplete="off"
            />
          </form>

          {showDropdown && (
            <div className="app-header__results" id="global-search-results" role="listbox">
              {results.length === 0 ? (
                <div className="app-header__results-empty">No matching stocks or funds</div>
              ) : (
                results.map((r) => (
                  <button
                    key={r.symbol}
                    type="button"
                    role="option"
                    aria-selected="false"
                    className="app-header__result"
                    onClick={() => handleSelect(r)}
                  >
                    <span
                      className={`app-header__result-badge app-header__result-badge--${r.instrumentType.toLowerCase()}`}
                    >
                      {r.instrumentType}
                    </span>
                    <span className="app-header__result-symbol">{r.symbol}</span>
                    <span className="app-header__result-name">{r.name}</span>
                  </button>
                ))
              )}
            </div>
          )}
        </div>

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
