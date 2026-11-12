/** @vitest-environment jsdom */
import { trackEvent } from '../../src/analytics';
vi.mock('../../src/analytics', () => ({ trackEvent: vi.fn() }));
import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { IssueHomePage } from '../../src/features/news/issue/IssueHomePage';
import { createFeed } from '../../src/features/news/issue/feed';
import {
  feedContentReference,
  getContentPopularity,
  sortByPopularity,
} from '../../src/features/news/issue/popularity';
import { getNewsFeed } from '../../src/features/news/issue/feedApi';

vi.mock('../../src/features/news/issue/feedApi', () => ({
  getNewsFeed: vi.fn(),
}));

vi.mock('../../src/features/investment-calendar/InvestmentCalendarPage', () => ({
  InvestmentCalendarPage: () => (
    <section aria-labelledby="test-investment-calendar-title">
      <h2 id="test-investment-calendar-title">투자 캘린더</h2>
    </section>
  ),
}));
const article = {
  id: 7,
  title: '오늘의 데이노미 이슈',
  category: 'STOCK' as const,
  imageUrl: null,
  publishedAt: '2026-10-12T09:00:00Z',
};
const person = {
  id: 1,
  name: '테스트 인물',
  profileImageUrl: null,
  xProfileUrl: null,
  threadsProfileUrl: null,
  youtubeProfileUrl: null,
  createdAt: null,
  posts: [
    {
      id: 1,
      sourceType: 'X' as const,
      title: '첫 번째 인물 글',
      url: 'https://x.com/example/status/1',
      createdAt: '2026-10-12T08:00:00Z',
    },
    {
      id: 2,
      sourceType: 'THREADS' as const,
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
function peoplePage(people: unknown[]) {
  const totalElements = people.reduce<number>(
    (sum, value) => sum + ((value as typeof person).posts?.length ?? 0),
    0,
  );
  return {
    people,
    page: 1,
    size: 100,
    totalPages: totalElements ? 1 : 0,
    totalElements,
    hasNext: false,
  };
}
function mockApi(peopleFail = false) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) =>
      new URL(String(input), 'http://localhost').pathname === '/api/contents'
        ? json(newsPage([]))
        : new URL(String(input), 'http://localhost').pathname === '/api/people'
          ? json(peopleFail ? {} : peoplePage([person]), peopleFail ? 500 : 200)
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
  vi.mocked(getNewsFeed).mockReset();
  vi.mocked(getNewsFeed).mockImplementation(
    async (source, currentPage = 1, size = 12, sort = 'LATEST') => {
      const [newsResponse, contentsResponse, peopleResponse] = await Promise.all([
        fetch(`/api/news?page=1&size=${size}`),
        fetch(`/api/contents?page=1&size=${size}`),
        fetch(`/api/people?page=1&size=${size}`),
      ]);
      if (!newsResponse.ok || !contentsResponse.ok) throw new Error('offline');
      const news = (await newsResponse.json()) as ReturnType<typeof newsPage>;
      const contents = (await contentsResponse.json()) as ReturnType<typeof newsPage>;
      const people = peopleResponse.ok
        ? ((await peopleResponse.json()) as ReturnType<typeof peoplePage>)
        : peoplePage([]);
      let items = createFeed(
        news.items,
        people.people as (typeof person)[],
        contents.items as never[],
      ).filter((item) => source === 'ALL' || item.source === source);
      if (sort === 'POPULAR') {
        items = sortByPopularity(items, feedContentReference, await getContentPopularity());
      }
      const totalElements = items.length;
      const start = (currentPage - 1) * size;
      return {
        items: items.slice(start, start + size),
        page: currentPage,
        size,
        totalElements,
        totalPages: Math.ceil(totalElements / size),
      };
    },
  );
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
describe('이슈 홈과 전체 목록', () => {
  it.each([false, true])(
    '홈과 전체 콘텐츠를 최근 7일 인기 점수순으로 정렬한다 (fullFeed=%s)',
    async (fullFeed) => {
      vi.stubGlobal(
        'fetch',
        vi.fn(async (input: RequestInfo | URL) => {
          const url = new URL(String(input), 'http://localhost');
          if (url.pathname === '/api/content-rankings')
            return json({
              periodDays: 7,
              items: [
                {
                  contentType: 'PERSON_POST',
                  contentId: 2,
                  score: 10,
                  lastInteractedAt: '2026-10-20T02:00:00Z',
                },
              ],
            });
          if (url.pathname === '/api/contents') return json(newsPage([]));
          if (url.pathname === '/api/people') return json(peoplePage([person]));
          return json(newsPage());
        }),
      );
      const view = render(
        <MemoryRouter
          initialEntries={[fullFeed ? '/news/contents?sort=popular' : '/news?sort=popular']}
        >
          <IssueHomePage fullFeed={fullFeed} />
        </MemoryRouter>,
      );

      expect(await view.findByRole('button', { name: '인기순', pressed: true })).toBeTruthy();
      await waitFor(() =>
        expect(view.container.querySelector('.issue-feed > li')?.textContent).toContain(
          '두 번째 인물 글',
        ),
      );
    },
  );

  it('투자 캘린더와 인물 글을 함께 표시하고 콘텐츠 경로를 제공한다', async () => {
    mockApi();
    const view = render(
      <MemoryRouter>
        <IssueHomePage />
      </MemoryRouter>,
    );
    await view.findByRole('button', { name: /첫 번째 인물 글/ });
    expect(view.getByRole('heading', { name: '투자 캘린더' })).toBeTruthy();
    expect(view.queryByText('최근 이슈')).toBeNull();
    expect(
      view.getByRole('link', { name: '주목받는 인물의 글 전체 보기' }).getAttribute('href'),
    ).toBe('/people');
    expect(view.queryByRole('link', { name: /전체 콘텐츠 더 보기/ })).toBeNull();
    expect(view.getByRole('heading', { name: '최근 올라온 콘텐츠' })).toBeTruthy();
    expect(view.container.querySelector('.issue-people-preview a')?.getAttribute('href')).toBe(
      '/people?person=1',
    );
    expect(view.container.querySelector('blockquote')).toBeNull();
    fireEvent.click(view.getByRole('button', { name: 'Threads' }));
    expect(view.getByRole('button', { name: 'Threads', pressed: true })).toBeTruthy();
    expect(view.getByRole('button', { name: '전체', pressed: false })).toBeTruthy();
    await waitFor(() => expect(view.queryByRole('button', { name: /첫 번째 인물 글/ })).toBeNull());
    fireEvent.click(await view.findByRole('button', { name: /두 번째 인물 글/ }));
    expect(view.container.querySelector('blockquote.text-post-media')).toBeTruthy();
  });
  it('인물 API가 실패해도 전체 콘텐츠 조회 결과를 유지한다', async () => {
    mockApi(true);
    const view = render(
      <MemoryRouter>
        <IssueHomePage fullFeed />
      </MemoryRouter>,
    );
    expect(await view.findByRole('link', { name: /오늘의 데이노미 이슈/ })).toBeTruthy();
    expect(view.queryByRole('alert')).toBeNull();
  });
  it.each([false, true])(
    '홈과 전체 목록은 요청한 페이지만 가져온다 (fullFeed=%s)',
    async (fullFeed) => {
      const older = { ...article, id: 8, title: '이전 이슈', publishedAt: '2026-10-10T00:00:00Z' };
      vi.mocked(getNewsFeed).mockImplementation(async (_source, currentPage = 1, size = 12) => ({
        items: [currentPage === 1 ? article : older].map((item) => ({
          key: `news-${item.id}`,
          source: 'DAYNOMY' as const,
          title: item.title,
          date: item.publishedAt,
          article: item,
        })),
        page: currentPage,
        size,
        totalElements: 2,
        totalPages: 2,
      }));
      mockApi();
      const view = render(
        <MemoryRouter>
          <IssueHomePage fullFeed={fullFeed} />
        </MemoryRouter>,
      );
      await view.findByText(article.title);
      expect(view.container.querySelector('.issue-feed')?.textContent).toContain(article.title);
      expect(view.container.querySelector('.issue-feed')?.textContent).not.toContain('이전 이슈');
      expect(getNewsFeed).toHaveBeenCalledWith(
        'ALL',
        1,
        fullFeed ? 12 : 6,
        'LATEST',
        expect.anything(),
      );
      fireEvent.click(view.getByRole('button', { name: '2페이지' }));
      await view.findByText('이전 이슈');
    },
  );
  it('홈은 여섯 개씩 페이지를 이동하고 출처 변경 시 첫 페이지로 돌아온다', async () => {
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
            ? json(peoplePage([{ ...person, posts }]))
            : json(newsPage()),
      ),
    );
    const view = render(
      <MemoryRouter>
        <IssueHomePage />
      </MemoryRouter>,
    );
    expect(await view.findByRole('button', { name: /오늘 글 14/ })).toBeTruthy();
    expect(view.container.querySelectorAll('.issue-feed > li')).toHaveLength(6);
    expect(view.queryByRole('button', { name: /어제 글/ })).toBeNull();
    fireEvent.click(view.getByRole('button', { name: '2페이지' }));
    await view.findByRole('button', { name: /오늘 글 9/ });
    expect(view.container.querySelectorAll('.issue-feed > li')).toHaveLength(6);
    fireEvent.click(view.getByRole('button', { name: '3페이지' }));
    await view.findByRole('button', { name: /어제 글/ });
    expect(view.container.querySelectorAll('.issue-feed > li')).toHaveLength(5);
    expect(view.getByRole('button', { name: /어제 글/ })).toBeTruthy();
    expect(view.queryByRole('link', { name: '전체 콘텐츠 보기' })).toBeNull();
    fireEvent.click(view.getByRole('button', { name: 'X' }));
    await waitFor(() =>
      expect(view.getByRole('button', { name: '1페이지' }).getAttribute('aria-current')).toBe(
        'page',
      ),
    );
    expect(view.container.querySelectorAll('.issue-feed > li')).toHaveLength(6);
    expect(trackEvent).toHaveBeenCalledWith(
      'filter_content_source',
      expect.objectContaining({ surface: 'news_home', source_type: 'X' }),
    );
  });
});

