import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { trackEvent } from '../../analytics';
import { useLoginStatus } from '../../hooks/useLoginStatus';
import { ArticleCard } from '../news/newslist/components/ArticleCard';
import { CategoryTabs } from '../news/newslist/components/CategoryTabs';
import { NEWS_CATEGORIES } from '../news/newslist/constants';
import { isCategory } from '../news/newslist/types';
import type { NewsCategory, NewsListItem } from '../news/newslist/types';
import { searchKosdaqTopStocks } from '../stocks/api';
import { useStockBookmarks } from '../stocks/hooks/useStockBookmarks';
import type { StockCandidate } from '../stocks/types';
import { searchNews } from './api';
import { StockSearchResults } from './components/StockSearchResults';
import './SearchPage.css';

const PAGE_SIZE = 10;
const MAX_VISIBLE_PAGES = 5;

function getCategory(searchParams: URLSearchParams): NewsCategory {
  const category = searchParams.get('category');
  return category === 'ALL' || isCategory(category) ? category : 'ALL';
}

function getPage(searchParams: URLSearchParams) {
  const page = Number(searchParams.get('page'));
  return Number.isSafeInteger(page) && page > 0 ? page : 1;
}

function getVisiblePages(currentPage: number, totalPages: number) {
  const pageCount = Math.min(MAX_VISIBLE_PAGES, totalPages);
  const startPage = Math.min(
    Math.max(currentPage - Math.floor(pageCount / 2), 1),
    totalPages - pageCount + 1,
  );

  return Array.from({ length: pageCount }, (_, index) => startPage + index);
}

function SearchPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const searchedKeyword = searchParams.get('q')?.trim() ?? '';
  const selectedCategory = getCategory(searchParams);
  const page = getPage(searchParams);
  const [results, setResults] = useState<NewsListItem[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(Boolean(searchedKeyword));
  const [error, setError] = useState<string | null>(null);
  const [searchAttempt, setSearchAttempt] = useState(0);
  const [stockResult, setStockResult] = useState<{
    keyword: string;
    rankings: StockCandidate[];
  }>({ keyword: '', rankings: [] });
  const [stocksLoading, setStocksLoading] = useState(Boolean(searchedKeyword));
  const [stocksError, setStocksError] = useState<string | null>(null);
  const isLoggedIn = useLoginStatus();
  const { bookmarkedCodeSet, toggleBookmark } = useStockBookmarks(isLoggedIn);
  const resultTitleRef = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    if (searchedKeyword) resultTitleRef.current?.focus();
  }, [searchedKeyword]);

  useEffect(() => {
    if (!searchedKeyword) return;

    let ignore = false;

    async function loadSearchResults() {
      setLoading(true);
      setError(null);

      try {
        const newsPage = await searchNews(searchedKeyword, selectedCategory, page, PAGE_SIZE);

        if (!ignore) {
          if (newsPage.totalPages > 0 && page > newsPage.totalPages) {
            setSearchParams(
              {
                q: searchedKeyword,
                category: selectedCategory,
                page: String(newsPage.totalPages),
              },
              { replace: true },
            );
            return;
          }

          setResults(newsPage.content);
          setTotalPages(newsPage.totalPages);
        }
      } catch (caughtError) {
        if (!ignore) {
          setResults([]);
          setTotalPages(0);
          setError(
            caughtError instanceof Error ? caughtError.message : '검색 결과를 불러오지 못했습니다.',
          );
        }
      } finally {
        if (!ignore) setLoading(false);
      }
    }

    loadSearchResults();

    return () => {
      ignore = true;
    };
  }, [searchedKeyword, selectedCategory, page, searchAttempt, setSearchParams]);

  useEffect(() => {
    if (!searchedKeyword) return;

    let ignore = false;
    setStocksLoading(true);
    setStocksError(null);

    searchKosdaqTopStocks(searchedKeyword)
      .then((response) => {
        if (!ignore) {
          setStockResult({ keyword: searchedKeyword, rankings: response.rankings });
        }
      })
      .catch((caughtError: unknown) => {
        if (!ignore) {
          setStockResult({ keyword: searchedKeyword, rankings: [] });
          setStocksError(
            caughtError instanceof Error
              ? caughtError.message
              : '종목 검색 결과를 불러오지 못했습니다.',
          );
        }
      })
      .finally(() => {
        if (!ignore) setStocksLoading(false);
      });

    return () => {
      ignore = true;
    };
  }, [searchedKeyword]);

  useEffect(() => {
    if (searchedKeyword) {
      trackEvent('search_news', { search_length: searchedKeyword.length });
    }
  }, [searchedKeyword]);

  function changeCategory(category: NewsCategory) {
    setSearchParams({ q: searchedKeyword, category, page: '1' });
  }

  const changePage = (nextPage: number) => {
    setSearchParams({
      q: searchedKeyword,
      category: selectedCategory,
      page: String(nextPage),
    });
    document.getElementById('news-results')?.scrollIntoView({ behavior: 'smooth' });
  };

  const visiblePages = getVisiblePages(page, totalPages);
  const hasCurrentStockResult = stockResult.keyword === searchedKeyword;
  const stocks = hasCurrentStockResult ? stockResult.rankings : [];
  const currentStocksLoading =
    Boolean(searchedKeyword) && (!hasCurrentStockResult || stocksLoading);
  const currentStocksError = hasCurrentStockResult ? stocksError : null;
  const showStockError =
    !loading &&
    !currentStocksLoading &&
    !error &&
    results.length === 0 &&
    currentStocksError !== null;
  const showEmpty =
    !loading &&
    !currentStocksLoading &&
    !error &&
    !currentStocksError &&
    results.length === 0 &&
    stocks.length === 0;

  return (
    <main className="search-page">
      <div className={stocks.length > 0 ? 'search-panel has-stocks' : 'search-panel'}>
        {searchedKeyword ? (
          <>
            <h1 ref={resultTitleRef} id="result-title" className="sr-only" tabIndex={-1}>
              ‘{searchedKeyword}’ 뉴스
            </h1>
            <span className="sr-only" role="status">
              {currentStocksLoading
                ? '종목 검색 중입니다.'
                : currentStocksError && !showStockError
                  ? `종목 검색 결과를 불러오지 못했습니다. ${currentStocksError}`
                  : ''}
            </span>

            {stocks.length > 0 ? (
              <StockSearchResults
                keyword={searchedKeyword}
                stocks={stocks}
                isLoggedIn={isLoggedIn}
                bookmarkedCodeSet={bookmarkedCodeSet}
                onBookmarkToggle={toggleBookmark}
              />
            ) : null}

            <CategoryTabs
              categories={NEWS_CATEGORIES}
              selectedCategory={selectedCategory}
              onChange={changeCategory}
            />

            <section id="news-results" className="news-results" aria-label="검색된 뉴스 목록">
              <div className="result-heading">
                <span>{loading ? '검색 중' : '최신순'}</span>
              </div>

              {loading ? <p className="search-state">검색 중입니다.</p> : null}

              {!loading && error ? (
                <div className="search-state" role="alert">
                  <p>{error}</p>
                  <button type="button" onClick={() => setSearchAttempt((attempt) => attempt + 1)}>
                    다시 시도
                  </button>
                </div>
              ) : null}

              {showStockError ? (
                <p className="search-state" role="alert">
                  {currentStocksError}
                </p>
              ) : null}

              {!loading && results.length > 0 ? (
                <>
                  <div className="article-list">
                    {results.map((item) => (
                      <ArticleCard article={item} key={item.id} />
                    ))}
                  </div>

                  {totalPages > 1 ? (
                    <nav className="pagination" aria-label="검색 결과 페이지">
                      <button
                        type="button"
                        disabled={page === 1}
                        onClick={() => changePage(page - 1)}
                      >
                        이전
                      </button>
                      {visiblePages.map((pageNumber) => (
                        <button
                          type="button"
                          className={page === pageNumber ? 'active' : ''}
                          aria-current={page === pageNumber ? 'page' : undefined}
                          onClick={() => changePage(pageNumber)}
                          key={pageNumber}
                        >
                          {pageNumber}
                        </button>
                      ))}
                      <button
                        type="button"
                        disabled={page === totalPages}
                        onClick={() => changePage(page + 1)}
                      >
                        다음
                      </button>
                    </nav>
                  ) : null}
                </>
              ) : null}

              {showEmpty ? <p className="search-state">검색된 결과가 없습니다.</p> : null}
            </section>
          </>
        ) : null}
      </div>
    </main>
  );
}

export default SearchPage;
