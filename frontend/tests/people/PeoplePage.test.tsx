/** @vitest-environment jsdom */
import { trackEvent } from '../../src/analytics';
vi.mock('../../src/analytics', () => ({ trackEvent: vi.fn() }));

import { cleanup, fireEvent, render } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Link, MemoryRouter, useNavigate } from 'react-router-dom';
import { PeoplePage } from '../../src/features/people/PeoplePage';

function jsonResponse(body: unknown) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'content-type': 'application/json' },
  });
}

function peoplePage(people: unknown[], page = 1, size = 6, totalElements = 0) {
  const totalPages = Math.ceil(totalElements / size);
  return {
    people,
    page,
    size,
    totalElements,
    totalPages,
    hasNext: page < totalPages,
  };
}

beforeEach(() => {
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe() {}
      disconnect() {}
    },
  );
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-10-12T10:00:00Z'));
  vi.stubGlobal(
    'fetch',
    vi.fn(async () =>
      jsonResponse(
        peoplePage(
          [
            {
              id: 1,
              name: '일론 머스크',
              xProfileUrl: 'https://x.com/elonmusk',
              threadsProfileUrl: null,
              createdAt: null,
              posts: [
                {
                  id: 11,
                  sourceType: 'X',
                  title: '일론 머스크 X 게시물',
                  url: 'https://x.com/elonmusk/status/12345',
                  createdAt: '2026-10-11T09:00:00Z',
                },
              ],
            },
            {
              id: 2,
              name: '도널드 트럼프',
              xProfileUrl: null,
              threadsProfileUrl: 'https://www.threads.net/@realdonaldtrump',
              createdAt: null,
              posts: [
                {
                  id: 12,
                  sourceType: 'THREADS',
                  title: '도널드 트럼프 Threads 게시물',
                  url: 'https://www.threads.net/@realdonaldtrump/post/ABC',
                  createdAt: '2026-10-12T09:00:00Z',
                },
              ],
            },
          ],
          1,
          6,
          2,
        ),
      ),
    ),
  );
});

afterEach(() => {
  cleanup();
  vi.mocked(trackEvent).mockClear();
  document.getElementById('daynomy-x-embed-script')?.remove();
  document.getElementById('daynomy-threads-embed-script')?.remove();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  vi.useRealTimers();
});

