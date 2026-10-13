import { afterEach, expect, it, vi } from 'vitest';

afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.resetModules();
});

it('개발 환경의 샘플 상세는 API 없이 열리고 샘플임을 표시한다', async () => {
  vi.stubEnv('DEV', true);
  vi.stubEnv('MODE', 'development');
  const fetch = vi.fn();
  vi.stubGlobal('fetch', fetch);
  const { getNewsDetail } = await import('../../src/features/news/newsdetail/api');
  const result = await getNewsDetail('sample-today');
  expect(result.news.title).toContain('[샘플]');
  expect(result.news.content[0]).toContain('실제 오늘의 뉴스');
  expect(new Date(result.news.publishedAt).toDateString()).toBe(new Date().toDateString());
  expect(fetch).not.toHaveBeenCalled();
});

it('프로덕션에서는 샘플을 노출하거나 실제 기사처럼 반환하지 않는다', async () => {
  vi.stubEnv('DEV', false);
  vi.stubEnv('MODE', 'production');
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({ ok: false, status: 404, statusText: 'Not Found' }),
  );
  const { showSampleNews } = await import('../../src/features/news/issue/sampleNews');
  const { getNewsDetail } = await import('../../src/features/news/newsdetail/api');
  expect(showSampleNews).toBe(false);
  await expect(getNewsDetail('sample-today')).rejects.toThrow('404');
});
