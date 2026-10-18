/** @vitest-environment jsdom */
import { cleanup, fireEvent, render } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { IssuePagination } from '../../src/features/news/issue/IssuePagination';

vi.mock('../../src/analytics', () => ({ trackEvent: vi.fn() }));
afterEach(cleanup);

it.each([
  [2, '다음', 6],
  [6, '다음', 7],
  [8, '이전', 7],
  [7, '이전', 6],
  [13, '다음', 14],
])('%i페이지에서 %s 화살표는 %i페이지로 이동한다', (page, direction, target) => {
  const onChange = vi.fn();
  const view = render(<IssuePagination page={page} totalPages={14} onChange={onChange} />);
  fireEvent.click(view.getByRole('button', { name: `${direction} 방향 ${target}페이지로 이동` }));
  expect(onChange).toHaveBeenCalledExactlyOnceWith(target);
  expect(view.getAllByRole('button').length).toBeLessThanOrEqual(8);
});

it.each([
  [1, '이전'],
  [14, '다음'],
])('전체 경계인 %i페이지의 %s 화살표는 비활성화한다', (page, direction) => {
  const onChange = vi.fn();
  const view = render(<IssuePagination page={page} totalPages={14} onChange={onChange} />);
  const button = view.getByRole('button', { name: `${direction} 방향 ${page}페이지로 이동` });
  expect(button.hasAttribute('disabled')).toBe(true);
  fireEvent.click(button);
  expect(onChange).not.toHaveBeenCalled();
});
