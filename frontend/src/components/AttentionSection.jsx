import AttentionCard from './AttentionCard.jsx';
import { LoadingState, ErrorState, EmptyState } from './StatusStates.jsx';

export default function AttentionSection({ items, loading, error, onRetry }) {
  return (
    <section className="attention-section" aria-label="What changed">
      <div className="attention-section__heading">
        <h2>What changed</h2>
        <p className="attention-section__subtitle">
          Meaningful moves since your instruments' recent behavior — not just today's price.
        </p>
      </div>

      {loading && <LoadingState label="Scanning for meaningful changes…" />}

      {!loading && error && (
        <ErrorState message={error} onRetry={onRetry} />
      )}

      {!loading && !error && items.length === 0 && (
        <EmptyState
          title="Nothing important changed"
          message="Everything on your watchlist is moving within its normal range."
        />
      )}

      {!loading && !error && items.length > 0 && (
        <ul className="attention-list" data-testid="attention-list">
          {items.map((item) => (
            <AttentionCard key={`${item.symbol}-${item.asOfDate}`} item={item} />
          ))}
        </ul>
      )}
    </section>
  );
}
