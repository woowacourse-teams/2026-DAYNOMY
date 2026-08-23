export function formatBaseDate(baseDate: string | null) {
  if (!baseDate) {
    return '기준일 없음';
  }

  return baseDate.replaceAll('-', '.');
}

export function readStringArrayStorage(key: string) {
  try {
    const storedValue = localStorage.getItem(key);
    const parsedValue = storedValue ? JSON.parse(storedValue) : [];

    return Array.isArray(parsedValue)
      ? parsedValue.filter((value): value is string => typeof value === 'string')
      : [];
  } catch {
    return [];
  }
}
