import { getNews } from '../newslist/api';

export async function getAllNews() {
  const first = await getNews('ALL', 1, 100);
  const content = [...first.content];
  let current = first;
  while (current.page < current.totalPages && current.content.length) {
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
