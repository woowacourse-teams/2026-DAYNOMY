/** @vitest-environment jsdom */
import { trackEvent } from '../../src/analytics';
vi.mock('../../src/analytics', () => ({ trackEvent: vi.fn() }));
import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { IssueHomePage } from '../../src/features/news/issue/IssueHomePage';
const article = {
  id: 7,
  title: '오늘의 데이노미 이슈',
  category: 'STOCK',
  imageUrl: null,
  publishedAt: '2026-10-12T09:00:00Z',
};
const person = {
  id: 1,
  name: '테스트 인물',
  xProfileUrl: null,
  threadsProfileUrl: null,
  createdAt: null,
  posts: [
    {
      id: 1,
      sourceType: 'X',
      title: '첫 번째 인물 글',
      url: 'https://x.com/example/status/1',
      createdAt: '2026-10-12T08:00:00Z',
    },
    {
      id: 2,
      sourceType: 'THREADS',
      title: '두 번째 인물 글',
      url: 'https://www.threads.net/@example/post/ABC',
      createdAt: '2026-10-12T07:00:00Z',
    },
  ],
};
function json(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}
function newsPage(items = [article]) {
  return { items, page: 1, size: 6, totalPages: 1, totalElements: items.length, hasNext: false };
}
function mockApi(peopleFail = false) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) =>
      new URL(String(input), 'http://localhost').pathname === '/api/contents'
        ? json(newsPage([]))
        : new URL(String(input), 'http://localhost').pathname === '/api/people'
          ? json(peopleFail ? {} : { people: [person] }, peopleFail ? 500 : 200)
          : json(newsPage()),
    ),
  );
}
beforeEach(() => {
  vi.stubGlobal('matchMedia', () => ({
    matches: false,
    addEventListener() {},
    removeEventListener() {},
  }));
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe() {}
      disconnect() {}
    },
  );
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-10-12T10:00:00Z'));
});
afterEach(() => {
  vi.useRealTimers();
  cleanup();
  vi.mocked(trackEvent).mockClear();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  document.getElementById('daynomy-x-embed-script')?.remove();
  document.getElementById('daynomy-threads-embed-script')?.remove();
});
describe('이슈 홈과 더보기', () => {
  it('오늘의 이슈와 인물 글을 함께 표시하고 각각의 더보기 경로를 제공한다', async () => {
    mockApi();
    const view = render(
      <MemoryRouter>
        <IssueHomePage />
      </MemoryRouter>,
    );
    await view.findByRole('button', { name: /첫 번째 인물 글/ });
    expect(view.queryByRole('link', { name: '오늘의 이슈 더 보기' })).toBeNull();
    expect(view.container.querySelector('.issue-story-card')?.getAttribute('href')).toBe('/news/7');
    expect(
      view.getByRole('link', { name: '주목받는 인물의 글 전체 보기' }).getAttribute('href'),
    ).toBe('/people');
    expect(view.getByRole('link', { name: /전체 콘텐츠 더 보기/ }).getAttribute('href')).toBe(
      '/news/contents',
    );
    expect(view.container.querySelector('.issue-people-preview a')?.getAttribute('href')).toBe(
      '/people?person=1',
    );
    expect(view.container.querySelector('blockquote')).toBeNull();
    fireEvent.click(view.getByRole('button', { name: 'Threads' }));
    expect(view.getByRole('button', { name: 'Threads', pressed: true })).toBeTruthy();
    expect(view.getByRole('button', { name: '전체', pressed: false })).toBeTruthy();
    expect(view.queryByRole('button', { name: /첫 번째 인물 글/ })).toBeNull();
    fireEvent.click(view.getByRole('button', { name: /두 번째 인물 글/ }));
    expect(view.container.querySelector('blockquote.text-post-media')).toBeTruthy();
  });
  it('인물 API가 실패해도 뉴스는 유지하고 재시도할 수 있다', async () => {
    mockApi(true);
    const view = render(
      <MemoryRouter>
        <IssueHomePage fullFeed />
      </MemoryRouter>,
    );
    expect(await view.findByRole('alert')).toBeTruthy();
    expect(await view.findByRole('link', { name: /오늘의 데이노미 이슈/ })).toBeTruthy();
    mockApi();
    fireEvent.click(view.getByRole('button', { name: '다시 시도' }));
    expect(await view.findByRole('button', { name: /첫 번째 인물 글/ })).toBeTruthy();
    expect(view.queryByRole('alert')).toBeNull();
  });
  it('전체 콘텐츠는 모든 뉴스 페이지를 모아 최신순으로 표시한다', async () => {
    const older = { ...article, id: 8, title: '이전 이슈', publishedAt: '2026-10-10T00:00:00Z' };
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        if (new URL(String(input), 'http://localhost').pathname === '/api/contents')
          return json({
            ...newsPage([]),
            page: Number(new URL(String(input), 'http://localhost').searchParams.get('page')),
          });
        if (new URL(String(input), 'http://localhost').pathname === '/api/people')
          return json({ people: [person] });
        const page = Number(new URL(String(input), 'http://localhost').searchParams.get('page'));
        return json({
          ...newsPage(page === 1 ? [article] : [older]),
          page,
          totalPages: 2,
          totalElements: 2,
          hasNext: page === 1,
        });
      }),
    );
    const view = render(
      <MemoryRouter>
        <IssueHomePage fullFeed />
      </MemoryRouter>,
    );
    expect(await view.findByRole('link', { name: /이전 이슈/ })).toBeTruthy();
    expect(view.getByRole('link', { name: /오늘의 데이노미 이슈/ })).toBeTruthy();
    expect(view.getByRole('button', { name: /첫 번째 인물 글/ })).toBeTruthy();
    expect(view.queryByRole('button', { name: /콘텐츠 더 보기/ })).toBeNull();
  });
  it('홈에는 오늘 등록된 글을 최대 여덟 개 표시하고 지난 글은 제외한다', async () => {
    const posts = Array.from({ length: 15 }, (_, index) => ({
      ...person.posts[0],
      id: index + 10,
      title: `오늘 글 ${index}`,
    }));
    posts.push({ ...person.posts[0], id: 99, title: '어제 글', createdAt: '2026-10-11T08:00:00Z' });
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) =>
        new URL(String(input), 'http://localhost').pathname === '/api/contents'
          ? json(newsPage([]))
          : new URL(String(input), 'http://localhost').pathname === '/api/people'
            ? json({ people: [{ ...person, posts }] })
            : json(newsPage()),
      ),
    );
    const view = render(
      <MemoryRouter>
        <IssueHomePage />
      </MemoryRouter>,
    );
    expect(await view.findByRole('button', { name: /오늘 글 14/ })).toBeTruthy();
    expect(view.container.querySelectorAll('.issue-feed > li')).toHaveLength(8);
    expect(view.queryByRole('button', { name: /오늘 글 6/ })).toBeNull();
    expect(view.queryByRole('button', { name: /어제 글/ })).toBeNull();
  });
});

