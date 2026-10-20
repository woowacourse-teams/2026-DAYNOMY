/** @vitest-environment jsdom */
import { trackEvent } from '../../src/analytics';
vi.mock('../../src/analytics', () => ({ trackEvent: vi.fn() }));
import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { PortfolioNews } from '../../src/features/portfolio/components/PortfolioNews';
import { getStockRelatedContents } from '../../src/features/stock-content/api';
vi.mock('../../src/features/stock-content/api', () => ({ getStockRelatedContents: vi.fn() }));
afterEach(() => {
  cleanup();
  vi.mocked(trackEvent).mockClear();
  vi.resetAllMocks();
});
const content = {
  id: 1,
  assetId: 1,
  sourceType: 'X' as const,
  title: '공통 소식',
  url: 'https://x.com/test/status/1',
  imageUrl: null,
  createdAt: '2026-10-12T10:00:00Z',
};
it('모든 보유 종목의 소식을 최신순으로 병합하고 같은 글에 관련 종목을 모은다', async () => {
  vi.mocked(getStockRelatedContents).mockImplementation(async (id) => ({
    assetId: id,
    assetCode: String(id),
    assetName: `종목${id}`,
    contents:
      id === 1
        ? [content]
        : [
            { ...content, id: 2, assetId: 2 },
            {
              ...content,
              id: 3,
              assetId: 2,
              title: '새 소식',
              url: 'https://x.com/test/status/2',
              createdAt: '2026-10-13T01:00:00Z',
            },
          ],
  }));
  const view = render(
    <PortfolioNews
      holdings={[
        { assetId: 1, name: '종목1' },
        { assetId: 2, name: '종목2' },
      ]}
    />,
  );
  await view.findByText('새 소식');
  expect(getStockRelatedContents).toHaveBeenCalledTimes(2);
  expect(view.container.querySelectorAll('.portfolio-news-list a')).toHaveLength(2);
  expect(view.getByRole('link', { name: /보유 종목 소식 더 보기/ }).getAttribute('href')).toBe(
    '/news/contents?scope=portfolio',
  );
  expect(view.getAllByRole('link')[0].textContent).toContain('새 소식');
  expect(view.getByText('종목1 · 종목2')).toBeTruthy();
  expect(view.container.querySelector('.portfolio-news-thumbnail.source-x')).toBeTruthy();
  view.rerender(<PortfolioNews holdings={[]} />);
  await waitFor(() => expect(view.queryByText('새 소식')).toBeNull());
  expect(view.getByText('자산을 등록하면 관련 소식이 여기에 모입니다.')).toBeTruthy();
});

it('YouTube는 저장 이미지 대신 영상 썸네일을 사용하고 로드 실패 시 출처 아이콘을 표시한다', async () => {
  vi.mocked(getStockRelatedContents).mockResolvedValue({
    assetId: 1,
    assetCode: '1',
    assetName: '종목1',
    contents: [
      content,
      {
        ...content,
        id: 2,
        sourceType: 'YOUTUBE',
        url: 'https://youtube.com/watch?v=gLniGzh7oWo',
        imageUrl: 'https://images.example.test/video.jpg',
      },
    ],
  });
  const view = render(<PortfolioNews holdings={[{ assetId: 1, name: '종목1' }]} />);

  await view.findAllByText('공통 소식');
  expect(view.container.querySelector('.portfolio-news-thumbnail.source-x > img')).toBeNull();
  expect(
    view.container.querySelector('.source-x .portfolio-news-thumbnail-fallback img'),
  ).toBeTruthy();
  expect(
    view.container
      .querySelector('.portfolio-news-thumbnail.source-youtube > img')
      ?.getAttribute('src'),
  ).toBe('https://i.ytimg.com/vi/gLniGzh7oWo/hqdefault.jpg');
  const image = view.container.querySelector('.portfolio-news-thumbnail.source-youtube > img')!;
  fireEvent.error(image);
  expect(image.hasAttribute('hidden')).toBe(true);
  expect(
    view.container.querySelector('.source-youtube .portfolio-news-thumbnail-fallback svg'),
  ).toBeTruthy();
});

