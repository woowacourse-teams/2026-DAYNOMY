import defaultNewsImage from '../../../../assets/default-news-real-estate.png';
import { getCategoryLabel } from '../constants';
import type { NewsListItem } from '../types';
import { formatDate } from '../utils';

type ArticleCardProps = {
  article: NewsListItem;
};

export function ArticleCard({ article }: ArticleCardProps) {
  const publishedAt = formatDate(article.publishedAt);

  return (
    <a className="article-card" href={`/news/${article.id}`}>
      <div className="article-meta">
        <span>{getCategoryLabel(article.category)}</span>
      </div>
      <img src={article.imageUrl ?? defaultNewsImage} alt="" className="article-thumbnail" />
      <div className="article-body">
        <h2>{article.title}</h2>
        {article.description ? <p>{article.description}</p> : null}
        <time className="article-time" dateTime={article.publishedAt ?? undefined}>
          {publishedAt}
        </time>
      </div>
    </a>
  );
}
