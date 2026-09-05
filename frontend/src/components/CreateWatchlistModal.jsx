import { useEffect, useState } from 'react';

const DEFAULT_NAME = 'My Watchlist';

// Opens whenever the user clicks "+ Watchlist" (or "Create watchlist" on the
// empty state). Nothing is created until they confirm — cancelling, or
// dismissing via the overlay, creates nothing. Keeps the default name
// pre-filled (so a quick "Create" still works like before) but lets the
// user rename it before it exists, which is the actual fix for duplicate
// "My Watchlist" tabs.
export default function CreateWatchlistModal({ open, submitting, error, onCancel, onConfirm }) {
  const [name, setName] = useState(DEFAULT_NAME);
  const [validationError, setValidationError] = useState(null);

  useEffect(() => {
    if (open) {
      setName(DEFAULT_NAME);
      setValidationError(null);
    }
  }, [open]);

  if (!open) return null;

  function handleSubmit(e) {
    e.preventDefault();
    const trimmed = name.trim();
    if (!trimmed) {
      setValidationError('Watchlist name cannot be empty.');
      return;
    }
    setValidationError(null);
    onConfirm(trimmed);
  }

  function handleOverlayMouseDown(e) {
    if (e.target === e.currentTarget && !submitting) onCancel();
  }

  return (
    <div
      className="modal-overlay"
      role="presentation"
      onMouseDown={handleOverlayMouseDown}
    >
      <div className="modal" role="dialog" aria-modal="true" aria-label="Create watchlist">
        <h2 className="modal__title">Name your watchlist</h2>
        <form onSubmit={handleSubmit}>
          <div className="modal__field">
            <label htmlFor="new-watchlist-name">Watchlist name</label>
            <input
              id="new-watchlist-name"
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              autoFocus
              autoComplete="off"
            />
          </div>

          {(validationError || error) && (
            <p className="modal__error" role="alert">
              {validationError || error}
            </p>
          )}

          <div className="modal__actions">
            <button type="button" className="btn btn--outline" onClick={onCancel} disabled={submitting}>
              Cancel
            </button>
            <button type="submit" className="btn btn--primary" disabled={submitting}>
              {submitting ? 'Creating…' : 'Create'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
