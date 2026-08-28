import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { getNews, getTodayNews } from './api';
import { ArticleCard } from './components/ArticleCard';
import { CategoryTabs } from './components/CategoryTabs';
import { NewsListSkeleton } from './components/NewsListSkeleton';
import { TodayNewsBanner } from './components/TodayNewsBanner';
import { NEWS_CATEGORIES } from './constants';
import { getMockNewsPage, getMockTodayNews } from './mock';
import type { NewsCategory, NewsListItem } from './types';
import './newsList.css';
import { trackEvent } from '../../../analytics';

const initialNewsPage = getMockNewsPage('ALL', 1);
let todayNewsCache: NewsListItem[] = [getMockTodayNews('ALL')];
const newsPageCache = new Map<string, ReturnType<typeof getMockNewsPage>>([
  ['ALL:1', initialNewsPage],
]);

export function NewsListPage() {
  const navigate = useNavigate();
  const [selectedCategory, setSelectedCategory] = useState<NewsCategory>('ALL');
  const [articles, setArticles] = useState<NewsListItem[]>(() => initialNewsPage.content);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(() => initialNewsPage.totalPages);
  const [todayNews, setTodayNews] = useState<NewsListItem[]>(() => todayNewsCache);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const selectedCategoryLabel = useMemo(
    () => NEWS_CATEGORIES.find((category) => category.value === selectedCategory)?.label ?? '전체',
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
      }

      setLoading(!cachedPage && newsPageCache.size === 0);
      setError(null);

      try {
        const newsPage = await getNews(selectedCategory, page);
        const mockNewsPage = getMockNewsPage(selectedCategory, page);
        const content = newsPage.content.length > 0 ? newsPage.content : mockNewsPage.content;

        if (!ignore) {
          newsPageCache.set(cacheKey, {
            ...(newsPage.content.length > 0 ? newsPage : mockNewsPage),
            content,
          });
          setArticles(content);
          setTotalPages(
            newsPage.content.length > 0 ? newsPage.totalPages : mockNewsPage.totalPages,
          );
        }
      } catch (caughtError) {
        if (!ignore) {
          const mockNewsPage = getMockNewsPage(selectedCategory, page);
          newsPageCache.set(cacheKey, mockNewsPage);
          setArticles(mockNewsPage.content);
          setTotalPages(mockNewsPage.totalPages);
          setError(
            caughtError instanceof Error ? caughtError.message : '뉴스 목록을 불러오지 못했습니다.',
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

    async function loadTodayNews() {
      try {
        const todayNewsPage = await getTodayNews();
        const content =
          todayNewsPage.content.length > 0 ? todayNewsPage.content : [getMockTodayNews('ALL')];

        if (!ignore) {
          todayNewsCache = content;
          setTodayNews(content);
        }
      } catch {
        if (!ignore) {
          todayNewsCache = [getMockTodayNews('ALL')];
          setTodayNews(todayNewsCache);
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

  function handleArticleSelect(article: NewsListItem) {
    if (!article.id) {
      return;
    }

    navigate(`/news/${article.id}`);
  }

  return (
    <main className="news-home">
      <div className="news-title-row">
        <h1>오늘의 뉴스</h1>
      </div>

      <TodayNewsBanner articles={todayNews} onSelect={handleArticleSelect} />

      <CategoryTabs
        categories={NEWS_CATEGORIES}
        selectedCategory={selectedCategory}
        onChange={handleCategoryChange}
      />

      <section className="section-header" aria-live="polite">
        <div>
          <p>{selectedCategoryLabel === '전체' ? '최신' : selectedCategoryLabel} 뉴스</p>
          <strong>{loading ? '불러오는 중' : `${articles.length}건`}</strong>
        </div>
        <button type="button" className="sort-button">
          최신순
        </button>
      </section>

      <span className="sr-only" role="status">
        {error ? `뉴스 목록 API 응답을 받지 못했습니다. ${error}` : ''}
      </span>

      {loading && articles.length === 0 ? <NewsListSkeleton /> : null}

      {!loading && !error && articles.length === 0 ? (
        <section className="state-panel">
          <strong>표시할 뉴스가 없습니다.</strong>
          <p>다른 카테고리를 선택하거나 잠시 후 다시 확인해 주세요.</p>
        </section>
      ) : null}

      {articles.length > 0 ? (
        <section className="article-list" aria-label="뉴스 목록">
          {articles.map((article) => (
            <ArticleCard article={article} key={article.id} />
          ))}
        </section>
      ) : null}

      {articles.length > 0 && totalPages > 1 ? (
        <footer className="pagination" aria-label="뉴스 페이지네이션">
          <button
            type="button"
            className="pagination-arrow"
            aria-label="이전 페이지"
            onClick={() => handlePageChange(Math.max(page - 1, 1))}
            disabled={loading || page === 1}
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
            aria-label="다음 페이지"
            onClick={() => handlePageChange(Math.min(page + 1, totalPages))}
            disabled={loading || page === totalPages}
          >
            &gt;
          </button>
        </footer>
      ) : null}
    </main>
  );
}
