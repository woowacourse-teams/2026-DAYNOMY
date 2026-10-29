/** @vitest-environment jsdom */
import { cleanup, render, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, expect, it, vi } from 'vitest';
import { NewsDetailPage } from '../../src/features/news/newsdetail/NewsDetailPage';
import { getNewsDetail } from '../../src/features/news/newsdetail/api';
import { trackEvent } from '../../src/analytics';
vi.mock('../../src/features/news/newsdetail/api', () => ({ getNewsDetail: vi.fn() }));
vi.mock('../../src/analytics', () => ({ trackEvent: vi.fn() }));
afterEach(() => {
  cleanup();
  window.history.replaceState(null, '', '/');
  vi.clearAllMocks();
});
it('예시도 상세 본문을 표시하고 읽은 뒤 분석으로 한 번만 연결한다', async () => {
  window.history.replaceState(null, '', '/news/900001');
  const view = render(
    <MemoryRouter initialEntries={['/news/900001?data=example&from=portfolio']}>
      <NewsDetailPage />
    </MemoryRouter>,
  );
  await view.findByRole('heading', { name: /삼성전자, 반도체 영업이익 감소/ });
  expect(
    within(view.getByRole('region', { name: '뉴스 본문' })).getByText(
      /가격 하락이 다음 분기에도 이어질지/,
    ),
  ).toBeTruthy();
  const links = view.getAllByRole('link', { name: '내 포트폴리오 AI 분석 보기' });
  expect(links).toHaveLength(1);
  expect(links[0].getAttribute('href')).toBe('/');
  expect(getNewsDetail).not.toHaveBeenCalled();
  expect(trackEvent).not.toHaveBeenCalled();
});
it('없는 예시 ID는 실제 API나 다른 기사로 대체하지 않는다', async () => {
  window.history.replaceState(null, '', '/news/999999');
  const view = render(
    <MemoryRouter initialEntries={['/news/999999?data=example&from=portfolio']}>
      <NewsDetailPage />
    </MemoryRouter>,
  );
  expect(await view.findByRole('alert')).toHaveProperty(
    'textContent',
    '예시 기사를 찾을 수 없습니다.',
  );
  expect(getNewsDetail).not.toHaveBeenCalled();
});
