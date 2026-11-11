import assert from 'node:assert/strict';
import test from 'node:test';
import axios from 'axios';
import { buildNewsFeedUrl, getNewsFeed } from '../../src/features/news/issue/feedApi';

test('소식 피드 URL에 출처와 페이지·정렬 조건을 반영한다', () => {
  assert.equal(buildNewsFeedUrl('ALL'), '/api/news/feed?page=1&size=8');
  assert.equal(
    buildNewsFeedUrl('DAYNOMY', 2, 4, 'POPULAR'),
    '/api/news/feed?page=2&size=4&source=DAYNOMY&sort=POPULAR',
  );
});

test('소식 피드 응답을 화면 모델로 변환한다', async () => {
  const originalAdapter = axios.defaults.adapter;
  axios.defaults.adapter = async (config) => {
    assert.equal(config.url, '/api/news/feed?page=1&size=8');
    return {
      data: {
        items: [
          {
            kind: 'NEWS',
            id: 7,
            source: 'DAYNOMY',
            title: '최신 소식',
            date: '2026-11-08T00:00:00Z',
            category: 'STOCK',
            imageUrl: null,
          },
          {
            kind: 'PERSON_POST',
            id: 8,
            source: 'YOUTUBE',
            title: '인물 영상',
            date: '2026-11-07T00:00:00Z',
            url: 'https://youtu.be/abcdefghijk',
            personId: 3,
            personName: '테스트 인물',
          },
        ],
        page: 1,
        size: 8,
        totalElements: 2,
        totalPages: 1,
      },
      status: 200,
      statusText: 'OK',
      headers: {},
      config,
    };
  };

  try {
    const result = await getNewsFeed('ALL');
    assert.equal(result.items[0].key, 'news-7');
    assert.equal(result.items[0].title, '최신 소식');
    assert.equal(result.items[1].key, 'person-3-8');
    assert.equal(result.items[1].source, 'YOUTUBE');
    assert.equal(result.totalElements, 2);
  } finally {
    axios.defaults.adapter = originalAdapter;
  }
});
