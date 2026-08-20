import defaultNewsImage from '../../../../assets/default-news-real-estate.png';
import { getCategoryLabel } from '../constants';
import type { NewsArticle } from '../types';
import { formatDate } from '../utils';

type ArticleCardProps = {
  article: NewsArticle;
  onSelect: (article: NewsArticle) => void;
};

export function ArticleCard({ article, onSelect }: ArticleCardProps) {
  return (
    <article className="article-card" onClick={() => onSelect(article)}>
      <img src={article.imageUrl ?? defaultNewsImage} alt="" className="article-thumbnail" />
      <div className="article-body">
        <div className="article-meta">
          <span>{getCategoryLabel(article.category)}</span>
          <time dateTime={article.publishedAt}>{formatDate(article.publishedAt)}</time>
        </div>
        <h2>{article.title}</h2>
        {article.description ? <p>{article.description}</p> : null}
        {article.source ? <span className="article-source">{article.source}</span> : null}
      </div>
    </article>
  );
}
