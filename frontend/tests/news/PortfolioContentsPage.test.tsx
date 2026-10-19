/** @vitest-environment jsdom */
import { cleanup, fireEvent, render } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, expect, it, vi } from 'vitest';
import { IssueHomePage } from '../../src/features/news/issue/IssueHomePage';

vi.mock('../../src/analytics', () => ({ trackEvent: vi.fn() }));
vi.mock('../../src/hooks/useLoginStatus', () => ({
  useAuth: () => ({ isLoggedIn: true, loading: false }),
}));
vi.mock('../../src/features/portfolio/hooks/usePortfolioHoldings', () => ({
  usePortfolioHoldings: () => ({ holdings: [], loading: false, error: '' }),
}));
afterEach(cleanup);

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
