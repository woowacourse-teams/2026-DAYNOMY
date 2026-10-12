/** @vitest-environment jsdom */
import { act, cleanup, fireEvent, render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { IssueStories } from '../../src/features/news/issue/IssueStories';

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date('2026-10-12T10:00:00Z'));
  vi.stubGlobal('matchMedia', () => ({
    matches: false,
    addEventListener() {},
    removeEventListener() {},
  }));
});
afterEach(() => {
  cleanup();
  vi.useRealTimers();
  vi.unstubAllGlobals();
});
function show() {
  return render(
    <MemoryRouter>
      <IssueStories
        news={[1, 2].map((id) => ({
          id,
          title: `기사 ${id}`,
          category: 'STOCK',
          imageUrl: null,
          publishedAt: '2026-10-12T09:00:00Z',
        }))}
        loading={false}
        error=""
        onRetry={() => {}}
      />
    </MemoryRouter>,
  );
}
it('버튼 없이 한 기사씩 3초마다 자동 순환한다', () => {
  const view = show();
  expect(view.getAllByRole('link')).toHaveLength(1);
  expect(view.queryByRole('button')).toBeNull();
  expect(view.queryByLabelText('이슈 날짜')).toBeNull();
  expect(view.getByRole('heading', { name: '기사 1' })).toBeTruthy();
  fireEvent.mouseEnter(view.getByRole('region', { name: '오늘의 이슈' }));
  act(() => vi.advanceTimersByTime(3000));
  expect(view.getByRole('heading', { name: '기사 2' })).toBeTruthy();
  act(() => vi.advanceTimersByTime(3000));
  expect(view.getByRole('heading', { name: '기사 1' })).toBeTruthy();
});
it('키보드 포커스 중 링크를 유지하고 떠나면 자동으로 다시 순환한다', () => {
  const view = show();
  fireEvent.focus(view.getByRole('link'));
  act(() => vi.advanceTimersByTime(3000));
  expect(view.getByRole('heading', { name: '기사 1' })).toBeTruthy();
  fireEvent.blur(view.getByRole('link'));
  act(() => vi.advanceTimersByTime(3000));
  expect(view.getByRole('heading', { name: '기사 2' })).toBeTruthy();
});
