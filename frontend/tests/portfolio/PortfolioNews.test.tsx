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

it('이미지가 있는 소식에는 원본 썸네일을, 없는 소식에는 출처별 대체 썸네일을 표시한다', async () => {
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
        url: 'https://youtube.com/watch?v=1',
        imageUrl: 'https://images.example.test/video.jpg',
      },
    ],
  });
  const view = render(<PortfolioNews holdings={[{ assetId: 1, name: '종목1' }]} />);

  await view.findAllByText('공통 소식');
  expect(view.container.querySelector('.portfolio-news-thumbnail.source-x img')).toBeNull();
  expect(
    view.container
      .querySelector('.portfolio-news-thumbnail.source-youtube img')
      ?.getAttribute('src'),
  ).toBe('https://images.example.test/video.jpg');
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
    'click_content',
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
  '중복 소식은 조회 순서와 무관하게 최신 등록일로 표시하고 정렬한다 (%s, %s)',
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
