/** @vitest-environment jsdom */
import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, expect, it, vi } from 'vitest';
import { trackEvent } from '../../src/analytics';
import { PortfolioContentsPage } from '../../src/features/news/issue/PortfolioContentsPage';
import { getStockRelatedContents } from '../../src/features/stock-content/api';

vi.mock('../../src/analytics', () => ({ trackEvent: vi.fn() }));
vi.mock('../../src/hooks/useLoginStatus', () => ({
  useAuth: () => ({ isLoggedIn: true, loading: false }),
}));
vi.mock('../../src/features/portfolio/hooks/usePortfolioHoldings', () => ({
  usePortfolioHoldings: () => ({
    holdings: [
      { assetId: 1, name: '종목1' },
      { assetId: 2, name: '종목2' },
    ],
    loading: false,
    error: '',
  }),
}));
vi.mock('../../src/features/stock-content/api', () => ({ getStockRelatedContents: vi.fn() }));
afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});

it('조회·목록·필터·콘텐츠 행동을 공통 이벤트 스키마로 기록한다', async () => {
  vi.mocked(getStockRelatedContents).mockImplementation(async (id) => ({
    assetId: id,
    assetCode: String(id),
    assetName: `종목${id}`,
    contents:
      id === 1
        ? [
            {
              id: 10,
              assetId: id,
              sourceType: 'INTERNAL_NEWS',
              title: '내부 기사',
              url: '/news/10',
              imageUrl: null,
              createdAt: '2026-10-19T00:00:00Z',
            },
          ]
        : [
            {
              id: 20,
              assetId: id,
              sourceType: 'YOUTUBE',
              title: '외부 영상',
              url: 'https://youtube.com/watch?v=abcdefghijk',
              imageUrl: null,
              createdAt: '2026-10-18T00:00:00Z',
            },
          ],
  }));
  const view = render(
    <MemoryRouter>
      <PortfolioContentsPage />
    </MemoryRouter>,
  );

  const articleLink = await view.findByRole('link', { name: /내부 기사/ });
  await waitFor(() =>
    expect(trackEvent).toHaveBeenCalledWith(
      'content_load_result',
      expect.objectContaining({
        surface: 'portfolio_contents',
        area: 'all',
        status: 'success',
        result_count: 2,
        attempt: 0,
      }),
    ),
  );
  expect(trackEvent).toHaveBeenCalledWith('view_content_list', {
    surface: 'portfolio_contents',
    source_type: 'ALL',
    result_count: 2,
    page_number: 1,
    status: 'ready',
  });

  articleLink.addEventListener('click', (event) => event.preventDefault());
  fireEvent.click(articleLink);
  expect(trackEvent).toHaveBeenCalledWith(
    'click_content',
    expect.objectContaining({
      surface: 'portfolio_contents',
      content_id: 'content-10',
      source_type: 'DAYNOMY',
    }),
  );

  fireEvent.click(view.getByRole('button', { name: 'YouTube' }));
  expect(trackEvent).toHaveBeenCalledWith('filter_content_source', {
    surface: 'portfolio_contents',
    previous_source: 'ALL',
    source_type: 'YOUTUBE',
    result_count: 1,
  });
  const videoButton = await view.findByRole('button', { name: /외부 영상/ });
  fireEvent.click(videoButton);
  expect(trackEvent).toHaveBeenCalledWith('expand_content', {
    surface: 'portfolio_contents',
    content_id: 'content-20',
    content_type: 'external',
    source_type: 'YOUTUBE',
    position: 1,
  });
  fireEvent.click(view.getByRole('link', { name: /YouTube에서 원문 보기/ }));
  expect(trackEvent).toHaveBeenCalledWith(
    'click_content_original',
    expect.objectContaining({ content_id: 'content-20' }),
  );
});

