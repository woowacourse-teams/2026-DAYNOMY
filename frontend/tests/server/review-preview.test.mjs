import assert from 'node:assert/strict';
import test from 'node:test';
import { createReviewPreview } from '../../scripts/review-preview-data.mjs';
const url = (path) => new URL(path, 'http://localhost');
test('실제 페이지의 목데이터 복기를 검증·저장·수정하고 초기화한다', () => {
  const preview = createReviewPreview();
  const get = () =>
    preview.request('GET', url('/api/users/me/portfolio/transactions')).data.transactions[0];
  assert.equal(get().reviews.length, 0);
  assert.equal(
    preview.request('POST', url('/api/users/me/portfolio/decisions/7/reviews'), {}).status,
    400,
  );
  const draft = { actualResult: '결과', differenceFromExpectation: '차이', nextAction: '기준' };
  const saved = preview.request('POST', url('/api/users/me/portfolio/decisions/7/reviews'), draft);
  assert.equal(saved.status, 200);
  assert.equal(get().reviews[0].actualResult, draft.actualResult);
  const id = saved.data.id;
  assert.equal(
    preview.request('PUT', url(`/api/users/me/portfolio/decisions/7/reviews/${id}`), {
      ...draft,
      nextAction: '수정',
    }).status,
    200,
  );
  assert.equal(get().reviews[0].nextAction, '수정');
  assert.equal(
    preview.request('POST', url('/api/users/me/portfolio/decisions/7/review-guide')).status,
    409,
  );
  assert.equal(preview.request('POST', url('/api/users/me/unknown'), draft).status, 404);
  preview.request('POST', url('/api/__review-preview/reset'));
  assert.equal(get().reviews.length, 0);
});

test('종목 추가 예시는 원본을 유지하며 보유 이유와 덮어쓰기 확인 후에만 리그에 저장한다', () => {
  const preview = createReviewPreview();
  const shared = () => preview.request('GET', url('/api/users/me/shared-portfolio')).data;
  const source = () => preview.request('GET', url('/api/users/me/portfolio')).data;
  const originalSource = structuredClone(source());
  const value = { assetId: 2, quantity: 5, averagePurchasePrice: 110000 };
  assert.equal(
    preview.request('POST', url('/api/users/me/shared-portfolio/import'), { holdings: [value] })
      .status,
    400,
  );
  assert.equal(shared().holdings.length, 1);
  const withReason = {
    ...value,
    decision: {
      reason: '업황 개선 기대',
      expectedHoldingPeriod: null,
      expectedChange: '',
      invalidationCondition: '',
      maximumAcceptableLossRate: null,
    },
  };
  assert.equal(
    preview.request('POST', url('/api/users/me/shared-portfolio/import'), {
      holdings: [withReason],
      overwriteExisting: false,
    }).status,
    200,
  );
  assert.equal(shared().holdings.length, 2);
  assert.equal(shared().holdings.find((h) => h.assetId === 2).reason, '업황 개선 기대');
  assert.equal(
    preview.request('POST', url('/api/users/me/shared-portfolio/import'), {
      holdings: [value],
      overwriteExisting: false,
    }).status,
    409,
  );
  assert.equal(
    preview.request('POST', url('/api/users/me/shared-portfolio/import'), {
      holdings: [{ ...value, quantity: 7 }],
      overwriteExisting: true,
    }).status,
    200,
  );
  assert.deepEqual(source(), originalSource);
  assert.equal(shared().holdings.find((h) => h.assetId === 2).quantity, 7);
  preview.request('POST', url('/api/__review-preview/reset'));
  assert.equal(shared().holdings.length, 1);
});
