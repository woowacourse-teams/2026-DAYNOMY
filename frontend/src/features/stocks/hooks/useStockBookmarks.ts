import { useEffect, useMemo, useState } from 'react';
import { trackEvent } from '../../../analytics';
import { STOCK_BOOKMARK_STORAGE_KEY } from '../constants';
import type { StockCandidate } from '../types';
import { readStringArrayStorage } from '../utils';

export function useStockBookmarks(isLoggedIn: boolean) {
  const [bookmarkedCodes, setBookmarkedCodes] = useState<string[]>([]);
  const bookmarkedCodeSet = useMemo(() => new Set(bookmarkedCodes), [bookmarkedCodes]);

  useEffect(() => {
    setBookmarkedCodes(isLoggedIn ? readStringArrayStorage(STOCK_BOOKMARK_STORAGE_KEY) : []);
  }, [isLoggedIn]);

  useEffect(() => {
    if (isLoggedIn) {
      localStorage.setItem(STOCK_BOOKMARK_STORAGE_KEY, JSON.stringify(bookmarkedCodes));
    }
  }, [bookmarkedCodes, isLoggedIn]);

  function toggleBookmark(stock: StockCandidate) {
    if (!isLoggedIn) {
      return;
    }

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
