import defaultNewsImage from '../../../../assets/default-news-real-estate.png';
import { getCategoryLabel } from '../constants';
import type { NewsArticle } from '../types';
import { formatDate } from '../utils';

type TodayNewsBannerProps = {
  article: NewsArticle;
  onSelect: (article: NewsArticle) => void;
};

export function TodayNewsBanner({ article, onSelect }: TodayNewsBannerProps) {
  return (
    <section className="today-banner" aria-label="오늘의 뉴스" onClick={() => onSelect(article)}>
      <div className="banner-content">
        <div className="article-meta">
          <span>{getCategoryLabel(article.category)}</span>
          <time dateTime={article.publishedAt}>{formatDate(article.publishedAt)}</time>
        </div>
        <h2>{article.title}</h2>
        <p>{article.description}</p>
        {article.source ? <span className="article-source">{article.source}</span> : null}
      </div>
      <div className="banner-visual" aria-hidden="true">
        <img src={article.imageUrl ?? defaultNewsImage} alt="" />
      </div>
    </section>
  );
}
