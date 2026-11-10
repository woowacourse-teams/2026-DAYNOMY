/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, waitFor, within } from '@testing-library/react';
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
              youtubeProfileUrl: 'https://www.youtube.com/@Tesla',
              createdAt: null,
              posts: [],
            },
            {
              id: 2,
              name: '제롬 파월',
              xProfileUrl: null,
              threadsProfileUrl: null,
              youtubeProfileUrl: null,
              createdAt: null,
              posts: [],
            },
          ],
        });
      }
      if (url.endsWith('/api/auth/csrf')) {
        return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'test-token' });
      }
      if (url.includes('/api/admin/news/image-search')) {
        return jsonResponse({
          items: [
            {
              title: 'File:Elon Musk.jpg',
              thumbnailUrl: 'https://upload.wikimedia.org/elon.jpg',
              sourceUrl: 'https://commons.wikimedia.org/wiki/File:Elon_Musk.jpg',
              author: 'Photographer',
              license: 'CC BY 4.0',
              licenseUrl: 'https://creativecommons.org/licenses/by/4.0/',
              width: 100,
              height: 100,
            },
          ],
        });
      }
      if (url.endsWith('/api/admin/people/1') && init?.method === 'PUT') {
        const body = JSON.parse(String(init.body));
        expect(body.profileImageSelection).toEqual({ title: 'File:Elon Musk.jpg' });
        return jsonResponse({
          id: 1,
          name: '일론 머스크',
          xProfileUrl: 'https://x.com/elonmusk',
          threadsProfileUrl: null,
          youtubeProfileUrl: 'https://www.youtube.com/@Tesla',
          profileImageUrl: 'https://images.daynomy.test/elon.jpg',
          createdAt: null,
          posts: [],
        });
      }
      if (url.includes('/api/admin/people/post-candidates?') && !init?.method) {
        return jsonResponse({
          candidates: [
            {
              id: 21,
              personId: 1,
              personName: '일론 머스크',
              personProfileImageUrl: null,
              sourceType: 'X',
              title: '일론 머스크 전체 후보',
              url: 'https://x.com/elonmusk/status/33333',
              publishedAt: '2026-10-18T00:00:00Z',
              status: 'PENDING',
              createdAt: null,
            },
            {
              id: 22,
              personId: 2,
              personName: '제롬 파월',
              personProfileImageUrl: null,
              sourceType: 'YOUTUBE',
              title: '제롬 파월 전체 후보',
              url: 'https://www.youtube.com/watch?v=abcdefghijk',
              publishedAt: null,
              status: 'PENDING',
              createdAt: null,
            },
          ],
          page: 1,
          size: 20,
          totalElements: 2,
          totalPages: 1,
        });
      }
      if (url.includes('/post-candidates') && !init?.method) {
        return jsonResponse({ candidates: [] });
      }
      if (url.includes('/api/admin/people/1/post-candidates/collect?') && init?.method === 'POST') {
        return jsonResponse({
          candidates: [
            {
              id: 11,
              sourceType: 'X',
              title: '일론 머스크 X 검색 결과',
              url: 'https://x.com/elonmusk/status/22222',
              publishedAt: null,
              status: 'PENDING',
              createdAt: null,
            },
            {
              id: 12,
              sourceType: 'THREADS',
              title: '일론 머스크 Threads 검색 결과',
              url: 'https://www.threads.net/@elonmusk/post/ABC',
              publishedAt: '2026-10-12T00:00:00Z',
              status: 'PENDING',
              createdAt: null,
            },
          ],
        });
      }
      if (url.endsWith('/api/admin/people/1/post-candidates/bulk-approve')) {
        const body = JSON.parse(String(init?.body)) as { candidateIds: number[] };
        return jsonResponse({
          succeeded: body.candidateIds.map((candidateId) => ({
            candidateId,
            post: {
              id: candidateId + 100,
              sourceType: candidateId === 12 ? 'THREADS' : 'X',
              title:
                candidateId === 12 ? '일론 머스크 Threads 검색 결과' : '일론 머스크 X 검색 결과',
              url:
                candidateId === 12
                  ? 'https://www.threads.net/@elonmusk/post/ABC'
                  : 'https://x.com/elonmusk/status/22222',
              publishedAt: candidateId === 12 ? '2026-10-12T00:00:00Z' : null,
              createdAt: null,
            },
          })),
          failed: [],
        });
      }
      if (url.endsWith('/api/admin/people/1/post-candidates/bulk-reject')) {
        const body = JSON.parse(String(init?.body)) as { candidateIds: number[] };
        return jsonResponse({
          succeeded: body.candidateIds.map((candidateId) => ({ candidateId, post: null })),
          failed: [],
        });
      }
      if (url.endsWith('/api/admin/people/post-candidates/bulk-approve')) {
        const body = JSON.parse(String(init?.body)) as { candidateIds: number[] };
        return jsonResponse({
          succeeded: body.candidateIds.map((candidateId) => ({
            candidateId,
            post: {
              id: candidateId + 100,
              sourceType: candidateId === 22 ? 'YOUTUBE' : 'X',
              title: candidateId === 22 ? '제롬 파월 전체 후보' : '일론 머스크 전체 후보',
              url:
                candidateId === 22
                  ? 'https://www.youtube.com/watch?v=abcdefghijk'
                  : 'https://x.com/elonmusk/status/33333',
              publishedAt: null,
              createdAt: null,
            },
          })),
          failed: [],
        });
      }
      if (url.endsWith('/api/admin/people/post-candidates/bulk-reject')) {
        const body = JSON.parse(String(init?.body)) as { candidateIds: number[] };
        return jsonResponse({
          succeeded: body.candidateIds.map((candidateId) => ({ candidateId, post: null })),
          failed: [],
        });
      }
      if (/\/post-candidates\/\d+\/approve$/.test(url) && init?.method === 'POST') {
        const threads = url.includes('/12/');
        return jsonResponse({
          id: threads ? 3 : 2,
          sourceType: threads ? 'THREADS' : 'X',
          title: threads ? '일론 머스크 Threads 검색 결과' : '일론 머스크 X 검색 결과',
          url: threads
            ? 'https://www.threads.net/@elonmusk/post/ABC'
            : 'https://x.com/elonmusk/status/22222',
          publishedAt: threads ? '2026-10-12T00:00:00Z' : null,
          createdAt: null,
        });
      }
      if (/\/post-candidates\/\d+\/reject$/.test(url) && init?.method === 'POST') {
        return new Response(null, { status: 204 });
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

function renderPeopleMode() {
  const view = render(<AdminPeoplePage />);
  fireEvent.click(view.getByRole('button', { name: '인물별 관리' }));
  return view;
}

describe('주요 인물 관리자 화면', () => {
  it('새 인물 입력은 화면 전환과 저장 실패 뒤에도 남고 성공한 뒤에만 비운다', async () => {
    const originalFetch = fetch;
    let shouldFail = true;
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        if (String(input).endsWith('/api/admin/people') && init?.method === 'POST') {
          return shouldFail
            ? jsonResponse({ message: '등록 실패' }, 500)
            : jsonResponse(
                {
                  id: 3,
                  name: '새 인물',
                  xProfileUrl: null,
                  threadsProfileUrl: null,
                  youtubeProfileUrl: null,
                  createdAt: null,
                  posts: [],
                },
                201,
              );
        }
        return originalFetch(input, init);
      }),
    );
    const view = renderPeopleMode();
    const disclosure = view.container.querySelector(
      'details.admin-create-disclosure',
    ) as HTMLDetailsElement;
    fireEvent.click(within(disclosure).getByText('새 인물 등록'));
    const name = within(disclosure).getByLabelText('인물 이름') as HTMLInputElement;
    fireEvent.change(name, { target: { value: '새 인물' } });
    fireEvent.click(view.getByRole('button', { name: /전체 수집 후보/ }));
    fireEvent.click(view.getByRole('button', { name: '인물별 관리' }));
    expect(name.value).toBe('새 인물');

    fireEvent.click(within(disclosure).getByRole('button', { name: '인물 추가' }));
    expect(await view.findByRole('alert')).toBeTruthy();
    expect(name.value).toBe('새 인물');

    shouldFail = false;
    fireEvent.click(within(disclosure).getByRole('button', { name: '인물 추가' }));
    await waitFor(() => expect(name.value).toBe(''));
    expect(await view.findByRole('heading', { name: '새 인물' })).toBeTruthy();
  });

  it('작업 전환은 입력을 유지하고 다른 인물로 바꿀 때만 폐기를 확인한다', async () => {
    const confirm = vi.fn().mockReturnValue(false);
    vi.stubGlobal('confirm', confirm);
    const view = renderPeopleMode();
    await view.findByRole('heading', { name: '일론 머스크' });
    expect(view.queryByLabelText('게시물 URL')).toBeNull();
    fireEvent.click(view.getByRole('button', { name: '게시물 직접 등록' }));
    fireEvent.change(view.getByLabelText('게시물 URL'), {
      target: { value: 'https://x.com/elonmusk/status/12345' },
    });
    fireEvent.click(view.getByRole('button', { name: '프로필 수정' }));
    fireEvent.click(view.getByRole('button', { name: '게시물 직접 등록' }));
    expect((view.getByLabelText('게시물 URL') as HTMLInputElement).value).toContain('12345');
    fireEvent.click(view.getByRole('button', { name: /제롬 파월/ }));
    expect(confirm).toHaveBeenCalledTimes(1);
    expect(view.getByRole('heading', { name: '일론 머스크' })).toBeTruthy();
    confirm.mockReturnValue(true);
    fireEvent.click(view.getByRole('button', { name: /제롬 파월/ }));
    await view.findByRole('heading', { name: '제롬 파월' });
    fireEvent.click(view.getByRole('button', { name: '게시물 직접 등록' }));
    expect((view.getByLabelText('게시물 URL') as HTMLInputElement).value).toBe('');
  });

  it('게시물 저장 중 인물 변경을 막고 저장 후에도 프로필 편집 내용을 유지한다', async () => {
    const originalFetch = fetch;
    let finishSave: (() => void) | undefined;
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        if (String(input).endsWith('/api/admin/people/1/posts') && init?.method === 'POST') {
          await new Promise<void>((resolve) => {
            finishSave = resolve;
          });
        }
        return originalFetch(input, init);
      }),
    );
    const view = renderPeopleMode();
    await view.findByRole('heading', { name: '일론 머스크' });
    fireEvent.click(view.getByRole('button', { name: '프로필 수정' }));
    const names = view.getAllByLabelText('인물 이름');
    fireEvent.change(names[names.length - 1], { target: { value: '편집 중인 이름' } });
    fireEvent.click(view.getByRole('button', { name: '게시물 직접 등록' }));
    fireEvent.change(view.getByLabelText('게시물 URL'), {
      target: { value: 'https://x.com/elonmusk/status/12345' },
    });
    fireEvent.click(view.getByRole('button', { name: '게시물 저장' }));
    await waitFor(() => expect(finishSave).toBeTypeOf('function'));
    expect((view.getByRole('button', { name: /제롬 파월/ }) as HTMLButtonElement).disabled).toBe(
      true,
    );
    finishSave!();
    await view.findByRole('button', { name: '등록된 게시물 보기' });
    fireEvent.click(view.getByRole('button', { name: '프로필 수정' }));
    expect(view.getByDisplayValue('편집 중인 이름')).toBeTruthy();
  });

  it('붙여넣은 게시물 주소에서 출처를 선택하고 잘못된 주소에는 안내한다', async () => {
    const view = renderPeopleMode();
    await view.findByRole('heading', { name: '일론 머스크' });
    fireEvent.click(view.getByRole('button', { name: '게시물 직접 등록' }));
    const input = await view.findByLabelText('게시물 URL');
    fireEvent.change(input, { target: { value: 'https://www.threads.net/@example/post/ABC' } });
    expect((view.getByLabelText('출처') as HTMLSelectElement).value).toBe('THREADS');
    expect((view.getByRole('button', { name: '게시물 저장' }) as HTMLButtonElement).disabled).toBe(
      false,
    );
    fireEvent.change(input, { target: { value: 'https://x.com/example' } });
    expect(input.getAttribute('aria-invalid')).toBe('true');
    expect(view.getByText(/프로필 주소 대신/)).toBeTruthy();
    expect((view.getByRole('button', { name: '게시물 저장' }) as HTMLButtonElement).disabled).toBe(
      true,
    );
  });

  it('프로필에서 복사한 게시물 URL을 미리 보고 저장한다', async () => {
    const view = renderPeopleMode();

    expect(await view.findByRole('heading', { name: '일론 머스크' })).toBeTruthy();
    expect(view.getByRole('link', { name: 'X 프로필 열기 ↗' }).getAttribute('href')).toBe(
      'https://x.com/elonmusk',
    );
    fireEvent.click(view.getByRole('button', { name: '게시물 직접 등록' }));

    fireEvent.change(view.getByLabelText('게시물 URL'), {
      target: { value: 'https://x.com/elonmusk/status/12345' },
    });

    expect(view.getByLabelText('게시물 미리보기')).toBeTruthy();
    fireEvent.click(view.getByRole('button', { name: '게시물 저장' }));

    fireEvent.click(await view.findByRole('button', { name: '등록된 게시물 보기' }));
    expect(await view.findByText('일론 머스크 X 게시물')).toBeTruthy();
    expect(view.getByRole('button', { name: '등록된 게시물 1' })).toBeTruthy();
    expect(
      view.getByText('게시물을 등록했습니다. 등록된 게시물에서 확인할 수 있습니다.'),
    ).toBeTruthy();
  });

  it('공식 프로필에서 수집한 게시물을 연속으로 승인한다', async () => {
    const view = renderPeopleMode();

    await view.findByRole('heading', { name: '일론 머스크' });
    fireEvent.click(view.getByRole('button', { name: /^수집 후보/ }));
    fireEvent.click(view.getByRole('button', { name: '공식 프로필 지금 수집' }));

    expect(await view.findByText('일론 머스크 X 검색 결과')).toBeTruthy();
    expect(view.getByText('일론 머스크 Threads 검색 결과')).toBeTruthy();
    expect(view.getByText('2026-10-12')).toBeTruthy();
    expect(view.getByRole('button', { name: '수집 후보 2' })).toBeTruthy();

    fireEvent.click(view.getAllByRole('button', { name: '승인' })[0]);

    await waitFor(() => expect(view.getAllByRole('button', { name: '승인' })).toHaveLength(1));
    expect(view.getByRole('button', { name: '등록된 게시물 1' })).toBeTruthy();
    expect(
      view.getByText('게시물을 승인했습니다. 등록된 게시물에서 확인할 수 있습니다.'),
    ).toBeTruthy();

    await waitFor(() => {
      expect((view.getByRole('button', { name: '승인' }) as HTMLButtonElement).disabled).toBe(
        false,
      );
    });
    fireEvent.click(view.getByRole('button', { name: '승인' }));

    await waitFor(() => {
      expect(view.queryByRole('button', { name: '승인' })).toBeNull();
    });
    expect(view.getByRole('button', { name: '수집 후보 0' })).toBeTruthy();
    fireEvent.click(view.getByRole('button', { name: '등록된 게시물 2' }));
    expect(view.getByText('2건 · 최근 등록순')).toBeTruthy();
    fireEvent.click(view.getByRole('button', { name: 'Threads 1' }));
    expect(view.queryByText('일론 머스크 X 검색 결과')).toBeNull();
    expect(view.getByText('일론 머스크 Threads 검색 결과')).toBeTruthy();
    fireEvent.change(view.getByRole('searchbox', { name: '등록된 게시물 검색' }), {
      target: { value: '없는 글' },
    });
    expect(view.getByText('조건에 맞는 게시물이 없습니다.')).toBeTruthy();
    fireEvent.click(view.getByRole('button', { name: '검색·필터 초기화' }));
    expect(view.getByText('일론 머스크 X 검색 결과')).toBeTruthy();
    expect(view.getByText('일론 머스크 Threads 검색 결과')).toBeTruthy();
    fireEvent.click(view.getAllByRole('button', { name: '미리보기' })[0]);
    expect(view.getAllByRole('button', { name: '미리보기' })[0].getAttribute('aria-expanded')).toBe(
      'true',
    );

    expect(
      vi
        .mocked(fetch)
        .mock.calls.filter(([input]) =>
          String(input).includes('/api/admin/people/1/post-candidates/collect?'),
        ),
    ).toHaveLength(1);
    const collectionRequest = vi
      .mocked(fetch)
      .mock.calls.find(([input]) =>
        String(input).includes('/api/admin/people/1/post-candidates/collect?'),
      );
    expect(String(collectionRequest?.[0])).toContain('from=');
    expect(String(collectionRequest?.[0])).toContain('to=');
  });

  it('수집한 게시물 후보를 거절하면 승인 대기 목록에서 제거한다', async () => {
    const view = renderPeopleMode();

    await view.findByRole('heading', { name: '일론 머스크' });
    fireEvent.click(view.getByRole('button', { name: /^수집 후보/ }));
    fireEvent.click(view.getByRole('button', { name: '공식 프로필 지금 수집' }));
    await view.findByText('일론 머스크 X 검색 결과');

    fireEvent.click(view.getAllByRole('button', { name: '거절' })[0]);

    await waitFor(() => expect(view.getAllByRole('button', { name: '거절' })).toHaveLength(1));
    expect(view.getByText('게시물 후보를 거절했습니다.')).toBeTruthy();
  });

  it('현재 목록을 선택해 일괄 승인한다', async () => {
    const confirm = vi.fn().mockReturnValue(true);
    vi.stubGlobal('confirm', confirm);
    const view = renderPeopleMode();

    await view.findByRole('heading', { name: '일론 머스크' });
    fireEvent.click(view.getByRole('button', { name: /^수집 후보/ }));
    fireEvent.click(view.getByRole('button', { name: '공식 프로필 지금 수집' }));
    await view.findByText('일론 머스크 X 검색 결과');

    fireEvent.click(view.getByRole('checkbox', { name: '현재 목록 전체 선택' }));
    fireEvent.click(view.getByRole('button', { name: '선택 승인 (2)' }));

    expect(confirm).toHaveBeenCalledWith('선택한 2건을 승인할까요?');
    expect(await view.findByText('2건을 승인했습니다.')).toBeTruthy();
    expect(view.getByText('승인 대기 중인 게시물이 없습니다.')).toBeTruthy();
    expect(view.getByRole('button', { name: '등록된 게시물 2' })).toBeTruthy();
    const request = vi
      .mocked(fetch)
      .mock.calls.find(([input]) => String(input).endsWith('/post-candidates/bulk-approve'));
    expect(JSON.parse(String(request?.[1]?.body))).toEqual({ candidateIds: [11, 12] });
  });

  it('일괄 승인에 일부 실패하면 실패 후보만 선택 상태로 남긴다', async () => {
    vi.stubGlobal('confirm', vi.fn().mockReturnValue(true));
    const originalFetch = fetch;
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        if (String(input).endsWith('/post-candidates/bulk-approve')) {
          return jsonResponse({
            succeeded: [
              {
                candidateId: 11,
                post: {
                  id: 111,
                  sourceType: 'X',
                  title: '일론 머스크 X 검색 결과',
                  url: 'https://x.com/elonmusk/status/22222',
                  publishedAt: null,
                  createdAt: null,
                },
              },
            ],
            failed: [
              {
                candidateId: 12,
                errorCode: 'FEATURED_PERSON_POST_CANDIDATE_NOT_PENDING',
                message: '대기 중인 게시물 후보만 검토할 수 있습니다.',
              },
            ],
          });
        }
        return originalFetch(input, init);
      }),
    );
    const view = renderPeopleMode();

    await view.findByRole('heading', { name: '일론 머스크' });
    fireEvent.click(view.getByRole('button', { name: /^수집 후보/ }));
    fireEvent.click(view.getByRole('button', { name: '공식 프로필 지금 수집' }));
    await view.findByText('일론 머스크 X 검색 결과');
    fireEvent.click(view.getByRole('checkbox', { name: '현재 목록 전체 선택' }));
    fireEvent.click(view.getByRole('button', { name: '선택 승인 (2)' }));

    expect(await view.findByText(/1건을 처리하지 못했습니다/)).toBeTruthy();
    expect(view.queryByText('일론 머스크 X 검색 결과')).toBeNull();
    expect(view.getByText('일론 머스크 Threads 검색 결과')).toBeTruthy();
    expect(
      (
        view.getByRole('checkbox', {
          name: '일론 머스크 Threads 검색 결과 선택',
        }) as HTMLInputElement
      ).checked,
    ).toBe(true);
  });

  it('선택한 후보를 일괄 거절한다', async () => {
    vi.stubGlobal('confirm', vi.fn().mockReturnValue(true));
    const view = renderPeopleMode();

    await view.findByRole('heading', { name: '일론 머스크' });
    fireEvent.click(view.getByRole('button', { name: /^수집 후보/ }));
    fireEvent.click(view.getByRole('button', { name: '공식 프로필 지금 수집' }));
    await view.findByText('일론 머스크 X 검색 결과');

    fireEvent.click(view.getByRole('checkbox', { name: '일론 머스크 X 검색 결과 선택' }));
    fireEvent.click(view.getByRole('button', { name: '선택 거절 (1)' }));

    expect(await view.findByText('1건을 거절했습니다.')).toBeTruthy();
    expect(view.queryByText('일론 머스크 X 검색 결과')).toBeNull();
    expect(view.getByText('일론 머스크 Threads 검색 결과')).toBeTruthy();
  });

  it('수동 수집 기간을 최대 31일로 제한한다', async () => {
    const view = renderPeopleMode();

    await view.findByRole('heading', { name: '일론 머스크' });
    fireEvent.click(view.getByRole('button', { name: /^수집 후보/ }));
    fireEvent.change(view.getByLabelText('수집 시작일'), {
      target: { value: '2026-01-01' },
    });
    fireEvent.change(view.getByLabelText('수집 종료일'), {
      target: { value: '2026-02-01' },
    });
    fireEvent.click(view.getByRole('button', { name: '공식 프로필 지금 수집' }));

    expect(view.getByText('수집 기간은 최대 31일까지 선택할 수 있습니다.')).toBeTruthy();
  });

  it('Wikimedia Commons에서 고른 이미지를 인물 프로필로 저장한다', async () => {
    const view = renderPeopleMode();
    fireEvent.click(await view.findByRole('button', { name: '프로필 수정' }));

    await view.findByDisplayValue('https://x.com/elonmusk');
    fireEvent.change(view.getByLabelText('프로필 이미지 검색어'), {
      target: { value: 'Elon Musk' },
    });
    fireEvent.click(view.getByRole('button', { name: 'Wikimedia 검색' }));
    expect(await view.findByAltText('File:Elon Musk.jpg')).toBeTruthy();

    fireEvent.click(view.getByRole('button', { name: /Elon Musk.jpg/ }));
    expect(view.getByText('저장할 이미지')).toBeTruthy();
    fireEvent.click(view.getByRole('button', { name: '선택 취소' }));
    expect(view.queryByText('저장할 이미지')).toBeNull();
    fireEvent.click(view.getByRole('button', { name: /Elon Musk.jpg/ }));
    fireEvent.click(view.getByRole('button', { name: '변경사항 저장' }));
    expect(
      (view.getByRole('button', { name: /Elon Musk.jpg/ }) as HTMLButtonElement).disabled,
    ).toBe(true);

    await waitFor(() => {
      expect(view.getByAltText('일론 머스크 프로필').getAttribute('src')).toBe(
        'https://images.daynomy.test/elon.jpg',
      );
    });
  });

  it('전체 수집 후보 탭에서 여러 인물의 후보를 한 번에 선택해 승인한다', async () => {
    const confirm = vi.fn().mockReturnValue(true);
    vi.stubGlobal('confirm', confirm);
    const view = render(<AdminPeoplePage />);

    const globalCandidateTab = await view.findByRole('button', { name: '전체 수집 후보 2' });
    expect(globalCandidateTab.getAttribute('aria-pressed')).toBe('true');

    expect(await view.findByRole('heading', { name: /전체 수집 후보/ })).toBeTruthy();
    expect(view.getByText('일론 머스크 전체 후보')).toBeTruthy();
    expect(view.getByText('제롬 파월 전체 후보')).toBeTruthy();
    fireEvent.click(view.getByRole('checkbox', { name: '현재 페이지 전체 선택' }));
    fireEvent.click(view.getByRole('button', { name: '선택 승인 (2)' }));

    expect(confirm).toHaveBeenCalledWith('선택한 2건을 승인할까요?');
    expect(await view.findByText('2건을 승인했습니다.')).toBeTruthy();
    const request = vi
      .mocked(fetch)
      .mock.calls.find(([input]) =>
        String(input).endsWith('/api/admin/people/post-candidates/bulk-approve'),
      );
    expect(JSON.parse(String(request?.[1]?.body))).toEqual({ candidateIds: [21, 22] });
  });

  it('전체 후보 일괄 승인에 일부 실패하면 실패 후보만 선택 상태로 남긴다', async () => {
    vi.stubGlobal('confirm', vi.fn().mockReturnValue(true));
    const originalFetch = fetch;
    let processed = false;
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        if (url.endsWith('/api/admin/people/post-candidates/bulk-approve')) {
          processed = true;
          return jsonResponse({
            succeeded: [{ candidateId: 21, post: null }],
            failed: [{ candidateId: 22, errorCode: 'INTERNAL_ERROR', message: '실패' }],
          });
        }
        if (processed && url.includes('/api/admin/people/post-candidates?') && !init?.method) {
          return jsonResponse({
            candidates: [
              {
                id: 22,
                personId: 2,
                personName: '제롬 파월',
                personProfileImageUrl: null,
                sourceType: 'YOUTUBE',
                title: '제롬 파월 전체 후보',
                url: 'https://www.youtube.com/watch?v=abcdefghijk',
                publishedAt: null,
                status: 'PENDING',
                createdAt: null,
              },
            ],
            page: 1,
            size: 20,
            totalElements: 1,
            totalPages: 1,
          });
        }
        return originalFetch(input, init);
      }),
    );
    const view = render(<AdminPeoplePage />);

    fireEvent.click(await view.findByRole('button', { name: '전체 수집 후보 2' }));
    await view.findByText('일론 머스크 전체 후보');
    fireEvent.click(view.getByRole('checkbox', { name: '현재 페이지 전체 선택' }));
    fireEvent.click(view.getByRole('button', { name: '선택 승인 (2)' }));

    expect(await view.findByText(/1건을 처리하지 못했습니다/)).toBeTruthy();
    await waitFor(() => expect(view.queryByText('일론 머스크 전체 후보')).toBeNull());
    expect(
      view.getByRole('checkbox', { name: '제롬 파월 제롬 파월 전체 후보 선택' }),
    ).toHaveProperty('checked', true);
  });
});
