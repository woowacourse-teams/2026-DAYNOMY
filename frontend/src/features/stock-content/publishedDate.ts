export function getPublishedDate(value: string | null | undefined): string | null {
  if (!value || !/^\d{4}-\d{2}-\d{2}(?:T|$)/.test(value)) return null;
  const date = value.slice(0, 10);
  const parsed = new Date(`${date}T00:00:00Z`);
  return Number.isFinite(parsed.getTime()) && parsed.toISOString().slice(0, 10) === date
    ? date
    : null;
}
