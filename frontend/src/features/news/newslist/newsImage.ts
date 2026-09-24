import newsHomeBanner from '../../../assets/news-home-banner.jpg';
import newsHomeCard1 from '../../../assets/news-home-card-1.jpg';
import newsHomeCard2 from '../../../assets/news-home-card-2.jpg';
import newsHomeCard3 from '../../../assets/news-home-card-3.jpg';
import newsHomeCard4 from '../../../assets/news-home-card-4.jpg';
import newsHomeCard5 from '../../../assets/news-home-card-5.jpg';
import type { NewsListItem } from './types';

const fallbackImagesByCategory: Partial<Record<NewsListItem['category'], string[]>> = {
  STOCK: [newsHomeCard3, newsHomeBanner],
  REAL_ESTATE: [newsHomeCard2, newsHomeCard1],
  ETF: [newsHomeCard4, newsHomeCard5, newsHomeBanner],
};

function getStableIndex(value: string, length: number) {
  return [...value].reduce((sum, character) => sum + character.charCodeAt(0), 0) % length;
}

export function getNewsImage(article: NewsListItem) {
  if (article.imageUrl) {
    return article.imageUrl;
  }

  const categoryImages = fallbackImagesByCategory[article.category] ?? [newsHomeBanner];

  return categoryImages[getStableIndex(String(article.id ?? article.title), categoryImages.length)];
}
