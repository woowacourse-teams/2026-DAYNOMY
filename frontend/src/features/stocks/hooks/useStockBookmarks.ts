import { useEffect, useMemo, useState } from 'react';
import { trackEvent } from '../../../analytics';
import { STOCK_BOOKMARK_DETAILS_STORAGE_KEY, STOCK_BOOKMARK_STORAGE_KEY } from '../constants';
import { searchKosdaqTopStocks } from '../api';
import type { StockCandidate } from '../types';
import { readStringArrayStorage } from '../utils';

type StockBookmarkDetails = Record<string, StockCandidate>;

function readStockBookmarkDetailsStorage() {
  try {
    const storedValue = localStorage.getItem(STOCK_BOOKMARK_DETAILS_STORAGE_KEY);
    const parsedValue = storedValue ? JSON.parse(storedValue) : {};

    if (!parsedValue || typeof parsedValue !== 'object' || Array.isArray(parsedValue)) {
      return {};
    }

    return Object.entries(parsedValue).reduce<StockBookmarkDetails>((details, [code, value]) => {
      if (
        typeof code === 'string' &&
        value &&
        typeof value === 'object' &&
        !Array.isArray(value) &&
        typeof (value as StockCandidate).code === 'string' &&
        (value as StockCandidate).code === code &&
        typeof (value as StockCandidate).name === 'string' &&
        typeof (value as StockCandidate).rank === 'number'
      ) {
        details[code] = value as StockCandidate;
      }

      return details;
    }, {});
  } catch {
    return {};
  }
}

export function useStockBookmarks() {
  const [bookmarkedCodes, setBookmarkedCodes] = useState<string[]>(() =>
    readStringArrayStorage(STOCK_BOOKMARK_STORAGE_KEY),
  );
  const [bookmarkedDetails, setBookmarkedDetails] = useState<StockBookmarkDetails>(() =>
    readStockBookmarkDetailsStorage(),
  );
  const bookmarkedCodeSet = useMemo(() => new Set(bookmarkedCodes), [bookmarkedCodes]);
  const bookmarkedStocks = useMemo(
    () => bookmarkedCodes.map((code) => bookmarkedDetails[code] ?? { rank: 0, code, name: code }),
    [bookmarkedCodes, bookmarkedDetails],
  );

  useEffect(() => {
    localStorage.setItem(STOCK_BOOKMARK_STORAGE_KEY, JSON.stringify(bookmarkedCodes));
  }, [bookmarkedCodes]);

  useEffect(() => {
    localStorage.setItem(STOCK_BOOKMARK_DETAILS_STORAGE_KEY, JSON.stringify(bookmarkedDetails));
  }, [bookmarkedDetails]);

  useEffect(() => {
    const missingCodes = bookmarkedCodes.filter((code) => !bookmarkedDetails[code]);

    if (missingCodes.length === 0) {
      return;
    }

    let cancelled = false;

    Promise.all(
      missingCodes.map(async (code) => {
        try {
          const response = await searchKosdaqTopStocks(code);
          return response.rankings.find((stock) => stock.code === code) ?? null;
        } catch {
          return null;
        }
      }),
    ).then((stocks) => {
      if (cancelled) {
        return;
      }

      const resolvedStocks = stocks.filter((stock): stock is StockCandidate => stock !== null);
      if (resolvedStocks.length === 0) {
        return;
      }

      setBookmarkedDetails((currentDetails) => ({
        ...currentDetails,
        ...Object.fromEntries(resolvedStocks.map((stock) => [stock.code, stock])),
      }));
    });

    return () => {
      cancelled = true;
    };
  }, [bookmarkedCodes, bookmarkedDetails]);

  function removeBookmark(code: string) {
    setBookmarkedCodes((currentCodes) =>
      currentCodes.filter((currentCode) => currentCode !== code),
    );
    setBookmarkedDetails((currentDetails) => {
      const { [code]: _removed, ...nextDetails } = currentDetails;
      return nextDetails;
    });
    trackEvent('remove_stock_bookmark', { stockCode: code });
  }

  function toggleBookmark(stock: StockCandidate) {
    const isBookmarked = bookmarkedCodeSet.has(stock.code);

    trackEvent(isBookmarked ? 'remove_stock_bookmark' : 'add_stock_bookmark', {
      stockCode: stock.code,
    });

    setBookmarkedCodes((currentCodes) =>
      isBookmarked
        ? currentCodes.filter((code) => code !== stock.code)
        : [...currentCodes, stock.code],
    );
    setBookmarkedDetails((currentDetails) => {
      if (isBookmarked) {
        const { [stock.code]: _removed, ...nextDetails } = currentDetails;
        return nextDetails;
      }

      return { ...currentDetails, [stock.code]: stock };
    });
  }

  return { bookmarkedCodeSet, bookmarkedStocks, removeBookmark, toggleBookmark };
}