it('홈은 오늘 글이 없으면 최신 글 여덟 개와 안내 문구를 표시한다', async () => {
  const posts = Array.from({ length: 10 }, (_, index) => ({
    ...person.posts[0],
    id: index + 10,
    title: `최근 글 ${index}`,
    createdAt: '2026-10-11T08:00:00Z',
  }));
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) =>
      new URL(String(input), 'http://localhost').pathname === '/api/contents'
        ? json(newsPage([]))
        : new URL(String(input), 'http://localhost').pathname === '/api/people'
          ? json({ people: [{ ...person, posts }] })
          : json(newsPage([])),
    ),
  );
  const view = render(
    <MemoryRouter>
      <IssueHomePage />
    </MemoryRouter>,
  );
  expect(await view.findByRole('heading', { name: '최근 올라온 글' })).toBeTruthy();
  expect(view.getByText('오늘 새로 올라온 글이 없어 최신 글을 보여드려요.')).toBeTruthy();
  expect(view.container.querySelectorAll('.issue-feed button')).toHaveLength(8);
  expect(view.queryByRole('button', { name: /최근 글 1/ })).toBeNull();
});

it('네 출처의 오늘 글을 표시하고 YouTube를 목록 안에서 재생한다', async () => {
  const video = {
    id: 50,
    assetId: 1,
    sourceType: 'YOUTUBE',
    title: '오늘의 영상',
    url: 'https://youtu.be/abcdefghijk',
    imageUrl: null,
    createdAt: '2026-10-12T09:30:00Z',
  };
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      const path = new URL(String(input), 'http://localhost').pathname;
      if (path === '/api/contents')
        return json({
          ...newsPage([]),
          items: [
            video,
            { ...video, id: 51, assetId: 2 },
            {
              ...video,
              id: 52,
              title: '어제 영상',
              url: 'https://youtu.be/oldabcdefgh',
              createdAt: '2026-10-11T09:00:00Z',
            },
          ],
        });
      if (path === '/api/people') return json({ people: [person] });
      return json(newsPage());
    }),
  );
  const view = render(
    <MemoryRouter>
      <IssueHomePage />
    </MemoryRouter>,
  );
  const trigger = await view.findByRole('button', { name: /오늘의 영상/ });
  expect(view.getAllByRole('button', { name: /오늘의 영상/ })).toHaveLength(1);
  expect(view.queryByRole('button', { name: /어제 영상/ })).toBeNull();
  expect(view.getByRole('button', { name: /첫 번째 인물 글/ })).toBeTruthy();
  expect(view.getByRole('button', { name: /두 번째 인물 글/ })).toBeTruthy();
  expect(view.container.querySelector('.issue-feed a[href="/news/7"]')).toBeTruthy();
  expect(view.container.querySelector('iframe')).toBeNull();
  trigger.focus();
  fireEvent.click(trigger);
  expect(view.queryByRole('dialog')).toBeNull();
  expect(trigger.getAttribute('aria-expanded')).toBe('true');
  expect(view.container.querySelector('iframe[title="오늘의 영상"]')?.getAttribute('src')).toBe(
    'https://www.youtube-nocookie.com/embed/abcdefghijk',
  );
  fireEvent.click(view.getByRole('button', { name: '글 접기 ↑' }));
  expect(view.container.querySelector('iframe')).toBeNull();
  expect(trigger.getAttribute('aria-expanded')).toBe('false');
  expect(view.queryByRole('dialog')).toBeNull();
  expect(document.activeElement).toBe(trigger);
});

