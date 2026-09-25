import type {
  KeywordsResponse,
  MarketAnalysisResponse,
  MarketAnalysisState,
  NewsDetailPayload,
  NewsDetailResponse,
} from './types.ts';
import { isCategory } from '../newslist/types.ts';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

class ApiError extends Error {
  readonly status: number;

  constructor(status: number, statusText: string) {
    super(`${status} ${statusText}`);
    this.status = status;
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isNewsDetailResponse(value: unknown): value is NewsDetailResponse {
  return (
    isRecord(value) &&
    Number.isInteger(value.id) &&
    typeof value.title === 'string' &&
    isCategory(value.category) &&
    typeof value.publishedAt === 'string' &&
    (typeof value.content === 'string' ||
      (Array.isArray(value.content) && value.content.every((item) => typeof item === 'string'))) &&
    (value.imageUrl === undefined ||
      value.imageUrl === null ||
      typeof value.imageUrl === 'string') &&
    Array.isArray(value.sources) &&
    value.sources.every(
      (source: unknown) =>
        isRecord(source) && typeof source.name === 'string' && typeof source.url === 'string',
    )
  );
}

function isKeywordsResponse(value: unknown): value is KeywordsResponse {
  return (
    isRecord(value) &&
    Array.isArray(value.keywords) &&
    value.keywords.every(
      (keyword: unknown) =>
        isRecord(keyword) &&
        typeof keyword.keyword === 'string' &&
        (keyword.category === 'PERSON' ||
          keyword.category === 'POLICY' ||
          keyword.category === 'EVENT' ||
          keyword.category === 'TERM' ||
          keyword.category === 'TREND') &&
        Array.isArray(keyword.points) &&
        keyword.points.every((point: unknown) => typeof point === 'string'),
    )
  );
}

function isMarketAnalysisResponse(value: unknown): value is MarketAnalysisResponse {
  return isRecord(value) && typeof value.summary === 'string';
}

async function getJson<T>(path: string, isValid: (value: unknown) => value is T): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`);

  if (!response.ok) {
    throw new ApiError(response.status, response.statusText);
  }

  const data: unknown = await response.json();
  if (!isValid(data)) {
    throw new Error('뉴스 API 응답 형식이 올바르지 않습니다.');
  }

  return data;
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
  const news = await getJson(`/api/news/${newsId}`, isNewsDetailResponse);
  const [marketAnalysis, keywords] = await Promise.allSettled([
    getJson(`/api/news/${newsId}/market-analysis`, isMarketAnalysisResponse),
    getJson(`/api/news/${newsId}/keywords`, isKeywordsResponse),
  ]);

  return {
    news,
    keywords: keywords.status === 'fulfilled' ? keywords.value.keywords : [],
    marketAnalysis: getMarketAnalysisState(marketAnalysis),
  };
}
