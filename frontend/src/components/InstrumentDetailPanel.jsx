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

// Plain SVG polyline builder — deliberately no charting library
// (frontend-design constraint): just min/max-normalized coordinates over
// whatever numeric series is passed in. Shared by both the daily-history
// chart and the Feature 5 live intraday chart below.
function buildSparklinePoints(values) {
  const clean = values.map(Number).filter((v) => !Number.isNaN(v));
  if (clean.length < 2) return null;

  const min = Math.min(...clean);
  const max = Math.max(...clean);
  const range = max - min || 1;
  const usableWidth = CHART_WIDTH - CHART_PADDING * 2;
  const usableHeight = CHART_HEIGHT - CHART_PADDING * 2;
  const step = usableWidth / (clean.length - 1);

  return clean
    .map((v, i) => {
      const x = CHART_PADDING + i * step;
      const y = CHART_PADDING + usableHeight - ((v - min) / range) * usableHeight;
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    })
    .join(' ');
}

// How many recent simulated ticks to keep for the live intraday sparkline
// — a rolling window, not an unbounded history, since this is a demo
// convenience built purely from what's arrived over this SSE connection
// (see App.jsx), not something the backend persists or returns.
const MAX_LIVE_TICKS = 50;

export default function InstrumentDetailPanel({ watchlistId, symbol, onClose, liveValue }) {
  const [detail, setDetail] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [liveTicks, setLiveTicks] = useState([]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    setDetail(null);
    setLiveTicks([]); // fresh intraday history whenever a different instrument is opened
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

  // Accumulates this session's simulated ticks for the OPEN instrument only
  // — App.jsx owns the single SSE connection and the full liveValues map;
  // this just watches the one entry relevant to this panel.
  useEffect(() => {
    if (liveValue?.value == null) return;
    setLiveTicks((prev) => {
      const nextValue = Number(liveValue.value);
      if (prev.length > 0 && prev[prev.length - 1] === nextValue) return prev; // no visible change, skip a re-render
      return [...prev, nextValue].slice(-MAX_LIVE_TICKS);
    });
  }, [liveValue]);

  const md = detail?.marketData;
  const detected = detail?.detectedChange;
  const pct = detected ? getPercentChange(detected.metrics, detail.instrumentType) : null;
  const direction = directionFromPercent(pct);
  const volumeRatio = detected ? getVolumeRatio(detected.metrics) : null;
  const historyPoints = detail?.recentHistory?.length
    ? buildSparklinePoints(detail.recentHistory.map((p) => p.value))
    : null;
  const liveChartPoints = liveTicks.length >= 2 ? buildSparklinePoints(liveTicks) : null;

  // Feature 5: prefer the live simulated price once one has arrived, same
  // "display overlay, source of truth stays server-side" pattern as the
  // main table (see WatchlistTable.jsx) — the fetched `detail` snapshot
  // itself is never mutated.
  const displayPrice = liveValue?.value ?? md?.latestValue;

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
                {md?.dataAvailable ? formatPrice(displayPrice, detail.instrumentType) : 'No data'}
              </span>
              {liveValue && (
                <span className="detail-panel__live-badge" title="Simulated live update — not real market data">
                  ● live
                </span>
              )}
              {pct !== null && (
                <span className={`detail-panel__pct detail-panel__pct--${direction ?? 'flat'}`}>
                  {pct > 0 ? '+' : ''}
                  {pct.toFixed(2)}% today
                </span>
              )}
            </div>

            {historyPoints && (
              <div className="detail-panel__chart">
                <svg viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`} preserveAspectRatio="none">
                  <polyline points={historyPoints} fill="none" strokeWidth="2" className="detail-panel__chart-line" />
                </svg>
                <p className="detail-panel__chart-caption">
                  Last {detail.recentHistory.length} trading days
                  {volumeRatio !== null && <> · Volume {formatVolumeRatio(volumeRatio)}</>}
                </p>
              </div>
            )}

            {liveChartPoints && (
              <div className="detail-panel__chart detail-panel__chart--live">
                <svg viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`} preserveAspectRatio="none">
                  <polyline
                    points={liveChartPoints}
                    fill="none"
                    strokeWidth="2"
                    className="detail-panel__chart-line detail-panel__chart-line--live"
                  />
                </svg>
                <p className="detail-panel__chart-caption">
                  Simulated live data · last {liveTicks.length} update{liveTicks.length === 1 ? '' : 's'} this session
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