it('외부 자료 수집이 실패하면 번호 이동을 막고 재시도한다', async () => {
  let fail = true;
  const requestedPages: number[] = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      const url = new URL(String(input), 'http://localhost');
      const page = Number(url.searchParams.get('page') ?? 1);
      if (url.pathname === '/api/people') return json({ people: [] });
      if (url.pathname === '/api/contents') {
        requestedPages.push(page);
        if (page === 2 && fail) return json({}, 500);
        return json({
          ...newsPage([]),
          page,
          totalPages: 3,
          items: [
            {
              id: page,
              assetId: 1,
              sourceType: 'YOUTUBE',
              title: `영상 페이지 ${page}`,
              url: `https://youtu.be/abcdefghij${page}`,
              imageUrl: null,
              createdAt: `2026-10-${13 - page}T09:00:00Z`,
            },
          ],
        });
      }
      return json({ ...newsPage([]), page });
    }),
  );
  const view = render(
    <MemoryRouter>
      <IssueHomePage fullFeed />
    </MemoryRouter>,
  );
  await view.findByRole('alert');
  expect(view.queryByRole('button', { name: /콘텐츠 더 보기/ })).toBeNull();
  expect(view.queryByRole('navigation', { name: '콘텐츠 페이지' })).toBeNull();
  fail = false;
  fireEvent.click(view.getByRole('button', { name: '다시 시도' }));
  expect(await view.findByRole('button', { name: /영상 페이지 2/ })).toBeTruthy();
  expect(requestedPages).toEqual([1, 2, 1, 2, 3]);
});

it('홈은 달력 없이 오늘 기사만 보여준다', async () => {
  const older = { ...article, id: 8, title: '지난 날짜 기사', publishedAt: '2026-10-11T09:00:00Z' };
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      const path = new URL(String(input), 'http://localhost').pathname;
      if (path === '/api/people') return json({ people: [] });
      if (path === '/api/contents') return json(newsPage([]));
      return json(newsPage([article, older]));
    }),
  );
  const view = render(
    <MemoryRouter>
      <IssueHomePage />
    </MemoryRouter>,
  );
  await waitFor(() =>
    expect(view.container.querySelector('.issue-story-card')?.textContent).toContain(article.title),
  );
  expect(view.queryByLabelText('이슈 날짜')).toBeNull();
  expect(view.container.querySelector('.issue-story-card')?.textContent).not.toContain(older.title);
  expect(view.container.querySelector('.issue-feed')?.textContent).not.toContain(older.title);
});

