import assert from 'node:assert/strict';
import test from 'node:test';
import {
  addAdminIndustryAsset,
  createAdminIndustry,
  getAdminAssetIndustries,
  getAdminIndustryAssets,
  getAdminIndustries,
  removeAdminIndustryAsset,
  updateAdminAssetIndustries,
  updateAdminIndustry,
} from '../../src/features/admin/api.ts';

const semiconductor = {
  id: 1,
  code: 'SEMICONDUCTOR',
  name: '반도체',
  displayOrder: 1,
  active: true,
};
const stock = {
  assetId: 7,
  assetCode: '005930',
  name: '삼성전자',
  category: 'STOCK' as const,
  market: 'KOSPI' as const,
  currency: 'KRW' as const,
};
const stockPage = {
  industryId: 1,
  items: [stock],
  page: 2,
  size: 20,
  totalPages: 3,
  totalElements: 42,
  hasNext: true,
};

function jsonResponse(body: unknown) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'content-type': 'application/json' },
  });
}

function noContentResponse() {
  return new Response(null, { status: 204 });
}

test('관리자 업종과 종목 연결 API를 검증하고 CSRF와 함께 저장한다', async () => {
  const calls: Array<{ url: string; init?: RequestInit }> = [];
  globalThis.fetch = async (input, init) => {
    const url = String(input);
    calls.push({ url, init });
    if (url === '/api/auth/csrf')
      return jsonResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' });
    if (url === '/api/admin/industries' && !init?.method)
      return jsonResponse({ industries: [semiconductor] });
    if (url === '/api/admin/assets/7/industries' && !init?.method)
      return jsonResponse({ assetId: 7, industries: [semiconductor] });
    if (url === '/api/admin/industries/1/assets?page=2&size=20' && !init?.method)
      return jsonResponse(stockPage);
    if (url === '/api/admin/industries/1/assets/7') return noContentResponse();
    if (url === '/api/admin/industries' && init?.method === 'POST')
      return jsonResponse({ ...semiconductor, id: 2, code: 'AI', name: '인공지능' });
    if (url === '/api/admin/industries/1')
      return jsonResponse({ ...semiconductor, name: '반도체·장비', active: false });
    return jsonResponse({ assetId: 7, industries: [semiconductor] });
  };

  assert.deepEqual(await getAdminIndustries(), [semiconductor]);
  assert.equal((await getAdminAssetIndustries(7)).assetId, 7);
  assert.equal((await getAdminIndustryAssets(1, 2)).items[0].assetId, 7);
  assert.equal(
    (await createAdminIndustry({ code: 'AI', name: '인공지능', displayOrder: 2 })).code,
    'AI',
  );
  assert.equal(
    (await updateAdminIndustry(1, { name: '반도체·장비', displayOrder: 1, active: false })).active,
    false,
  );
  await addAdminIndustryAsset(1, 7);
  await removeAdminIndustryAsset(1, 7);
  assert.equal((await updateAdminAssetIndustries(7, [1])).industries[0].id, 1);

  const writes = calls.filter((call) => call.init?.method);
  assert.deepEqual(
    writes.map((call) => [call.url, call.init?.method]),
    [
      ['/api/admin/industries', 'POST'],
      ['/api/admin/industries/1', 'PATCH'],
      ['/api/admin/industries/1/assets/7', 'PUT'],
      ['/api/admin/industries/1/assets/7', 'DELETE'],
      ['/api/admin/assets/7/industries', 'PUT'],
    ],
  );
  for (const call of writes) {
    assert.equal(new Headers(call.init?.headers).get('X-CSRF-TOKEN'), 'csrf-token');
  }
});

test('잘못된 관리자 업종 종목 페이지 응답을 거부한다', async () => {
  globalThis.fetch = async () => jsonResponse({ ...stockPage, page: 0 });
  await assert.rejects(getAdminIndustryAssets(1), /응답 형식/);
});

test('잘못된 관리자 업종 응답을 거부한다', async () => {
  globalThis.fetch = async () => jsonResponse({ industries: [{ ...semiconductor, active: null }] });
  await assert.rejects(getAdminIndustries(), /응답 형식/);
});
