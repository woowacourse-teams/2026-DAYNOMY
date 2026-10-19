/** @vitest-environment jsdom */
import { cleanup, render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, expect, it, vi } from 'vitest';
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
