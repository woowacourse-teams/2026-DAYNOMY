import assert from 'node:assert/strict';
import test from 'node:test';
import { resolveStockCandidatesCache } from '../../src/features/stocks/hooks/useStockCandidates.ts';
import { mockStockCandidates } from '../../src/features/stocks/mock.ts';

test('종목 목록 응답이 비어 있으면 목데이터를 사용한다', () => {
  const cache = resolveStockCandidatesCache({
    baseDate: '2026-08-28',
    rankings: [],
    page: 1,
    size: 10,
    totalPages: 0,
    totalElements: 0,
    hasNext: false,
  });

  assert.equal(cache.isFallback, true);
  assert.equal(cache.baseDate, mockStockCandidates.baseDate);
  assert.deepEqual(cache.stocks, mockStockCandidates.rankings);
});

test('종목 목록 응답이 있으면 API 응답을 사용한다', () => {
  const rankings = [{ rank: 1, code: '247540', name: '에코프로비엠' }];
  const cache = resolveStockCandidatesCache({
    baseDate: '2026-08-28',
    rankings,
    page: 1,
    size: 10,
    totalPages: 15,
    totalElements: 150,
    hasNext: true,
  });

  assert.equal(cache.isFallback, false);
  assert.equal(cache.baseDate, '2026-08-28');
  assert.deepEqual(cache.stocks, rankings);
  assert.equal(cache.totalPages, 15);
  assert.equal(cache.totalElements, 150);
});
