import type { NewsArticle, NewsCategory, NewsPage } from './types'

type RawNewsPage =
  | NewsArticle[]
  | {
      content?: NewsArticle[]
      data?: NewsArticle[]
      news?: NewsArticle[]
      page?: number
      number?: number
      size?: number
      totalPages?: number
      totalElements?: number
    }

const DEFAULT_PAGE_SIZE = 10

function normalizeNewsPage(data: RawNewsPage, page: number): NewsPage {
  if (Array.isArray(data)) {
    return {
      content: data,
      page,
      size: data.length,
      totalPages: 1,
      totalElements: data.length,
    }
  }

  const content = data.content ?? data.data ?? data.news ?? []

  return {
    content,
    page: data.page ?? data.number ?? page,
    size: data.size ?? content.length,
    totalPages: data.totalPages ?? 1,
    totalElements: data.totalElements ?? content.length,
  }
}

export async function getNews(
  category: NewsCategory = 'ALL',
  page = 0,
  size = DEFAULT_PAGE_SIZE,
): Promise<NewsPage> {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  })

  if (category !== 'ALL') {
    params.set('category', category)
  }

  const response = await fetch(`/api/news?${params.toString()}`)

  if (!response.ok) {
    throw new Error('뉴스 목록을 불러오지 못했습니다.')
  }

  const contentType = response.headers.get('content-type') ?? ''

  if (!contentType.includes('application/json')) {
    throw new Error('뉴스 API 응답 형식이 올바르지 않습니다.')
  }

  const data = (await response.json()) as RawNewsPage

  return normalizeNewsPage(data, page)
}

export async function getTodayNews(): Promise<NewsArticle> {
  const response = await fetch('/api/news/today')

  if (!response.ok) {
    throw new Error('오늘의 뉴스를 불러오지 못했습니다.')
  }

  return (await response.json()) as NewsArticle
}
