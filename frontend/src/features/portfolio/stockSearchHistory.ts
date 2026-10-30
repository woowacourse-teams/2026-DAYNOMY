const STORAGE_KEY = 'daynomy:portfolio-stock-searches:v1';
const LIMIT = 5;

export function loadStockSearchHistory(): string[] {
  try {
    const value: unknown = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]');
    if (!Array.isArray(value)) return [];
    return [
      ...new Set(
        value.filter(
          (item): item is string =>
            typeof item === 'string' &&
            item === item.trim() &&
            item.length <= 50 &&
            /[\p{L}\p{N}]/u.test(item),
        ),
      ),
    ].slice(0, LIMIT);
  } catch {
    return [];
  }
}

export function saveStockSearchHistory(history: string[]): string[] {
  const next = history.slice(0, LIMIT);
  try {
    if (next.length) localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    else localStorage.removeItem(STORAGE_KEY);
  } catch {
    // 브라우저 저장소를 사용할 수 없어도 현재 창의 검색은 계속한다.
  }
  return next;
}
