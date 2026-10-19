import { trackEvent } from '../../../analytics';
import { useContentImpression, useContentView } from './analytics';
import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { getCategoryLabel, type NewsListItem } from '../newslist/types';
import { feedDate } from './feed';
import { newsDate } from './todayNews';

export function IssueStories({
  news,
  loading,
  error,
  onRetry,
}: {
  news: NewsListItem[];
  loading: boolean;
  error: string;
  onRetry: () => void;
}) {
  const today = newsDate(new Date().toISOString());
  const todayArticles = news.filter((item) => newsDate(item.publishedAt) === today);
  const showingRecent = !todayArticles.length;
  const articles = (showingRecent ? news : todayArticles).slice(0, 4);
  const [index, setIndex] = useState(0);
  const [focused, setFocused] = useState(false);
  const activeIndex = index % Math.max(articles.length, 1);

  const cardRef = useRef<HTMLAnchorElement>(null);
  const article = articles[activeIndex];
  const analytics = {
    surface: 'today_story',
    content_id: article ? `news-${article.id}` : '',
    content_type: 'news',
    source_type: 'DAYNOMY',
    position: activeIndex + 1,
  };
  useContentImpression(
    cardRef,
    analytics.content_id,
    'view_content',
    analytics,
    !loading && !error && !!article,
  );
  useContentView(!loading && !error && !articles.length, 'view_content_empty', {
    surface: 'today_story',
    reason: 'no_today_content',
  });

  useEffect(() => {
    if (focused || loading || error || articles.length < 2) return;
    const timer = window.setInterval(() => {
      if (!document.hidden) setIndex((value) => (value + 1) % articles.length);
    }, 3000);
    return () => window.clearInterval(timer);
  }, [focused, loading, error, articles.length]);

  return (
    <section
      className="issue-today"
      aria-labelledby="issue-today-heading"
      onFocusCapture={() => setFocused(true)}
      onBlurCapture={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget)) setFocused(false);
      }}
    >
      <div className="issue-section-heading">
        <h2 id="issue-today-heading">{showingRecent ? '최근 이슈' : '오늘의 이슈'}</h2>
      </div>
      {loading ? (
        <p className="issue-state" role="status">
          이슈를 불러오는 중입니다.
        </p>
      ) : error ? (
        <p className="issue-state" role="alert">
          {error} <button onClick={onRetry}>다시 시도</button>
        </p>
      ) : !articles.length ? (
        <div className="issue-story-empty">
          <p>표시할 데이노미 이슈가 없습니다.</p>
        </div>
      ) : (
        <>
          {showingRecent && (
            <p className="issue-story-note">오늘 발행된 이슈가 없어 최근 이슈를 보여드려요.</p>
          )}
          <div className="issue-story-carousel" aria-label="데이노미 기사 목록">
            {articles.slice(activeIndex, activeIndex + 1).map((article) => (
              <Link
                ref={cardRef}
                onClick={() => trackEvent('click_content', analytics)}
                className="issue-story-card"
                key={article.id}
                to={`/news/${article.id}`}
              >
                <span className="issue-story-image" aria-hidden="true">
                  <span>데이노미</span>
                  {article.imageUrl && (
                    <img
                      src={article.imageUrl}
                      alt=""
                      onError={(event) => {
                        event.currentTarget.hidden = true;
                      }}
                    />
                  )}
                </span>
                <h3>{article.title}</h3>
                <div className="issue-meta issue-story-meta">
                  {article.category !== 'ALL' && <span>{getCategoryLabel(article.category)}</span>}
                  <time dateTime={article.publishedAt ?? undefined}>
                    {feedDate(article.publishedAt, '발행')}
                  </time>
                </div>
              </Link>
            ))}
          </div>
        </>
      )}
    </section>
  );
}
