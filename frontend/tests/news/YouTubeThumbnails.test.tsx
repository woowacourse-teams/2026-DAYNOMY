/** @vitest-environment jsdom */
import { cleanup, fireEvent, render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, expect, it } from 'vitest';
import { IssueFeed } from '../../src/features/news/issue/IssueFeed';
import { createFeed, youtubeThumbnail } from '../../src/features/news/issue/feed';

afterEach(cleanup);

it.each([
  'https://www.youtube.com/watch?v=5qRYLp9z05M',
  'https://youtu.be/5qRYLp9z05M?t=30',
  'https://m.youtube.com/watch?v=5qRYLp9z05M',
  'https://youtube.com/shorts/5qRYLp9z05M',
  'https://youtube.com/live/5qRYLp9z05M',
  'https://youtube.com/embed/5qRYLp9z05M',
])('영상 URL에서 썸네일을 만든다: %s', (url) => {
  expect(youtubeThumbnail(url)).toBe('https://i.ytimg.com/vi/5qRYLp9z05M/hqdefault.jpg');
});

it.each(['invalid', 'https://example.com/watch?v=5qRYLp9z05M', 'https://youtube.com/watch?v=abc'])(
  '잘못된 영상 주소는 썸네일을 만들지 않는다: %s',
  (url) => {
    expect(youtubeThumbnail(url)).toBeNull();
  },
);

it.each([null, 'https://example.com/stale.jpg'])(
  '저장된 이미지 %s 대신 영상 썸네일을 표시한다',
  (imageUrl) => {
    const items = createFeed(
      [],
      [],
      [
        {
          id: 1,
          assetId: 1,
          sourceType: 'YOUTUBE',
          title: '영상',
          url: 'https://youtu.be/5qRYLp9z05M',
          imageUrl,
          createdAt: null,
        },
      ],
    );
    const view = render(
      <MemoryRouter>
        <IssueFeed items={items} />
      </MemoryRouter>,
    );
    const image = view.container.querySelector<HTMLImageElement>('.issue-feed-thumbnail > img')!;
    expect(image.getAttribute('src')).toBe('https://i.ytimg.com/vi/5qRYLp9z05M/hqdefault.jpg');
    fireEvent.error(image);
    expect(image.hidden).toBe(true);
    expect(view.container.querySelector('.issue-feed-thumbnail-fallback')).toBeTruthy();
  },
);

it('전체 콘텐츠에서 원문 게시일을 우선 표시하고 없으면 등록일을 표시한다', () => {
  const items = createFeed(
    [],
    [],
    [
      {
        id: 1,
        assetId: 1,
        sourceType: 'X',
        title: '게시일이 있는 글',
        url: 'https://x.com/example/status/1',
        imageUrl: null,
        publishedDate: '2026-10-20',
        createdAt: '2026-10-21T09:00:00Z',
      },
      {
        id: 2,
        assetId: 1,
        sourceType: 'X',
        title: '게시일이 없는 글',
        url: 'https://x.com/example/status/2',
        imageUrl: null,
        createdAt: '2026-10-19T09:00:00Z',
      },
    ],
  );
  const view = render(
    <MemoryRouter>
      <IssueFeed items={items} surface="news_contents" />
    </MemoryRouter>,
  );

  expect(view.getByText('10.20 게시')).toBeTruthy();
  expect(view.getByText('10.19 등록')).toBeTruthy();
});
