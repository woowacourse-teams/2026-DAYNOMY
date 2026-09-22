import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { trackEvent } from '../../analytics';
import { ArticleCard } from '../news/newslist/components/ArticleCard';
import { CategoryTabs } from '../news/newslist/components/CategoryTabs';
import { isCategory } from '../news/newslist/types';
import type { NewsCategory, NewsCategoryOption, NewsListItem } from '../news/newslist/types';
import { searchNews } from './api';
import './SearchPage.css';

const PAGE_SIZE = 10;
const MAX_VISIBLE_PAGES = 5;
const SEARCH_CATEGORIES: NewsCategoryOption[] = [
  { label: '전체', value: 'ALL' },
  { label: '주식', value: 'STOCK' },
  { label: 'ETF', value: 'ETF' },
  { label: '부동산', value: 'REAL_ESTATE' },
];

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
        if (ignore) return;
        if (newsPage.totalPages > 0 && page > newsPage.totalPages) {
          setSearchParams(
            { q: searchedKeyword, category: selectedCategory, page: String(newsPage.totalPages) },
            { replace: true },
          );
          return;
        }
        setResults(newsPage.content);
        setTotalPages(newsPage.totalPages);
      } catch (caughtError) {
        if (ignore) return;
        setResults([]);
        setTotalPages(0);
        setError(
          caughtError instanceof Error ? caughtError.message : '검색 결과를 불러오지 못했습니다.',
        );
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
    if (searchedKeyword) trackEvent('search_news', { search_length: searchedKeyword.length });
  }, [searchedKeyword]);

  function changeCategory(category: NewsCategory) {
    setSearchParams({ q: searchedKeyword, category, page: '1' });
  }

  function changePage(nextPage: number) {
    setSearchParams({ q: searchedKeyword, category: selectedCategory, page: String(nextPage) });
    document.getElementById('news-results')?.scrollIntoView({ behavior: 'smooth' });
  }

  const visiblePages = getVisiblePages(page, totalPages);

  return (
    <main className="search-page">
      <div className="search-panel">
        {searchedKeyword ? (
          <>
            <h1 ref={resultTitleRef} id="result-title" className="sr-only" tabIndex={-1}>
              ‘{searchedKeyword}’ 뉴스
            </h1>
            <CategoryTabs
              categories={SEARCH_CATEGORIES}
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
              {!loading && !error && results.length > 0 ? (
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
              {!loading && !error && results.length === 0 ? (
                <p className="search-state">검색된 결과가 없습니다.</p>
              ) : null}
            </section>
          </>
        ) : null}
      </div>
    </main>
  );
}

export default SearchPage;
