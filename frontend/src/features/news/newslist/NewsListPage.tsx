import { useEffect, useMemo, useState } from 'react';
import { getNews, getTodayNews } from './api';
import { ArticleCard } from './components/ArticleCard';
import { CategoryTabs } from './components/CategoryTabs';
import { NewsListSkeleton } from './components/NewsListSkeleton';
import { TodayNewsBanner } from './components/TodayNewsBanner';
import { NEWS_LIST_CATEGORIES } from './constants';
import { addTodayNewsDemoData } from './todayNewsDemoData';
import type { NewsCategory, NewsListItem, NewsPage } from './types';
import './newsList.css';
import { trackEvent } from '../../../analytics';

let todayNewsCache: NewsListItem[] = [];
const newsPageCache = new Map<string, NewsPage>();

export function NewsListPage() {
  const [selectedCategory, setSelectedCategory] = useState<NewsCategory>('ALL');
  const [articles, setArticles] = useState<NewsListItem[]>([]);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  const [todayNews, setTodayNews] = useState<NewsListItem[]>(() => todayNewsCache);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [todayNewsError, setTodayNewsError] = useState<string | null>(null);
  const [todayNewsLoading, setTodayNewsLoading] = useState(true);

  const selectedCategoryLabel = useMemo(
    () =>
      NEWS_LIST_CATEGORIES.find((category) => category.value === selectedCategory)?.label ?? '전체',
    [selectedCategory],
  );
  const paginationPages = useMemo(() => {
    const maxVisiblePages = 5;
    const visiblePageCount = Math.min(totalPages, maxVisiblePages);
    const firstPage = Math.min(
      Math.max(page - Math.floor(visiblePageCount / 2), 1),
      Math.max(totalPages - visiblePageCount + 1, 1),
    );

    return Array.from({ length: visiblePageCount }, (_, index) => firstPage + index);
  }, [page, totalPages]);

  useEffect(() => {
    trackEvent('view_news_list', { category: selectedCategory });
  }, [selectedCategory]);

  useEffect(() => {
    let ignore = false;

    async function loadNews() {
      const cacheKey = `${selectedCategory}:${page}`;
      const cachedPage = newsPageCache.get(cacheKey);

      if (cachedPage) {
        setArticles(cachedPage.content);
        setTotalPages(cachedPage.totalPages);
        setTotalElements(cachedPage.totalElements);
      }

      setLoading(!cachedPage);
      setError(null);

      try {
        const newsPage = await getNews(selectedCategory, page);

        if (!ignore) {
          newsPageCache.set(cacheKey, newsPage);
          setArticles(newsPage.content);
          setTotalPages(newsPage.totalPages);
          setTotalElements(newsPage.totalElements);
        }
      } catch (caughtError) {
        if (!ignore) {
          setArticles([]);
          setTotalPages(1);
          setTotalElements(0);
          setError(
            caughtError instanceof Error ? caughtError.message : '이슈 목록을 불러오지 못했습니다.',
          );
        }
      } finally {
        if (!ignore) {
          setLoading(false);
        }
      }
    }

    loadNews();

    return () => {
      ignore = true;
    };
  }, [selectedCategory, page]);

  useEffect(() => {
    let ignore = false;
    setTodayNewsLoading(true);
    setTodayNewsError(null);

    async function loadTodayNews() {
      try {
        const todayNewsPage = await getTodayNews();
        const content = addTodayNewsDemoData(todayNewsPage.content);

        if (!ignore) {
          todayNewsCache = content;
          setTodayNews(content);
        }
      } catch {
        if (!ignore) {
          todayNewsCache = [];
          setTodayNews([]);
          setTodayNewsError('오늘의 이슈를 불러오지 못했습니다.');
        }
      } finally {
        if (!ignore) {
          setTodayNewsLoading(false);
        }
      }
    }

    loadTodayNews();

    return () => {
      ignore = true;
    };
  }, []);

  function handleCategoryChange(category: NewsCategory) {
    setSelectedCategory(category);
    setPage(1);
    window.scrollTo({ top: 0 });
  }

  function handlePageChange(nextPage: number) {
    setPage(nextPage);
    window.scrollTo({ top: 0 });
  }

  return (
    <main className="news-home">
      <div className="news-title-row">
        <h1>오늘의 이슈</h1>
      </div>

      {!todayNewsError ? <TodayNewsBanner articles={todayNews} loading={todayNewsLoading} /> : null}

      {!todayNews.length && todayNewsError ? (
        <section className="state-panel" role="alert">
          <strong>오늘의 이슈를 불러오지 못했습니다.</strong>
          <p>잠시 후 다시 확인해 주세요.</p>
        </section>
      ) : null}

      <section className="section-header" aria-live="polite">
        <div className="section-title">
          <p>{selectedCategoryLabel === '전체' ? '최신' : selectedCategoryLabel} 이슈</p>
        </div>
        <div className="section-meta">
          <span>최신순</span>
          <span aria-hidden="true">·</span>
          <span>{loading ? '불러오는 중' : `총 ${totalElements}건`}</span>
        </div>
      </section>

      <CategoryTabs
        categories={NEWS_LIST_CATEGORIES}
        selectedCategory={selectedCategory}
        onChange={handleCategoryChange}
        ariaLabel="이슈 카테고리"
      />

      <span className="sr-only" role="status">
        {error ? `이슈 목록 API 응답을 받지 못했습니다. ${error}` : ''}
        {todayNewsError ? ` ${todayNewsError}` : ''}
      </span>

      {loading && articles.length === 0 ? <NewsListSkeleton /> : null}

      {!loading && !error && articles.length === 0 ? (
        <section className="state-panel">
          <strong>표시할 이슈가 없습니다.</strong>
          <p>다른 카테고리를 선택하거나 잠시 후 다시 확인해 주세요.</p>
        </section>
      ) : null}

      {!loading && error ? (
        <section className="state-panel" role="alert">
          <strong>이슈 목록을 불러오지 못했습니다.</strong>
          <p>잠시 후 다시 확인해 주세요.</p>
        </section>
      ) : null}

      {articles.length > 0 ? (
        <section className="article-list" aria-label="이슈 목록">
          {articles.map((article) => (
            <ArticleCard article={article} key={article.id} />
          ))}
        </section>
      ) : null}

      {articles.length > 0 && totalPages > 1 ? (
        <footer className="pagination" aria-label="이슈 페이지네이션">
          <button
            type="button"
            className="pagination-arrow"
            aria-label="표시된 페이지 범위의 첫 페이지"
            onClick={() => handlePageChange(paginationPages[0] ?? 1)}
            disabled={loading || page === paginationPages[0]}
          >
            &lt;
          </button>
          {paginationPages.map((pageNumber) => (
            <button
              type="button"
              key={pageNumber}
              className={page === pageNumber ? 'active' : undefined}
              aria-current={page === pageNumber ? 'page' : undefined}
              onClick={() => handlePageChange(pageNumber)}
              disabled={loading}
            >
              {pageNumber}
            </button>
          ))}
          <button
            type="button"
            className="pagination-arrow"
            aria-label="표시된 페이지 범위의 마지막 페이지"
            onClick={() =>
              handlePageChange(paginationPages[paginationPages.length - 1] ?? totalPages)
            }
            disabled={loading || page === paginationPages[paginationPages.length - 1]}
          >
            &gt;
          </button>
        </footer>
      ) : null}
    </main>
  );
}