it('홈은 오늘 글이 없어도 최근 글을 여섯 개씩 표시한다', async () => {
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
          ? json(peoplePage([{ ...person, posts }]))
          : json(newsPage([])),
    ),
  );
  const view = render(
    <MemoryRouter>
      <IssueHomePage />
    </MemoryRouter>,
  );
  await view.findByRole('button', { name: /최근 글 9/ });
  expect(view.getByRole('heading', { name: '최근 올라온 콘텐츠' })).toBeTruthy();
  expect(view.queryByText('오늘 새로 올라온 글이 없어 최신 글을 보여드려요.')).toBeNull();
  expect(view.queryByRole('link', { name: '전체 콘텐츠 보기' })).toBeNull();
  expect(view.getByRole('button', { name: '2페이지' })).toBeTruthy();
  expect(view.container.querySelectorAll('.issue-feed button')).toHaveLength(6);
});

it('네 출처의 전체 글을 표시하고 YouTube를 목록 안에서 재생한다', async () => {
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
      if (path === '/api/people') return json(peoplePage([person]));
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
  expect(view.getByRole('button', { name: /어제 영상/ })).toBeTruthy();
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

it('콘텐츠 페이지 조회가 실패하면 번호 이동을 막고 재시도한다', async () => {
  vi.mocked(getNewsFeed)
    .mockRejectedValueOnce(new Error('offline'))
    .mockResolvedValueOnce({
      items: [
        {
          key: 'content-2',
          source: 'YOUTUBE',
          title: '영상 페이지 2',
          date: '2026-10-11T09:00:00Z',
          content: {
            id: 2,
            assetId: 1,
            sourceType: 'YOUTUBE',
            title: '영상 페이지 2',
            url: 'https://youtu.be/abcdefghij2',
            imageUrl: null,
            createdAt: '2026-10-11T09:00:00Z',
          },
        },
      ],
      page: 1,
      size: 12,
      totalElements: 1,
      totalPages: 1,
    });
  mockApi();
  const view = render(
    <MemoryRouter>
      <IssueHomePage fullFeed />
    </MemoryRouter>,
  );
  await view.findByRole('alert');
  expect(view.queryByRole('button', { name: /콘텐츠 더 보기/ })).toBeNull();
  expect(view.queryByRole('navigation', { name: '콘텐츠 페이지' })).toBeNull();
  fireEvent.click(view.getByRole('button', { name: '다시 시도' }));
  expect(await view.findByRole('button', { name: /영상 페이지 2/ })).toBeTruthy();
  expect(getNewsFeed).toHaveBeenCalledTimes(2);
});

it('홈은 투자 캘린더와 전체 기사 목록을 함께 보여준다', async () => {
  const older = { ...article, id: 8, title: '지난 날짜 기사', publishedAt: '2026-10-11T09:00:00Z' };
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      const path = new URL(String(input), 'http://localhost').pathname;
      if (path === '/api/people') return json(peoplePage([]));
      if (path === '/api/contents') return json(newsPage([]));
      return json(newsPage([article, older]));
    }),
  );
  const view = render(
    <MemoryRouter>
      <IssueHomePage />
    </MemoryRouter>,
  );
  expect(await view.findByRole('heading', { name: '투자 캘린더' })).toBeTruthy();
  expect(await view.findByRole('link', { name: /오늘의 데이노미 이슈/ })).toBeTruthy();
  expect(view.queryByLabelText('이슈 날짜')).toBeNull();
  expect(view.container.querySelector('.issue-feed')?.textContent).toContain(older.title);
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
  expect(home.container.querySelector('.issue-feed-thumbnail-daynomy')?.textContent).toBe('D');
  expect(home.container.querySelector('.issue-feed-thumbnail-x')?.textContent).toBe('X');
  const externalRow = home.getByRole('button', { name: /첫 번째 인물 글/ }).closest('article')!;
  expect(externalRow.querySelector('time')?.textContent).toBe('10.12 등록');
  expect(externalRow.querySelector('.issue-feed-thumbnail')).toBeTruthy();
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
  expect(contents.container.querySelector('.issue-feed-thumbnail-daynomy')?.textContent).toBe('D');
  expect(contents.container.querySelector('.issue-feed-thumbnail-x')?.textContent).toBe('X');
});

