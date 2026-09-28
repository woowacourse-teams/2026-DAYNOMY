import { useEffect, useRef, useState, type PointerEvent } from 'react';
import defaultNewsImage from '../../../../assets/default-news-real-estate.webp';
import { getNewsImage } from '../newsImage';
import type { NewsListItem } from '../types';

type TodayNewsBannerProps = {
  articles: NewsListItem[];
  loading?: boolean;
};

const AUTO_ADVANCE_MS = 3000;
const ISSUE_DRAG_THRESHOLD = 8;

function getMastheadDate(value?: string | null) {
  const date = value ? new Date(value) : new Date();
  const validDate = Number.isNaN(date.getTime()) ? new Date() : date;

  return {
    month: new Intl.DateTimeFormat('en-US', { month: 'short' }).format(validDate).toUpperCase(),
    day: new Intl.DateTimeFormat('en-US', { day: '2-digit' }).format(validDate),
    weekday: new Intl.DateTimeFormat('en-US', { weekday: 'long' }).format(validDate).toUpperCase(),
  };
}

function DateMasthead({ value }: { value?: string | null }) {
  const mastheadDate = getMastheadDate(value);

  return (
    <div className="date-masthead" aria-label={`${mastheadDate.month} ${mastheadDate.day}`}>
      <span>{mastheadDate.month}</span>
      <strong>{mastheadDate.day}</strong>
      <span>{mastheadDate.weekday}</span>
      <small>TODAY&apos;S ISSUE</small>
    </div>
  );
}

