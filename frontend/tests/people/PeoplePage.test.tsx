/** @vitest-environment jsdom */

import { cleanup, fireEvent, render } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PeoplePage } from '../../src/features/people/PeoplePage';

function jsonResponse(body: unknown) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'content-type': 'application/json' },
  });
}

beforeEach(() => {
  vi.stubGlobal(
    'fetch',
    vi.fn(async () =>
      jsonResponse({
        people: [
          {
            id: 1,
            name: '일론 머스크',
            xProfileUrl: 'https://x.com/elonmusk',
            threadsProfileUrl: null,
            createdAt: null,
            posts: [
              {
                id: 11,
                sourceType: 'X',
                title: '일론 머스크 X 게시물',
                url: 'https://x.com/elonmusk/status/12345',
                createdAt: '2026-10-11T09:00:00Z',
              },
            ],
          },
          {
            id: 2,
            name: '도널드 트럼프',
            xProfileUrl: null,
            threadsProfileUrl: 'https://www.threads.net/@realdonaldtrump',
            createdAt: null,
            posts: [
              {
                id: 12,
                sourceType: 'THREADS',
                title: '도널드 트럼프 Threads 게시물',
                url: 'https://www.threads.net/@realdonaldtrump/post/ABC',
                createdAt: '2026-10-12T09:00:00Z',
              },
            ],
          },
        ],
      }),
    ),
  );
});

afterEach(() => {
  cleanup();
  document.getElementById('daynomy-x-embed-script')?.remove();
  document.getElementById('daynomy-threads-embed-script')?.remove();
  vi.unstubAllGlobals();
});

describe('주요 인물 화면', () => {
  it('최근 발언과 전체 인물을 표시하고 선택한 인물의 게시물을 보여준다', async () => {
    const view = render(<PeoplePage />);

    expect(await view.findByRole('heading', { name: '요즘 뜨는 말' })).toBeTruthy();
    expect(view.getByText('도널드 트럼프 Threads 게시물', { selector: 'blockquote' })).toBeTruthy();
    expect(view.getByRole('heading', { name: '전체 인물' })).toBeTruthy();
    expect(view.getByRole('button', { name: /일론 머스크/ })).toBeTruthy();
    expect(view.getByRole('button', { name: /도널드 트럼프/ })).toBeTruthy();

    expect(await view.findByRole('heading', { name: '일론 머스크' })).toBeTruthy();
    expect(view.getByRole('link', { name: 'X 프로필' }).getAttribute('href')).toBe(
      'https://x.com/elonmusk',
    );
    expect(view.container.querySelector('blockquote.twitter-tweet')).toBeTruthy();

    fireEvent.click(view.getByRole('button', { name: /도널드 트럼프/ }));

    expect(view.getByRole('heading', { name: '도널드 트럼프' })).toBeTruthy();
    expect(
      view.container
        .querySelector('blockquote.text-post-media')
        ?.getAttribute('data-text-post-permalink'),
    ).toBe('https://www.threads.net/@realdonaldtrump/post/ABC');
  });

  it('이름으로 전체 인물 목록을 검색한다', async () => {
    const view = render(<PeoplePage />);

    await view.findByRole('button', { name: /일론 머스크/ });
    fireEvent.change(view.getByRole('searchbox', { name: '전체 인물 검색' }), {
      target: { value: '트럼프' },
    });

    expect(view.queryByRole('button', { name: /일론 머스크/ })).toBeNull();
    expect(view.getByRole('button', { name: /도널드 트럼프/ })).toBeTruthy();
  });
});
