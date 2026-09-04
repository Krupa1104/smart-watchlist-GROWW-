import { formatSeverity, formatChangeType, formatDate } from '../utils/format.js';
import { getPercentChange, directionFromPercent } from '../utils/metrics.js';

function severityTier(severity) {
  const num = Number(severity);
  if (Number.isNaN(num)) return 'moderate';
  if (num >= 3) return 'high';
  if (num >= 1.5) return 'moderate';
  return 'low';
}

const TIER_LABEL = { high: 'High attention', moderate: 'Attention', low: 'Minor' };

export default function AttentionCard({ item }) {
  const pct = getPercentChange(item.metrics, item.instrumentType);
  const direction = directionFromPercent(pct);
  const tier = severityTier(item.severity);

  return (
    <li className={`attn-card attn-card--${tier}`} data-testid="attention-card">
      <div className="attn-card__headline">
        <span className="attn-card__symbol">{item.symbol}</span>
        {pct !== null && (
          <span className={`attn-card__pct attn-card__pct--${direction}`}>
            {direction === 'up' ? '▲' : direction === 'down' ? '▼' : '–'}{' '}
            {pct > 0 ? '+' : ''}
            {pct.toFixed(2)}%
          </span>
        )}
        <span className={`attn-card__badge attn-card__badge--${tier}`}>{TIER_LABEL[tier]}</span>
      </div>

      <p className="attn-card__explanation">{item.explanation}</p>

      <p className="attn-card__meta">
        {formatChangeType(item.changeType)} · Severity {formatSeverity(item.severity)} ·{' '}
        {formatDate(item.asOfDate)}
      </p>
    </li>
  );
}
