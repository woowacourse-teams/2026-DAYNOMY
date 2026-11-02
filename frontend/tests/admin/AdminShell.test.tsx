/** @vitest-environment jsdom */
import { cleanup, fireEvent, render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, expect, it } from 'vitest';
import { AdminShell } from '../../src/features/admin/components/AdminShell';

afterEach(cleanup);

it('뉴스 수정에서도 현재 메뉴를 표시하고 모든 관리자 영역으로 이동한다', () => {
  const view = render(
    <MemoryRouter initialEntries={['/admin/news/7/edit']}>
      <AdminShell>
        <main>관리자 작업</main>
      </AdminShell>
    </MemoryRouter>,
  );
  expect(view.getByRole('link', { name: '뉴스 관리' }).getAttribute('aria-current')).toBe('page');
  for (const [label, path] of [
    ['주요 인물', '/admin/people'],
    ['종목 연결', '/admin/stock-contents'],
    ['소식 출처', '/admin/content-sources'],
    ['소식 검수', '/admin/content-reviews'],
    ['피드백', '/admin/feedbacks'],
    ['자산 데이터', '/admin/stocks'],
  ]) {
    const link = view.getByRole('link', { name: label });
    expect(link.getAttribute('href')).toBe(path);
    fireEvent.click(link);
    expect(link.getAttribute('aria-current')).toBe('page');
  }
  expect(view.queryByRole('link', { name: '이메일 알림 관리' })).toBeNull();
  expect(view.getByRole('link', { name: /서비스 홈/ }).getAttribute('href')).toBe('/');
  expect(view.getByRole('link', { name: '본문으로 이동' }).getAttribute('href')).toBe(
    '#admin-workspace',
  );
});
