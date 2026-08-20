import { mockKeywords, mockMarketAnalysis, mockNews } from './mock.ts';
import { isCategory } from '../newslist/types.ts';
import type {
  KeywordsResponse,
  MarketAnalysisResponse,
  NewsDetailPayload,
  NewsDetailResponse,
  NewsDetailView,
} from './types.ts';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`);

  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText}`);
  }

  return response.json() as Promise<T>;
}

function normalizeNews(raw: Partial<NewsDetailResponse> & Record<string, unknown>): NewsDetailView {
  return {
    title: String(raw.title ?? mockNews.title),
    category: isCategory(raw.category) ? raw.category : mockNews.category,
    source: String(raw.source ?? mockNews.source),
    publishedAt: String(raw.publishedAt ?? raw.date ?? mockNews.publishedAt),
    originalUrl:
      typeof raw.originalUrl === 'string'
        ? raw.originalUrl
        : typeof raw.sourceUrl === 'string'
          ? raw.sourceUrl
          : mockNews.originalUrl,
    description: String(raw.description ?? mockNews.description),
    content: Array.isArray(raw.content)
      ? raw.content.map(String)
      : String(raw.content ?? raw.originalText ?? raw.description ?? '')
          .split('\n')
          .filter(Boolean),
    imageUrl: typeof raw.imageUrl === 'string' ? raw.imageUrl : undefined,
  };
}

export async function getNewsDetail(newsId: string): Promise<NewsDetailPayload> {
  const [news, marketAnalysis, keywords] = await Promise.allSettled([
    getJson<Partial<NewsDetailResponse> & Record<string, unknown>>(`/api/news/${newsId}`),
    getJson<MarketAnalysisResponse>(`/api/news/${newsId}/market-analysis`),
    getJson<KeywordsResponse>(`/api/news/${newsId}/keywords`),
  ]);

  return {
    news: news.status === 'fulfilled' ? normalizeNews(news.value) : mockNews,
    marketAnalysis:
      marketAnalysis.status === 'fulfilled' ? marketAnalysis.value : mockMarketAnalysis,
    keywords: keywords.status === 'fulfilled' ? keywords.value.keywords : mockKeywords,
  };
}
