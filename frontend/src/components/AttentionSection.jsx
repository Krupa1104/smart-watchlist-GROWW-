import AttentionCard from './AttentionCard.jsx';
import { LoadingState, ErrorState, EmptyState } from './StatusStates.jsx';

export default function AttentionSection({ items, loading, error, onRetry }) {
  return (
    <section className="attn-section" aria-label="What changed">
      <div className="attn-section__heading">
        <h2>What changed</h2>
        <p className="attn-section__subtitle">Meaningful moves since your last check</p>
      </div>

      {loading && <LoadingState label="Scanning for meaningful changes…" />}

      {!loading && error && <ErrorState message={error} onRetry={onRetry} />}

      {!loading && !error && items.length === 0 && (
        <EmptyState
          title="Nothing important changed"
          message="Your watchlist looks normal based on its recent behavior."
        />
      )}

      {!loading && !error && items.length > 0 && (
        <ul className="attn-list" data-testid="attention-list">
          {items.map((item) => (
            <AttentionCard key={`${item.symbol}-${item.asOfDate}`} item={item} />
          ))}
        </ul>
      )}
    </section>
  );
}
