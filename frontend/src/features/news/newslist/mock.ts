import newsHomeBannerImage from '../../../assets/news-home-banner.jpg';
import newsHomeCardImage1 from '../../../assets/news-home-card-1.jpg';
import type { NewsCategory, NewsListItem, NewsPage } from './types';

const PAGE_SIZE = 9;

const firstPageArticles: NewsListItem[] = [
  {
    id: 'mock-etf-1',
    title: 'ETF 순자산 증가, 분산 투자 수요 확대',
    description:
      '시장 변동성이 커지며 ETF 자금 유입이 이어집니다. 분산형 상품 선호가 강해지고 있습니다.',
    category: 'ETF',
    publishedAt: '6시간 전',
    imageUrl: newsHomeCardImage1,
  },
];

const mockArticles = firstPageArticles;

export function getMockNewsPage(category: NewsCategory, page = 1): NewsPage {
  const source =
    category === 'ALL'
      ? mockArticles
      : mockArticles.filter((article) => article.category === category);
  const totalPages = Math.max(Math.ceil(source.length / PAGE_SIZE), 1);
  const currentPage = Math.min(Math.max(page, 1), totalPages);
  const startIndex = (currentPage - 1) * PAGE_SIZE;
  const content = source.slice(startIndex, startIndex + PAGE_SIZE);

  return {
    content,
    page: currentPage,
    size: PAGE_SIZE,
    totalPages,
    totalElements: source.length,
  };
}

export function getMockTodayNews(category: NewsCategory): NewsListItem {
  const article = getMockNewsPage(category, 1).content[0] ?? firstPageArticles[0];

  return {
    ...article,
    imageUrl: newsHomeBannerImage,
    publishedAt: '2026-08-24T00:00:00+09:00',
  };
}
