import { formatPrice, formatVolumeRatio } from '../utils/format.js';
import { getPercentChange, getVolumeRatio, directionFromPercent } from '../utils/metrics.js';

export default function WatchlistTable({
  items,
  totalCount,
  attentionSymbols,
  detectedBySymbol,
  editMode,
  onRemove,
  removingSymbol,
}) {
  if (totalCount === 0) {
    return (
      <p className="wl-table__empty">
        No instruments on this watchlist yet. Use "+ Add stocks" above to get started.
      </p>
    );
  }

  if (items.length === 0) {
    return <p className="wl-table__empty">No instruments match your search.</p>;
  }

  return (
    <table className="wl-table" data-testid="watchlist-table">
      <thead>
        <tr>
          <th scope="col">Instrument</th>
          <th scope="col">Type</th>
          <th scope="col">Sector / Category</th>
          <th scope="col" className="wl-table__num">Price / NAV</th>
          <th scope="col" className="wl-table__num">1D change</th>
          <th scope="col" className="wl-table__num">Volume</th>
          <th scope="col" aria-label="Attention" />
          {editMode && <th scope="col" aria-label="Remove" />}
        </tr>
      </thead>
      <tbody>
        {items.map((item) => {
          const md = item.marketData;
          const detected = detectedBySymbol.get(item.symbol);
          const pct = detected ? getPercentChange(detected.metrics, item.instrumentType) : null;
          const direction = directionFromPercent(pct);
          const volumeRatio = detected ? getVolumeRatio(detected.metrics) : null;
          const flagged = attentionSymbols.has(item.symbol);

          return (
            <tr key={item.itemId} className={flagged ? 'wl-table__row--flagged' : undefined}>
              <th scope="row" className="wl-table__symbol">
                <span className="wl-table__symbol-code">{item.symbol}</span>
                <span className="wl-table__display-name">{md?.displayName}</span>
              </th>
              <td>
                <span className={`type-pill type-pill--${item.instrumentType.toLowerCase()}`}>
                  {item.instrumentType}
                </span>
              </td>
              <td>{md?.groupLabel ?? '—'}</td>
              <td className="wl-table__num wl-table__value">
                {md?.dataAvailable ? formatPrice(md.latestValue, item.instrumentType) : 'No data'}
              </td>
              <td className={`wl-table__num wl-table__change wl-table__change--${direction ?? 'flat'}`}>
                {pct === null ? '—' : `${pct > 0 ? '+' : ''}${pct.toFixed(2)}%`}
              </td>
              <td className="wl-table__num wl-table__mono">{formatVolumeRatio(volumeRatio)}</td>
              <td>
                {flagged && (
                  <span
                    className="wl-table__flag"
                    title="Meaningful change — see 'What changed' above"
                  >
                    ⚡ Changed
                  </span>
                )}
              </td>
              {editMode && (
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
              )}
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
