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
it('오늘 이슈는 최대 네 개를 한 기사씩 3초마다 자동 순환한다', () => {
  const view = show();
  expect(view.getAllByRole('link')).toHaveLength(1);
  expect(view.queryByRole('button')).toBeNull();
  expect(view.queryByLabelText('이슈 날짜')).toBeNull();
  expect(view.getByRole('heading', { name: '기사 1' })).toBeTruthy();
  fireEvent.mouseEnter(view.getByRole('region', { name: '오늘의 이슈' }));
  act(() => vi.advanceTimersByTime(3000));
  expect(view.getByRole('heading', { name: '기사 2' })).toBeTruthy();
});

it('오늘 이슈가 없으면 최근 이슈를 최대 네 개 표시한다', () => {
  const view = render(
    <MemoryRouter>
      <IssueStories
        news={Array.from({ length: 5 }, (_, index) => ({
          id: index + 1,
          title: `최근 기사 ${index + 1}`,
          category: 'STOCK',
          imageUrl: null,
          publishedAt: '2026-10-11T09:00:00Z',
        }))}
        loading={false}
        error=""
        onRetry={() => {}}
      />
    </MemoryRouter>,
  );
  expect(view.getByRole('heading', { name: '최근 이슈' })).toBeTruthy();
  expect(view.getByText('오늘 발행된 이슈가 없어 최근 이슈를 보여드려요.')).toBeTruthy();
  expect(view.getAllByRole('link')).toHaveLength(1);
  expect(view.getByRole('heading', { name: '최근 기사 1' })).toBeTruthy();
});

it('이슈가 없으면 빈 상태만 표시하고 전체 콘텐츠로 가는 링크는 표시하지 않는다', () => {
  const view = render(
    <MemoryRouter>
      <IssueStories news={[]} loading={false} error="" onRetry={() => {}} />
    </MemoryRouter>,
  );
  expect(view.getByText('표시할 데이노미 이슈가 없습니다.')).toBeTruthy();
  expect(view.queryByRole('link')).toBeNull();
});
