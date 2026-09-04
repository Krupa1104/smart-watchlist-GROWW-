import { formatDate } from '../utils/format.js';

export default function WatchlistHeader({ watchlist, onCheck, checking }) {
  const itemCount = watchlist?.items?.length ?? 0;

  return (
    <header className="wl-header">
      <div className="wl-header__identity">
        <p className="wl-header__eyebrow">Watchlist</p>
        <h1 className="wl-header__name">{watchlist?.name ?? 'My Watchlist'}</h1>
        <p className="wl-header__meta">
          {itemCount} {itemCount === 1 ? 'instrument' : 'instruments'}
          {watchlist?.dataAsOf && <> · data as of {formatDate(watchlist.dataAsOf)}</>}
        </p>
      </div>

      <div className="wl-header__action">
        <button
          type="button"
          className="btn btn--primary"
          onClick={onCheck}
          disabled={checking}
          title="Compares today's values against what you last saw, and updates your baseline."
        >
          {checking ? 'Checking…' : 'Check for changes'}
        </button>
        <p className="wl-header__action-hint">Marks today as "seen" for next time</p>
      </div>
    </header>
  );
}
