export function holdingReturn(rate: number | null | undefined) {
  if (rate == null || !Number.isFinite(rate)) return '';
  const sign = rate > 0 ? '+' : rate < 0 ? '−' : '';
  return `(${sign}${Math.abs(rate).toFixed(2)}%)`;
}
