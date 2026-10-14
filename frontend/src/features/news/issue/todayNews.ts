import { getNews } from '../newslist/api';
import { isToday } from './feed';

// /news/today is capped at nine items; walk the dated list until today's boundary.
export async function getNewsThroughToday(includeHistory = false) {
  const now = new Date();
  const first = await getNews('ALL', 1, 100);
  const content = [...first.content];
  let current = first;
  while (
    current.page < current.totalPages &&
    current.content.length &&
    (includeHistory || current.content.every((item) => isToday(item.publishedAt, now)))
  ) {
    current = await getNews('ALL', current.page + 1, 100);
    content.push(...current.content);
  }
  return { ...first, content };
}

export function newsDate(value: string | null) {
  if (!value || Number.isNaN(Date.parse(value))) return '';
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date(value));
}

export async function getNewsForDate(date: string) {
  let current = await getNews('ALL', 1, 100);
  const articles = current.content.filter((item) => newsDate(item.publishedAt) === date);
  while (
    current.page < current.totalPages &&
    current.content.length &&
    current.content.every((item) => newsDate(item.publishedAt) >= date)
  ) {
    current = await getNews('ALL', current.page + 1, 100);
    articles.push(...current.content.filter((item) => newsDate(item.publishedAt) === date));
  }
  return articles;
}
