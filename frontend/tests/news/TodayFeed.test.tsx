import { afterEach, describe, expect, it, vi } from 'vitest';
import { isToday } from '../../src/features/news/issue/feed';
import { getNewsThroughToday } from '../../src/features/news/issue/todayNews';
import { getNews } from '../../src/features/news/newslist/api';
vi.mock('../../src/features/news/newslist/api', () => ({ getNews: vi.fn() }));
afterEach(() => {
  vi.useRealTimers();
  vi.resetAllMocks();
});
describe('오늘 글 기준', () => {
  it('한국 자정 기준으로 날짜를 구분하며 알 수 없는 날짜를 제외한다', () => {
    const now = new Date('2026-10-12T00:00:00+09:00');
    expect(isToday('2026-10-11T15:00:00Z', now)).toBe(true);
    expect(isToday('2026-10-11T14:59:59Z', now)).toBe(false);
    expect(isToday('2026-10-12T15:00:00Z', now)).toBe(false);
    expect(isToday(null, now)).toBe(false);
    expect(isToday('invalid', now)).toBe(false);
  });
  it('오늘 뉴스가 한 페이지를 넘으면 다음 페이지도 읽고 지난 날짜에서 멈춘다', async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-10-12T10:00:00Z'));
    const article = {
      id: 1,
      title: '오늘 뉴스',
      category: 'STOCK' as const,
      imageUrl: null,
      publishedAt: '2026-10-12T09:00:00Z',
    };
    const page = {
      page: 1,
      size: 100,
      totalPages: 3,
      totalElements: 250,
      content: Array.from({ length: 100 }, (_, index) => ({ ...article, id: index + 1 })),
    };
    vi.mocked(getNews)
      .mockResolvedValueOnce(page)
      .mockResolvedValueOnce({
        ...page,
        page: 2,
        content: [
          { ...article, id: 101 },
          { ...article, id: 102, publishedAt: '2026-10-11T09:00:00Z' },
        ],
      });
    const result = await getNewsThroughToday();
    expect(result.content.filter((item) => isToday(item.publishedAt))).toHaveLength(101);
    expect(getNews).toHaveBeenCalledTimes(2);
    expect(getNews).toHaveBeenLastCalledWith('ALL', 2, 100);
  });
});

it('지난 날짜 기사를 페이지 경계까지 모으고 더 오래된 페이지는 요청하지 않는다', async () => {
  const { getNewsForDate } = await import('../../src/features/news/issue/todayNews');
  const article = {
    id: 1,
    title: '기사',
    category: 'STOCK' as const,
    imageUrl: null,
    publishedAt: '2026-10-12T01:00:00Z',
  };
  const page = { page: 1, size: 100, totalPages: 4, totalElements: 301, content: [article] };
  vi.mocked(getNews)
    .mockResolvedValueOnce(page)
    .mockResolvedValueOnce({
      ...page,
      page: 2,
      content: [{ ...article, id: 2, publishedAt: '2026-10-11T14:59:59Z' }],
    })
    .mockResolvedValueOnce({
      ...page,
      page: 3,
      content: [
        { ...article, id: 3, publishedAt: '2026-10-10T15:00:00Z' },
        { ...article, id: 4, publishedAt: '2026-10-10T14:59:59Z' },
      ],
    });
  expect((await getNewsForDate('2026-10-11')).map((item) => item.id)).toEqual([2, 3]);
  expect(getNews).toHaveBeenCalledTimes(3);
});
