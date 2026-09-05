import type {
  KeywordsResponse,
  MarketAnalysisResponse,
  MarketAnalysisState,
  NewsDetailPayload,
  NewsDetailResponse,
} from './types.ts';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

class ApiError extends Error {
  readonly status: number;

  constructor(status: number, statusText: string) {
    super(`${status} ${statusText}`);
    this.status = status;
  }
}

async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`);

  if (!response.ok) {
    throw new ApiError(response.status, response.statusText);
  }

  return response.json() as Promise<T>;
}

function getMarketAnalysisState(
  result: PromiseSettledResult<MarketAnalysisResponse>,
): MarketAnalysisState {
  if (result.status === 'fulfilled') {
    return result.value.summary.trim()
      ? { status: 'success', data: result.value }
      : { status: 'empty' };
  }

  return result.reason instanceof ApiError && result.reason.status === 404
    ? { status: 'empty' }
    : { status: 'error' };
}

export async function getNewsDetail(newsId: string): Promise<NewsDetailPayload> {
  const news = await getJson<NewsDetailResponse>(`/api/news/${newsId}`);
  const [marketAnalysis, keywords] = await Promise.allSettled([
    getJson<MarketAnalysisResponse>(`/api/news/${newsId}/market-analysis`),
    getJson<KeywordsResponse>(`/api/news/${newsId}/keywords`),
  ]);

  return {
    news,
    keywords: keywords.status === 'fulfilled' ? keywords.value.keywords : [],
    marketAnalysis: getMarketAnalysisState(marketAnalysis),
  };
}
