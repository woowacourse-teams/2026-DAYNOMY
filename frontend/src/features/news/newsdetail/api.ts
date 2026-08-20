import { mockImpacts, mockNews, mockRelatedIssues } from './mock.ts';
import { isCategory } from '../newslist/types.ts';
import type { Impact, NewsDetail, NewsDetailPayload, RelatedIssue } from './types.ts';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

type NewsDetailResponse = {
  id: number;
  title: string;
  content: string;
  description: string;
  imageUrl: string | null;
  category: string;
  publishedAt: string;
};

async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`);

  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText}`);
  }

  return response.json() as Promise<T>;
}

function normalizeNews(raw: Partial<NewsDetailResponse> & Record<string, unknown>): NewsDetail {
  return {
    id: typeof raw.id === 'number' ? raw.id : mockNews.id,
    title: String(raw.title ?? mockNews.title),
    category: isCategory(raw.category) ? raw.category : mockNews.category,
    publishedAt: String(raw.publishedAt ?? mockNews.publishedAt),
    description: String(raw.description ?? mockNews.description),
    content: String(raw.content ?? raw.description ?? '')
      .split('\n')
      .filter(Boolean),
    imageUrl: typeof raw.imageUrl === 'string' ? raw.imageUrl : undefined,
  };
}

function normalizeRelatedIssues(
  raw: Array<Partial<RelatedIssue> & Record<string, unknown>>,
): RelatedIssue[] {
  return raw.map((issue) => ({
    keyword: typeof issue.keyword === 'string' ? issue.keyword : String(issue.title ?? ''),
    title: String(issue.title ?? issue.keyword ?? '키워드'),
    probability: typeof issue.probability === 'number' ? issue.probability : undefined,
    description: String(issue.description ?? issue.analysis ?? ''),
  }));
}

export async function getNewsDetail(newsId: string): Promise<NewsDetailPayload> {
  const [news, impacts, relatedIssues] = await Promise.allSettled([
    getJson<NewsDetailResponse>(`/api/news/${newsId}`),
    getJson<Impact[]>(`/api/news/${newsId}/impacts`),
    getJson<Array<Partial<RelatedIssue> & Record<string, unknown>>>(`/api/news/${newsId}/related`),
  ]);

  return {
    news: news.status === 'fulfilled' ? normalizeNews(news.value) : mockNews,
    impacts: impacts.status === 'fulfilled' ? impacts.value : mockImpacts,
    relatedIssues:
      relatedIssues.status === 'fulfilled'
        ? normalizeRelatedIssues(relatedIssues.value)
        : mockRelatedIssues,
  };
}
