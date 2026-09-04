// The backend's `metrics` field is a generic Map<String, BigDecimal> whose
// keys vary by instrument type and detector (z-score, volume ratio, category
// deviation...). Rather than hardcoding key names that could silently break
// if the backend renames a metric, scan for the first signed numeric metric
// that plausibly represents "which way did it move" and use its sign.
// If nothing matches, we simply don't show a directional arrow — the
// explanation text still carries the meaning.
const DIRECTIONAL_KEY_HINTS = ['return', 'pct', 'change', 'deviation', 'zscore', 'z_score'];

export function inferDirection(metrics) {
  if (!metrics) return null;
  const entries = Object.entries(metrics);
  const match = entries.find(([key]) =>
    DIRECTIONAL_KEY_HINTS.some((hint) => key.toLowerCase().includes(hint))
  );
  if (!match) return null;
  const num = Number(match[1]);
  if (Number.isNaN(num) || num === 0) return 'flat';
  return num > 0 ? 'up' : 'down';
}
