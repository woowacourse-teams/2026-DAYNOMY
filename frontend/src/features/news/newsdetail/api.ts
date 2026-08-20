import { isCategory } from '../newslist/types.ts';
import type {
  Direction,
  MarketAnalysis,
  NewsDetail,
  NewsDetailPayload,
  NewsKeyword,
} from './types.ts';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

type NewsDetailResponse = {
  id: number;
  title: string;
  content: string;
  description: string;
  imageUrl: string | null;
  sourceUrl?: string | null;
  category: string;
  publishedAt: string;
};

type MarketAnalysisResponse = {
  cause: string;
  assets: AssetImpactResponse[];
  scenarios: ScenarioResponse[];
};

type AssetImpactResponse = {
  asset: string;
  direction: 'POSITIVE' | 'NEGATIVE';
  impactLevel: 'HIGH' | 'MEDIUM' | 'LOW';
  reason: string;
};

type ScenarioResponse = {
  timeHorizon: 'SHORT_TERM' | 'MID_TERM' | 'LONG_TERM';
  prediction: string;
  probability: number;
  reason: string;
};

type KeywordsResponse = {
  keywords: NewsKeyword[];
};

const assetLabel: Record<string, string> = {
  GOLD: '금',
  STOCK: '주식',
  BOND: '채권',
  REAL_ESTATE: '부동산',
  FOREIGN_EXCHANGE: '환율',
  VIRTUAL_ASSET: '가상자산',
  DEPOSIT_SAVINGS: '예적금',
  ETF: 'ETF',
  PENSION: '연금',
  MOCK: '기타',
};

const timeHorizonLabel: Record<ScenarioResponse['timeHorizon'], string> = {
  SHORT_TERM: '단기 시나리오',
  MID_TERM: '중기 시나리오',
  LONG_TERM: '장기 시나리오',
};

async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`);

  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText}`);
  }

  return response.json() as Promise<T>;
}

function normalizeNews(raw: NewsDetailResponse): NewsDetail {
  return {
    id: raw.id,
    title: raw.title,
    category: isCategory(raw.category) ? raw.category : 'STOCK',
    publishedAt: raw.publishedAt,
    description: raw.description,
    content: raw.content
      .split('\n')
      .filter(Boolean),
    imageUrl: raw.imageUrl ?? undefined,
    sourceUrl: raw.sourceUrl ?? undefined,
  };
}

function normalizeDirection(direction: AssetImpactResponse['direction']): Direction {
  return direction === 'POSITIVE' ? 'positive' : 'negative';
}

function normalizeMarketAnalysis(raw: MarketAnalysisResponse): MarketAnalysis {
  return {
    cause: raw.cause,
    impacts: raw.assets.map((impact) => ({
      asset: assetLabel[impact.asset] ?? impact.asset,
      direction: normalizeDirection(impact.direction),
      impactLevel: impact.impactLevel,
    })),
    scenarios: raw.scenarios.map((scenario) => ({
      title: timeHorizonLabel[scenario.timeHorizon],
      probability: scenario.probability,
      description: `${scenario.prediction} ${scenario.reason}`,
    })),
  };
}

export async function getNewsDetail(newsId: string): Promise<NewsDetailPayload> {
  const news = await getJson<NewsDetailResponse>(`/api/news/${newsId}`);
  const [marketAnalysis, keywords] = await Promise.allSettled([
    getJson<MarketAnalysisResponse>(`/api/news/${newsId}/market-analysis`),
    getJson<KeywordsResponse>(`/api/news/${newsId}/keywords`),
  ]);

  return {
    news: normalizeNews(news),
    keywords: keywords.status === 'fulfilled' ? keywords.value.keywords : [],
    marketAnalysis:
      marketAnalysis.status === 'fulfilled'
        ? normalizeMarketAnalysis(marketAnalysis.value)
        : undefined,
  };
}
