import { trackEvent } from '../../../analytics';
import { useContentImpression, useContentView } from './analytics';
import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { getNewsImage } from '../newslist/newsImage';
import type { NewsListItem } from '../newslist/types';
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
  const [index, setIndex] = useState(0);
  const [focused, setFocused] = useState(false);
  const articles = news.filter((item) => newsDate(item.publishedAt) === today);
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
        <h2 id="issue-today-heading">오늘의 이슈</h2>
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
          <p>오늘 발행된 데이노미 이슈가 없습니다.</p>
          <Link
            onClick={() =>
              trackEvent('click_content_more', {
                surface: 'today_story_empty',
                destination: 'news_contents',
              })
            }
            to="/news/contents"
          >
            전체 콘텐츠에서 지난 이슈 보기 ↗
          </Link>
        </div>
      ) : (
        <>
          <div className="issue-story-carousel" aria-label="데이노미 기사 목록">
            {articles.slice(activeIndex, activeIndex + 1).map((article) => (
              <Link
                ref={cardRef}
                onClick={() => trackEvent('click_content', analytics)}
                className="issue-story-card"
                key={article.id}
                to={`/news/${article.id}`}
              >
                <img
                  src={getNewsImage(article)}
                  alt=""
                  onError={(event) => {
                    const fallback = getNewsImage({ ...article, imageUrl: null });
                    if (event.currentTarget.getAttribute('src') !== fallback)
                      event.currentTarget.src = fallback;
                  }}
                />
                <h3>{article.title}</h3>
                <span>
                  기사 읽기 <span aria-hidden="true">↗</span>
                </span>
              </Link>
            ))}
          </div>
          {articles.length > 1 && (
            <div className="issue-story-paging">
              <span>
                {activeIndex + 1} / {articles.length}
              </span>
            </div>
          )}
        </>
      )}
    </section>
  );
}