describe('인물의 글 화면', () => {
  it('인물 찾기 입력은 목록을 유지하고 검색 결과에서 인물을 선택한다', async () => {
    const view = render(
      <MemoryRouter>
        <PeoplePage />
      </MemoryRouter>,
    );
    await view.findByRole('button', { name: /일론 머스크 X 게시물/ });
    fireEvent.click(view.getByRole('button', { name: '인물 찾기' }));
    fireEvent.change(view.getByRole('searchbox', { name: '인물 이름 검색' }), {
      target: { value: '도널드' },
    });
    expect(view.container.querySelectorAll('.people-search-results button')).toHaveLength(1);
    expect(view.getByRole('button', { name: /일론 머스크 X 게시물/ })).toBeTruthy();
    fireEvent.click(view.container.querySelector('.people-search-results button')!);
    expect(await view.findByRole('button', { name: /도널드 트럼프 Threads 게시물/ })).toBeTruthy();
    expect(view.getByRole('button', { name: '인물 찾기' }).getAttribute('aria-expanded')).toBe(
      'false',
    );
  });

  it('목록이 화면에 들어오면 화살표를 숨기고 넘치면 표시한다', async () => {
    const view = render(
      <MemoryRouter>
        <PeoplePage />
      </MemoryRouter>,
    );
    await view.findByRole('button', { name: '일론 머스크' });
    const strip = view.getByRole('navigation', { name: '인물 선택' });
    strip.scrollLeft = 0;
    fireEvent.scroll(strip);
    expect(view.queryByRole('button', { name: '이전 인물 보기' })).toBeNull();
    expect(view.queryByRole('button', { name: '다음 인물 보기' })).toBeNull();
    Object.defineProperty(strip, 'clientWidth', { configurable: true, value: 300 });
    Object.defineProperty(strip, 'scrollWidth', { configurable: true, value: 600 });
    fireEvent.scroll(strip);
    expect(view.getByRole('button', { name: '다음 인물 보기' })).toBeTruthy();
    expect(
      (view.getByRole('button', { name: '이전 인물 보기' }) as HTMLButtonElement).disabled,
    ).toBe(true);
    view.container.querySelectorAll('.people-scroll-arrow').forEach((arrow) => {
      Object.defineProperty(arrow, 'offsetWidth', { configurable: true, value: 32 });
    });
    Object.defineProperty(strip, 'clientWidth', { configurable: true, value: 536 });
    fireEvent.scroll(strip);
    expect(view.queryByRole('button', { name: '다음 인물 보기' })).toBeNull();
  });

  it('최근 등록순으로 글을 보여주고 선택한 글만 원문을 펼친다', async () => {
    const view = render(
      <MemoryRouter initialEntries={['/people?view=all']}>
        <PeoplePage />
      </MemoryRouter>,
    );
    expect(await view.findByRole('button', { name: /도널드 트럼프 Threads 게시물/ })).toBeTruthy();
    expect(view.container.querySelector('blockquote')).toBeNull();
    expect(view.getByRole('link', { name: '일론 머스크' }).getAttribute('href')).toBe(
      '/people?person=1&view=all',
    );
    fireEvent.click(view.getByRole('button', { name: /도널드 트럼프 Threads 게시물/ }));
    expect(
      view.container
        .querySelector('blockquote.text-post-media')
        ?.getAttribute('data-text-post-permalink'),
    ).toBe('https://www.threads.net/@realdonaldtrump/post/ABC');
    fireEvent.click(view.getByRole('button', { name: /일론 머스크 X 게시물/ }));
    expect(view.container.querySelector('blockquote.text-post-media')).toBeNull();
    expect(view.container.querySelector('blockquote.twitter-tweet')).toBeTruthy();
    fireEvent.click(view.getByRole('button', { name: '일론 머스크' }));
    expect(view.getByRole('heading', { name: '인물의 글' })).toBeTruthy();
    expect((await view.findByRole('link', { name: /X 프로필/ })).getAttribute('href')).toBe(
      'https://x.com/elonmusk',
    );
    expect(view.queryByRole('button', { name: /도널드 트럼프 Threads 게시물/ })).toBeNull();
  });

  it('상단 이름 메뉴로 인물을 선택하고 전체로 돌아온다', async () => {
    const view = render(
      <MemoryRouter initialEntries={['/people?view=all']}>
        <PeoplePage />
      </MemoryRouter>,
    );
    await view.findByRole('button', { name: /일론 머스크 X 게시물/ });
    fireEvent.click(view.getByRole('button', { name: '도널드 트럼프' }));
    await view.findByRole('button', { name: /도널드 트럼프 Threads 게시물/ });
    expect(view.queryByRole('button', { name: /일론 머스크 X 게시물/ })).toBeNull();
    expect(view.getByRole('button', { name: '도널드 트럼프' }).getAttribute('aria-pressed')).toBe(
      'true',
    );
    fireEvent.click(view.getByRole('button', { name: '전체' }));
    expect(await view.findByRole('button', { name: /일론 머스크 X 게시물/ })).toBeTruthy();
    expect(view.queryByRole('searchbox')).toBeNull();
    expect(view.queryByRole('combobox')).toBeNull();
  });

  it('인물 링크를 직접 열면 해당 인물의 글과 프로필을 보여준다', async () => {
    const view = render(
      <MemoryRouter initialEntries={['/people?person=2']}>
        <PeoplePage />
      </MemoryRouter>,
    );
    expect(await view.findByRole('button', { name: /도널드 트럼프 Threads 게시물/ })).toBeTruthy();
    expect(view.queryByRole('button', { name: /일론 머스크 X 게시물/ })).toBeNull();
    expect(view.container.querySelector('.issue-feed--visual .issue-feed-thumbnail')).toBeTruthy();
    expect(view.container.querySelector('.issue-feed-thumbnail-threads')?.textContent).toBe(
      'Threads',
    );
  });
  it('지난 글도 처음부터 보여주고 홈 링크와 제목을 유지한다', async () => {
    const view = render(
      <MemoryRouter>
        <PeoplePage />
      </MemoryRouter>,
    );
    expect(await view.findByRole('button', { name: /일론 머스크 X 게시물/ })).toBeTruthy();
    expect(view.queryByRole('link', { name: /지난 글까지 전체 보기/ })).toBeNull();
    fireEvent.click(view.getByRole('button', { name: '일론 머스크' }));
    await view.findByRole('button', { name: /일론 머스크 X 게시물/ });
    expect(view.getByRole('heading', { name: '인물의 글' })).toBeTruthy();
    expect(view.getByRole('link', { name: '← 소식 홈' }).getAttribute('href')).toBe('/news');
    expect(view.getByRole('button', { name: '1페이지' })).toBeTruthy();
  });
});

