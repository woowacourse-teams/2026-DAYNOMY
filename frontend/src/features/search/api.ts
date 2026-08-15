import type { NewsArticle, NewsCategory } from '../news/newslist/types'

type SearchNewsResponse = NewsArticle[] | { content?: NewsArticle[] }

export function buildNewsSearchUrl(keyword: string, category: NewsCategory) {
  const params = new URLSearchParams({ q: keyword })

  if (category !== 'ALL') params.set('category', category)

  return `/api/search/news?${params.toString()}`
}

export async function searchNews(keyword: string, category: NewsCategory) {
  const response = await fetch(buildNewsSearchUrl(keyword, category))

  if (!response.ok) throw new Error('검색 결과를 불러오지 못했습니다.')

  const contentType = response.headers.get('content-type') ?? ''

  if (!contentType.includes('application/json')) {
    throw new Error('검색 API 응답 형식이 올바르지 않습니다.')
  }

  const data = (await response.json()) as SearchNewsResponse

  return Array.isArray(data) ? data : (data.content ?? [])
}
