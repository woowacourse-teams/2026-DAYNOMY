import assert from 'node:assert/strict';
import test from 'node:test';
import {
  GUIDE_PROGRESS_STORAGE_KEY,
  isGuideComplete,
  loadGuideProgress,
  saveGuideSteps,
} from '../../src/features/guides/guideProgress';

function createStorage() {
  const values = new Map<string, string>();
  return {
    getItem(key: string) {
      return values.get(key) ?? null;
    },
    setItem(key: string, value: string) {
      values.set(key, value);
    },
    removeItem(key: string) {
      values.delete(key);
    },
    clear() {
      values.clear();
    },
    key(index: number) {
      return [...values.keys()][index] ?? null;
    },
    get length() {
      return values.size;
    },
  } satisfies Storage;
}

test.beforeEach(() => {
  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    value: createStorage(),
  });
});

test('가이드별 완료 단계를 중복 없이 저장한다', () => {
  saveGuideSteps('account-opening', ['prepare', 'prepare', 'verify']);

  assert.deepEqual(loadGuideProgress()['account-opening'], ['prepare', 'verify']);
  assert.equal(isGuideComplete('account-opening', 2), true);
  assert.match(localStorage.getItem(GUIDE_PROGRESS_STORAGE_KEY) ?? '', /account-opening/);
});

test('손상된 저장값은 빈 진행 상태로 처리한다', () => {
  localStorage.setItem(GUIDE_PROGRESS_STORAGE_KEY, '{not-json');

  assert.deepEqual(loadGuideProgress(), {});
});
