import { afterEach, describe, expect, it, vi } from 'vitest';
import { createFeed, feedDate, isToday } from '../../src/features/news/issue/feed';
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
});

it('외부 콘텐츠는 원문 게시일로, 같은 날짜의 콘텐츠는 등록 시각순으로 정렬한다', () => {
  const feed = createFeed(
    [
      {
        id: 10,
        title: '데이노미 이슈',
        category: 'STOCK',
        imageUrl: null,
        publishedAt: '2026-10-12T09:00:00Z',
      },
    ],
    [],
    [
      {
        id: 20,
        assetId: 1,
        sourceType: 'YOUTUBE',
        title: '실제 발행일이 더 이른 영상',
        url: 'https://youtu.be/abcdefghij1',
        imageUrl: null,
        createdAt: '2026-10-13T09:00:00Z',
        publishedDate: '2026-10-11',
      },
      {
        id: 30,
        assetId: 1,
        sourceType: 'X',
        title: '같은 날짜의 외부 콘텐츠',
        url: 'https://x.com/example/status/30',
        imageUrl: null,
        createdAt: '2026-10-10T09:00:00Z',
        publishedDate: '2026-10-12',
      },
    ],
  );

  expect(feed.map((item) => item.title)).toEqual([
    '데이노미 이슈',
    '같은 날짜의 외부 콘텐츠',
    '실제 발행일이 더 이른 영상',
  ]);
});

it('짧은 날짜는 한국 시간으로 발행과 등록을 구분하고 누락된 날짜를 명시한다', () => {
  expect(feedDate('2026-10-11T15:00:00Z', '발행')).toBe('10.12 발행');
  expect(feedDate('2026-10-11T14:59:59Z', '등록')).toBe('10.11 등록');
  expect(feedDate(null, '발행')).toBe('발행일 미제공');
  expect(feedDate('invalid', '등록')).toBe('등록일 미제공');
});
