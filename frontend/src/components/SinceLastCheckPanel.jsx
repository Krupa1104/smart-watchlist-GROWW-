import { formatPrice, formatVolumeRatio, formatRelativeTime } from '../utils/format.js';
import { computeSinceLastCheckMovement, getVolumeRatio } from '../utils/metrics.js';

// Renders the raw /check diffs (SnapshotDiffResponse[]) the app already
// fetches on every "Check for changes" click — previously these were
// discarded after computing a one-line summary. This is purely a richer
// presentation of that same response, joined with the already-loaded
// /detect metrics (for volume ratio + "normal vs unusual") — no new API call.
export default function SinceLastCheckPanel({ diffs, detectedBySymbol, displayNameBySymbol }) {
  const withBaseline = diffs.filter((d) => d.dataAvailable && !d.firstView);
  const firstChecks = diffs.filter((d) => d.dataAvailable && d.firstView);

  if (diffs.length === 0) return null;

  return (
    <div className="since-check" data-testid="since-check-panel">
      <h3 className="since-check__title">Since your last check</h3>

      {withBaseline.length > 0 && (
        <ul className="since-check__list">
          {withBaseline.map((d) => {
            const detected = detectedBySymbol.get(d.symbol);
            const { absChange, pctChange, direction } = computeSinceLastCheckMovement(d);
            const volumeRatio = detected ? getVolumeRatio(detected.metrics) : null;
            const unusual = detected?.meaningful === true;
            const elapsed = formatRelativeTime(d.previousViewedAt);

            return (
              <li key={d.itemId} className="since-check__row">
                <div className="since-check__row-head">
                  <span className="since-check__symbol">{d.symbol}</span>
                  {displayNameBySymbol.get(d.symbol) && (
                    <span className="since-check__name">{displayNameBySymbol.get(d.symbol)}</span>
                  )}
                  <span
                    className={`since-check__status since-check__status--${unusual ? 'unusual' : 'normal'}`}
                  >
                    {unusual ? 'Unusual' : 'Normal'}
                  </span>
                </div>

                <div className="since-check__values">
                  <span className="since-check__value">
                    {formatPrice(d.previousValue, d.instrumentType)}
                  </span>
                  <span className="since-check__arrow" aria-hidden="true">→</span>
                  <span className="since-check__value since-check__value--current">
                    {formatPrice(d.currentValue, d.instrumentType)}
                  </span>
                  {pctChange !== null && (
                    <span className={`since-check__pct since-check__pct--${direction ?? 'flat'}`}>
                      {absChange >= 0 ? '+' : ''}
                      {formatPrice(Math.abs(absChange), d.instrumentType)} (
                      {pctChange > 0 ? '+' : ''}
                      {pctChange.toFixed(2)}%)
                    </span>
                  )}
                </div>

                <div className="since-check__meta">
                  {elapsed && <span>Last checked {elapsed}</span>}
                  {volumeRatio !== null && <span>Volume {formatVolumeRatio(volumeRatio)}</span>}
                </div>
              </li>
            );
          })}
        </ul>
      )}

      {firstChecks.length > 0 && (
        <p className="since-check__baseline-note">
          {firstChecks.length} instrument{firstChecks.length === 1 ? '' : 's'} checked for the
          first time — baseline recorded, next check will show what changed.
        </p>
      )}
    </div>
  );
}
