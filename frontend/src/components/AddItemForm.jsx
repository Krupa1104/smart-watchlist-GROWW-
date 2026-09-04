import { useState } from 'react';

export default function AddItemForm({ onAdd, submitting, error }) {
  const [symbol, setSymbol] = useState('');
  const [instrumentType, setInstrumentType] = useState('STOCK');

  function handleSubmit(e) {
    e.preventDefault();
    const trimmed = symbol.trim();
    if (!trimmed) return;
    onAdd(trimmed, instrumentType);
  }

  return (
    <form className="add-item-form" onSubmit={handleSubmit} aria-label="Add instrument">
      <div className="add-item-form__field">
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

      <div className="add-item-form__field add-item-form__field--grow">
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

      {error && <p className="add-item-form__error" role="alert">{error}</p>}
    </form>
  );
}
