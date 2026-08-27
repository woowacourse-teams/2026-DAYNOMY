import defaultNewsImage from '../../../../assets/default-news-real-estate.png';
import { getCategoryLabel } from '../constants';
import type { NewsListItemResponse } from '../types';
import { formatDate } from '../utils';

type ArticleCardProps = {
  article: NewsListItemResponse;
  onSelect: (article: NewsListItemResponse) => void;
};

export function ArticleCard({ article, onSelect }: ArticleCardProps) {
  const publishedAt = formatDate(article.publishedAt);

  return (
    <article className="article-card" onClick={() => onSelect(article)}>
      <div className="article-meta">
        <span>{getCategoryLabel(article.category)}</span>
      </div>
      <img src={article.imageUrl ?? defaultNewsImage} alt="" className="article-thumbnail" />
      <div className="article-body">
        <h2>{article.title}</h2>
        {article.description ? <p>{article.description}</p> : null}
        <time className="article-time" dateTime={article.publishedAt}>
          {publishedAt}
        </time>
        {article.source ? <span className="article-source">{article.source}</span> : null}
      </div>
    </article>
  );
}
