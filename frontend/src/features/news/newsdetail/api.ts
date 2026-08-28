import type {
  Asset,
  AssetImpactResponse,
  KeywordsResponse,
  MarketAnalysisResponse,
  NewsDetailPayload,
  NewsDetailResponse,
} from './types.ts';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

type BackendAssetImpactResponse = Omit<AssetImpactResponse, 'asset'> & {
  category: Asset;
};

type BackendMarketAnalysisResponse = Omit<MarketAnalysisResponse, 'assets'> & {
  assets: BackendAssetImpactResponse[];
};

async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`);

  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText}`);
  }

  return response.json() as Promise<T>;
}

function normalizeMarketAnalysis(response: BackendMarketAnalysisResponse): MarketAnalysisResponse {
  return {
    cause: response.cause,
    importance: response.importance,
    assets: response.assets.map(({ category, ...impact }) => ({
      ...impact,
      asset: category,
    })),
    scenarios: response.scenarios,
  };
}

export async function getNewsDetail(newsId: string): Promise<NewsDetailPayload> {
  const news = await getJson<NewsDetailResponse>(`/api/news/${newsId}`);
  const [marketAnalysis, keywords] = await Promise.allSettled([
    getJson<BackendMarketAnalysisResponse>(`/api/news/${newsId}/market-analysis`),
    getJson<KeywordsResponse>(`/api/news/${newsId}/keywords`),
  ]);

  return {
    news,
    keywords: keywords.status === 'fulfilled' ? keywords.value.keywords : [],
    marketAnalysis:
      marketAnalysis.status === 'fulfilled'
        ? normalizeMarketAnalysis(marketAnalysis.value)
        : undefined,
  };
}
