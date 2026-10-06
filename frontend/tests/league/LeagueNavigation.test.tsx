/** @vitest-environment jsdom */

import { cleanup, render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, expect, it } from 'vitest';
import { LeagueNavigation } from '../../src/features/league/LeagueNavigation';

afterEach(cleanup);

it('삭제한 돈 관리 화면에서는 더 이상 기능 메뉴를 표시하지 않는다', () => {
  const view = render(
    <MemoryRouter initialEntries={['/finance/guides/brokerage-account']}>
      <LeagueNavigation />
    </MemoryRouter>,
  );
  expect(view.queryByRole('navigation')).toBeNull();
  expect(view.queryByRole('link', { name: /초보 가이드|저축·투자 계획/ })).toBeNull();
});

it('공유용 자산에서는 리그 메뉴로 연결하고 다른 화면에는 표시하지 않는다', () => {
  const view = render(
    <MemoryRouter initialEntries={['/league/portfolio']}>
      <LeagueNavigation />
    </MemoryRouter>,
  );
  expect(view.getByRole('link', { name: /공유 포트폴리오/ }).getAttribute('aria-current')).toBe(
    'page',
  );
  expect(view.getByRole('link', { name: /리그 참여 설정/ }).getAttribute('href')).toBe(
    '/portfolio/publication',
  );
  view.unmount();
  const news = render(
    <MemoryRouter initialEntries={['/news']}>
      <LeagueNavigation />
    </MemoryRouter>,
  );
  expect(news.queryByRole('navigation')).toBeNull();
});