it('전체 콘텐츠는 번호로 12개씩 이동하고 출처 변경 시 첫 페이지로 돌아온다', async () => {
  const articles = Array.from({ length: 14 }, (_, index) => ({
    ...article,
    id: 100 + index,
    title: `페이지 기사 ${index}`,
    publishedAt: `2026-10-${String(28 - index).padStart(2, '0')}T09:00:00Z`,
  }));
  vi.mocked(getNewsFeed).mockImplementation(async (source, currentPage = 1, size = 12) => {
    const filtered = source === 'ALL' || source === 'DAYNOMY' ? articles : [];
    const start = (currentPage - 1) * size;
    return {
      items: filtered.slice(start, start + size).map((item) => ({
        key: `news-${item.id}`,
        source: 'DAYNOMY' as const,
        title: item.title,
        date: item.publishedAt,
        article: item,
      })),
      page: currentPage,
      size,
      totalElements: filtered.length,
      totalPages: Math.ceil(filtered.length / size),
    };
  });
  mockApi();
  const view = render(
    <MemoryRouter>
      <IssueHomePage fullFeed />
    </MemoryRouter>,
  );
  await view.findByRole('button', { name: '2페이지' });
  expect(view.getAllByRole('link', { name: /페이지 기사/ })).toHaveLength(12);
  fireEvent.click(view.getByRole('button', { name: '2페이지' }));
  await view.findByRole('link', { name: '페이지 기사 12 기사 읽기' });
  expect(view.getAllByRole('link', { name: /페이지 기사/ })).toHaveLength(2);
  expect(view.queryByRole('link', { name: '페이지 기사 0 기사 읽기' })).toBeNull();
  expect(
    view.getByRole('button', { name: '다음 방향 2페이지로 이동' }).hasAttribute('disabled'),
  ).toBe(true);
  fireEvent.click(view.getByRole('button', { name: '이전 방향 1페이지로 이동' }));
  expect(await view.findByRole('link', { name: '페이지 기사 0 기사 읽기' })).toBeTruthy();
  fireEvent.click(view.getByRole('button', { name: '2페이지' }));
  await view.findByRole('link', { name: '페이지 기사 12 기사 읽기' });
  fireEvent.click(view.getByRole('button', { name: '데이노미' }));
  await waitFor(() =>
    expect(view.getByRole('button', { name: '1페이지' }).getAttribute('aria-current')).toBe('page'),
  );
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
  });
  await waitFor(() =>
    expect(trackEvent).toHaveBeenCalledWith('view_content_empty', {
      surface: 'news_contents',
      source_type: 'YOUTUBE',
      reason: 'filter_empty',
    }),
  );
  const count = vi.mocked(trackEvent).mock.calls.length;
  fireEvent.click(view.getByRole('button', { name: 'YouTube' }));
  expect(vi.mocked(trackEvent).mock.calls).toHaveLength(count);
});

