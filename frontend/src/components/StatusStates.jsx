export function LoadingState({ label = 'Loading…' }) {
  return (
    <div className="status-state status-state--loading" role="status" aria-live="polite">
      <span className="spinner" aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}

export function ErrorState({ message, onRetry }) {
  return (
    <div className="status-state status-state--error" role="alert">
      <p className="status-state__title">Something went wrong</p>
      <p className="status-state__message">{message}</p>
      {onRetry && (
        <button type="button" className="btn btn--ghost" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  );
}

export function EmptyState({ title, message }) {
  return (
    <div className="status-state status-state--empty">
      <p className="status-state__title">{title}</p>
      {message && <p className="status-state__message">{message}</p>}
    </div>
  );
}