it('YouTube 이미지가 없으면 영상 URL에서 썸네일을 만든다', async () => {
  vi.mocked(getStockRelatedContents).mockResolvedValue({
    assetId: 1,
    assetCode: '1',
    assetName: '종목1',
    contents: [
      {
        ...content,
        sourceType: 'YOUTUBE',
        url: 'https://www.youtube.com/watch?v=gLniGzh7oWo',
      },
    ],
  });
  const view = render(<PortfolioNews holdings={[{ assetId: 1, name: '종목1' }]} />);

  await view.findByText('공통 소식');
  expect(
    view.container
      .querySelector('.portfolio-news-thumbnail.source-youtube > img')
      ?.getAttribute('src'),
  ).toBe('https://i.ytimg.com/vi/gLniGzh7oWo/hqdefault.jpg');
});

it('일부 종목 조회가 실패해도 성공한 소식은 유지하고 재시도한다', async () => {
  vi.mocked(getStockRelatedContents).mockImplementation(async (id) => {
    if (id === 2) throw new Error('failed');
    return { assetId: id, assetCode: '1', assetName: '종목1', contents: [content] };
  });
  const view = render(
    <PortfolioNews
      holdings={[
        { assetId: 1, name: '종목1' },
        { assetId: 2, name: '종목2' },
      ]}
    />,
  );
  await view.findByRole('alert');
  expect(trackEvent).toHaveBeenCalledWith(
    'content_load_result',
    expect.objectContaining({
      surface: 'portfolio_news',
      status: 'partial_error',
      result_count: 1,
      attempt: 0,
    }),
  );
  expect(view.getByText('공통 소식')).toBeTruthy();
  vi.mocked(getStockRelatedContents).mockResolvedValue({
    assetId: 1,
    assetCode: '1',
    assetName: '종목1',
    contents: [content],
  });
  fireEvent.click(view.getByRole('button', { name: '소식 다시 불러오기' }));
  await waitFor(() => expect(view.queryByRole('alert')).toBeNull());
  expect(trackEvent).toHaveBeenCalledWith('retry_content_load', {
    surface: 'portfolio_news',
    area: 'all',
    attempt: 1,
  });
  expect(trackEvent).toHaveBeenCalledWith(
    'content_load_result',
    expect.objectContaining({ status: 'success', result_count: 1, attempt: 1 }),
  );
  fireEvent.click(view.getByRole('link', { name: /공통 소식/ }));
  expect(trackEvent).toHaveBeenCalledWith(
    'click_content_original',
    expect.objectContaining({ surface: 'portfolio_news', position: 1 }),
  );
  expect(JSON.stringify(vi.mocked(trackEvent).mock.calls)).not.toContain('종목1');
  expect(view.getByText('공통 소식')).toBeTruthy();
});

it.each([
  ['2026-10-12T10:00:00Z', '2026-10-14T01:00:00Z'],
  ['2026-10-14T01:00:00Z', '2026-10-12T10:00:00Z'],
  [null, '2026-10-14T01:00:00Z'],
])(
  '게시일 없는 중복 소식은 조회 순서와 무관하게 최신 등록일로 표시하고 정렬한다 (%s, %s)',
  async (first, second) => {
    vi.mocked(getStockRelatedContents).mockImplementation(async (id) => ({
      assetId: id,
      assetCode: String(id),
      assetName: `종목${id}`,
      contents: [
        { ...content, assetId: id, createdAt: id === 1 ? first : second },
        ...(id === 1
          ? [
              {
                ...content,
                id: 3,
                title: '중간 소식',
                url: 'https://x.com/test/status/3',
                createdAt: '2026-10-13T01:00:00Z',
              },
            ]
          : []),
      ],
    }));
    const view = render(
      <PortfolioNews
        holdings={[
          { assetId: 1, name: '종목1' },
          { assetId: 2, name: '종목2' },
        ]}
      />,
    );
    await view.findByText('공통 소식');
    const links = view.container.querySelectorAll('.portfolio-news-list a');
    expect(links).toHaveLength(2);
    expect(links[0].textContent).toContain('공통 소식');
    expect(links[0].querySelector('time')?.getAttribute('datetime')).toBe('2026-10-14T01:00:00Z');
    expect(links[0].textContent).toContain('종목1 · 종목2');
  },
);