it('전체 콘텐츠 정렬 변경은 선택한 기준만 기록하고 반복 선택은 무시한다', async () => {
  mockApi();
  const view = render(
    <MemoryRouter>
      <IssueHomePage fullFeed />
    </MemoryRouter>,
  );
  await view.findByRole('button', { name: '인기순' });
  fireEvent.click(view.getByRole('button', { name: '인기순' }));
  expect(trackEvent).toHaveBeenCalledWith('sort_content', {
    surface: 'news_contents',
    sort: 'popular',
  });
  const count = vi.mocked(trackEvent).mock.calls.filter(([name]) => name === 'sort_content').length;
  fireEvent.click(view.getByRole('button', { name: '인기순' }));
  expect(vi.mocked(trackEvent).mock.calls.filter(([name]) => name === 'sort_content')).toHaveLength(
    count,
  );
});

it('주목받는 인물의 글은 같은 인물도 포함해 최신 글 세 개만 제공한다', async () => {
  const people = [
    {
      ...person,
      posts: [
        {
          ...person.posts[0],
          id: 11,
          title: '최신 글',
          publishedAt: '2026-10-12T09:00:00Z',
          createdAt: '2026-10-10T00:00:00Z',
        },
        { ...person.posts[0], id: 12, title: '세 번째 글', createdAt: '2026-10-12T07:00:00Z' },
        {
          ...person.posts[0],
          id: 13,
          title: '네 번째 글',
          createdAt: '2026-10-11T07:00:00Z',
        },
      ],
    },
    {
      ...person,
      id: 2,
      name: '다른 인물',
      posts: [
        { ...person.posts[0], id: 21, title: '두 번째 글', createdAt: '2026-10-12T08:00:00Z' },
      ],
    },
    { ...person, id: 3, posts: [] },
  ];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) =>
      new URL(String(input), 'http://localhost').pathname === '/api/people'
        ? json(peoplePage(people))
        : json(newsPage([])),
    ),
  );
  const view = render(
    <MemoryRouter>
      <IssueHomePage />
    </MemoryRouter>,
  );
  await waitFor(() =>
    expect(view.container.querySelectorAll('.issue-people-preview > a')).toHaveLength(3),
  );
  expect(
    [...view.container.querySelectorAll('.issue-people-preview h3')].map(
      (node) => node.textContent,
    ),
  ).toEqual(['최신 글', '두 번째 글', '세 번째 글']);
  expect(
    [...view.container.querySelectorAll('.issue-people-preview > a')].map((node) =>
      node.getAttribute('href'),
    ),
  ).toEqual(['/people?person=1&view=all', '/people?person=2', '/people?person=1']);
});
