/** @vitest-environment jsdom */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  getContentPopularity,
  recordContentInteraction,
  sortByPopularity,
  type ContentReference,
} from '../../src/features/news/issue/popularity';

function json(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

beforeEach(() => {
  localStorage.clear();
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-10-20T03:00:00Z'));
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('콘텐츠 인기순', () => {
  it('인기 점수가 높은 콘텐츠를 먼저 정렬하고 동점은 기존 순서를 유지한다', () => {
    const items = [
      { id: 1, type: 'NEWS' },
      { id: 2, type: 'NEWS' },
      { id: 3, type: 'NEWS' },
    ];
    const reference = (item: (typeof items)[number]): ContentReference => ({
      contentType: item.type as 'NEWS',
      contentId: item.id,
    });

    const sorted = sortByPopularity(
      items,
      reference,
      new Map([
        ['NEWS:2', 4],
        ['NEWS:3', 4],
      ]),
    );

    expect(sorted.map((item) => item.id)).toEqual([2, 3, 1]);
  });

  it('인기 점수 응답을 검증해 콘텐츠 키별 점수로 변환한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        json({
          periodDays: 7,
          items: [
            {
              contentType: 'NEWS',
              contentId: 3,
              score: 5,
              lastInteractedAt: '2026-10-20T02:00:00Z',
            },
          ],
        }),
      ),
    );

    const scores = await getContentPopularity();

    expect(scores.get('NEWS:3')).toBe(5);
  });

  it('동일 브라우저의 같은 일자 반응은 한 번만 전송한다', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = new URL(String(input), 'http://localhost');
      if (url.pathname === '/api/auth/csrf') {
        return json({ headerName: 'X-XSRF-TOKEN', token: 'token' });
      }
      return new Response(null, { status: 204 });
    });
    vi.stubGlobal('fetch', fetchMock);

    const reference: ContentReference = { contentType: 'NEWS', contentId: 1 };
    await recordContentInteraction(reference, 'OPEN');
    await recordContentInteraction(reference, 'OPEN');

    expect(
      fetchMock.mock.calls.filter(
        ([input]) =>
          new URL(String(input), 'http://localhost').pathname === '/api/content-interactions',
      ),
    ).toHaveLength(1);
  });
});
