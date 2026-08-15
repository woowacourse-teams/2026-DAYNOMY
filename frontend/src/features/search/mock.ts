import { dummyNews } from '../news/newslist/mock'
import type { NewsCategory } from '../news/newslist/types'

export function getMockSearchNews(keyword: string, category: NewsCategory) {
  const query = keyword.trim().toLocaleLowerCase('ko-KR')

  return dummyNews.filter((article) => {
    const matchesCategory = category === 'ALL' || article.category === category
    const text = [article.title, article.summary, article.source]
      .filter(Boolean)
      .join(' ')
      .toLocaleLowerCase('ko-KR')

    return matchesCategory && text.includes(query)
  })
}
