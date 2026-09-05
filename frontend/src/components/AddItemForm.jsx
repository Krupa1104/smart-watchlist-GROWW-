import { useEffect, useState } from 'react';

export default function AddItemForm({
  onAdd,
  submitting,
  error,
  success,
  prefillSymbol,
  prefillInstrumentType,
}) {
  const [symbol, setSymbol] = useState(prefillSymbol || '');
  const [instrumentType, setInstrumentType] = useState(prefillInstrumentType || 'STOCK');

  useEffect(() => {
    if (prefillSymbol) setSymbol(prefillSymbol);
  }, [prefillSymbol]);

  // Set together with prefillSymbol when a global search result is picked,
  // so e.g. selecting a FUND result doesn't leave the type dropdown on the
  // default STOCK — but only when the parent actually specifies one, so
  // typing into the plain "Symbol" field never resets the user's own choice.
  useEffect(() => {
    if (prefillInstrumentType) setInstrumentType(prefillInstrumentType);
  }, [prefillInstrumentType]);

  function handleSubmit(e) {
    e.preventDefault();
    const trimmed = symbol.trim();
    if (!trimmed) return;
    onAdd(trimmed, instrumentType);
  }

  return (
    <div className="add-item">
      <form className="add-item__form" onSubmit={handleSubmit} aria-label="Add instrument">
        <div className="add-item__field">
          <label htmlFor="add-item-type">Type</label>
          <select
            id="add-item-type"
            value={instrumentType}
            onChange={(e) => setInstrumentType(e.target.value)}
          >
            <option value="STOCK">Stock</option>
            <option value="FUND">Fund</option>
          </select>
        </div>

        <div className="add-item__field add-item__field--grow">
          <label htmlFor="add-item-symbol">Symbol</label>
          <input
            id="add-item-symbol"
            type="text"
            placeholder="e.g. STK09"
            value={symbol}
            onChange={(e) => setSymbol(e.target.value)}
            autoComplete="off"
          />
        </div>

        <button type="submit" className="btn btn--secondary" disabled={submitting || !symbol.trim()}>
          {submitting ? 'Adding…' : 'Add to watchlist'}
        </button>
      </form>

      {error && (
        <p className="add-item__error" role="alert">
          {error}
        </p>
      )}
      {success && (
        <p className="add-item__success" role="status">
          {success}
        </p>
      )}
    </div>
  );
}
