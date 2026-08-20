import { useEffect, useState } from 'react';
import { ArticleCard } from '../news/newslist/components/ArticleCard';
import { CategoryTabs } from '../news/newslist/components/CategoryTabs';
import { CATEGORY_LABELS } from '../news/newslist/types';
import type {
  NewsCategory,
  NewsCategoryOption,
  NewsListItemResponse,
} from '../news/newslist/types';
import { searchNews } from './api';
import './SearchPage.css';
import { trackEvent } from '../../analytics';

const PAGE_SIZE = 10;
const SEARCH_CATEGORIES: NewsCategoryOption[] = [
  { label: '전체', value: 'ALL' },
  ...Object.entries(CATEGORY_LABELS).map(([value, label]) => ({
    label,
    value: value as keyof typeof CATEGORY_LABELS,
  })),
];

function SearchIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="11" cy="11" r="6.5" />
      <path d="m16 16 4 4" />
    </svg>
  );
}

function SearchPage() {
  const [query, setQuery] = useState('');
  const [searchedKeyword, setSearchedKeyword] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<NewsCategory>('ALL');
  const [results, setResults] = useState<NewsListItemResponse[]>([]);
  const [page, setPage] = useState(1);
  const [totalResults, setTotalResults] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!searchedKeyword) return;

    let ignore = false;

    async function loadSearchResults() {
      setLoading(true);
      setError(null);

      try {
        const newsPage = await searchNews(searchedKeyword, selectedCategory, page, PAGE_SIZE);

        if (!ignore) {
          setResults(newsPage.content);
          setTotalResults(newsPage.totalElements);
          setTotalPages(newsPage.totalPages);
          if (newsPage.content.length === 0) {
            trackEvent('search_no_result', { search_length: searchedKeyword.length });
          }
        }
      } catch (caughtError) {
        if (!ignore) {
          setResults([]);
          setTotalResults(0);
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
  }, [searchedKeyword, selectedCategory, page]);

  function search() {
    const keyword = query.trim();

    if (!keyword) return;

    setQuery(keyword);
    setSearchedKeyword(keyword);
    setPage(1);
    trackEvent('search_news', { search_length: keyword.length });
  }

  function changeCategory(category: NewsCategory) {
    setSelectedCategory(category);
    setPage(1);
  }

  const changePage = (nextPage: number) => {
    setPage(nextPage);
    document.getElementById('news-results')?.scrollIntoView({ behavior: 'smooth' });
  };

  function selectArticle(article: NewsListItemResponse) {
    window.location.assign(`/news/${article.id}`);
  }

  return (
    <main className="search-page">
      <div className="search-panel">
        <div className="search-row">
          <button
            type="button"
            className="back-button"
            aria-label="이전 화면으로 이동"
            onClick={() => window.history.back()}
          >
            ‹
          </button>

          <form
            className="search-form"
            onSubmit={(event) => {
              event.preventDefault();
              search();
            }}
          >
            <button type="submit" className="search-submit" aria-label="뉴스 검색">
              <SearchIcon />
            </button>
            <label className="sr-only" htmlFor="news-search">
              뉴스 키워드 검색
            </label>
            <input
              id="news-search"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="검색어를 입력하세요"
              autoComplete="off"
            />
          </form>
        </div>

        <CategoryTabs
          categories={SEARCH_CATEGORIES}
          selectedCategory={selectedCategory}
          onChange={changeCategory}
        />

        {searchedKeyword ? (
          <section id="news-results" className="news-results" aria-labelledby="result-title">
            <div className="result-heading" aria-live="polite">
              <h1 id="result-title">‘{searchedKeyword}’ 뉴스</h1>
              <span>{loading ? '검색 중' : `${totalResults}건`}</span>
            </div>

            {loading ? <p className="search-state">검색 중입니다.</p> : null}

            {!loading && error ? (
              <p className="search-state" role="alert">
                {error}
              </p>
            ) : null}

            {!loading && results.length > 0 ? (
              <>
                <section className="article-list" aria-label="검색된 뉴스 목록">
                  {results.map((item) => (
                    <ArticleCard article={item} key={item.id} onSelect={selectArticle} />
                  ))}
                </section>

                {totalPages > 1 ? (
                  <nav className="pagination" aria-label="검색 결과 페이지">
                    <button
                      type="button"
                      disabled={page === 1}
                      onClick={() => changePage(page - 1)}
                    >
                      이전
                    </button>
                    {Array.from({ length: totalPages }, (_, index) => index + 1).map(
                      (pageNumber) => (
                        <button
                          type="button"
                          className={page === pageNumber ? 'active' : ''}
                          aria-current={page === pageNumber ? 'page' : undefined}
                          onClick={() => changePage(pageNumber)}
                          key={pageNumber}
                        >
                          {pageNumber}
                        </button>
                      ),
                    )}
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
              <p className="search-state">검색 결과가 없습니다.</p>
            ) : null}
          </section>
        ) : null}
      </div>
    </main>
  );
}

export default SearchPage;
