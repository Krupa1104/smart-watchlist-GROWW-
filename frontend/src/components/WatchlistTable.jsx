import { formatPrice, formatDate } from '../utils/format.js';

export default function WatchlistTable({ items, attentionSymbols, onRemove, removingSymbol }) {
  if (items.length === 0) {
    return (
      <p className="wl-table__empty">
        No instruments on this watchlist yet. Add a stock or fund below to get started.
      </p>
    );
  }

  return (
    <table className="wl-table" data-testid="watchlist-table">
      <thead>
        <tr>
          <th scope="col">Symbol</th>
          <th scope="col">Type</th>
          <th scope="col">Sector / Category</th>
          <th scope="col">Latest value</th>
          <th scope="col">As of</th>
          <th scope="col" aria-label="Status" />
          <th scope="col" aria-label="Actions" />
        </tr>
      </thead>
      <tbody>
        {items.map((item) => {
          const md = item.marketData;
          const flagged = attentionSymbols.has(item.symbol);
          return (
            <tr key={item.itemId} className={flagged ? 'wl-table__row--flagged' : undefined}>
              <th scope="row" className="wl-table__symbol">
                {item.symbol}
                <span className="wl-table__display-name">{md?.displayName}</span>
              </th>
              <td>
                <span className={`type-pill type-pill--${item.instrumentType.toLowerCase()}`}>
                  {item.instrumentType}
                </span>
              </td>
              <td>{md?.groupLabel ?? '—'}</td>
              <td className="wl-table__value">
                {md?.dataAvailable ? formatPrice(md.latestValue, item.instrumentType) : 'No data'}
              </td>
              <td>{md?.asOfDate ? formatDate(md.asOfDate) : '—'}</td>
              <td>
                {flagged && (
                  <span className="wl-table__flag" title="This instrument has a meaningful change — see 'What changed' above">
                    ⚡ Changed
                  </span>
                )}
              </td>
              <td>
                <button
                  type="button"
                  className="btn btn--text"
                  onClick={() => onRemove(item.symbol)}
                  disabled={removingSymbol === item.symbol}
                >
                  {removingSymbol === item.symbol ? 'Removing…' : 'Remove'}
                </button>
              </td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
