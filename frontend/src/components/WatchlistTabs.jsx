export default function WatchlistTabs({ watchlists, selectedId, onSelect, onCreate, creating }) {
  return (
    <div className="wl-tabs" role="tablist" aria-label="Your watchlists">
      {watchlists.map((w) => (
        <button
          key={w.id}
          type="button"
          role="tab"
          aria-selected={w.id === selectedId}
          className={`wl-tabs__tab ${w.id === selectedId ? 'wl-tabs__tab--active' : ''}`}
          onClick={() => onSelect(w.id)}
        >
          {w.name}
        </button>
      ))}
      <button type="button" className="wl-tabs__add" onClick={onCreate} disabled={creating}>
        {creating ? 'Creating…' : '+ Watchlist'}
      </button>
    </div>
  );
}