it('홈과 전체 콘텐츠에서 기사와 외부 글의 썸네일 및 날짜를 표시한다', async () => {
  mockApi();
  const home = render(
    <MemoryRouter>
      <IssueHomePage />
    </MemoryRouter>,
  );
  await home.findByRole('button', { name: /첫 번째 인물 글/ });
  expect(home.container.querySelector('.issue-feed--visual .issue-feed-thumbnail')).toBeTruthy();
  expect(home.container.querySelector('.issue-feed-thumbnail-daynomy img')).toBeNull();
  expect(home.container.querySelector('.issue-feed-thumbnail-daynomy')?.textContent).toBe(
    '데이노미',
  );
  expect(home.container.querySelector('.issue-story-image')?.textContent).toBe('데이노미');
  expect(home.container.querySelector('.issue-feed-thumbnail-x')?.textContent).toBe('X');
  const externalRow = home.getByRole('button', { name: /첫 번째 인물 글/ }).closest('article')!;
  expect(externalRow.querySelector('time')?.textContent).toBe('10.12 등록');
  expect(externalRow.querySelector('.issue-feed-thumbnail')).toBeTruthy();
  expect(home.container.querySelector('.issue-story-meta')?.textContent).toContain('주식');
  expect(home.container.querySelector('.issue-story-meta time')?.textContent).toBe('10.12 발행');
  fireEvent.click(home.getByRole('button', { name: /첫 번째 인물 글/ }));
  expect(home.getByRole('button', { name: /첫 번째 인물 글/ }).getAttribute('aria-expanded')).toBe(
    'true',
  );
  fireEvent.click(home.getByRole('button', { name: /글 접기/ }));
  expect(document.activeElement).toBe(home.getByRole('button', { name: /첫 번째 인물 글/ }));
  home.unmount();

  const contents = render(
    <MemoryRouter>
      <IssueHomePage fullFeed />
    </MemoryRouter>,
  );
  await contents.findByRole('button', { name: /첫 번째 인물 글/ });
  expect(
    contents.container.querySelector('.issue-feed--visual .issue-feed-thumbnail'),
  ).toBeTruthy();
  expect(contents.container.querySelector('.issue-feed-thumbnail-daynomy img')).toBeNull();
  expect(contents.container.querySelector('.issue-feed-thumbnail-daynomy')?.textContent).toBe(
    '데이노미',
  );
  expect(contents.container.querySelector('.issue-feed-thumbnail-x')?.textContent).toBe('X');
});

it('전체 콘텐츠는 번호로 12개씩 이동하고 출처 변경 시 첫 페이지로 돌아온다', async () => {
  const articles = Array.from({ length: 14 }, (_, index) => ({
    ...article,
    id: 100 + index,
    title: `페이지 기사 ${index}`,
    publishedAt: `2026-10-${String(28 - index).padStart(2, '0')}T09:00:00Z`,
  }));
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      const path = new URL(String(input), 'http://localhost').pathname;
      if (path === '/api/people') return json({ people: [] });
      if (path === '/api/contents') return json(newsPage([]));
      return json(newsPage(articles));
    }),
  );
  const view = render(
    <MemoryRouter>
      <IssueHomePage fullFeed />
    </MemoryRouter>,
  );
  await view.findByRole('button', { name: '2페이지' });
  expect(view.getAllByRole('link', { name: /페이지 기사/ })).toHaveLength(12);
  fireEvent.click(view.getByRole('button', { name: '2페이지' }));
  expect(view.getAllByRole('link', { name: /페이지 기사/ })).toHaveLength(2);
  expect(view.queryByRole('link', { name: '페이지 기사 0 기사 읽기' })).toBeNull();
  expect(
    view.getByRole('button', { name: '다음 방향 2페이지로 이동' }).hasAttribute('disabled'),
  ).toBe(true);
  fireEvent.click(view.getByRole('button', { name: '이전 방향 1페이지로 이동' }));
  expect(view.getByRole('link', { name: '페이지 기사 0 기사 읽기' })).toBeTruthy();
  fireEvent.click(view.getByRole('button', { name: '2페이지' }));
  fireEvent.click(view.getByRole('button', { name: '데이노미' }));
  expect(view.getByRole('button', { name: '1페이지' }).getAttribute('aria-current')).toBe('page');
});

it('출처별 빈 결과를 기록하고 같은 필터의 반복 선택은 중복 집계하지 않는다', async () => {
  mockApi();
  const view = render(
    <MemoryRouter>
      <IssueHomePage fullFeed />
    </MemoryRouter>,
  );
  await view.findByRole('button', { name: /첫 번째 인물 글/ });
  fireEvent.click(view.getByRole('button', { name: 'YouTube' }));
  expect(trackEvent).toHaveBeenCalledWith('filter_content_source', {
    surface: 'news_contents',
    previous_source: 'ALL',
    source_type: 'YOUTUBE',
    result_count: 0,
  });
  expect(trackEvent).toHaveBeenCalledWith('view_content_empty', {
    surface: 'news_contents',
    source_type: 'YOUTUBE',
    reason: 'filter_empty',
  });
  const count = vi.mocked(trackEvent).mock.calls.length;
  fireEvent.click(view.getByRole('button', { name: 'YouTube' }));
  expect(vi.mocked(trackEvent).mock.calls).toHaveLength(count);
});
