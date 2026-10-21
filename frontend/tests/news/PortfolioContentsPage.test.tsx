/** @vitest-environment jsdom */
import { act, cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { getStockRelatedContents } from '../../src/features/stock-content/api';
import type { StockRelatedContentsResponse } from '../../src/features/stock-content/types';
import { IssueHomePage } from '../../src/features/news/issue/IssueHomePage';

const state = vi.hoisted(() => ({ holdings: [] as { assetId: number; name: string }[] }));
vi.mock('../../src/features/stock-content/api', () => ({ getStockRelatedContents: vi.fn() }));
vi.mock('../../src/analytics', () => ({ trackEvent: vi.fn() }));
vi.mock('../../src/hooks/useLoginStatus', () => ({
  useAuth: () => ({ isLoggedIn: true, loading: false }),
}));
vi.mock('../../src/features/portfolio/hooks/usePortfolioHoldings', () => ({
  usePortfolioHoldings: () => ({ holdings: state.holdings, loading: false, error: '' }),
}));
beforeEach(() => {
  state.holdings = [];
  vi.resetAllMocks();
});
afterEach(cleanup);

function response(id: number): StockRelatedContentsResponse {
  return {
    assetId: id,
    assetCode: String(id),
    assetName: `종목${id}`,
    contents: [
      {
        id,
        assetId: id,
        sourceType: 'X',
        title: `종목${id} 소식`,
        url: `https://x.com/test/status/${id}`,
        imageUrl: null,
        createdAt: '2026-10-19T00:00:00Z',
      },
    ],
  };
}
function show(query = '') {
  return render(
    <MemoryRouter initialEntries={[`/news/contents?scope=portfolio${query}`]}>
      <IssueHomePage fullFeed />
    </MemoryRouter>,
  );
}

it('내 종목 소식을 표시하고 모든 소식 보기는 소식 탭으로 이동한다', async () => {
  const view = render(
    <MemoryRouter initialEntries={['/news/contents?scope=portfolio']}>
      <Routes>
        <Route path="/news/contents" element={<IssueHomePage fullFeed />} />
        <Route path="/news" element={<h1>소식 탭 도착</h1>} />
      </Routes>
    </MemoryRouter>,
  );
  expect(await view.findByText('내 종목 소식')).toBeTruthy();
  expect(view.getByRole('heading', { name: '전체 보유 종목' })).toBeTruthy();
  const link = view.getByRole('link', { name: '모든 소식 보기' });
  expect(link.getAttribute('href')).toBe('/news');
  fireEvent.click(link);
  expect(await view.findByRole('heading', { name: '소식 탭 도착' })).toBeTruthy();
});

it('선택한 종목만 요청하고 선택하지 않은 종목은 기다리지 않는다', async () => {
  state.holdings = [
    { assetId: 1, name: '종목1' },
    { assetId: 2, name: '종목2' },
  ];
  vi.mocked(getStockRelatedContents).mockImplementation((id) =>
    id === 1 ? Promise.resolve(response(id)) : new Promise(() => {}),
  );
  const view = show('&asset=1');
  expect(await view.findByRole('button', { name: /종목1 소식/ })).toBeTruthy();
  expect(getStockRelatedContents).toHaveBeenCalledExactlyOnceWith(1, expect.any(AbortSignal));
});

it('전체 조회가 느려도 종목을 선택하면 새 결과를 표시하고 이전 응답은 무시한다', async () => {
  state.holdings = [
    { assetId: 1, name: '종목1' },
    { assetId: 2, name: '종목2' },
  ];
  let resolveSlow!: (value: StockRelatedContentsResponse) => void;
  const slow = new Promise<StockRelatedContentsResponse>((resolve) => {
    resolveSlow = resolve;
  });
  vi.mocked(getStockRelatedContents).mockImplementation((id) =>
    id === 2 ? slow : Promise.resolve(response(id)),
  );
  const view = show();
  await waitFor(() => expect(getStockRelatedContents).toHaveBeenCalledTimes(2));
  const oldSignal = vi.mocked(getStockRelatedContents).mock.calls[0][1]!;
  fireEvent.click(view.getByRole('button', { name: '종목1' }));
  expect(await view.findByRole('button', { name: /종목1 소식/ })).toBeTruthy();
  expect(oldSignal.aborted).toBe(true);
  await act(async () => resolveSlow(response(2)));
  expect(view.queryByRole('button', { name: /종목2 소식/ })).toBeNull();
  expect(view.getByRole('button', { name: /종목1 소식/ })).toBeTruthy();
});

it('종목별 콘텐츠에서도 인기순을 선택할 수 있다', async () => {
  state.holdings = [
    { assetId: 1, name: '종목1' },
    { assetId: 2, name: '종목2' },
  ];
  vi.mocked(getStockRelatedContents).mockImplementation((id) => Promise.resolve(response(id)));
  vi.stubGlobal(
    'fetch',
    vi.fn(
      async () =>
        new Response(
          JSON.stringify({
            periodDays: 7,
            items: [
              {
                contentType: 'ASSET_CONTENT',
                contentId: 1,
                score: 8,
                lastInteractedAt: '2026-10-20T02:00:00Z',
              },
            ],
          }),
          { status: 200, headers: { 'content-type': 'application/json' } },
        ),
    ),
  );
  const view = show('&sort=popular');

  expect(await view.findByRole('button', { name: '인기순', pressed: true })).toBeTruthy();
  await waitFor(() => expect(view.container.querySelectorAll('.issue-feed > li')).toHaveLength(2));
  expect(view.container.querySelector('.issue-feed > li')?.textContent).toContain('종목1 소식');
});
