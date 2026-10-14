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

it('순위에서는 중복 탭을 표시하지 않는다', () => {
  const view = render(
    <MemoryRouter initialEntries={['/league']}>
      <LeagueNavigation />
    </MemoryRouter>,
  );
  expect(view.queryByRole('navigation')).toBeNull();
});
it.each([
  '/league/profile',
  '/league/portfolio',
  '/league/reviews',
  '/league/following',
  '/portfolio/publication',
])('%s에서는 리그로 돌아가는 링크 하나만 제공한다', (path) => {
  const view = render(
    <MemoryRouter initialEntries={[path]}>
      <LeagueNavigation />
    </MemoryRouter>,
  );
  expect(view.getAllByRole('link')).toHaveLength(1);
  expect(view.getByRole('link', { name: '← 주간 리그' }).getAttribute('href')).toBe('/league');
});
