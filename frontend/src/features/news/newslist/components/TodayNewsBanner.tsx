import { useEffect, useRef, useState } from 'react';
import type { KeyboardEvent, PointerEvent } from 'react';
import defaultNewsImage from '../../../../assets/default-news-real-estate.webp';
import { getCategoryLabel } from '../constants';
import type { NewsListItem } from '../types';
import { formatDate } from '../utils';

type TodayNewsBannerProps = {
  articles: NewsListItem[];
  onSelect: (article: NewsListItem) => void;
};

const SWIPE_THRESHOLD = 48;
const AUTO_ADVANCE_MS = 3000;

function formatBannerDate(value?: string | null) {
  if (!value) {
    return '';
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');

  return `${year}.${month}.${day}`;
}

export function TodayNewsBanner({ articles, onSelect }: TodayNewsBannerProps) {
  const [activeIndex, setActiveIndex] = useState(0);
  const [isPaused, setIsPaused] = useState(false);
  const [prefersReducedMotion, setPrefersReducedMotion] = useState(false);
  const pointerStartX = useRef<number | null>(null);

  useEffect(() => {
    if (activeIndex >= articles.length) {
      setActiveIndex(0);
    }
  }, [activeIndex, articles.length]);

  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) {
      return;
    }

    const mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
    const handlePreferenceChange = () => setPrefersReducedMotion(mediaQuery.matches);

    handlePreferenceChange();
    mediaQuery.addEventListener?.('change', handlePreferenceChange);

    return () => {
      mediaQuery.removeEventListener?.('change', handlePreferenceChange);
    };
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

  function moveSlide(direction: 1 | -1) {
    setActiveIndex((currentIndex) => {
      if (articles.length === 0) {
        return 0;
      }

      return (currentIndex + direction + articles.length) % articles.length;
    });
  }

  function handlePointerDown(event: PointerEvent<HTMLElement>) {
    pointerStartX.current = event.clientX;
    event.currentTarget.setPointerCapture(event.pointerId);
  }

  function handlePointerUp(event: PointerEvent<HTMLElement>) {
    const startX = pointerStartX.current;
    pointerStartX.current = null;

    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }

    if (startX === null) {
      return;
    }

    const distance = event.clientX - startX;

    if (Math.abs(distance) >= SWIPE_THRESHOLD) {
      moveSlide(distance > 0 ? -1 : 1);
      return;
    }

    const activeArticle = articles[activeIndex];

    if (activeArticle) {
      onSelect(activeArticle);
    }
  }

  function handleKeyDown(event: KeyboardEvent<HTMLElement>) {
    if (event.key === 'ArrowLeft') {
      moveSlide(-1);
      return;
    }

    if (event.key === 'ArrowRight') {
      moveSlide(1);
      return;
    }

    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();

      const activeArticle = articles[activeIndex];

      if (activeArticle) {
        onSelect(activeArticle);
      }
    }
  }

  if (articles.length === 0) {
    return null;
  }

  return (
    <>
      <section
        className="today-banner"
        aria-label="오늘의 뉴스"
        role="button"
        tabIndex={0}
        onMouseEnter={() => setIsPaused(true)}
        onMouseLeave={() => setIsPaused(false)}
        onFocus={() => setIsPaused(true)}
        onBlur={() => setIsPaused(false)}
        onKeyDown={handleKeyDown}
        onPointerDown={handlePointerDown}
        onPointerUp={handlePointerUp}
        onPointerCancel={() => {
          pointerStartX.current = null;
        }}
      >
        <div className="banner-track" style={{ transform: `translateX(-${activeIndex * 100}%)` }}>
          {articles.map((article) => (
            <article className="banner-slide" key={article.id}>
              <div className="banner-content">
                <div className="article-meta">
                  <span>{getCategoryLabel(article.category)}</span>
                  <time dateTime={article.publishedAt ?? undefined}>
                    {formatDate(article.publishedAt)}
                  </time>
                </div>
                <h2>{article.title}</h2>
                <p>{article.description}</p>
                <time className="banner-date" dateTime={article.publishedAt ?? undefined}>
                  {formatBannerDate(article.publishedAt)}
                </time>
              </div>
              <div className="banner-visual" aria-hidden="true">
                <img src={article.imageUrl ?? defaultNewsImage} alt="" draggable={false} />
              </div>
            </article>
          ))}
        </div>
      </section>

      <div className="carousel-dots" aria-label="오늘의 뉴스 배너">
        {articles.map((article, index) => (
          <button
            type="button"
            key={article.id}
            className={index === activeIndex ? 'active' : undefined}
            aria-label={`${index + 1}번째 배너 보기`}
            aria-current={index === activeIndex ? 'true' : undefined}
            onClick={() => setActiveIndex(index)}
          />
        ))}
      </div>
    </>
  );
}
