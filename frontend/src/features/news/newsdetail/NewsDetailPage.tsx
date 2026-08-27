import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import defaultNewsImage from '../../../assets/default-news-real-estate.png';
import { getCategoryLabel } from '../newslist/types.ts';
import { getNewsDetail } from './api.ts';
import { KeywordText } from './components/KeywordText.tsx';
import { getMockNewsDetail } from './mock.ts';
import type {
  Asset,
  AssetImpactResponse,
  ImpactDirection,
  ImpactLevel,
  MarketAnalysisResponse,
  NewsDetailPayload,
} from './types.ts';
import './newsDetail.css';
import { trackEvent } from '../../../analytics';
import { useAuth } from '../../../hooks/useLoginStatus.ts';
import { getApiUrl } from '../../pages/api.ts';

function getNewsIdFromUrl() {
  return window.location.pathname.match(/^\/news\/([^/]+)$/)?.[1] ?? '1';
}

function getContentParagraphs(content: string | string[]) {
  return Array.isArray(content) ? content : content.split('\n').filter(Boolean);
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

function GoogleIcon() {
  return (
    <svg className="detail-google-icon" viewBox="0 0 24 24" aria-hidden="true">
      <path
        fill="#4285F4"
        d="M21.6 12.227c0-.709-.064-1.391-.182-2.045H12v3.868h5.382a4.6 4.6 0 0 1-1.995 3.018v2.509h3.231c1.891-1.741 2.982-4.304 2.982-7.35Z"
      />
      <path
        fill="#34A853"
        d="M12 22c2.7 0 4.964-.895 6.618-2.423l-3.231-2.509c-.895.6-2.041.955-3.387.955-2.605 0-4.81-1.76-5.595-4.123H3.064v2.591A9.997 9.997 0 0 0 12 22Z"
      />
      <path
        fill="#FBBC05"
        d="M6.405 13.9A6.01 6.01 0 0 1 6.091 12c0-.659.114-1.3.314-1.9V7.509H3.064A9.997 9.997 0 0 0 2 12c0 1.614.386 3.141 1.064 4.491L6.405 13.9Z"
      />
      <path
        fill="#EA4335"
        d="M12 5.977c1.468 0 2.786.505 3.823 1.496l2.868-2.868C16.959 2.991 14.695 2 12 2a9.997 9.997 0 0 0-8.936 5.509L6.405 10.1C7.19 7.737 9.395 5.977 12 5.977Z"
      />
    </svg>
  );
}

const fallbackAssetImpacts: AssetImpactResponse[] = [
  {
    asset: 'FOREIGN_EXCHANGE',
    direction: 'NEGATIVE',
    impactLevel: 'HIGH',
    reason: '달러 강세가 이어지면 원화 약세 압력이 커져 수입 결제 부담이 커질 수 있어요.',
  },
  {
    asset: 'BOND',
    direction: 'NEGATIVE',
    impactLevel: 'MEDIUM',
    reason: '환율과 물가 부담이 남아 있으면 기준금리 인하 기대가 늦춰질 수 있어요.',
  },
  {
    asset: 'STOCK',
    direction: 'NEGATIVE',
    impactLevel: 'MEDIUM',
    reason: '수입 원가 부담이 큰 업종은 마진 압박을 받을 수 있어요.',
  },
];

const portfolioSummaries = [
  {
    rank: 'TOP 1',
    title: '삼성전자',
    direction: 'POSITIVE',
    impactLevel: 'HIGH',
    body: '환율 상승 구간에서 수출주로 꼽히지만 원재료 가격 부담도 함께 확인해야 해요.',
  },
  {
    rank: 'TOP 2',
    title: '미국 달러',
    direction: 'POSITIVE',
    impactLevel: 'MEDIUM',
    body: '원화 약세가 이어지면 보유 외화 가치가 방어적으로 움직일 수 있어요.',
  },
  {
    rank: 'TOP 3',
    title: '국내 채권형 ETF',
    direction: 'NEGATIVE',
    impactLevel: 'MEDIUM',
    body: '금리 인하 기대가 늦춰지면 단기 가격 흐름이 제한될 수 있어요.',
  },
] as const;

const directionLabel: Record<ImpactDirection, string> = {
  POSITIVE: '긍정',
  NEGATIVE: '부정',
  NEUTRAL: '중립',
};

const levelLabel: Record<ImpactLevel, string> = {
  HIGH: '영향 높음',
  MEDIUM: '영향 중간',
  LOW: '영향 낮음',
};

function getImpactTone(direction: ImpactDirection, impactLevel: ImpactLevel) {
  if (direction === 'NEUTRAL') {
    return 'impact-tone-neutral';
  }

  return `impact-tone-${direction.toLowerCase()}-${impactLevel.toLowerCase()}`;
}

function getImpactLabel(direction: ImpactDirection, impactLevel: ImpactLevel) {
  return direction === 'NEUTRAL'
    ? '영향 없음'
    : `${directionLabel[direction]} · ${levelLabel[impactLevel]}`;
}

function getAssetLabel(asset: Asset) {
  return asset === 'MOCK' ? '기타' : getCategoryLabel(asset);
}

function DetailAnalysisSections({ marketAnalysis }: { marketAnalysis?: MarketAnalysisResponse }) {
  const assetImpacts = (
    marketAnalysis?.assets.length ? marketAnalysis.assets : fallbackAssetImpacts
  ).slice(0, 3);

  return (
    <>
      <section className="detail-market" aria-labelledby="detail-market-title">
        <h2 id="detail-market-title">시장 분석</h2>

        <section className="market-step">
          <h3>1. 발생 원인</h3>
          <ul>
            <li>글로벌 달러 강세가 이어지면서 원화 가치가 상대적으로 약해지고 있어요.</li>
            <li>
              에너지·원자재를 달러로 결제하는 수입 품목은 환율 상승의 영향을 먼저 받을 수 있어요.
            </li>
            <li>
              수입 원가 부담이 커지면 기업 비용과 소비자물가에 시차를 두고 반영될 가능성이 있어요.
            </li>
            <li>물가 부담이 다시 커지면 한국은행의 기준금리 인하 기대도 늦춰질 수 있어요.</li>
          </ul>
        </section>

        <section className="market-step">
          <h3>2. 이슈가 중요한 이유</h3>
          <p>
            환율은 단순히 외환시장 숫자에 그치지 않아요. 수입 물가, 기업 마진, 기준금리 기대, 외국인
            자금 흐름이 한 번에 연결되는 지표예요. 특히 수입 비중이 높은 업종은 비용 부담이 빠르게
            커질 수 있고, 반대로 수출 비중이 높은 기업은 환율 효과를 일부 기대할 수 있어요.
          </p>
        </section>

        <section className="market-step">
          <h3>3. 가장 영향 가능성이 높은 자산 TOP 3</h3>
          <div className="asset-impact-list">
            {assetImpacts.map((asset, index) => (
              <article className="asset-impact-card" key={asset.asset}>
                <div>
                  <strong>{`TOP ${index + 1}`}</strong>
                  <span
                    className={`impact-pill ${getImpactTone(asset.direction, asset.impactLevel)}`}
                  >
                    {getImpactLabel(asset.direction, asset.impactLevel)}
                  </span>
                </div>
                <h4>{getAssetLabel(asset.asset)}</h4>
                <p>{asset.reason}</p>
              </article>
            ))}
          </div>
        </section>

        <section className="market-step">
          <h3>4. 단기·중기·장기 시나리오</h3>
          <div className="scenario-stack">
            {(marketAnalysis?.scenarios ?? []).slice(0, 3).map((scenario, index) => (
              <article className="scenario-strip" key={scenario.timeHorizon}>
                <div>
                  <h4>
                    {index === 0
                      ? '단기 · 환율 변동성 확대'
                      : index === 1
                        ? '중기 · 금리 인하 기대 조절'
                        : '장기 · 업종별 실적 차별화'}
                  </h4>
                  <p>{`${scenario.prediction} ${scenario.reason}`}</p>
                </div>
                <strong>가능성 {scenario.probability}%</strong>
              </article>
            ))}
          </div>
        </section>
      </section>

      <section className="detail-portfolio" aria-labelledby="detail-portfolio-title">
        <h2 id="detail-portfolio-title">포트폴리오 분석</h2>
        <div className="portfolio-detail-list">
          {portfolioSummaries.map((asset) => (
            <article className="portfolio-detail-row" key={asset.rank}>
              <div>
                <strong>{asset.rank}</strong>
                <h3>{asset.title}</h3>
                <p>{asset.body}</p>
              </div>
              <span className={`impact-pill ${getImpactTone(asset.direction, asset.impactLevel)}`}>
                {getImpactLabel(asset.direction, asset.impactLevel)}
              </span>
            </article>
          ))}
        </div>
      </section>
    </>
  );
}

function AnalysisLockOverlay() {
  return (
    <div className="analysis-lock-overlay" role="dialog" aria-modal="false">
      <h2>
        <span>3초면 돼요!</span> 분석을 이어서 확인하세요
      </h2>
      <p>뉴스 뒤에 숨은 시장 흐름과 내 관심종목 영향을 함께 볼 수 있어요</p>
      <ul>
        <li>
          <span aria-hidden="true">📊</span>
          시장이 움직인 이유를 한눈에 정리해요
        </li>
        <li>
          <span aria-hidden="true">💜</span>내 관심종목에 생길 변화를 이어서 확인해요
        </li>
      </ul>
      <a href={getApiUrl('/api/auth/google')}>
        <GoogleIcon />
        Google로 시작하기
      </a>
    </div>
  );
}

export function NewsDetailPage() {
  const newsId = getNewsIdFromUrl();
  const navigate = useNavigate();
  const { isLoggedIn, loading: isAuthLoading } = useAuth();
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

        <span className="detail-category">{getCategoryLabel(news.category)}</span>

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
              <li>원·달러 환율이 최근 다시 오름세를 보이고 있어요</li>
              <li>환율 상승으로 에너지와 원자재 수입 가격 부담이 커지고 있어요</li>
              <li>수입 비용이 늘면서 관련 업종의 비용 부담도 함께 주목되고 있어요</li>
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

        {!isAuthLoading ? (
          <div className={isLoggedIn ? 'analysis-area' : 'analysis-area is-locked'}>
            <div className="analysis-content">
              <DetailAnalysisSections marketAnalysis={marketAnalysis} />
            </div>
            {!isLoggedIn ? <AnalysisLockOverlay /> : null}
          </div>
        ) : null}
      </article>
    </main>
  );
}
