/** @vitest-environment jsdom */

import { cleanup, fireEvent, render } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  AdminContentReviewsPage,
  AdminContentSourcesPage,
} from '../../src/features/admin/AdminContentCollectionPage';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

const source = {
  id: 1,
  name: '경제 채널',
  sourceType: 'YOUTUBE',
  sourceUrl: 'https://www.youtube.com/@economy',
  assetId: null,
  assetName: null,
  enabled: true,
  lastCollectedAt: null,
};

const xSource = {
  id: 2,
  name: '한국경제신문',
  sourceType: 'X',
  sourceUrl: 'https://x.com/hankyungmedia',
  assetId: null,
  assetName: null,
  enabled: true,
  lastCollectedAt: null,
};

const emptyReviews = {
  items: [],
  page: 1,
  size: 100,
  totalPages: 0,
  totalElements: 0,
  hasNext: false,
};

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('관리자 소식 출처·검수 화면', () => {
  it('등록된 출처를 조회하고 활성 출처 전체 수집 결과를 표시한다', async () => {
    const calls: string[] = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        calls.push(url);
        if (url.endsWith('/api/auth/csrf')) {
          return jsonResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' });
        }
        if (url.endsWith('/collect-all')) {
          return jsonResponse({
            sourceCount: 1,
            successCount: 1,
            failureCount: 0,
            fetchedCount: 4,
            savedCount: 3,
            duplicateCount: 1,
            linkedCount: 2,
          });
        }
        if (url.includes('/api/admin/content-collection-reviews')) {
          return jsonResponse(emptyReviews);
        }
        return jsonResponse({ sources: [source] });
      }),
    );

    const view = render(<AdminContentSourcesPage />);
    expect(await view.findByText('경제 채널')).toBeTruthy();
    expect(view.getByText('자동 매칭')).toBeTruthy();

    fireEvent.click(view.getByRole('button', { name: '활성 출처 전체 수집' }));

    expect(await view.findByText('출처 1개 중 1개 성공')).toBeTruthy();
    expect(view.getByText('활성 출처 전체 수집을 완료했습니다.')).toBeTruthy();
    expect(calls.some((url) => url.endsWith('/collect-all'))).toBe(true);
    expect(calls.some((url) => url.includes('/api/admin/content-collection-reviews'))).toBe(false);
  });

  it('출처 URL을 검증하고 새 YouTube 출처를 등록한다', async () => {
    let createBody: Record<string, unknown> | null = null;
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        if (url.endsWith('/api/auth/csrf')) {
          return jsonResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' });
        }
        if (url.includes('/api/admin/content-collection-reviews')) {
          return jsonResponse(emptyReviews);
        }
        if (init?.method === 'POST') {
          createBody = JSON.parse(String(init.body));
          return jsonResponse(source, 201);
        }
        return jsonResponse({ sources: [] });
      }),
    );

    const view = render(<AdminContentSourcesPage />);
    await view.findByText('등록된 수집 출처가 없습니다.');
    fireEvent.change(view.getByLabelText('출처 이름'), { target: { value: '경제 채널' } });
    fireEvent.change(view.getByLabelText('채널·프로필 URL'), {
      target: { value: 'https://x.com/not-youtube' },
    });
    fireEvent.click(view.getByRole('button', { name: '출처 등록' }));
    expect((await view.findByRole('alert')).textContent).toContain('YouTube');

    fireEvent.change(view.getByLabelText('채널·프로필 URL'), {
      target: { value: 'https://www.youtube.com/@economy' },
    });
    fireEvent.click(view.getByRole('button', { name: '출처 등록' }));

    expect(await view.findByText('수집 출처를 등록했습니다.')).toBeTruthy();
    expect(createBody).toEqual({
      name: '경제 채널',
      sourceType: 'YOUTUBE',
      sourceUrl: 'https://www.youtube.com/@economy',
      assetId: null,
      enabled: true,
    });
  });

  it('개별 출처를 즉시 수집한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        if (url.endsWith('/api/auth/csrf')) {
          return jsonResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' });
        }
        if (url.endsWith('/1/collect')) {
          return jsonResponse({
            sourceId: 1,
            fetchedCount: 2,
            savedCount: 1,
            duplicateCount: 1,
            linkedCount: 1,
          });
        }
        if (url.includes('/api/admin/content-collection-reviews')) {
          return jsonResponse(emptyReviews);
        }
        return jsonResponse({ sources: [source] });
      }),
    );

    const view = render(<AdminContentSourcesPage />);
    await view.findByText('경제 채널');
    fireEvent.click(view.getByRole('button', { name: '지금 수집' }));

    expect(await view.findByText(/2건 수집, 1건 저장, 1건 종목 후보/)).toBeTruthy();
  });

  it('자동 매칭된 수집 콘텐츠를 승인해 공개한다', async () => {
    let approved = false;
    let approveBody: Record<string, unknown> | null = null;
    const review = {
      id: 7,
      sourceName: '경제 채널',
      sourceType: 'YOUTUBE',
      title: '삼성전자 실적 발표',
      description: '삼성전자 분기 실적을 살펴봅니다.',
      url: 'https://www.youtube.com/watch?v=review',
      imageUrl: null,
      publishedDate: null,
      suggestedAssets: [
        { id: 10, name: '삼성전자', assetCode: '005930' },
        { id: 20, name: 'SK하이닉스', assetCode: '000660' },
      ],
      createdAt: '2026-10-27T00:00:00Z',
    };
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        if (url.endsWith('/api/auth/csrf')) {
          return jsonResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' });
        }
        if (url.endsWith('/7/approve')) {
          approveBody = JSON.parse(String(init?.body));
          approved = true;
          return new Response(null, { status: 204 });
        }
        if (url.includes('/api/admin/content-collection-reviews')) {
          return jsonResponse({
            ...emptyReviews,
            items: approved ? [] : [review],
            totalPages: approved ? 0 : 1,
            totalElements: approved ? 0 : 1,
          });
        }
        return jsonResponse({ sources: [source] });
      }),
    );

    const view = render(<AdminContentReviewsPage />);
    expect(await view.findByText('삼성전자 실적 발표')).toBeTruthy();
    expect(view.getByText('연결 종목 2개')).toBeTruthy();
    expect(view.getByText('삼성전자 · 005930 ×')).toBeTruthy();
    expect(view.getByText('SK하이닉스 · 000660 ×')).toBeTruthy();
    expect(view.getByText('날짜 미확인')).toBeTruthy();

    fireEvent.change(view.getByLabelText('삼성전자 실적 발표 게시일'), {
      target: { value: '2026-10-26' },
    });

    fireEvent.click(view.getByRole('button', { name: '승인 및 공개' }));

    expect(await view.findByText('수집 콘텐츠를 승인해 종목 소식에 공개했습니다.')).toBeTruthy();
    expect(view.queryByText('삼성전자 실적 발표')).toBeNull();
    expect(approveBody).toEqual({ assetIds: [10, 20], publishedDate: '2026-10-26' });
  });

  it('게시물 URL을 등록하고 자동 연결된 종목 수를 표시한다', async () => {
    let registerBody: Record<string, unknown> | null = null;
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        if (url.endsWith('/api/auth/csrf')) {
          return jsonResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' });
        }
        if (url.endsWith('/2/contents')) {
          registerBody = JSON.parse(String(init?.body));
          return jsonResponse(
            {
              sourceId: 2,
              fetchedCount: 1,
              savedCount: 1,
              duplicateCount: 0,
              linkedCount: 2,
            },
            201,
          );
        }
        if (url.includes('/api/admin/content-collection-reviews')) {
          return jsonResponse(emptyReviews);
        }
        return jsonResponse({ sources: [source, xSource] });
      }),
    );

    const view = render(<AdminContentReviewsPage />);
    await view.findByRole('option', { name: 'X · 한국경제신문' });
    fireEvent.change(view.getByLabelText('수동 게시물 출처'), { target: { value: '2' } });
    fireEvent.change(view.getByLabelText('게시물 URL'), {
      target: { value: 'https://x.com/hankyungmedia/status/2115009742426808418?s=20' },
    });
    fireEvent.change(view.getByLabelText('본문·설명 (선택)'), {
      target: { value: '삼성전자와 SK하이닉스 전망' },
    });
    fireEvent.click(view.getByRole('button', { name: '검수 대기에 등록' }));

    expect(
      await view.findByText('게시물을 검수 대기에 등록하고 종목 후보 2개를 자동 연결했습니다.'),
    ).toBeTruthy();
    expect(registerBody).toEqual({
      url: 'https://x.com/hankyungmedia/status/2115009742426808418?s=20',
      title: null,
      description: '삼성전자와 SK하이닉스 전망',
      publishedDate: null,
    });
  });

  it('선택한 수집 콘텐츠를 일괄 거절한다', async () => {
    let rejectedBody: Record<string, unknown> | null = null;
    let rejected = false;
    vi.stubGlobal(
      'confirm',
      vi.fn(() => true),
    );
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        if (url.endsWith('/api/auth/csrf')) {
          return jsonResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' });
        }
        if (url.endsWith('/bulk-reject')) {
          rejectedBody = JSON.parse(String(init?.body));
          rejected = true;
          return new Response(null, { status: 204 });
        }
        if (url.includes('/api/admin/content-collection-reviews')) {
          return jsonResponse({
            ...emptyReviews,
            items: rejected
              ? []
              : [
                  {
                    id: 7,
                    sourceName: '경제 채널',
                    sourceType: 'YOUTUBE',
                    title: '첫 번째 소식',
                    description: null,
                    url: 'https://youtube.com/watch?v=1',
                    imageUrl: null,
                    publishedDate: null,
                    suggestedAssets: [],
                    createdAt: '2026-10-27T00:00:00Z',
                  },
                  {
                    id: 8,
                    sourceName: '경제 채널',
                    sourceType: 'YOUTUBE',
                    title: '두 번째 소식',
                    description: null,
                    url: 'https://youtube.com/watch?v=2',
                    imageUrl: null,
                    publishedDate: null,
                    suggestedAssets: [],
                    createdAt: '2026-10-27T00:00:00Z',
                  },
                ],
            totalPages: rejected ? 0 : 1,
            totalElements: rejected ? 0 : 2,
          });
        }
        return jsonResponse({ sources: [source] });
      }),
    );

    const view = render(<AdminContentReviewsPage />);
    expect(await view.findByText('첫 번째 소식')).toBeTruthy();
    expect(view.getAllByText('날짜 미확인')).toHaveLength(2);
    fireEvent.click(view.getByLabelText('현재 목록 전체 선택'));
    fireEvent.click(view.getByRole('button', { name: '선택 거절 (2)' }));

    expect(await view.findByText('수집 콘텐츠 2건을 거절했습니다.')).toBeTruthy();
    expect(rejectedBody).toEqual({ contentIds: [7, 8] });
  });
});
