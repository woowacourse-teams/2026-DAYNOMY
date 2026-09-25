import { useState } from 'react';
import defaultNewsImage from '../../../../assets/default-news-real-estate.webp';
import { getCategoryLabel } from '../constants';
import { getNewsImage } from '../newsImage';
import type { NewsListItem } from '../types';
import { formatDate } from '../utils';

type ArticleCardProps = {
  article: NewsListItem;
};

export function ArticleCard({ article }: ArticleCardProps) {
  const publishedAt = formatDate(article.publishedAt);
  const [imageUrl, setImageUrl] = useState(() => getNewsImage(article));

  return (
    <a className="article-card" href={`/news/${article.id}`}>
      <img
        src={imageUrl}
        alt=""
        className="article-thumbnail"
        loading="lazy"
        decoding="async"
        onError={(event) => {
          if (event.currentTarget.getAttribute('src') !== defaultNewsImage) {
            event.currentTarget.src = defaultNewsImage;
            setImageUrl(defaultNewsImage);
          }
        }}
      />
      <div className="article-body">
        <div className="article-meta">
          <span>{getCategoryLabel(article.category)}</span>
          <span aria-hidden="true">·</span>
          <time dateTime={article.publishedAt ?? undefined}>{publishedAt}</time>
        </div>
        <h2>{article.title}</h2>
      </div>
      <span className="article-arrow" aria-hidden="true">
        →
      </span>
    </a>
  );
}
