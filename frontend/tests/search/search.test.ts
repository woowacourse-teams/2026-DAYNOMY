import assert from 'node:assert/strict'
import test from 'node:test'
import { buildNewsSearchUrl } from '../../src/features/search/api.ts'
import { getMockSearchNews } from '../../src/features/search/mock.ts'

test('검색어와 선택한 카테고리로 뉴스 검색 URL을 만든다', () => {
  assert.equal(buildNewsSearchUrl('기준 금리', 'ALL'), '/api/search/news?q=%EA%B8%B0%EC%A4%80+%EA%B8%88%EB%A6%AC')
  assert.equal(
    buildNewsSearchUrl('금', 'GOLD'),
    '/api/search/news?q=%EA%B8%88&category=GOLD',
  )
})

test('임시 검색 결과에도 카테고리 필터를 적용한다', () => {
  const results = getMockSearchNews('금', 'GOLD')

  assert.ok(results.length > 0)
  assert.ok(results.every((article) => article.category === 'GOLD'))
})
