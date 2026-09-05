import { useEffect, useMemo, useState } from 'react';
import { trackEvent } from '../../../analytics';
import { STOCK_BOOKMARK_STORAGE_KEY } from '../constants';
import type { StockCandidate } from '../types';
import { readStringArrayStorage } from '../utils';

export function useStockBookmarks() {
  const [bookmarkedCodes, setBookmarkedCodes] = useState<string[]>(() =>
    readStringArrayStorage(STOCK_BOOKMARK_STORAGE_KEY),
  );
  const bookmarkedCodeSet = useMemo(() => new Set(bookmarkedCodes), [bookmarkedCodes]);

  useEffect(() => {
    localStorage.setItem(STOCK_BOOKMARK_STORAGE_KEY, JSON.stringify(bookmarkedCodes));
  }, [bookmarkedCodes]);

  function toggleBookmark(stock: StockCandidate) {
    setBookmarkedCodes((currentCodes) => {
      const isBookmarked = currentCodes.includes(stock.code);

      trackEvent(isBookmarked ? 'remove_stock_bookmark' : 'add_stock_bookmark', {
        stockCode: stock.code,
      });

      return isBookmarked
        ? currentCodes.filter((code) => code !== stock.code)
        : [...currentCodes, stock.code];
    });
  }

  return { bookmarkedCodeSet, toggleBookmark };
}
