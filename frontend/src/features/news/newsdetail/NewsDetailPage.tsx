import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import defaultNewsImage from '../../../assets/default-news-real-estate.webp';
import { getCategoryLabel } from '../newslist/types.ts';
import { getNewsDetail } from './api.ts';
import { KeywordText } from './components/KeywordText.tsx';
import { calculatePortfolio } from '../../portfolio/api.ts';
import { PortfolioAnalysis } from '../../portfolio/components/PortfolioAnalysis.tsx';
import { usePortfolioHoldings } from '../../portfolio/hooks/usePortfolioHoldings.ts';
import type { PortfolioAsset } from '../../portfolio/types.ts';
import type { MarketAnalysisState, NewsDetailPayload } from './types.ts';
import './newsDetail.css';
import { trackEvent } from '../../../analytics';

type PortfolioAssetsStatus = 'loading' | 'ready' | 'error';

function getNewsIdFromUrl() {
  return window.location.pathname.match(/^\/news\/([^/]+)$/)?.[1] ?? '1';
}

function getContentParagraphs(content: string | string[]) {
  return Array.isArray(content) ? content : content.split('\n').filter(Boolean);
}

function getMarketSummaryItems(summary: string) {
  return summary
    .split(/\r?\n|(?<=[.!?。！？])\s+/)
    .map((item) => item.trim())
    .filter(Boolean);
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

function toPortfolioAssets(
  holdings: Awaited<ReturnType<typeof calculatePortfolio>>['holdings'],
): PortfolioAsset[] {
  if (holdings.length === 0) return [];

  const assets = holdings.map(({ name, weight }) => ({ assetName: name, weight }));
  const totalWeight = assets.reduce((sum, asset) => sum + asset.weight, 0);
  const adjustmentIndex = assets.reduce(
    (largestIndex, asset, index) =>
      asset.weight > assets[largestIndex].weight ? index : largestIndex,
    0,
  );

  return assets.map((asset, index) =>
    index === adjustmentIndex
      ? { ...asset, weight: Number((asset.weight + 100 - totalWeight).toFixed(2)) }
      : asset,
  );
}

function DetailAnalysisSections({ marketAnalysis }: { marketAnalysis: MarketAnalysisState }) {
  return (
    <section className="detail-market" aria-labelledby="detail-market-title">
      <h2 id="detail-market-title">시장 분석</h2>
      {marketAnalysis.status === 'success' ? (
        <ul className="market-summary-card">
          {getMarketSummaryItems(marketAnalysis.data.summary).map((item, index) => (
            <li key={`${item}-${index}`}>{item}</li>
          ))}
        </ul>
      ) : (
        <p
          className="market-summary-card market-analysis-message"
          role={marketAnalysis.status === 'error' ? 'alert' : 'status'}
        >
          {marketAnalysis.status === 'empty'
            ? '아직 제공된 시장 분석이 없습니다.'
            : '시장 분석을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'}
        </p>
      )}
    </section>
  );
}

export function NewsDetailPage() {
  const newsId = getNewsIdFromUrl();
  const navigate = useNavigate();
  const { holdings } = usePortfolioHoldings();
  const [payload, setPayload] = useState<NewsDetailPayload>();
  const [error, setError] = useState('');
  const [portfolioAssets, setPortfolioAssets] = useState<PortfolioAsset[]>([]);
  const [portfolioAssetsStatus, setPortfolioAssetsStatus] = useState<PortfolioAssetsStatus>(
    holdings.length > 0 ? 'loading' : 'ready',
  );
  const goBack = () => {
    navigate('/');
  };

  useEffect(() => {
    let ignore = false;

    trackEvent('view_news_detail', { news_id: newsId });
    setPayload(undefined);
    setError('');
    getNewsDetail(newsId)
      .then((nextPayload) => {
        if (!ignore) {
          setPayload(nextPayload);
        }
      })
      .catch(() => {
        if (!ignore) {
          setPayload(undefined);
          setError('뉴스를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.');
        }
      });

    return () => {
      ignore = true;
    };
  }, [newsId]);

  useEffect(() => {
    if (holdings.length === 0) {
      setPortfolioAssets([]);
      setPortfolioAssetsStatus('ready');
      return;
    }

    const controller = new AbortController();
    setPortfolioAssets([]);
    setPortfolioAssetsStatus('loading');

    calculatePortfolio(holdings, controller.signal)
      .then((calculation) => {
        setPortfolioAssets(toPortfolioAssets(calculation.holdings));
        setPortfolioAssetsStatus('ready');
      })
      .catch(() => {
        if (controller.signal.aborted) return;
        setPortfolioAssets([]);
        setPortfolioAssetsStatus('error');
      });

    return () => {
      controller.abort();
    };
  }, [holdings]);

  if (error) {
    return (
      <main className="news-page">
        <p className="loading" role="alert">
          {error}
        </p>
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
          {news.sources.length > 0 ? (
            <div className="detail-sources">
              <span>출처:</span>
              <ul>
                {news.sources.map((source, index) => (
                  <li key={`${source.url}-${index}`}>
                    <a
                      className="source-link"
                      href={source.url}
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      {source.name}
                    </a>
                  </li>
                ))}
              </ul>
            </div>
          ) : null}
        </div>

        <h1>{news.title}</h1>

        <time className="detail-date" dateTime={news.publishedAt}>
          {formatDetailDate(news.publishedAt)}
        </time>

        <img className="news-image" src={imageUrl} alt="" />

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
            <PortfolioAnalysis
              newsId={newsId}
              assets={portfolioAssets}
              portfolioStatus={portfolioAssetsStatus}
            />
          </div>
        </div>
      </article>
    </main>
  );
}
