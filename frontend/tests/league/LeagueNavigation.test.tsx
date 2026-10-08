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

it('상세 화면에는 리그로 돌아가는 링크만 표시하고 첫 화면에는 보조 탭이 없다', () => {
  const view = render(
    <MemoryRouter initialEntries={['/league/portfolio']}>
      <LeagueNavigation />
    </MemoryRouter>,
  );
  expect(view.getAllByRole('link')).toHaveLength(1);
  expect(view.getByRole('link', { name: '← 투자 리그' }).getAttribute('href')).toBe('/league');
  view.unmount();
  const news = render(
    <MemoryRouter initialEntries={['/league']}>
      <LeagueNavigation />
    </MemoryRouter>,
  );
  expect(news.queryByRole('navigation')).toBeNull();
});
