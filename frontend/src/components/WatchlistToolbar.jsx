export default function WatchlistToolbar({
  itemCount,
  dataAsOf,
  search,
  onSearchChange,
  addOpen,
  onToggleAdd,
  editMode,
  onToggleEdit,
  onCheck,
  checking,
  onDeleteWatchlist,
  deletingWatchlist,
}) {
  return (
    <div className="wl-toolbar">
      <div className="wl-toolbar__left">
        <div className="wl-toolbar__search">
          <span aria-hidden="true">⌕</span>
          <input
            type="text"
            placeholder="Search your watchlist"
            value={search}
            onChange={(e) => onSearchChange(e.target.value)}
            aria-label="Search your watchlist"
          />
        </div>
        <span className="wl-toolbar__count">
          {itemCount} {itemCount === 1 ? 'instrument' : 'instruments'}
          {dataAsOf && <> · data as of {dataAsOf}</>}
        </span>
      </div>

      <div className="wl-toolbar__right">
        <button type="button" className="btn btn--outline" onClick={onToggleAdd}>
          {addOpen ? 'Close' : '+ Add stocks'}
        </button>
        <button type="button" className="btn btn--outline" onClick={onToggleEdit}>
          {editMode ? 'Done' : 'Edit'}
        </button>
        {editMode && (
          <button
            type="button"
            className="btn btn--text"
            onClick={onDeleteWatchlist}
            disabled={deletingWatchlist}
          >
            {deletingWatchlist ? 'Deleting…' : 'Delete watchlist'}
          </button>
        )}
        <div className="wl-toolbar__check">
          <button type="button" className="btn btn--primary" onClick={onCheck} disabled={checking}>
            {checking ? 'Checking…' : 'Check for changes'}
          </button>
          <span className="wl-toolbar__check-hint">See what's different since your last check</span>
        </div>
      </div>
    </div>
  );
}
