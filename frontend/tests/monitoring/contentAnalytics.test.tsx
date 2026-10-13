/** @vitest-environment jsdom */
import { StrictMode, useRef } from 'react';
import { act, cleanup, fireEvent, render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { trackEvent } from '../../src/analytics';
import { useContentImpression } from '../../src/features/news/issue/analytics';
import { IssueFeed } from '../../src/features/news/issue/IssueFeed';
import { IssuePagination } from '../../src/features/news/issue/IssuePagination';

vi.mock('../../src/analytics', () => ({ trackEvent: vi.fn() }));
let intersect: IntersectionObserverCallback;
beforeEach(() => {
  vi.stubGlobal(
    'IntersectionObserver',
    class {
      constructor(callback: IntersectionObserverCallback) {
        intersect = callback;
      }
      observe() {}
      disconnect() {}
    },
  );
});
afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  vi.unstubAllGlobals();
});

function Card({ id }: { id: string }) {
  const ref = useRef<HTMLDivElement>(null);
  useContentImpression(ref, id, 'view_content', { content_id: id });
  return <div ref={ref}>{id}</div>;
}
function visibility(ratio: number) {
  act(() =>
    intersect(
      [{ isIntersecting: ratio > 0, intersectionRatio: ratio } as IntersectionObserverEntry],
      {} as IntersectionObserver,
    ),
  );
}
it('실제 노출 전에는 보내지 않고 StrictMode·재렌더·순환 재노출은 중복 집계하지 않는다', () => {
  const view = render(
    <StrictMode>
      <Card id="a" />
    </StrictMode>,
  );
  visibility(0.1);
  expect(trackEvent).not.toHaveBeenCalled();
  visibility(0.6);
  visibility(1);
  view.rerender(
    <StrictMode>
      <Card id="b" />
    </StrictMode>,
  );
  visibility(1);
  view.rerender(
    <StrictMode>
      <Card id="a" />
    </StrictMode>,
  );
  expect(trackEvent).toHaveBeenCalledTimes(2);
});
it('피드 펼치기와 원문 클릭은 종류·위치만 보내고 제목·URL은 보내지 않는다', () => {
  const view = render(
    <MemoryRouter>
      <IssueFeed
        surface="news_contents"
        offset={12}
        items={[
          {
            key: 'content-9',
            source: 'YOUTUBE',
            title: 'private-title',
            date: null,
            content: {
              id: 9,
              assetId: 2,
              title: 'private-title',
              sourceType: 'YOUTUBE',
              url: 'https://youtu.be/abcdefghijk?private-query',
              imageUrl: null,
              createdAt: null,
            },
          },
        ]}
      />
    </MemoryRouter>,
  );
  fireEvent.click(view.getByRole('button', { name: /private-title/ }));
  expect(trackEvent).toHaveBeenCalledWith('expand_content', {
    surface: 'news_contents',
    content_id: 'content-9',
    content_type: 'external',
    source_type: 'YOUTUBE',
    position: 13,
  });
  fireEvent.click(view.getByRole('link', { name: /원문 보기/ }));
  fireEvent.click(view.getByRole('button', { name: /글 접기/ }));
  expect(trackEvent).toHaveBeenCalledTimes(2);
  expect(JSON.stringify(vi.mocked(trackEvent).mock.calls)).not.toContain('private-');
});
it('현재 페이지 재클릭은 집계하지 않고 실제 페이지 변경만 기록한다', () => {
  const onChange = vi.fn();
  const view = render(
    <IssuePagination
      page={1}
      totalPages={3}
      onChange={onChange}
      analytics={{ surface: 'people', person_id: 1 }}
    />,
  );
  fireEvent.click(view.getByRole('button', { name: '1페이지' }));
  expect(trackEvent).not.toHaveBeenCalled();
  fireEvent.click(view.getByRole('button', { name: '다음 페이지' }));
  expect(trackEvent).toHaveBeenCalledWith('change_content_page', {
    surface: 'people',
    person_id: 1,
    previous_page: 1,
    page_number: 2,
  });
  expect(onChange).toHaveBeenCalledWith(2);
});
