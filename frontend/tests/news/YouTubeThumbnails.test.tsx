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
