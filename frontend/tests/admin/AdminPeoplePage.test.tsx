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
  it('작업 전환은 입력을 유지하고 다른 인물로 바꿀 때만 폐기를 확인한다', async () => {
    const confirm = vi.fn().mockReturnValue(false);
    vi.stubGlobal('confirm', confirm);
    const view = render(<AdminPeoplePage />);
    await view.findByRole('heading', { name: '일론 머스크' });
    expect(view.queryByLabelText('게시물 URL')).toBeNull();
    fireEvent.click(view.getByRole('button', { name: '게시물 추가' }));
    fireEvent.change(view.getByLabelText('게시물 URL'), {
      target: { value: 'https://x.com/elonmusk/status/12345' },
    });
    fireEvent.click(view.getByRole('button', { name: '프로필 수정' }));
    fireEvent.click(view.getByRole('button', { name: '게시물 추가' }));
    expect((view.getByLabelText('게시물 URL') as HTMLInputElement).value).toContain('12345');
    fireEvent.click(view.getByRole('button', { name: /제롬 파월/ }));
    expect(confirm).toHaveBeenCalledTimes(1);
    expect(view.getByRole('heading', { name: '일론 머스크' })).toBeTruthy();
    confirm.mockReturnValue(true);
    fireEvent.click(view.getByRole('button', { name: /제롬 파월/ }));
    await view.findByRole('heading', { name: '제롬 파월' });
    fireEvent.click(view.getByRole('button', { name: '게시물 추가' }));
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
    const view = render(<AdminPeoplePage />);
    await view.findByRole('heading', { name: '일론 머스크' });
    fireEvent.click(view.getByRole('button', { name: '프로필 수정' }));
    const names = view.getAllByLabelText('인물 이름');
    fireEvent.change(names[names.length - 1], { target: { value: '편집 중인 이름' } });
    fireEvent.click(view.getByRole('button', { name: '게시물 추가' }));
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
    const view = render(<AdminPeoplePage />);
    await view.findByRole('heading', { name: '일론 머스크' });
    fireEvent.click(view.getByRole('button', { name: '게시물 추가' }));
    const input = view.getByLabelText('게시물 URL');
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
    const view = render(<AdminPeoplePage />);

    expect(await view.findByRole('heading', { name: '일론 머스크' })).toBeTruthy();
    expect(view.getByRole('link', { name: 'X 프로필 열기 ↗' }).getAttribute('href')).toBe(
      'https://x.com/elonmusk',
    );
    fireEvent.click(view.getByRole('button', { name: '게시물 추가' }));

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

  it('한 번 검색한 X·Threads 게시물을 연속으로 등록한다', async () => {
    const view = render(<AdminPeoplePage />);

    await view.findByRole('heading', { name: '일론 머스크' });
    fireEvent.click(view.getByRole('button', { name: '게시물 추가' }));
    fireEvent.click(view.getByRole('button', { name: 'AI로 찾아 등록' }));
    fireEvent.click(view.getByRole('button', { name: 'AI로 X·Threads 검색' }));

    expect(await view.findByText('일론 머스크 X 검색 결과')).toBeTruthy();
    expect(view.getByText('일론 머스크 Threads 검색 결과')).toBeTruthy();
    expect(view.getByText('2026-10-12')).toBeTruthy();

    fireEvent.click(view.getAllByRole('button', { name: '이 게시물 등록' })[0]);

    expect(await view.findByRole('button', { name: '등록됨' })).toBeTruthy();
    expect(view.getByRole('button', { name: '등록된 게시물 1' })).toBeTruthy();
    expect(
      view.getByText('게시물을 등록했습니다. 등록된 게시물에서 확인할 수 있습니다.'),
    ).toBeTruthy();

    await waitFor(() => {
      expect(
        (view.getByRole('button', { name: '이 게시물 등록' }) as HTMLButtonElement).disabled,
      ).toBe(false);
    });
    fireEvent.click(view.getByRole('button', { name: '이 게시물 등록' }));

    await waitFor(() => {
      expect(view.getAllByRole('button', { name: '등록됨' })).toHaveLength(2);
    });
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
        .mock.calls.filter(([input]) => String(input).endsWith('/api/admin/people/1/posts/search')),
    ).toHaveLength(1);
  });

  it('Wikimedia Commons에서 고른 이미지를 인물 프로필로 저장한다', async () => {
    const view = render(<AdminPeoplePage />);
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
});
