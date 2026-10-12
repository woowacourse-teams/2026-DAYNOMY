/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AdminPeoplePage } from '../../src/features/admin/AdminPeoplePage';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

beforeEach(() => {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/api/admin/people') && !init?.method) {
        return jsonResponse({
          people: [
            {
              id: 1,
              name: '일론 머스크',
              xProfileUrl: 'https://x.com/elonmusk',
              threadsProfileUrl: null,
              createdAt: null,
              posts: [],
            },
          ],
        });
      }
      if (url.endsWith('/api/auth/csrf')) {
        return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'test-token' });
      }
      if (url.endsWith('/api/admin/people/1/posts/search') && !init?.method) {
        return jsonResponse({
          items: [
            {
              sourceType: 'X',
              title: '일론 머스크 X 검색 결과',
              url: 'https://x.com/elonmusk/status/22222',
              publishedDate: '',
            },
            {
              sourceType: 'THREADS',
              title: '일론 머스크 Threads 검색 결과',
              url: 'https://www.threads.net/@elonmusk/post/ABC',
              publishedDate: '2026-10-12',
            },
          ],
        });
      }
      if (url.endsWith('/api/admin/people/1/posts') && init?.method === 'POST') {
        const body = JSON.parse(String(init.body)) as {
          sourceType: 'X' | 'THREADS';
          title: string | null;
          url: string;
        };
        const isManualPost = body.url === 'https://x.com/elonmusk/status/12345';
        expect(body).toEqual(
          isManualPost
            ? { sourceType: 'X', title: null, url: 'https://x.com/elonmusk/status/12345' }
            : expect.objectContaining({
                title: expect.stringContaining('일론 머스크'),
              }),
        );
        return jsonResponse(
          {
            id: body.sourceType === 'X' ? 2 : 3,
            sourceType: body.sourceType,
            title: body.title ?? '일론 머스크 X 게시물',
            url: body.url,
            createdAt: null,
          },
          201,
        );
      }
      throw new Error(`예상하지 못한 요청: ${url}`);
    }),
  );
});

afterEach(() => {
  cleanup();
  document.getElementById('daynomy-x-embed-script')?.remove();
  document.getElementById('daynomy-threads-embed-script')?.remove();
  vi.unstubAllGlobals();
});

describe('주요 인물 관리자 화면', () => {
  it('프로필에서 복사한 게시물 URL을 미리 보고 저장한다', async () => {
    const view = render(<AdminPeoplePage />);

    expect(await view.findByRole('heading', { name: '일론 머스크' })).toBeTruthy();
    expect(view.getByRole('link', { name: 'X 프로필 열기 ↗' }).getAttribute('href')).toBe(
      'https://x.com/elonmusk',
    );
    await view.findByDisplayValue('https://x.com/elonmusk');

    fireEvent.change(view.getByLabelText('게시물 URL'), {
      target: { value: 'https://x.com/elonmusk/status/12345' },
    });

    expect(view.getByLabelText('게시물 미리보기')).toBeTruthy();
    fireEvent.click(view.getByRole('button', { name: '게시물 저장' }));

    expect(await view.findByText('일론 머스크 X 게시물')).toBeTruthy();
    expect(view.getByText('1건 · 최근 등록순')).toBeTruthy();
  });

  it('한 번 검색한 X·Threads 게시물을 연속으로 등록한다', async () => {
    const view = render(<AdminPeoplePage />);

    await view.findByDisplayValue('https://x.com/elonmusk');
    fireEvent.click(view.getByRole('button', { name: 'AI로 X·Threads 검색' }));

    expect(await view.findByText('일론 머스크 X 검색 결과')).toBeTruthy();
    expect(view.getByText('일론 머스크 Threads 검색 결과')).toBeTruthy();
    expect(view.getByText('2026-10-12')).toBeTruthy();

    fireEvent.click(view.getAllByRole('button', { name: '이 게시물 등록' })[0]);

    expect(await view.findByRole('button', { name: '등록됨' })).toBeTruthy();
    expect(view.getByText('1건 · 최근 등록순')).toBeTruthy();

    await waitFor(() => {
      expect(
        (view.getByRole('button', { name: '이 게시물 등록' }) as HTMLButtonElement).disabled,
      ).toBe(false);
    });
    fireEvent.click(view.getByRole('button', { name: '이 게시물 등록' }));

    await waitFor(() => {
      expect(view.getAllByRole('button', { name: '등록됨' })).toHaveLength(2);
    });
    expect(view.getByText('2건 · 최근 등록순')).toBeTruthy();
    expect(
      vi
        .mocked(fetch)
        .mock.calls.filter(([input]) => String(input).endsWith('/api/admin/people/1/posts/search')),
    ).toHaveLength(1);
  });
});
