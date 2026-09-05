import { useEffect, useState } from 'react';
import {
  formatPrice,
  formatVolumeRatio,
  formatSeverity,
  formatChangeType,
  formatDate,
  formatRelativeTime,
} from '../utils/format.js';
import { getPercentChange, getVolumeRatio, directionFromPercent, computeSinceLastCheckMovement } from '../utils/metrics.js';
import { getInstrumentDetail } from '../api/watchlistApi.js';
import { LoadingState, ErrorState } from './StatusStates.jsx';

const CHART_WIDTH = 560;
const CHART_HEIGHT = 120;
const CHART_PADDING = 8;

// Plain SVG polyline built from PricePointResponse[] — deliberately no
// charting library (frontend-design constraint): just min/max-normalized
// coordinates over the existing recent-history data the backend returns.
function buildSparklinePoints(recentHistory) {
  const values = recentHistory.map((p) => Number(p.value)).filter((v) => !Number.isNaN(v));
  if (values.length < 2) return null;

  const min = Math.min(...values);
  const max = Math.max(...values);
  const range = max - min || 1;
  const usableWidth = CHART_WIDTH - CHART_PADDING * 2;
  const usableHeight = CHART_HEIGHT - CHART_PADDING * 2;
  const step = usableWidth / (values.length - 1);

  return values
    .map((v, i) => {
      const x = CHART_PADDING + i * step;
      const y = CHART_PADDING + usableHeight - ((v - min) / range) * usableHeight;
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    })
    .join(' ');
}

export default function InstrumentDetailPanel({ watchlistId, symbol, onClose }) {
  const [detail, setDetail] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    setDetail(null);
    getInstrumentDetail(watchlistId, symbol)
      .then((data) => {
        if (!cancelled) setDetail(data);
      })
      .catch((err) => {
        if (!cancelled) setError(err.message);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [watchlistId, symbol]);

  const md = detail?.marketData;
  const detected = detail?.detectedChange;
  const pct = detected ? getPercentChange(detected.metrics, detail.instrumentType) : null;
  const direction = directionFromPercent(pct);
  const volumeRatio = detected ? getVolumeRatio(detected.metrics) : null;
  const points = detail?.recentHistory?.length ? buildSparklinePoints(detail.recentHistory) : null;

  const sinceLastCheck = detail?.sinceLastCheck;
  const movement = sinceLastCheck ? computeSinceLastCheckMovement(sinceLastCheck) : null;

  return (
    <div
      className="detail-panel-overlay"
      role="presentation"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <aside className="detail-panel" role="dialog" aria-modal="true" aria-label={`${symbol} details`}>
        <div className="detail-panel__header">
          <div>
            <span className="detail-panel__symbol">{symbol}</span>
            {md?.displayName && <span className="detail-panel__name">{md.displayName}</span>}
          </div>
          <button type="button" className="detail-panel__close" onClick={onClose} aria-label="Close">
            ×
          </button>
        </div>

        {loading && <LoadingState label="Loading instrument details…" />}
        {!loading && error && <ErrorState message={error} />}

        {!loading && !error && detail && (
          <div className="detail-panel__body">
            <div className="detail-panel__price-row">
              <span className={`type-pill type-pill--${detail.instrumentType.toLowerCase()}`}>
                {detail.instrumentType}
              </span>
              {md?.groupLabel && <span className="detail-panel__group">{md.groupLabel}</span>}
              <span className="detail-panel__price">
                {md?.dataAvailable ? formatPrice(md.latestValue, detail.instrumentType) : 'No data'}
              </span>
              {pct !== null && (
                <span className={`detail-panel__pct detail-panel__pct--${direction ?? 'flat'}`}>
                  {pct > 0 ? '+' : ''}
                  {pct.toFixed(2)}% today
                </span>
              )}
            </div>

            {points && (
              <div className="detail-panel__chart">
                <svg viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`} preserveAspectRatio="none">
                  <polyline points={points} fill="none" strokeWidth="2" className="detail-panel__chart-line" />
                </svg>
                <p className="detail-panel__chart-caption">
                  Last {detail.recentHistory.length} trading days
                  {volumeRatio !== null && <> · Volume {formatVolumeRatio(volumeRatio)}</>}
                </p>
              </div>
            )}

            <section className="detail-panel__section">
              <h4>Since your last check</h4>
              {sinceLastCheck?.firstView ? (
                <p className="detail-panel__muted">
                  Not checked before — this will show a comparison after your next "Check for changes".
                </p>
              ) : sinceLastCheck?.dataAvailable ? (
                <div className="detail-panel__since-check">
                  <span>
                    {formatPrice(sinceLastCheck.previousValue, detail.instrumentType)}
                    {' → '}
                    {formatPrice(sinceLastCheck.currentValue, detail.instrumentType)}
                  </span>
                  {movement?.pctChange !== null && movement?.pctChange !== undefined && (
                    <span className={`detail-panel__pct detail-panel__pct--${movement.direction ?? 'flat'}`}>
                      {movement.pctChange > 0 ? '+' : ''}
                      {movement.pctChange.toFixed(2)}%
                    </span>
                  )}
                  {sinceLastCheck.previousViewedAt && (
                    <span className="detail-panel__muted">
                      {' '}
                      · last checked {formatRelativeTime(sinceLastCheck.previousViewedAt)}
                    </span>
                  )}
                </div>
              ) : (
                <p className="detail-panel__muted">No data available yet for this instrument.</p>
              )}
            </section>

            <section className="detail-panel__section">
              <h4>Detected change</h4>
              <p>{detected?.explanation}</p>
              {detected?.meaningful && (
                <p className="detail-panel__meta">
                  {formatChangeType(detected.changeType)} · Severity {formatSeverity(detected.severityScore)} ·{' '}
                  {formatDate(detected.asOfDate)}
                </p>
              )}
            </section>

            <section className="detail-panel__section">
              <h4>Related event</h4>
              {detail.relatedEvent ? (
                <div className="detail-panel__event">
                  <span className="detail-panel__event-type">
                    {formatChangeType(detail.relatedEvent.eventType)}
                  </span>
                  <span className="detail-panel__muted">{formatDate(detail.relatedEvent.eventDate)}</span>
                  <p>{detail.relatedEvent.description}</p>
                </div>
              ) : (
                <p className="detail-panel__muted">No recorded event — statistical anomaly only.</p>
              )}
            </section>

            {detail.suggestedActions?.length > 0 && (
              <section className="detail-panel__section">
                <h4>What you could do</h4>
                <ul className="detail-panel__suggestions">
                  {detail.suggestedActions.map((s) => (
                    <li key={s}>{s}</li>
                  ))}
                </ul>
              </section>
            )}
          </div>
        )}
      </aside>
    </div>
  );
}