export function TodayNewsBanner({ articles, loading = false }: TodayNewsBannerProps) {
  const [activeIndex, setActiveIndex] = useState(0);
  const [isPaused, setIsPaused] = useState(false);
  const [prefersReducedMotion, setPrefersReducedMotion] = useState(false);
  const [isIssueDragging, setIsIssueDragging] = useState(false);
  const issueViewportRef = useRef<HTMLDivElement>(null);
  const issueDragRef = useRef<{
    startY: number;
    startScrollTop: number;
    moved: boolean;
  } | null>(null);

  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) {
      return;
    }

    const mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
    const handlePreferenceChange = () => setPrefersReducedMotion(mediaQuery.matches);

    handlePreferenceChange();
    mediaQuery.addEventListener?.('change', handlePreferenceChange);

    return () => mediaQuery.removeEventListener?.('change', handlePreferenceChange);
  }, []);

  useEffect(() => {
    if (articles.length <= 1 || isPaused || prefersReducedMotion) {
      return;
    }

    const timerId = window.setInterval(() => {
      setActiveIndex((currentIndex) => (currentIndex + 1) % articles.length);
    }, AUTO_ADVANCE_MS);

    return () => window.clearInterval(timerId);
  }, [articles.length, isPaused, prefersReducedMotion]);

  const currentIndex = articles.length > 0 ? Math.min(activeIndex, articles.length - 1) : 0;
  const activeArticle = articles[currentIndex];
  const mastheadValue = activeArticle?.publishedAt ?? articles[0]?.publishedAt;
  const visibleIssueStartIndex = Math.min(
    Math.max(currentIndex - 1, 0),
    Math.max(articles.length - 4, 0),
  );

  useEffect(() => {
    const viewport = issueViewportRef.current;
    const issueRows = viewport
      ? Array.from(viewport.querySelectorAll<HTMLElement>('[data-issue-row]'))
      : [];

    if (!viewport || issueRows.length === 0) {
      return;
    }

    const targetTop = issueRows
      .slice(0, visibleIssueStartIndex)
      .reduce((totalHeight, issueRow) => totalHeight + issueRow.offsetHeight, 0);

    viewport.scrollTo({
      top: targetTop,
      behavior: prefersReducedMotion ? 'auto' : 'smooth',
    });
  }, [prefersReducedMotion, visibleIssueStartIndex]);

  function handleIssuePointerDown(event: PointerEvent<HTMLDivElement>) {
    if (event.pointerType === 'mouse' && event.button !== 0) {
      return;
    }

    const viewport = event.currentTarget;

    issueDragRef.current = {
      startY: event.clientY,
      startScrollTop: viewport.scrollTop,
      moved: false,
    };
  }

  function handleIssuePointerMove(event: PointerEvent<HTMLDivElement>) {
    const drag = issueDragRef.current;

    if (!drag) {
      return;
    }

    const distance = event.clientY - drag.startY;

    if (Math.abs(distance) > ISSUE_DRAG_THRESHOLD) {
      drag.moved = true;
      setIsIssueDragging(true);
    }

    if (drag.moved) {
      event.currentTarget.scrollTop = drag.startScrollTop - distance;
    }
  }

  function handleIssuePointerEnd() {
    const drag = issueDragRef.current;

    if (!drag) {
      return;
    }

    issueDragRef.current = null;
    setIsIssueDragging(false);
  }

  function handleIssueSelect(index: number) {
    setActiveIndex(index);
    setIsPaused(true);
  }

  if (loading) {
    return (
      <section className="today-banner today-news-loading" aria-label="오늘의 이슈 불러오는 중">
        <DateMasthead />
        <div className="today-story-placeholder" aria-hidden="true">
          <div className="today-story-image skeleton-block" />
          <div className="today-story-copy">
            <div className="skeleton-line skeleton-line-meta" />
            <div className="skeleton-line skeleton-line-title" />
          </div>
        </div>
        <ol className="today-issue-list" aria-hidden="true">
          {Array.from({ length: 3 }).map((_, index) => (
            <li key={index}>
              <div className="skeleton-line skeleton-line-issue" />
            </li>
          ))}
        </ol>
      </section>
    );
  }

  if (articles.length === 0) {
    return (
      <section className="today-banner today-news-empty" aria-label="오늘의 이슈">
        <DateMasthead />
        <div className="today-empty-message">
          <strong>오늘의 이슈는 없습니다!</strong>
        </div>
      </section>
    );
  }

  return (
    <section
      className="today-banner"
      aria-label="오늘의 이슈"
      onMouseEnter={() => setIsPaused(true)}
      onMouseLeave={() => setIsPaused(false)}
      onFocus={() => setIsPaused(true)}
      onBlur={() => setIsPaused(false)}
    >
      <DateMasthead value={mastheadValue} />

      <a className="today-story" href={`/news/${activeArticle.id}`}>
        <div className="today-story-image banner-visual">
          <img
            src={getNewsImage(activeArticle)}
            alt=""
            draggable={false}
            onError={(event) => {
              if (event.currentTarget.getAttribute('src') !== defaultNewsImage) {
                event.currentTarget.src = defaultNewsImage;
              }
            }}
          />
        </div>
        <div className="today-story-copy">
          <h2>{activeArticle.title}</h2>
        </div>
      </a>

      <div className="today-issue-list" role="list" aria-label="오늘의 이슈 목록">
        <div
          className={isIssueDragging ? 'today-issue-viewport is-dragging' : 'today-issue-viewport'}
          ref={issueViewportRef}
          onPointerDown={handleIssuePointerDown}
          onPointerMove={handleIssuePointerMove}
          onPointerUp={handleIssuePointerEnd}
          onPointerCancel={handleIssuePointerEnd}
        >
          <div className="today-issue-track">
            {articles.map((article, articleIndex) => (
              <div className="today-issue-row" data-issue-row role="listitem" key={article.id}>
                <button
                  type="button"
                  className={
                    articleIndex === currentIndex
                      ? 'today-issue-button active'
                      : 'today-issue-button'
                  }
                  aria-label={article.title}
                  aria-pressed={articleIndex === currentIndex}
                  onClick={() => handleIssueSelect(articleIndex)}
                >
                  <span className="issue-number">{String(articleIndex + 1).padStart(2, '0')}</span>
                  <span>{article.title}</span>
                </button>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
