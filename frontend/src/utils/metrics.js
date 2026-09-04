// Reads the ACTUAL metric keys ChangeDetectionService puts into `metrics`
// (verified against backend/.../service/ChangeDetectionService.java) rather
// than guessing field names. If the backend ever renames a key, these
// gracefully fall back to null/"—" instead of showing a wrong number.

// Stocks: dailyReturn is a fraction (e.g. 0.058 = +5.8%).
// Funds: fundChangePct is a fraction (e.g. -0.0101 = -1.01%).
export function getPercentChange(metrics, instrumentType) {
  if (!metrics) return null;
  const fraction =
    instrumentType === 'FUND' ? metrics.fundChangePct : metrics.dailyReturn;
  if (fraction === null || fraction === undefined) return null;
  return Number(fraction) * 100;
}

// Stocks only — ratio of today's volume to its 20-day average.
export function getVolumeRatio(metrics) {
  if (!metrics || metrics.volumeRatio === null || metrics.volumeRatio === undefined) {
    return null;
  }
  return Number(metrics.volumeRatio);
}

export function directionFromPercent(pct) {
  if (pct === null || pct === undefined) return null;
  if (pct === 0) return 'flat';
  return pct > 0 ? 'up' : 'down';
}
