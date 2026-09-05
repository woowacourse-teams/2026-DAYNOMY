import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import defaultNewsImage from '../../../assets/default-news-real-estate.webp';
import { getCategoryLabel } from '../newslist/types.ts';
import { getNewsDetail } from './api.ts';
import { KeywordText } from './components/KeywordText.tsx';
import { PortfolioAnalysis } from '../../portfolio/components/PortfolioAnalysis.tsx';
import { getMockNewsDetail } from './mock.ts';
import type { MarketAnalysisResponse, NewsDetailPayload } from './types.ts';
import './newsDetail.css';
import { trackEvent } from '../../../analytics';
import { useAuth } from '../../../hooks/useLoginStatus.ts';

function getNewsIdFromUrl() {
  return window.location.pathname.match(/^\/news\/([^/]+)$/)?.[1] ?? '1';
}

function getContentParagraphs(content: string | string[]) {
  return Array.isArray(content) ? content : content.split('\n').filter(Boolean);
}

function getSummaryItems(description: string | null) {
  return (description ?? '')
    .split(/\r?\n|(?<=[.!?。！？])\s+/)
    .map((item) => item.trim())
    .filter(Boolean)
    .slice(0, 3);
}

function getMarketSummaryItems(summary: string) {
  return summary
    .split(/\r?\n|(?<=[.!?。！？])\s+/)
    .map((item) => item.trim())
    .filter(Boolean);
}

const NEWS_SOURCE_LABELS: Record<string, string> = {
  DART: 'DART',
  KOSIS: '국가통계포털',
  BOK: '한국은행',
};

function getNewsSourceLabel(source: string) {
  return NEWS_SOURCE_LABELS[source] ?? source;
}

function formatDetailDate(value?: string) {
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

function DetailAnalysisSections({ marketAnalysis }: { marketAnalysis?: MarketAnalysisResponse }) {
  const summary = marketAnalysis?.summary.trim();

  if (!summary) {
    return null;
  }

  return (
    <section className="detail-market" aria-labelledby="detail-market-title">
      <h2 id="detail-market-title">시장 분석</h2>
      <ul className="market-summary-card">
        {getMarketSummaryItems(summary).map((item, index) => (
          <li key={`${item}-${index}`}>{item}</li>
        ))}
      </ul>
    </section>
  );
}

export function NewsDetailPage() {
  const newsId = getNewsIdFromUrl();
  const navigate = useNavigate();
  const { isLoggedIn } = useAuth();
  const [payload, setPayload] = useState<NewsDetailPayload>();
  const [error, setError] = useState('');
  const goBack = () => {
    navigate('/');
  };

  useEffect(() => {
    trackEvent('view_news_detail', { news_id: newsId });
    setError('');
    getNewsDetail(newsId)
      .then(setPayload)
      .catch(() => setPayload(getMockNewsDetail(newsId)));
  }, [newsId]);

  if (error) {
    return (
      <main className="news-page">
        <p className="loading">{error}</p>
      </main>
    );
  }

  if (!payload) {
    return (
      <main className="news-page">
        <p className="loading">뉴스를 불러오는 중입니다.</p>
      </main>
    );
  }

  const { news, keywords, marketAnalysis } = payload;
  const imageUrl = news.imageUrl ?? defaultNewsImage;

  return (
    <main className="news-page">
      <article className="news-detail">
        <button
          className="detail-close-button"
          type="button"
          onClick={goBack}
          aria-label="전 페이지로 돌아가기"
        >
          ×
        </button>

        <div className="detail-meta">
          <span className="detail-category">{getCategoryLabel(news.category)}</span>
          {news.source && (
            <span className="detail-source">
              출처:{' '}
              {news.sourceUrl ? (
                <a
                  className="source-link"
                  href={news.sourceUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  {getNewsSourceLabel(news.source)}
                </a>
              ) : (
                getNewsSourceLabel(news.source)
              )}
            </span>
          )}
        </div>

        <h1>{news.title}</h1>

        <time className="detail-date" dateTime={news.publishedAt}>
          {formatDetailDate(news.publishedAt)}
        </time>

        <img className="news-image" src={imageUrl} alt="" />

        <section className="section summary-section">
          <h2>
            핵심 요약 <span aria-hidden="true">💡</span>
          </h2>
          <div className="summary">
            <ul>
              {getSummaryItems(news.description).map((item, index) => (
                <li key={`${item}-${index}`}>{item}</li>
              ))}
            </ul>
          </div>
        </section>

        <section className="body-section" aria-label="뉴스 본문">
          <div className="body-copy">
            {getContentParagraphs(news.content).map((paragraph) => (
              <p key={paragraph}>
                <KeywordText text={paragraph} keywords={keywords} />
              </p>
            ))}
          </div>
        </section>

        <div className="analysis-area">
          <div className="analysis-content">
            <DetailAnalysisSections marketAnalysis={marketAnalysis} />
            {isLoggedIn ? <PortfolioAnalysis newsId={newsId} /> : null}
          </div>
        </div>
      </article>
    </main>
  );
}
