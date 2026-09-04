import { formatSeverity, formatChangeType, formatDate } from '../utils/format.js';
import { inferDirection } from '../utils/direction.js';

function severityTier(severity) {
  const num = Number(severity);
  if (Number.isNaN(num)) return 'moderate';
  if (num >= 3) return 'high';
  if (num >= 2) return 'moderate';
  return 'low';
}

export default function AttentionCard({ item }) {
  const direction = inferDirection(item.metrics);
  const tier = severityTier(item.severity);

  return (
    <li className={`attention-card attention-card--${tier}`} data-testid="attention-card">
      <div className="attention-card__top">
        <span className="attention-card__symbol">{item.symbol}</span>
        <span className={`attention-card__badge attention-card__badge--${tier}`}>
          {tier === 'high' ? 'High attention' : tier === 'moderate' ? 'Worth a look' : 'Minor'}
        </span>
      </div>

      <p className="attention-card__explanation">
        {direction === 'up' && <span className="glyph glyph--up" aria-hidden="true">▲</span>}
        {direction === 'down' && <span className="glyph glyph--down" aria-hidden="true">▼</span>}
        {item.explanation}
      </p>

      <div className="attention-card__meta">
        <span>{formatChangeType(item.changeType)}</span>
        <span aria-hidden="true">·</span>
        <span>Severity {formatSeverity(item.severity)}</span>
        <span aria-hidden="true">·</span>
        <span>{formatDate(item.asOfDate)}</span>
      </div>
    </li>
  );
}
