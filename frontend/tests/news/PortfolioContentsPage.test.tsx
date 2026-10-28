/** @vitest-environment jsdom */
import { act, cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { trackEvent } from '../../src/analytics';
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

it('보유 종목 소식을 확인한 뒤 기존 포트폴리오의 비중·분석으로 이동한다', async () => {
  state.holdings = [{ assetId: 1, name: '종목1' }];
  vi.mocked(getStockRelatedContents).mockResolvedValue(response(1));
  const view = render(
    <MemoryRouter initialEntries={['/news/contents?scope=portfolio']}>
      <Routes>
        <Route path="/news/contents" element={<IssueHomePage fullFeed />} />
        <Route path="/" element={<h1>포트폴리오 도착</h1>} />
      </Routes>
    </MemoryRouter>,
  );
  fireEvent.click(await view.findByRole('button', { name: /종목1 소식/ }));
  fireEvent.click(view.getByRole('link', { name: '내 포트폴리오 AI 분석 보기' }));
  expect(await view.findByRole('heading', { name: '포트폴리오 도착' })).toBeTruthy();
});

it('보유 종목이 없으면 분석 대신 포트폴리오 등록 안내를 보여준다', async () => {
  const view = show();
  expect(await view.findByRole('link', { name: '포트폴리오로 이동' })).toBeTruthy();
  expect(view.queryByRole('link', { name: '내 포트폴리오 AI 분석 보기' })).toBeNull();
});

it('예시 데이노미 제목은 상세로 연결하고 목록에는 분석 버튼을 중복 표시하지 않는다', async () => {
  const view = show('&data=example');
  const article = await view.findByRole('link', { name: /삼성전자, 반도체/ });
  const url = new URL(article.getAttribute('href')!);
  expect(url.pathname).toBe('/news/900001');
  expect(url.searchParams.get('data')).toBe('example');
  expect(url.searchParams.get('from')).toBe('portfolio');
  expect(view.queryByRole('link', { name: '내 포트폴리오 AI 분석 보기' })).toBeNull();
  fireEvent.click(view.getByRole('button', { name: /^엔비디아$/ }));
  expect(await view.findByRole('link', { name: /엔비디아, 주요 고객사/ })).toBeTruthy();
  expect(getStockRelatedContents).not.toHaveBeenCalled();
  expect(trackEvent).not.toHaveBeenCalled();
});

it('데이노미 기사 상세로 이동해도 포트폴리오에서 온 맥락을 유지한다', async () => {
  state.holdings = [{ assetId: 1, name: '종목1' }];
  const data = response(1);
  data.contents[0].sourceType = 'INTERNAL_NEWS';
  data.contents[0].url = '/news/42?view=full#body';
  vi.mocked(getStockRelatedContents).mockResolvedValue(data);
  const view = show();
  const article = await view.findByRole('link', { name: /종목1 소식/ });
  const target = new URL(article.getAttribute('href')!);
  expect(target.pathname).toBe('/news/42');
  expect(target.searchParams.get('from')).toBe('portfolio');
  expect(target.searchParams.get('view')).toBe('full');
  expect(target.hash).toBe('#body');
  expect(view.queryByRole('link', { name: '내 포트폴리오 AI 분석 보기' })).toBeNull();
});

it('첫 화면에서 지정한 소셜 글은 바로 펼쳐지고 다시 접을 수 있다', async () => {
  state.holdings = [{ assetId: 1, name: '종목1' }];
  vi.mocked(getStockRelatedContents).mockResolvedValue(response(1));
  const view = show('&asset=1&open=1');
  const trigger = await view.findByRole('button', { name: /종목1 소식/ });
  await waitFor(() => expect(trigger.getAttribute('aria-expanded')).toBe('true'));
  fireEvent.click(trigger);
  expect(trigger.getAttribute('aria-expanded')).toBe('false');
});

it('존재하지 않는 소셜 글 요청은 다른 글을 펼치지 않는다', async () => {
  state.holdings = [{ assetId: 1, name: '종목1' }];
  vi.mocked(getStockRelatedContents).mockResolvedValue(response(1));
  const view = show('&asset=1&open=999999');
  const trigger = await view.findByRole('button', { name: /종목1 소식/ });
  expect(trigger.getAttribute('aria-expanded')).toBe('false');
});

it('지정한 글이 다음 페이지에 있어도 그 페이지에서 바로 펼친다', async () => {
  state.holdings = [{ assetId: 1, name: '종목1' }];
  const result = response(1);
  result.contents = Array.from({ length: 14 }, (_, index) => ({
    ...result.contents[0],
    id: index + 1,
    title: `지정 글 ${index + 1}`,
    url: `https://x.com/test/status/${index + 1}`,
  }));
  vi.mocked(getStockRelatedContents).mockResolvedValue(result);
  const view = show('&asset=1&open=1');
  const trigger = await view.findByRole('button', { name: /지정 글 1\s*(글 펼치기|접기)/ });
  await waitFor(() => expect(trigger.getAttribute('aria-expanded')).toBe('true'));
  expect(view.queryByRole('button', { name: /지정 글 14/ })).toBeNull();
});
