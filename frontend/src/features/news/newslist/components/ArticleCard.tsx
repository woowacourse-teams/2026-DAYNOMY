import defaultNewsImage from '../../../../assets/default-news-real-estate.png';
import { getCategoryLabel } from '../constants';
import type { NewsListItem } from '../types';
import { formatDate } from '../utils';

type ArticleCardProps = {
  article: NewsListItem;
};

export function ArticleCard({ article }: ArticleCardProps) {
  return (
    <a className="article-card" href={`/news/${article.id}`}>
      <img src={article.imageUrl ?? defaultNewsImage} alt="" className="article-thumbnail" />
      <div className="article-body">
        <div className="article-meta">
          <span>{getCategoryLabel(article.category)}</span>
          <time dateTime={article.publishedAt ?? undefined}>{formatDate(article.publishedAt)}</time>
        </div>
        <h2>{article.title}</h2>
        {article.description ? <p>{article.description}</p> : null}
      </div>
    </a>
  );
}
