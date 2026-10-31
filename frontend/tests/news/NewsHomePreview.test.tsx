/** @vitest-environment jsdom */
import { cleanup, fireEvent, render, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { getStockRelatedContents } from '../../src/features/stock-content/api';
import { useFeaturedPeople } from '../../src/features/people/useFeaturedPeople';
import NewsHomePreview from '../../src/features/news/issue/NewsHomePreview';
import { getAllContents } from '../../src/features/news/issue/contents';
import { getContentPopularity } from '../../src/features/news/issue/popularity';
import { getNews } from '../../src/features/news/newslist/api';

const holdingsState = vi.hoisted(() => ({
  holdings: [{ assetId: 11, name: '보유 회사' }],
  loading: false,
  error: '',
  retry: vi.fn(),
}));
const calendarRender = vi.hoisted(() => vi.fn());

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
vi.mock('../../src/features/news/issue/contents', () => ({
  getAllContents: vi.fn().mockResolvedValue({ items: [] }),
}));
vi.mock('../../src/features/news/issue/popularity', async (original) => ({
  ...(await original<typeof import('../../src/features/news/issue/popularity')>()),
  getContentPopularity: vi.fn(),
}));
vi.mock('../../src/features/stock-content/api', () => ({ getStockRelatedContents: vi.fn() }));
afterEach(cleanup);
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
  vi.mocked(getAllContents).mockReset();
  vi.mocked(getAllContents).mockResolvedValue({ items: [], page: 1, totalPages: 1 });
  vi.mocked(getContentPopularity).mockResolvedValue(new Map([['NEWS:7', 10]]));
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
  expect(getNews).toHaveBeenCalledWith('ALL', 1, 100);
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
    .mockResolvedValueOnce({ ...page, content: [], totalElements: 0 });
  const view = show();
  await view.findByRole('alert');
  fireEvent.click(view.getByRole('button', { name: '다시 시도' }));
  await waitFor(() => expect(view.getByText('아직 등록된 소식이 없어요')).toBeTruthy());
  expect(getNews).toHaveBeenCalledTimes(2);
});

it('트렌딩은 외부 콘텐츠를 제외하고 데이노미 이슈 인기순으로 네 개를 표시한다', async () => {
  vi.mocked(getNews).mockResolvedValue({
    ...page,
    content: [
      page.content[0],
      { ...page.content[0], id: 8, title: '집계 없는 데이노미 이슈', publishedAt: '2026-10-24' },
      { ...page.content[0], id: 9, title: '인기 데이노미 이슈', publishedAt: '2026-10-25' },
      { ...page.content[0], id: 10, title: '네 번째 데이노미 이슈', publishedAt: '2026-10-26' },
    ],
  });
  vi.mocked(getAllContents).mockResolvedValue({
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

it('예시 트렌딩을 펼치고 예시 보유 종목 소식으로 이동할 수 있다', () => {
  const view = render(
    <MemoryRouter initialEntries={['/news?preview=briefing&data=example']}>
      <NewsHomePreview preview />
    </MemoryRouter>,
  );
  fireEvent.click(view.getByText('미국 증시, 금리 경로에 주목… 기술주 중심으로 투자 심리 회복'));
  expect(view.getByText(/실제 서비스에서는 등록된 기사로 이동/).closest('details')?.open).toBe(
    true,
  );
  expect(view.getByRole('link', { name: '내 종목 소식 전체 보기' }).getAttribute('href')).toBe(
    '/news/contents?scope=portfolio&data=example',
  );
  const latest = within(view.getByRole('region', { name: '전체 소식' }));
  const exampleArticles = within(latest.getByRole('list'));
  expect(exampleArticles.getAllByRole('article')).toHaveLength(3);
  expect(exampleArticles.getAllByText('기사 읽기')).toHaveLength(3);
  expect(exampleArticles.getAllByText('데이노미')).toHaveLength(3);
  expect(view.queryByRole('link', { name: /AI 분석/ })).toBeNull();
  expect(calendarRender).toHaveBeenCalledWith({
    embedded: true,
    compact: true,
    upcomingOnly: false,
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

it('예시 화면은 실제 종목 소식 API를 호출하지 않는다', () => {
  render(
    <MemoryRouter initialEntries={['/news?preview=briefing&data=example']}>
      <NewsHomePreview preview />
    </MemoryRouter>,
  );
  expect(getStockRelatedContents).not.toHaveBeenCalled();
});

it('전체 소식은 전체보기와 같은 카테고리 필터를 보여준다', () => {
  const view = render(
    <MemoryRouter initialEntries={['/news?preview=briefing&data=example']}>
      <NewsHomePreview preview />
    </MemoryRouter>,
  );
  const latest = within(view.getByRole('region', { name: '전체 소식' }));
  const filters = within(view.getByRole('group', { name: '전체 소식 카테고리' }));
  expect(filters.getAllByRole('button').map((button) => button.textContent)).toEqual([
    '전체',
    '데이노미',
    'X',
    'Threads',
    'YouTube',
  ]);
  expect(latest.getByText(/대형 기술주 강세/)).toBeTruthy();
  expect(latest.getByText(/환율 상승이 수출 기업/)).toBeTruthy();
});

it('전체 소식 카테고리로 출처를 필터링한다', async () => {
  vi.mocked(getNews).mockResolvedValue(page);
  vi.mocked(getAllContents).mockResolvedValue({
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
  expect(latest.getByText('유튜브 소식')).toBeTruthy();
  expect(latest.queryByText('기준금리 동결 가능성 확대')).toBeNull();
});

it('실제 홈에서는 예시 쿼리를 무시하고 실제 API와 분석 경로만 사용한다', async () => {
  vi.mocked(getNews).mockResolvedValue(page);
  const view = render(
    <MemoryRouter initialEntries={['/news?preview=briefing&data=example']}>
      <NewsHomePreview />
    </MemoryRouter>,
  );
  expect(
    await within(view.getByRole('region', { name: '전체 소식' })).findByRole('link', {
      name: /기준금리 동결/,
    }),
  ).toBeTruthy();
  expect(view.queryByText(/가상 시나리오/)).toBeNull();
  expect(view.queryByRole('button', { name: /예시 콘텐츠로 보기|실제 데이터 보기/ })).toBeNull();
  expect(view.queryByText('삼성전자')).toBeNull();
  expect(getStockRelatedContents).not.toHaveBeenCalled();
  expect(document.title).toBe('소식 | DAYNOMY');
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

it('전체 소식은 최신순으로 최대 다섯 개를 표시한다', async () => {
  vi.mocked(getNews).mockResolvedValue({
    ...page,
    content: Array.from({ length: 6 }, (_, index) => ({
      ...page.content[0],
      id: index + 20,
      title: `최신 기사 ${index}`,
      publishedAt: `2026-10-${20 + index}T00:00:00Z`,
    })),
  });
  const view = show();
  const latest = within(view.getByRole('region', { name: '전체 소식' }));
  await latest.findByRole('link', { name: /최신 기사 5/ });
  expect(latest.getAllByRole('article')).toHaveLength(5);
  expect(latest.queryByRole('link', { name: /최신 기사 0/ })).toBeNull();
});
