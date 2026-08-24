import { useEffect, useMemo, useState } from 'react';
import { Header } from '../../../components/Header';
import { getNews, getTodayNews } from './api';
import { ArticleCard } from './components/ArticleCard';
import { CategoryTabs } from './components/CategoryTabs';
import { NewsListSkeleton } from './components/NewsListSkeleton';
import { TodayNewsBanner } from './components/TodayNewsBanner';
import { getEmptyTodayNews, NEWS_CATEGORIES } from './constants';
import type { NewsCategory, NewsListItemResponse } from './types';
import './newsList.css';
import { trackEvent } from '../../../analytics';

export function NewsListPage() {
  const [selectedCategory, setSelectedCategory] = useState<NewsCategory>('ALL');
  const [articles, setArticles] = useState<NewsListItemResponse[]>([]);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [todayMainNews, setTodayMainNews] = useState<NewsListItemResponse>(() =>
    getEmptyTodayNews(),
  );
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const selectedCategoryLabel = useMemo(
    () => NEWS_CATEGORIES.find((category) => category.value === selectedCategory)?.label ?? '전체',
    [selectedCategory],
  );

  useEffect(() => {
    trackEvent('view_news_list', { category: selectedCategory });
  }, [selectedCategory]);

  useEffect(() => {
    let ignore = false;

    async function loadNews() {
      setLoading(true);
      setError(null);

      try {
        const newsPage = await getNews(selectedCategory, page);

        if (!ignore) {
          setArticles(newsPage.content);
          setTotalPages(newsPage.totalPages);
        }
      } catch (caughtError) {
        if (!ignore) {
          setArticles([]);
          setTotalPages(1);
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
        const todayNews = await getTodayNews();

        if (!ignore) {
          setTodayMainNews(todayNews);
        }
      } catch {
        if (!ignore) {
          setTodayMainNews(getEmptyTodayNews());
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
  }

  function handleArticleSelect(article: NewsListItemResponse) {
    if (!article.id) {
      return;
    }

    window.location.assign(`/news/${article.id}`);
  }

  return (
    <main className="news-home">
      <Header />

      <div className="news-title-row">
        <h1>오늘의 뉴스</h1>
        <button type="button" className="sort-button">
          최신순
        </button>
      </div>

      <TodayNewsBanner article={todayMainNews} onSelect={handleArticleSelect} />

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
        <span>
          {page} / {totalPages}
        </span>
      </section>

      <span className="sr-only" role="status">
        {error ? `뉴스 목록 API 응답을 받지 못했습니다. ${error}` : ''}
      </span>

      {loading ? <NewsListSkeleton /> : null}

      {!loading && error ? (
        <section className="state-panel" role="alert">
          <strong>뉴스 목록을 불러오지 못했습니다.</strong>
          <p>{error}</p>
        </section>
      ) : null}

      {!loading && !error && articles.length === 0 ? (
        <section className="state-panel">
          <strong>표시할 뉴스가 없습니다.</strong>
          <p>다른 카테고리를 선택하거나 잠시 후 다시 확인해 주세요.</p>
        </section>
      ) : null}

      {!loading && articles.length > 0 ? (
        <section className="article-list" aria-label="뉴스 목록">
          {articles.map((article) => (
            <ArticleCard article={article} key={article.id} />
          ))}
        </section>
      ) : null}

      {articles.length > 0 && totalPages > 1 ? (
        <footer className="pagination">
          {Array.from({ length: totalPages }).map((_, index) => (
            <button
              type="button"
              key={index}
              className={page === index + 1 ? 'active' : undefined}
              onClick={() => setPage(index + 1)}
              disabled={loading}
            >
              {index + 1}
            </button>
          ))}
        </footer>
      ) : null}
    </main>
  );
}