function PersonUrlNavigation() {
  const navigate = useNavigate();
  return (
    <>
      <Link to="/people?person=2">다른 인물 URL로 이동</Link>
      <button onClick={() => navigate(-1)}>뒤로 가기</button>
    </>
  );
}

it('URL로 인물을 변경하거나 뒤로 가면 해당 인물의 첫 페이지를 보여준다', async () => {
  vi.mocked(fetch).mockImplementation(async (input) => {
    const url = new URL(String(input), 'http://localhost');
    const page = Number(url.searchParams.get('page'));
    const personId = Number(url.searchParams.get('personId'));
    const allPosts = Array.from({ length: 30 }, (_, index) => ({
      id: personId * 100 + index,
      sourceType: 'X',
      title: `인물${personId} 글${index + 1}`,
      url: `https://x.com/person${personId}/status/${index + 1}`,
      createdAt: new Date(Date.parse('2026-10-12T09:00:00Z') + index * 1000).toISOString(),
    })).reverse();
    const posts = allPosts.slice((page - 1) * 6, page * 6);
    return jsonResponse(
      peoplePage(
        [1, 2].map((id) => ({
          id,
          name: `인물${id}`,
          xProfileUrl: null,
          threadsProfileUrl: null,
          youtubeProfileUrl: null,
          profileImageUrl: null,
          createdAt: null,
          posts: id === personId ? posts : [],
        })),
        page,
        6,
        30,
      ),
    );
  });
  const view = render(
    <MemoryRouter initialEntries={['/people?person=1']}>
      <PersonUrlNavigation />
      <PeoplePage />
    </MemoryRouter>,
  );
  await view.findByRole('button', { name: '5페이지' });
  expect(view.container.querySelectorAll('.issue-feed > li')).toHaveLength(6);
  expect(view.queryByRole('button', { name: '6페이지' })).toBeNull();
  fireEvent.click(view.getByRole('button', { name: '3페이지' }));
  await view.findByText('인물1 글18', { exact: true });
  expect(view.container.querySelectorAll('.issue-feed > li')).toHaveLength(6);
  expect(view.getByText('인물1 글18', { exact: true })).toBeTruthy();
  expect(view.queryByText('인물1 글30', { exact: true })).toBeNull();
  expect(view.getByRole('button', { name: '3페이지' }).getAttribute('aria-current')).toBe('page');
  fireEvent.click(view.getByRole('link', { name: '다른 인물 URL로 이동' }));
  await view.findByText('인물2 글30', { exact: true });
  expect(view.getByRole('button', { name: '1페이지' }).getAttribute('aria-current')).toBe('page');
  expect(view.getByText('인물2 글30', { exact: true })).toBeTruthy();
  fireEvent.click(view.getByRole('button', { name: '2페이지' }));
  await view.findByText('인물2 글24', { exact: true });
  fireEvent.click(view.getByRole('button', { name: '뒤로 가기' }));
  await view.findByText('인물1 글30', { exact: true });
  expect(view.getByRole('button', { name: '1페이지' }).getAttribute('aria-current')).toBe('page');
  expect(view.getByText('인물1 글30', { exact: true })).toBeTruthy();
  const views = vi
    .mocked(trackEvent)
    .mock.calls.filter(([name]) => name === 'view_content_list')
    .map(([, params]) => [params?.person_id, params?.page_number])
    .filter((view, index, all) => index === 0 || String(view) !== String(all[index - 1]));
  expect(views).toEqual([
    [1, 1],
    [1, 3],
    [2, 1],
    [2, 2],
    [1, 1],
  ]);
  expect(vi.mocked(trackEvent).mock.calls.some(([name]) => name === 'select_featured_person')).toBe(
    false,
  );
});
