import type { FeedItem } from '../news/issue/feed';
import type { ContentSearchItemResponse, ContentSearchResponse } from './response';

function toFeedItem(item: ContentSearchItemResponse): FeedItem {
  if (item.kind === 'NEWS')
    return {
      key: `news-${item.id}`,
      source: item.source,
      title: item.title,
      date: item.date,
      article: {
        id: item.id,
        title: item.title,
        category: item.category,
        imageUrl: item.imageUrl,
        publishedAt: item.date,
      },
    };
  if (item.kind === 'PERSON_POST')
    return {
      key: `person-${item.personId}-${item.id}`,
      source: item.source,
      title: item.title,
      date: item.date,
      person: { id: item.personId, name: item.personName },
      post: {
        id: item.id,
        sourceType: item.source,
        title: item.title,
        url: item.url,
        createdAt: item.date,
      },
    };
  return {
    key: `content-${item.id}`,
    source: item.source,
    title: item.title,
    date: item.date,
    content: {
      id: item.id,
      assetId: item.assetId,
      sourceType: item.source,
      title: item.title,
      url: item.url,
      imageUrl: item.imageUrl,
      createdAt: item.date,
      publishedDate: item.publishedDate,
    },
  };
}

export function toContentSearchPage(response: ContentSearchResponse) {
  return { ...response, items: response.items.map(toFeedItem) };
}
