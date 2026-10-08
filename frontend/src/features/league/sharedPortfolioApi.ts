import { request, requestWithCsrf } from '../../api/client';
import { getSavedPortfolio } from '../portfolio/api';

export type SourceHolding = {
  assetId: number;
  assetCode: string;
  assetName: string;
  category: 'STOCK' | 'ETF';
  market: 'KOSPI' | 'KOSDAQ';
  quantity: number;
  averagePurchasePrice: number;
};
export type SharedHolding = SourceHolding & {
  hidden: boolean;
  reason: string;
  baseDate: string | null;
  closePrice: number | null;
  evaluationAmount: number | null;
  returnRate: number | null;
};
export type SharedPortfolio = {
  holdings: SharedHolding[];
  totalPurchaseAmount: number;
  totalEvaluationAmount: number | null;
  totalReturnRate: number | null;
  pricesComplete: boolean;
};
function record(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
function number(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}
function nullableNumber(value: unknown): value is number | null {
  return value === null || number(value);
}
function sourceHolding(value: unknown): value is SourceHolding {
  return (
    record(value) &&
    Number.isSafeInteger(value.assetId) &&
    Number(value.assetId) > 0 &&
    typeof value.assetCode === 'string' &&
    typeof value.assetName === 'string' &&
    (value.category === 'STOCK' || value.category === 'ETF') &&
    (value.market === 'KOSPI' || value.market === 'KOSDAQ') &&
    Number.isSafeInteger(value.quantity) &&
    Number(value.quantity) > 0 &&
    Number(value.quantity) <= 1e9 &&
    number(value.averagePurchasePrice) &&
    value.averagePurchasePrice > 0 &&
    value.averagePurchasePrice <= 1e9
  );
}
function sharedHolding(value: unknown): value is SharedHolding {
  return (
    record(value) &&
    typeof value.hidden === 'boolean' &&
    typeof value.reason === 'string' &&
    (value.baseDate === null ||
      (typeof value.baseDate === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value.baseDate))) &&
    nullableNumber(value.closePrice) &&
    nullableNumber(value.evaluationAmount) &&
    nullableNumber(value.returnRate) &&
    sourceHolding(value)
  );
}
function portfolio(value: unknown): SharedPortfolio {
  if (
    !record(value) ||
    !Array.isArray(value.holdings) ||
    !value.holdings.every(sharedHolding) ||
    !number(value.totalPurchaseAmount) ||
    !nullableNumber(value.totalEvaluationAmount) ||
    !nullableNumber(value.totalReturnRate) ||
    typeof value.pricesComplete !== 'boolean'
  ) {
    throw new Error('공유 포트폴리오 응답을 확인할 수 없습니다. 다시 불러와 주세요.');
  }
  return {
    holdings: value.holdings,
    totalPurchaseAmount: value.totalPurchaseAmount,
    totalEvaluationAmount: value.totalEvaluationAmount,
    totalReturnRate: value.totalReturnRate,
    pricesComplete: value.pricesComplete,
  };
}

/** 현재 계정의 서버 원본을 읽기만 한다. 원본 저장·수정 API는 호출하지 않는다. */
export async function readSourceHoldings(): Promise<SourceHolding[]> {
  const { holdings } = await getSavedPortfolio();
  const values = holdings.map((value) => {
    const candidate = { ...value, assetName: value.name };
    if (!sourceHolding(candidate))
      throw new Error('원본 자산의 수량과 평균 매수가를 확인해 주세요.');
    return candidate;
  });
  if (new Set(values.map((h) => h.assetId)).size !== values.length)
    throw new Error('원본에 중복 종목이 있습니다.');
  return values;
}
const path = '/api/users/me/shared-portfolio';
export async function getSharedPortfolio(signal?: AbortSignal) {
  return portfolio(await request<unknown>(path, { signal }));
}
export async function importSharedHoldings(holdings: SourceHolding[], overwriteExisting: boolean) {
  return portfolio(
    await requestWithCsrf<unknown>(`${path}/import`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        holdings: holdings.map(({ assetId, quantity, averagePurchasePrice }) => ({
          assetId,
          quantity,
          averagePurchasePrice,
        })),
        overwriteExisting,
      }),
    }),
  );
}
export async function changeSharedHoldingVisibility(assetId: number, hidden: boolean) {
  return portfolio(
    await requestWithCsrf<unknown>(`${path}/holdings/${assetId}/visibility`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ hidden }),
    }),
  );
}
