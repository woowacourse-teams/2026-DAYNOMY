import type { FeaturedPerson, FeaturedPersonPost } from '../../people/types';
import type { StockRelatedContent } from '../../stock-content/types';
import type { NewsListItem } from '../newslist/types';

export type ExternalContent = Omit<StockRelatedContent, 'sourceType'> & {
  sourceType: 'YOUTUBE' | 'X' | 'THREADS';
};

export type FeedItem =
  | {
      key: string;
      source: ExternalContent['sourceType'];
      title: string;
      date: string | null;
      content: ExternalContent;
    }
  | {
      key: string;
      source: 'DAYNOMY';
      title: string;
      date: string | null;
      article: NewsListItem;
    }
  | {
      key: string;
      source: FeaturedPersonPost['sourceType'];
      title: string;
      date: string | null;
      person: Pick<FeaturedPerson, 'id' | 'name'>;
      post: FeaturedPersonPost;
    };

const seoulDate = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Seoul',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

export function timestamp(date: string | null) {
  if (!date || Number.isNaN(Date.parse(date))) return 0;
  return Date.parse(`${seoulDate.format(new Date(date))}T00:00:00Z`);
}

export function isToday(date: string | null, now = new Date()) {
  return (
    !!date &&
    !Number.isNaN(Date.parse(date)) &&
    seoulDate.format(new Date(date)) === seoulDate.format(now)
  );
}

export function peopleFeed(people: FeaturedPerson[]): FeedItem[] {
  return people.flatMap((person) =>
    person.posts.map((post) => ({
      key: `person-${person.id}-${post.id}`,
      source: post.sourceType,
      title: post.title,
      date: post.publishedAt ?? post.createdAt,
      person,
      post,
    })),
  );
}

export function createFeed(
  news: NewsListItem[],
  people: FeaturedPerson[],
  contents: ExternalContent[] = [],
) {
  const items: FeedItem[] = news.map((article) => ({
    key: `news-${article.id}`,
    source: 'DAYNOMY',
    title: article.title,
    date: article.publishedAt,
    article,
  }));
  const posts = peopleFeed(people);
  const urls = new Set(posts.map((item) => ('post' in item ? canonicalUrl(item.post.url) : '')));
  const external: FeedItem[] = [];
  for (const content of contents) {
    const url = canonicalUrl(content.url);
    if (urls.has(url)) continue;
    urls.add(url);
    external.push({
      key: `content-${content.id}`,
      source: content.sourceType,
      title: content.title,
      date: content.publishedDate ?? content.createdAt,
      content,
    });
  }
  return [...items, ...posts, ...external].sort(
    (a, b) =>
      timestamp(b.date) - timestamp(a.date) ||
      createdTimestamp(createdAt(b)) - createdTimestamp(createdAt(a)) ||
      compareId(itemId(b), itemId(a)) ||
      a.key.localeCompare(b.key),
  );
}

function createdTimestamp(date: string | null) {
  return date && !Number.isNaN(Date.parse(date)) ? Date.parse(date) : 0;
}

function createdAt(item: FeedItem) {
  return 'article' in item
    ? item.article.publishedAt
    : 'content' in item
      ? item.content.createdAt
      : item.post.createdAt;
}

function itemId(item: FeedItem) {
  return 'article' in item ? item.article.id : 'content' in item ? item.content.id : item.post.id;
}

function compareId(left: string | number, right: string | number) {
  const leftNumber = Number(left);
  const rightNumber = Number(right);
  if (Number.isSafeInteger(leftNumber) && Number.isSafeInteger(rightNumber)) {
    return leftNumber - rightNumber;
  }
  return String(left).localeCompare(String(right));
}

export function sourceLabel(source: FeedItem['source']) {
  return source === 'DAYNOMY'
    ? '데이노미'
    : source === 'THREADS'
      ? 'Threads'
      : source === 'YOUTUBE'
        ? 'YouTube'
        : 'X';
}

export function feedDate(date: string | null, label?: '발행' | '등록') {
  if (label) {
    return date && !Number.isNaN(Date.parse(date))
      ? `${seoulDate.format(new Date(date)).slice(5).replace('-', '.')} ${label}`
      : `${label}일 미제공`;
  }
  return date && !Number.isNaN(Date.parse(date))
    ? new Intl.DateTimeFormat('ko-KR', {
        timeZone: 'Asia/Seoul',
        month: 'long',
        day: 'numeric',
      }).format(new Date(date))
    : '등록일 미제공';
}

function canonicalUrl(value: string) {
  try {
    const url = new URL(value);
    const host = url.hostname
      .replace(/^www\./, '')
      .replace('twitter.com', 'x.com')
      .replace('threads.net', 'threads.com');
    const video = youtubeId(value);
    return video ? `youtube:${video}` : `${host}${url.pathname.replace(/\/$/, '')}`;
  } catch {
    return value;
  }
}

export function youtubeId(value: string) {
  try {
    const url = new URL(value);
    const host = url.hostname.replace(/^www\./, '').replace(/^m\./, '');
    const id =
      host === 'youtu.be'
        ? url.pathname.slice(1)
        : host === 'youtube.com'
          ? (url.searchParams.get('v') ??
            url.pathname.match(/^\/(?:embed|shorts|live)\/([^/]+)/)?.[1])
          : null;
    return id && /^[a-zA-Z0-9_-]{11}$/.test(id) ? id : null;
  } catch {
    return null;
  }
}
