import { useEffect, useState } from 'react';
import { Header } from '../../../components/Header.tsx';
import defaultNewsImage from '../../../assets/default-news-real-estate.png';
import { getCategoryLabel } from '../newslist/types.ts';
import { formatDate } from '../newslist/utils.ts';
import { getNewsDetail } from './api.ts';
import { KeywordText } from './components/KeywordText.tsx';
import { MarketAnalysis } from './components/MarketAnalysis.tsx';
import type { NewsDetailPayload } from './types.ts';
import './newsDetail.css';
import { trackEvent } from '../../../analytics';
import { useAuth } from '../../../hooks/useLoginStatus.ts';
import { PortfolioAnalysis } from '../../portfolio/components/PortfolioAnalysis.tsx';
import { PortfolioLoginPrompt } from '../../portfolio/components/PortfolioLoginPrompt.tsx';

function getNewsIdFromUrl() {
  return window.location.pathname.match(/^\/news\/([^/]+)$/)?.[1] ?? '1';
}

function getContentParagraphs(content: string | string[]) {
  return Array.isArray(content) ? content : content.split('\n').filter(Boolean);
}

export function NewsDetailPage() {
  const newsId = getNewsIdFromUrl();
  const { isLoggedIn, loading: isAuthLoading } = useAuth();
  const [payload, setPayload] = useState<NewsDetailPayload>();
  const [error, setError] = useState('');
  const goBack = () => {
    window.location.href = '/';
  };

  useEffect(() => {
    trackEvent('view_news_detail', { news_id: newsId });
    setError('');
    getNewsDetail(newsId)
      .then(setPayload)
      .catch(() => setError('뉴스를 불러오지 못했습니다.'));
  }, [newsId]);

  if (error) {
    return (
      <main className="news-page">
        <Header />
        <p className="loading">{error}</p>
      </main>
    );
  }

  if (!payload) {
    return (
      <main className="news-page">
        <Header />
        <p className="loading">뉴스를 불러오는 중입니다.</p>
      </main>
    );
  }

  const { news, keywords, marketAnalysis } = payload;
  const imageUrl = news.imageUrl ?? defaultNewsImage;
  const sourceUrl = news.sourceUrl ?? news.originalUrl;

  return (
    <main className="news-page">
      <Header />

      <article className="news-detail">
        <button
          className="back-button"
          type="button"
          onClick={goBack}
          aria-label="전 페이지로 돌아가기"
        >
          <svg aria-hidden="true" className="back-icon" fill="none" viewBox="0 0 24 24">
            <path d="M15 5 8 12l7 7M9 12h11" />
          </svg>
        </button>

        <div className="meta">
          <span className="category">{getCategoryLabel(news.category)}</span>
          <time dateTime={news.publishedAt}>{formatDate(news.publishedAt)}</time>
          {news.source ? <span>{news.source}</span> : null}
          {sourceUrl ? (
            <a className="source-link" href={sourceUrl} target="_blank" rel="noreferrer">
              원본 보기
            </a>
          ) : null}
        </div>

        <h1>{news.title}</h1>

        <img className="news-image" src={imageUrl} alt="" />

        <section className="section">
          <h2>핵심 요약</h2>
          <p className="summary">{news.description}</p>
        </section>

        <section className="section">
          <h2>AI 본문</h2>
          <div className="body-copy">
            {getContentParagraphs(news.content).map((paragraph) => (
              <p key={paragraph}>
                <KeywordText text={paragraph} keywords={keywords} />
              </p>
            ))}
          </div>
        </section>

        {marketAnalysis ? <MarketAnalysis marketAnalysis={marketAnalysis} /> : null}

        {!isAuthLoading && isLoggedIn ? <PortfolioAnalysis newsId={newsId} /> : null}
        {!isAuthLoading && !isLoggedIn ? <PortfolioLoginPrompt /> : null}
      </article>
    </main>
  );
}
