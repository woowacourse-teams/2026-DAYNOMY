/** @vitest-environment jsdom */
import { cleanup, fireEvent, render, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { trackEvent } from '../../src/analytics';
import { getStockRelatedContents } from '../../src/features/stock-content/api';
import { useFeaturedPeople } from '../../src/features/people/useFeaturedPeople';
import NewsHomePreview from '../../src/features/news/issue/NewsHomePreview';
import {
  getContentPopularity,
  sortByPopularity,
  feedContentReference,
} from '../../src/features/news/issue/popularity';
import { getNews } from '../../src/features/news/newslist/api';
import { createFeed } from '../../src/features/news/issue/feed';
import { getNewsFeed } from '../../src/features/news/issue/feedApi';

const holdingsState = vi.hoisted(() => ({
  holdings: [{ assetId: 11, name: '보유 회사' }],
  loading: false,
  error: '',
  retry: vi.fn(),
}));
const calendarRender = vi.hoisted(() => vi.fn());
const getExternalContents = vi.hoisted(() => vi.fn());

vi.mock('../../src/analytics', () => ({ trackEvent: vi.fn() }));
vi.mock('../../src/hooks/useLoginStatus', () => ({
  useAuth: () => ({ isLoggedIn: false, loading: false }),
}));
vi.mock('../../src/features/portfolio/hooks/usePortfolioHoldings', () => ({
  usePortfolioHoldings: () => holdingsState,
}));
vi.mock('../../src/features/investment-calendar/api', () => ({
  getInvestmentCalendar: vi.fn().mockResolvedValue({ calendar: { events: [] }, isDemo: false }),
}));
vi.mock('../../src/features/news/newslist/api', () => ({ getNews: vi.fn() }));
vi.mock('../../src/features/news/issue/feedApi', () => ({ getNewsFeed: vi.fn() }));
vi.mock('../../src/features/people/useFeaturedPeople', () => ({
  useFeaturedPeople: vi.fn(() => ({
    people: [],
    page: 1,
    size: 100,
    totalElements: 0,
    totalPages: 0,
    hasNext: false,
    loading: false,
    error: '',
    retry: vi.fn(),
  })),
}));
vi.mock('../../src/features/investment-calendar/InvestmentCalendarPage', () => ({
  InvestmentCalendarPage: (props: unknown) => {
    calendarRender(props);
    return <div>기존 캘린더</div>;
  },
}));
vi.mock('../../src/features/news/issue/popularity', async (original) => ({
  ...(await original<typeof import('../../src/features/news/issue/popularity')>()),
  getContentPopularity: vi.fn(),
}));
vi.mock('../../src/features/stock-content/api', () => ({ getStockRelatedContents: vi.fn() }));
afterEach(() => {
  cleanup();
  vi.mocked(trackEvent).mockClear();
});
beforeEach(() => {
  calendarRender.mockReset();
  holdingsState.holdings = [{ assetId: 11, name: '보유 회사' }];
  holdingsState.loading = false;
  holdingsState.error = '';
  vi.mocked(getStockRelatedContents).mockReset();
  vi.mocked(getStockRelatedContents).mockResolvedValue({
    assetId: 11,
    assetCode: 'TEST',
    assetName: '보유 회사',
    contents: [],
  });
  vi.mocked(getNews).mockReset();
  getExternalContents.mockReset();
  getExternalContents.mockResolvedValue({ items: [], page: 1, totalPages: 1 });
  vi.mocked(getContentPopularity).mockResolvedValue(new Map([['NEWS:7', 10]]));
  vi.mocked(getNewsFeed).mockReset();
  vi.mocked(getNewsFeed).mockImplementation(
    async (source, currentPage = 1, size = 12, sort = 'LATEST') => {
      const [articles, external] = await Promise.all([
        getNews('ALL', 1, 100),
        getExternalContents(),
      ]);
      const people = vi.mocked(useFeaturedPeople)().people;
      let items = createFeed(articles.content, people, external.items).filter(
        (item) => source === 'ALL' || item.source === source,
      );
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
const page = {
  content: [
    {
      id: 7,
      title: '기준금리 동결 가능성 확대',
      category: 'ECONOMY' as const,
      imageUrl: null,
      publishedAt: '2026-10-23T00:00:00Z',
    },
  ],
  page: 1,
  size: 3,
  totalPages: 1,
  totalElements: 1,
};
function show() {
  return render(
    <MemoryRouter>
      <NewsHomePreview />
    </MemoryRouter>,
  );
}
it('기존 기사 API로 제목과 상세 링크를 보여주며 기존 화면으로 연결한다', async () => {
  vi.mocked(getNews).mockResolvedValue(page);
  const view = show();
  expect(view.getByRole('heading', { name: '소식' })).toBeTruthy();
  expect(view.getByText('최근 7일간 반응 기준')).toBeTruthy();
  const article = await within(view.getByRole('region', { name: '전체 소식' })).findByRole('link', {
    name: /기준금리 동결 가능성 확대/,
  });
  expect(article.getAttribute('href')).toBe('/news/7');
  expect(getNewsFeed).toHaveBeenCalledWith('DAYNOMY', 1, 4, 'POPULAR', expect.anything());
  expect(getNewsFeed).toHaveBeenCalledWith('ALL', 1, 8, 'LATEST', expect.anything());
  expect(
    view.getAllByRole('link', { name: '내 종목 소식 전체 보기' })[0].getAttribute('href'),
  ).toBe('/news/contents?scope=portfolio');
  expect(view.queryByRole('link', { name: '전체 콘텐츠' })).toBeNull();
  expect(view.getAllByRole('link', { name: '전체 소식 전체 보기' })).toHaveLength(1);
  expect(view.getAllByText('모두 보기')).toHaveLength(3);
  expect(view.queryByText(/가상 기사/)).toBeNull();
});
it('실패 시 재시도하고 빈 결과를 가상 소식으로 대체하지 않는다', async () => {
  vi.mocked(getNews)
    .mockRejectedValueOnce(new Error('offline'))
    .mockResolvedValue({ ...page, content: [], totalElements: 0 });
  const view = show();
  await view.findByRole('alert');
  fireEvent.click(view.getByRole('button', { name: '다시 시도' }));
  await waitFor(() => expect(view.getByText('아직 등록된 소식이 없어요')).toBeTruthy());
  expect(getNewsFeed).toHaveBeenCalledTimes(4);
  expect(trackEvent).toHaveBeenCalledWith('retry_content_load', {
    surface: 'news_home',
    area: 'all',
    attempt: 1,
  });
});

it('소식 홈 조회 결과와 목록 상태를 영역별로 기록한다', async () => {
  vi.mocked(getNews).mockResolvedValue(page);
  show();
  await waitFor(() =>
    expect(trackEvent).toHaveBeenCalledWith(
      'content_load_result',
      expect.objectContaining({
        surface: 'news_home',
        area: 'feed',
        status: 'success',
        result_count: 1,
        attempt: 0,
      }),
    ),
  );
  expect(trackEvent).toHaveBeenCalledWith(
    'content_load_result',
    expect.objectContaining({
      surface: 'news_home',
      area: 'popularity',
      status: 'success',
      result_count: 1,
      attempt: 0,
    }),
  );
  await waitFor(() =>
    expect(trackEvent).toHaveBeenCalledWith('view_content_list', {
      surface: 'news_home',
      source_type: 'ALL',
      result_count: 1,
      status: 'ready',
    }),
  );
});

it('트렌딩은 외부 콘텐츠를 제외하고 데이노미 이슈 인기순으로 네 개를 표시한다', async () => {
  vi.mocked(getNews).mockResolvedValue({
    ...page,
    content: [
      page.content[0],
      { ...page.content[0], id: 8, title: '집계 없는 데이노미 이슈', publishedAt: '2026-10-24' },
      {
        ...page.content[0],
        id: 9,
        title: '인기 데이노미 이슈',
        imageUrl: '/trending.jpg',
        publishedAt: '2026-10-25',
      },
      { ...page.content[0], id: 10, title: '네 번째 데이노미 이슈', publishedAt: '2026-10-26' },
    ],
  });
  getExternalContents.mockResolvedValue({
    items: [
      {
        id: 99,
        assetId: 1,
        sourceType: 'YOUTUBE',
        title: '가장 인기 있는 외부 영상',
        url: 'https://www.youtube.com/watch?v=abcdefghijk',
        imageUrl: null,
        createdAt: '2026-10-26',
      },
    ],
    page: 1,
    totalPages: 1,
  });
  vi.mocked(getContentPopularity).mockResolvedValue(
    new Map([
      ['ASSET_CONTENT:99', 100],
      ['NEWS:9', 30],
      ['NEWS:7', 20],
    ]),
  );
  const view = show();
  const trending = within(view.getByRole('region', { name: '트렌딩' }));
  expect(await trending.findByRole('link', { name: /인기 데이노미 이슈/ })).toBeTruthy();
  expect(trending.getAllByRole('article')).toHaveLength(4);
  expect(trending.getByText('트렌딩 04')).toBeTruthy();
  expect(trending.queryByText('가장 인기 있는 외부 영상')).toBeNull();
  const featureStory = view.container.querySelector('.briefing-feature-story.briefing-live-story');
  expect(featureStory).toBeTruthy();
  expect(featureStory?.querySelector('img')?.getAttribute('src')).toBe('/trending.jpg');
  expect(
    within(view.container.querySelector('.briefing-feature-story')!).getByRole('link', {
      name: /인기 데이노미 이슈/,
    }),
  ).toBeTruthy();
});

it('인기순 API가 실패하면 최신 기사를 인기 기사로 대체하지 않는다', async () => {
  vi.mocked(getNews).mockResolvedValue(page);
  vi.mocked(getContentPopularity).mockRejectedValue(new Error('offline'));
  const view = show();
  expect(await view.findByText('인기 소식을 잠시 불러올 수 없어요')).toBeTruthy();
  expect(within(view.getByRole('region', { name: '트렌딩' })).queryByRole('article')).toBeNull();
});
it('일정 요약 없이 기존 투자 캘린더를 바로 표시한다', async () => {
  vi.mocked(getNews).mockResolvedValue(page);
  const view = show();
  expect(await view.findByText('기존 캘린더')).toBeTruthy();
  expect(view.queryByRole('button', { name: '일정 접기' })).toBeNull();
  expect(view.queryByText('이달 남은 일정')).toBeNull();
});

it('전체 소식은 인기 여부와 관계없이 최신 콘텐츠를 보여준다', async () => {
  vi.mocked(getNews).mockResolvedValue({
    ...page,
    content: [...page.content, { ...page.content[0], id: 8, title: '새로 등록한 기사' }],
  });
  const view = show();
  const trending = within(view.getByRole('region', { name: '트렌딩' }));
  const latest = within(view.getByRole('region', { name: '전체 소식' }));
  await latest.findByRole('link', { name: /새로 등록한 기사/ });
  expect(trending.getByRole('link', { name: /기준금리/ })).toBeTruthy();
  expect(latest.getByRole('link', { name: /기준금리/ })).toBeTruthy();
  expect(latest.getByRole('link', { name: /새로 등록한 기사/ })).toBeTruthy();
});

it('첫 화면에서 보유 종목 소식과 기존 탐색 경로를 유지한다', async () => {
  vi.mocked(getNews).mockResolvedValue(page);
  const view = show();
  expect(view.getByRole('link', { name: '내 종목 소식 전체 보기' })).toBeTruthy();
  expect(view.getByRole('link', { name: '인물의 글 전체 보기' }).getAttribute('href')).toBe(
    '/people',
  );
  expect(view.getByRole('link', { name: '전체 소식 전체 보기' }).getAttribute('href')).toBe(
    '/news/contents',
  );
  expect(view.queryByRole('link', { name: /AI 분석/ })).toBeNull();
  await within(view.getByRole('region', { name: '전체 소식' })).findByRole('link', {
    name: /기준금리/,
  });
});

it('등록된 인물과 최신 글을 보여주고 기존 인물 필터로 연결한다', async () => {
  vi.mocked(getNews).mockResolvedValue(page);
  vi.mocked(useFeaturedPeople).mockReturnValue({
    people: [
      {
        id: 12,
        name: '테스트 인물',
        profileImageUrl: '/profile.jpg',
        xProfileUrl: null,
        threadsProfileUrl: null,
        youtubeProfileUrl: null,
        createdAt: null,
        posts: [
          {
            id: 91,
            sourceType: 'X',
            title: '인물의 최신 관점',
            url: 'https://x.com/example/status/91',
            publishedAt: '2026-10-24',
            createdAt: null,
          },
        ],
      },
    ],
    page: 1,
    size: 100,
    totalElements: 1,
    totalPages: 1,
    hasNext: false,
    loading: false,
    error: '',
    retry: vi.fn(),
  });
  try {
    const view = show();
    const section = within(view.getByRole('region', { name: '인물의 글' }));
    expect(
      section
        .getAllByRole('link', { name: /테스트 인물/ })
        .some((link) => link.getAttribute('href') === '/people?person=12&view=all'),
    ).toBe(true);
    expect(section.getByRole('link', { name: /인물의 최신 관점/ })).toBeTruthy();
    await within(view.getByRole('region', { name: '전체 소식' })).findByRole('link', {
      name: /기준금리/,
    });
  } finally {
    vi.mocked(useFeaturedPeople).mockReturnValue({
      people: [],
      page: 1,
      size: 100,
      totalElements: 0,
      totalPages: 0,
      hasNext: false,
      loading: false,
      error: '',
      retry: vi.fn(),
    });
  }
});

it('전체 소식 카테고리로 출처를 필터링한다', async () => {
  vi.mocked(getNews).mockResolvedValue(page);
  getExternalContents.mockResolvedValue({
    items: [
      {
        id: 99,
        assetId: 1,
        sourceType: 'YOUTUBE',
        title: '유튜브 소식',
        url: 'https://www.youtube.com/watch?v=abcdefghijk',
        imageUrl: null,
        createdAt: '2026-10-26',
      },
    ],
    page: 1,
    totalPages: 1,
  });
  const view = show();
  const latest = within(view.getByRole('region', { name: '전체 소식' }));
  await latest.findByText('유튜브 소식');
  fireEvent.click(
    within(view.getByRole('group', { name: '전체 소식 카테고리' })).getByRole('button', {
      name: 'YouTube',
    }),
  );
  expect(await latest.findByText('유튜브 소식')).toBeTruthy();
  expect(latest.queryByText('기준금리 동결 가능성 확대')).toBeNull();
  expect(trackEvent).toHaveBeenCalledWith('filter_content_source', {
    surface: 'news_home',
    previous_source: 'ALL',
    source_type: 'YOUTUBE',
  });
  expect(JSON.stringify(vi.mocked(trackEvent).mock.calls)).not.toContain('유튜브 소식');
});

it('더보기와 인물 미리보기 이동은 목적지와 위치만 기록한다', async () => {
  vi.mocked(getNews).mockResolvedValue(page);
  vi.mocked(useFeaturedPeople).mockReturnValue({
    people: [
      {
        id: 12,
        name: '민감한 인물 이름',
        profileImageUrl: null,
        xProfileUrl: null,
        threadsProfileUrl: null,
        youtubeProfileUrl: null,
        createdAt: null,
        posts: [
          {
            id: 91,
            sourceType: 'X',
            title: '민감한 글 제목',
            url: 'https://x.com/private',
            publishedAt: '2026-10-24',
            createdAt: null,
          },
        ],
      },
    ],
    page: 1,
    size: 100,
    totalElements: 1,
    totalPages: 1,
    hasNext: false,
    loading: false,
    error: '',
    retry: vi.fn(),
  });
  const view = show();
  const peopleSection = within(view.getByRole('region', { name: '인물의 글' }));
  await peopleSection.findByRole('link', { name: /민감한 인물 이름/ });
  fireEvent.click(view.getByRole('link', { name: '전체 소식 전체 보기' }));
  fireEvent.click(peopleSection.getByRole('link', { name: /민감한 인물 이름/ }));
  expect(trackEvent).toHaveBeenCalledWith('click_content_more', {
    surface: 'news_home',
    destination: 'news_contents',
  });
  expect(trackEvent).toHaveBeenCalledWith('click_person_preview', {
    surface: 'news_home_people',
    person_id: 12,
    position: 1,
  });
  expect(JSON.stringify(vi.mocked(trackEvent).mock.calls)).not.toContain('민감한');
});

it.each(['holdings', 'empty', 'loading', 'error'] as const)(
  '첫 화면은 %s 상태에서도 소식 진입 하나만 제공하고 종목별 조회와 분석을 시작하지 않는다',
  async (state) => {
    holdingsState.holdings = state === 'empty' ? [] : [{ assetId: 11, name: '보유 회사' }];
    holdingsState.loading = state === 'loading';
    holdingsState.error = state === 'error' ? 'offline' : '';
    vi.mocked(getNews).mockResolvedValue(page);
    const view = show();
    const section = within(view.getByRole('region', { name: '내 종목 소식' }));
    expect(section.getAllByRole('link')).toHaveLength(state === 'holdings' ? 2 : 1);
    expect(section.getByRole('link', { name: '내 종목 소식 전체 보기' }).getAttribute('href')).toBe(
      '/news/contents?scope=portfolio',
    );
    if (state === 'holdings') {
      expect(section.getByRole('link', { name: /보유 회사/ }).getAttribute('href')).toBe(
        '/news/contents?scope=portfolio&asset=11',
      );
    } else {
      expect(section.queryByRole('list')).toBeNull();
    }
    expect(view.queryByRole('link', { name: /AI 분석/ })).toBeNull();
    expect(getStockRelatedContents).not.toHaveBeenCalled();
    await within(view.getByRole('region', { name: '전체 소식' })).findByRole('link', {
      name: /기준금리/,
    });
  },
);

it('트렌딩은 인기순으로 고정하고 탐색 버튼을 표시하지 않는다', async () => {
  vi.mocked(getNews).mockResolvedValue({
    ...page,
    content: [
      ...page.content,
      { ...page.content[0], id: 8, title: '두 번째 트렌딩 기사' },
      { ...page.content[0], id: 9, title: '세 번째 트렌딩 기사' },
    ],
  });
  vi.mocked(getContentPopularity).mockResolvedValue(
    new Map([
      ['NEWS:8', 20],
      ['NEWS:9', 10],
      ['NEWS:7', 30],
    ]),
  );
  const view = show();
  const trending = within(view.getByRole('region', { name: '트렌딩' }));
  await trending.findByRole('link', { name: /세 번째 트렌딩 기사/ });
  const articles = trending.getAllByRole('article');
  expect(
    within(articles[0])
      .getByRole('link', { name: /기준금리/ })
      .getAttribute('href'),
  ).toBe('/news/7');
  expect(within(articles[1]).getByRole('link').getAttribute('href')).toBe('/news/8');
  expect(within(articles[2]).getByRole('link').getAttribute('href')).toBe('/news/9');
  expect(trending.queryByRole('button', { name: /트렌딩/ })).toBeNull();
});

it('전체 소식은 최신순으로 최대 여덟 개를 표시한다', async () => {
  vi.mocked(getNews).mockResolvedValue({
    ...page,
    content: Array.from({ length: 9 }, (_, index) => ({
      ...page.content[0],
      id: index + 20,
      title: `최신 기사 ${index}`,
      publishedAt: `2026-10-${20 + index}T00:00:00Z`,
    })),
  });
  const view = show();
  const latest = within(view.getByRole('region', { name: '전체 소식' }));
  await latest.findByRole('link', { name: /최신 기사 8/ });
  expect(latest.getAllByRole('article')).toHaveLength(8);
  expect(latest.queryByRole('link', { name: /최신 기사 0/ })).toBeNull();
});
