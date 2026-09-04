// Sensible financial formatting, kept in one place so every component
// renders numbers consistently.

export function formatPrice(value, instrumentType) {
  if (value === null || value === undefined) return '—';
  const num = Number(value);
  const prefix = instrumentType === 'FUND' ? '' : '\u20B9'; // ₹ for stocks; NAV shown unitless
  return prefix + num.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

export function formatPercent(value, { withSign = true } = {}) {
  if (value === null || value === undefined) return '—';
  const num = Number(value);
  const sign = withSign && num > 0 ? '+' : '';
  return `${sign}${num.toFixed(2)}%`;
}

export function formatSeverity(value) {
  if (value === null || value === undefined) return '—';
  return Number(value).toFixed(2);
}

export function formatDate(value) {
  if (!value) return '—';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return value;
  return d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
}

export function formatDateTime(value) {
  if (!value) return '—';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return value;
  return d.toLocaleString('en-IN', {
    day: '2-digit',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  });
}

// Human label for the backend's changeType strings (e.g. "PRICE_SPIKE").
export function formatChangeType(changeType) {
  if (!changeType) return '';
  return changeType
    .toLowerCase()
    .split('_')
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(' ');
}
