/** @vitest-environment jsdom */
import { cleanup, fireEvent, render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import * as analytics from '../../src/analytics';
import { IssueFeed } from '../../src/features/news/issue/IssueFeed';
import * as feed from '../../src/features/news/issue/feed';
import * as popularity from '../../src/features/news/issue/popularity';

function items(count: number) {
  return Array.from(
    { length: count },
    (_, index) =>
      ({
        key: `content-${index + 1}`,
        source: 'YOUTUBE',
        title: `영상 ${index + 1}`,
        date: '2026-10-21T00:00:00Z',
        content: {
          id: index + 1,
          assetId: 1,
          sourceType: 'YOUTUBE',
          title: `영상 ${index + 1}`,
          url: 'https://youtu.be/abcdefghijk',
          imageUrl: null,
          createdAt: '2026-10-21T00:00:00Z',
        },
      }) satisfies feed.FeedItem,
  );
}

beforeEach(() => {
  vi.spyOn(analytics, 'trackEvent').mockImplementation(() => {});
  vi.spyOn(popularity, 'recordContentInteraction').mockResolvedValue(undefined);
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

it.each([12, 100])('%i개 목록에서 선택이 바뀐 행만 날짜를 다시 계산한다', (count) => {
  const dates = vi.spyOn(feed, 'feedDate');
  const view = render(
    <MemoryRouter>
      <IssueFeed items={items(count)} surface="search_results" offset={12} />
    </MemoryRouter>,
  );
  expect(dates).toHaveBeenCalledTimes(count);
  const first = view.getByRole('button', { name: /^영상 1\s*영상 보기$/ });
  const second = view.getByRole('button', { name: /^영상 2\s*영상 보기$/ });

  dates.mockClear();
  fireEvent.click(first);
  expect(dates).toHaveBeenCalledTimes(1);
  expect(first.getAttribute('aria-expanded')).toBe('true');
  expect(view.getByTitle('영상 1')).toBeTruthy();
  expect(analytics.trackEvent).toHaveBeenLastCalledWith('expand_content', {
    surface: 'search_results',
    content_id: 'content-1',
    content_type: 'external',
    source_type: 'YOUTUBE',
    position: 13,
  });

  dates.mockClear();
  fireEvent.click(second);
  expect(dates).toHaveBeenCalledTimes(2);
  expect(first.getAttribute('aria-expanded')).toBe('false');
  expect(second.getAttribute('aria-expanded')).toBe('true');
  expect(view.queryByTitle('영상 1')).toBeNull();
  expect(view.container.querySelectorAll('iframe')).toHaveLength(1);
  expect(view.getByRole('region').id).toBe(second.getAttribute('aria-controls'));
  expect(view.getByRole('region').getAttribute('aria-labelledby')).toBe(second.id);

  dates.mockClear();
  fireEvent.click(view.getByRole('button', { name: '글 접기 ↑' }));
  expect(dates).toHaveBeenCalledTimes(1);
  expect(view.container.querySelector('iframe')).toBeNull();
  expect(document.activeElement).toBe(second);
});

it('같은 키의 콘텐츠가 바뀌면 펼친 내용과 새 위치의 이벤트를 갱신한다', () => {
  const initial = items(2);
  const view = render(
    <MemoryRouter>
      <IssueFeed items={initial} surface="search_results" />
    </MemoryRouter>,
  );
  fireEvent.click(view.getByRole('button', { name: /^영상 1\s*영상 보기$/ }));
  const updated = {
    ...initial[0],
    title: '수정한 영상',
    content: { ...initial[0].content, url: 'https://youtu.be/5qRYLp9z05M' },
  };
  view.rerender(
    <MemoryRouter>
      <IssueFeed items={[initial[1], updated]} surface="search_results" offset={12} />
    </MemoryRouter>,
  );
  expect(view.getByTitle('수정한 영상').getAttribute('src')).toBe(
    'https://www.youtube-nocookie.com/embed/5qRYLp9z05M',
  );
  fireEvent.click(view.getByRole('button', { name: '글 접기 ↑' }));
  fireEvent.click(view.getByRole('button', { name: /^수정한 영상\s*영상 보기$/ }));
  expect(analytics.trackEvent).toHaveBeenLastCalledWith(
    'expand_content',
    expect.objectContaining({ position: 14 }),
  );
});