it('페이지 변경 이벤트에 보유 종목 식별자를 포함하지 않는다', async () => {
  vi.mocked(getStockRelatedContents).mockImplementation(async (id) => ({
    assetId: id,
    assetCode: String(id),
    assetName: `종목${id}`,
    contents:
      id === 1
        ? Array.from({ length: 13 }, (_, index) => ({
            id: index + 1,
            assetId: id,
            sourceType: 'INTERNAL_NEWS' as const,
            title: `기사 ${index + 1}`,
            url: `/news/${index + 1}`,
            imageUrl: null,
            createdAt: `2026-10-${String(index + 1).padStart(2, '0')}T00:00:00Z`,
          }))
        : [],
  }));
  const view = render(
    <MemoryRouter>
      <PortfolioContentsPage />
    </MemoryRouter>,
  );

  await view.findByRole('button', { name: '2페이지' });
  fireEvent.click(view.getByRole('button', { name: '2페이지' }));
  expect(trackEvent).toHaveBeenCalledWith('change_content_page', {
    surface: 'portfolio_contents',
    source_type: 'ALL',
    previous_page: 1,
    page_number: 2,
  });
  expect(JSON.stringify(vi.mocked(trackEvent).mock.calls)).not.toContain('asset_id');
});

it('일부 종목 조회 실패와 재시도를 결과 이벤트로 기록한다', async () => {
  vi.mocked(getStockRelatedContents).mockImplementation(async (id) => {
    if (id === 2) throw new Error('failed');
    return {
      assetId: id,
      assetCode: String(id),
      assetName: `종목${id}`,
      contents: [
        {
          id: 1,
          assetId: id,
          sourceType: 'X',
          title: '성공한 소식',
          url: 'https://x.com/test/status/1',
          imageUrl: null,
          createdAt: '2026-10-19T00:00:00Z',
        },
      ],
    };
  });
  const view = render(
    <MemoryRouter>
      <PortfolioContentsPage />
    </MemoryRouter>,
  );

  await view.findByRole('alert');
  expect(trackEvent).toHaveBeenCalledWith(
    'content_load_result',
    expect.objectContaining({
      surface: 'portfolio_contents',
      status: 'partial_error',
      result_count: 1,
      attempt: 0,
    }),
  );
  fireEvent.click(view.getByRole('button', { name: '다시 시도' }));
  expect(trackEvent).toHaveBeenCalledWith('retry_content_load', {
    surface: 'portfolio_contents',
    area: 'all',
    attempt: 1,
  });
});

it.each([
  ['2026-10-12', '2026-10-15'],
  ['2026-10-15', '2026-10-12'],
  [null, '2026-10-15'],
  ['2026-10-15', null],
  [null, null],
])('중복 URL은 최신 날짜를 병합하고 게시일 우선으로 정렬한다 (%s, %s)', async (first, second) => {
  const shared = {
    id: 1,
    assetId: 1,
    sourceType: 'X' as const,
    title: '공통 소식',
    url: 'https://x.com/test/status/1',
    imageUrl: null,
  };
  vi.mocked(getStockRelatedContents).mockImplementation(async (id) => ({
    assetId: id,
    assetCode: String(id),
    assetName: `종목${id}`,
    contents: [
      {
        ...shared,
        assetId: id,
        publishedDate: id === 1 ? first : second,
        createdAt: id === 1 ? '2026-10-17T00:00:00Z' : '2026-10-19T00:00:00Z',
      },
      ...(id === 1
        ? [
            {
              ...shared,
              id: 2,
              title: '비교 소식',
              url: 'https://x.com/test/status/2',
              publishedDate: '2026-10-16',
              createdAt: '2026-10-16T00:00:00Z',
            },
          ]
        : []),
    ],
  }));
  const view = render(
    <MemoryRouter>
      <PortfolioContentsPage />
    </MemoryRouter>,
  );
  await view.findByRole('button', { name: /공통 소식/ });
  const rows = Array.from(view.container.querySelectorAll('.issue-feed > li'));
  expect(rows).toHaveLength(2);
  const hasPublishedDate = !!(first || second);
  expect(rows[0].querySelector('.issue-post-title')?.textContent).toBe(
    hasPublishedDate ? '비교 소식' : '공통 소식',
  );
  const sharedRow = view.getByRole('button', { name: /공통 소식/ }).closest('li')!;
  expect(sharedRow.querySelector('time')?.getAttribute('datetime')).toBe(
    hasPublishedDate ? '2026-10-15' : '2026-10-19T00:00:00Z',
  );
  expect(sharedRow.textContent).toContain('종목1 · 종목2');
});