it.each([null, undefined])(
  '게시일을 우선하고 게시일이 %s이면 등록일로 정렬한다',
  async (missingDate) => {
    vi.mocked(getStockRelatedContents).mockResolvedValue({
      assetId: 1,
      assetCode: '1',
      assetName: '종목1',
      contents: [
        {
          ...content,
          title: '최근 등록된 오래된 글',
          publishedDate: '2026-10-10',
          createdAt: '2026-10-19T00:00:00Z',
        },
        {
          ...content,
          id: 2,
          url: 'https://x.com/test/status/2',
          title: '최근 게시된 글',
          publishedDate: '2026-10-15',
          createdAt: '2026-10-16T00:00:00Z',
        },
        {
          ...content,
          id: 3,
          url: 'https://x.com/test/status/3',
          title: '게시일 없는 글',
          publishedDate: missingDate,
          createdAt: '2026-10-14T00:00:00Z',
        },
      ],
    });
    const view = render(<PortfolioNews holdings={[{ assetId: 1, name: '종목1' }]} />);
    await view.findByText('게시일 없는 글');
    const links = Array.from(view.container.querySelectorAll('.portfolio-news-list a'));
    expect(links.map((link) => link.querySelector('strong')?.textContent)).toEqual([
      '최근 게시된 글',
      '게시일 없는 글',
      '최근 등록된 오래된 글',
    ]);
    expect(links.map((link) => link.querySelector('time')?.textContent)).toEqual([
      '10.15 발행',
      '10.14 등록',
      '10.10 발행',
    ]);
    expect(links.map((link) => link.querySelector('time')?.getAttribute('datetime'))).toEqual([
      '2026-10-15',
      '2026-10-14T00:00:00Z',
      '2026-10-10',
    ]);
  },
);

it.each([
  ['2026-10-15', null],
  [null, '2026-10-15'],
])('중복 글의 게시일을 유지하며 등록일보다 우선한다 (%s, %s)', async (first, second) => {
  vi.mocked(getStockRelatedContents).mockImplementation(async (id) => ({
    assetId: id,
    assetCode: String(id),
    assetName: `종목${id}`,
    contents: [
      {
        ...content,
        assetId: id,
        publishedDate: id === 1 ? first : second,
        createdAt: '2026-10-19T00:00:00Z',
      },
      ...(id === 1
        ? [
            {
              ...content,
              id: 3,
              url: 'https://x.com/test/status/3',
              title: '더 최근 게시된 글',
              publishedDate: '2026-10-16',
              createdAt: '2026-10-17T00:00:00Z',
            },
          ]
        : []),
    ],
  }));
  const view = render(
    <PortfolioNews
      holdings={[
        { assetId: 1, name: '종목1' },
        { assetId: 2, name: '종목2' },
      ]}
    />,
  );
  await view.findByText('공통 소식');
  const links = view.container.querySelectorAll('.portfolio-news-list a');
  expect(links).toHaveLength(2);
  expect(links[0].querySelector('strong')?.textContent).toBe('더 최근 게시된 글');
  expect(links[1].querySelector('time')?.textContent).toBe('10.15 발행');
  expect(links[1].querySelector('time')?.getAttribute('datetime')).toBe('2026-10-15');
  expect(links[1].textContent).toContain('종목1 · 종목2');
});

it('체험 모드의 더보기는 보유 종목 없이 전체 소식으로 연결하고 분석 목적지를 구분한다', () => {
  const view = render(<PortfolioNews demo holdings={[]} />);
  const link = view.getByRole('link', { name: '모든 소식 보기' });
  expect(link.getAttribute('href')).toBe('/news');
  expect(view.queryByRole('link', { name: /보유 종목 소식 더 보기/ })).toBeNull();
  fireEvent.click(link);
  expect(trackEvent).toHaveBeenCalledWith('click_content_more', {
    surface: 'portfolio_news',
    destination: 'news_home',
  });
  expect(getStockRelatedContents).not.toHaveBeenCalled();
});

it('최신 소식 5개만 보여주고 나머지는 보유 종목 소식 더보기로 연결한다', async () => {
  vi.mocked(getStockRelatedContents).mockResolvedValue({
    assetId: 1,
    assetCode: '1',
    assetName: '종목1',
    contents: [1, 4, 2, 5, 3, 7, 6].map((id) => ({
      ...content,
      id,
      title: `소식 ${id}`,
      url: `https://x.com/test/status/${id}`,
      createdAt: `2026-10-12T0${id}:00:00Z`,
    })),
  });
  const view = render(<PortfolioNews holdings={[{ assetId: 1, name: '종목1' }]} />);
  await view.findByText('소식 7');
  expect(
    [...view.container.querySelectorAll('.portfolio-news-list strong')].map(
      (item) => item.textContent,
    ),
  ).toEqual(['소식 7', '소식 6', '소식 5', '소식 4', '소식 3']);
  expect(view.queryByText('소식 2')).toBeNull();
  expect(view.getByRole('link', { name: /보유 종목 소식 더 보기/ }).getAttribute('href')).toBe(
    '/news/contents?scope=portfolio',
  );
});
