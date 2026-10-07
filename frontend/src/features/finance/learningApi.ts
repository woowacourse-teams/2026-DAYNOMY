import { request, requestWithCsrf } from '../../api/client';
import type {
  WeeklyCheckIn,
  LearningItemType,
  SimulatedTrade,
  SimulatedTradeInput,
  LearningProgress,
  LearningStock,
  LearningStockPrice,
  SimulatedTradeType,
} from './learningTypes';

const PROGRESS_STORAGE_KEY = 'daynomy:learning-progress:v1';
const CHECK_INS_STORAGE_KEY = 'daynomy:weekly-check-ins:v1';
const MOCK_TRADES_STORAGE_KEY = 'daynomy:simulated-trades:v1';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isFiniteNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}

function isDate(value: unknown): value is string {
  return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value);
}

function isItemType(value: unknown): value is LearningItemType {
  return value === 'GUIDE' || value === 'MISSION';
}

function isTradeType(value: unknown): value is SimulatedTradeType {
  return value === 'BUY' || value === 'SELL';
}

function isProgress(value: unknown): value is LearningProgress {
  return (
    isRecord(value) &&
    typeof value.itemKey === 'string' &&
    isItemType(value.itemType) &&
    typeof value.completed === 'boolean' &&
    typeof value.bookmarked === 'boolean'
  );
}

function isCheckIn(value: unknown): value is WeeklyCheckIn {
  return (
    isRecord(value) &&
    isDate(value.weekStart) &&
    isFiniteNumber(value.targetSavings) &&
    isFiniteNumber(value.targetInvestment) &&
    isFiniteNumber(value.targetDebtPayment) &&
    isFiniteNumber(value.actualSavings) &&
    isFiniteNumber(value.actualInvestment) &&
    isFiniteNumber(value.actualDebtPayment) &&
    (value.note === null || typeof value.note === 'string')
  );
}

function isStock(value: unknown): value is LearningStock {
  return (
    isRecord(value) &&
    isFiniteNumber(value.assetId) &&
    typeof value.assetCode === 'string' &&
    typeof value.assetName === 'string' &&
    (value.category === 'STOCK' || value.category === 'ETF') &&
    (value.market === 'KOSPI' || value.market === 'KOSDAQ')
  );
}

function isSearchStock(value: unknown): value is {
  assetId: number;
  assetCode: string;
  name: string;
  category: 'STOCK' | 'ETF';
  market: 'KOSPI' | 'KOSDAQ';
} {
  return (
    isRecord(value) &&
    isFiniteNumber(value.assetId) &&
    typeof value.assetCode === 'string' &&
    typeof value.name === 'string' &&
    (value.category === 'STOCK' || value.category === 'ETF') &&
    (value.market === 'KOSPI' || value.market === 'KOSDAQ')
  );
}

function isStockPrice(value: unknown): value is LearningStockPrice {
  return (
    isRecord(value) &&
    isFiniteNumber(value.assetId) &&
    typeof value.assetCode === 'string' &&
    typeof value.name === 'string' &&
    isDate(value.baseDate) &&
    isFiniteNumber(value.closePrice)
  );
}

function isMockTrade(value: unknown): value is SimulatedTrade {
  if (!isRecord(value)) return false;
  const record: Record<string, unknown> = value;
  return (
    isStock(value) &&
    isFiniteNumber(record.id) &&
    isTradeType(record.tradeType) &&
    isFiniteNumber(record.quantity) &&
    isFiniteNumber(record.price) &&
    (record.reason === null || typeof record.reason === 'string') &&
    isDate(record.tradedOn)
  );
}

function readArray<T>(key: string, guard: (value: unknown) => value is T): T[] {
  try {
    const value: unknown = JSON.parse(localStorage.getItem(key) ?? '[]');
    return Array.isArray(value) ? value.filter(guard) : [];
  } catch {
    return [];
  }
}

function writeArray<T>(key: string, values: T[]) {
  localStorage.setItem(key, JSON.stringify(values));
}

function progressResponse(value: unknown): LearningProgress[] {
  if (!isRecord(value) || !Array.isArray(value.progress) || !value.progress.every(isProgress)) {
    throw new Error('금융 학습 진행도 응답 형식이 올바르지 않습니다.');
  }
  return value.progress;
}

function checkInResponse(value: unknown): WeeklyCheckIn[] {
  if (!isRecord(value) || !Array.isArray(value.checkIns) || !value.checkIns.every(isCheckIn)) {
    throw new Error('주간 체크인 응답 형식이 올바르지 않습니다.');
  }
  return value.checkIns;
}

function mockTradeResponse(value: unknown): SimulatedTrade[] {
  if (!isRecord(value) || !Array.isArray(value.trades) || !value.trades.every(isMockTrade)) {
    throw new Error('모의거래 응답 형식이 올바르지 않습니다.');
  }
  return value.trades;
}

export async function loadLearningProgress(
  isLoggedIn: boolean,
  signal?: AbortSignal,
): Promise<LearningProgress[]> {
  if (!isLoggedIn) return readArray(PROGRESS_STORAGE_KEY, isProgress);
  return progressResponse(await request<unknown>('/api/users/me/learning/progress', { signal }));
}

export async function saveLearningProgress(isLoggedIn: boolean, progress: LearningProgress) {
  if (isLoggedIn) {
    const response = await requestWithCsrf<unknown>(
      `/api/users/me/learning/progress/${encodeURIComponent(progress.itemKey)}`,
      {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          itemType: progress.itemType,
          completed: progress.completed,
          bookmarked: progress.bookmarked,
        }),
      },
    );
    if (!isProgress(response)) throw new Error('금융 학습 진행도 응답 형식이 올바르지 않습니다.');
    return response;
  }

  const current = readArray(PROGRESS_STORAGE_KEY, isProgress);
  const next = [progress, ...current.filter((item) => item.itemKey !== progress.itemKey)];
  writeArray(PROGRESS_STORAGE_KEY, next);
  return progress;
}

export async function loadWeeklyCheckIns(isLoggedIn: boolean): Promise<WeeklyCheckIn[]> {
  if (!isLoggedIn) {
    return readArray(CHECK_INS_STORAGE_KEY, isCheckIn).sort((a, b) =>
      b.weekStart.localeCompare(a.weekStart),
    );
  }
  return checkInResponse(await request<unknown>('/api/users/me/learning/check-ins?limit=12'));
}

export async function saveWeeklyCheckIn(isLoggedIn: boolean, checkIn: WeeklyCheckIn) {
  if (isLoggedIn) {
    const response = await requestWithCsrf<unknown>(
      `/api/users/me/learning/check-ins/${checkIn.weekStart}`,
      {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(checkIn),
      },
    );
    if (!isCheckIn(response)) throw new Error('주간 체크인 응답 형식이 올바르지 않습니다.');
    return response;
  }

  const current = readArray(CHECK_INS_STORAGE_KEY, isCheckIn);
  const next = [checkIn, ...current.filter((item) => item.weekStart !== checkIn.weekStart)].sort(
    (a, b) => b.weekStart.localeCompare(a.weekStart),
  );
  writeArray(CHECK_INS_STORAGE_KEY, next);
  return checkIn;
}

export async function loadSimulatedTrades(isLoggedIn: boolean): Promise<SimulatedTrade[]> {
  if (!isLoggedIn) return readArray(MOCK_TRADES_STORAGE_KEY, isMockTrade);
  return mockTradeResponse(await request<unknown>('/api/users/me/learning/mock-trades'));
}

export async function saveSimulatedTrade(isLoggedIn: boolean, trade: SimulatedTradeInput) {
  if (isLoggedIn) {
    const response = await requestWithCsrf<unknown>('/api/users/me/learning/mock-trades', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        assetId: trade.assetId,
        tradeType: trade.tradeType,
        quantity: trade.quantity,
        price: trade.price,
        reason: trade.reason,
        tradedOn: trade.tradedOn,
      }),
    });
    if (!isMockTrade(response)) throw new Error('모의거래 응답 형식이 올바르지 않습니다.');
    return response;
  }

  const saved = { ...trade, id: -Date.now() };
  writeArray(MOCK_TRADES_STORAGE_KEY, [saved, ...readArray(MOCK_TRADES_STORAGE_KEY, isMockTrade)]);
  return saved;
}

export async function removeSimulatedTrade(isLoggedIn: boolean, id: number) {
  if (isLoggedIn) {
    await requestWithCsrf<void>(`/api/users/me/learning/mock-trades/${id}`, { method: 'DELETE' });
    return;
  }
  writeArray(
    MOCK_TRADES_STORAGE_KEY,
    readArray(MOCK_TRADES_STORAGE_KEY, isMockTrade).filter((trade) => trade.id !== id),
  );
}

export async function searchLearningStocks(
  keyword: string,
  signal?: AbortSignal,
): Promise<LearningStock[]> {
  const query = new URLSearchParams({ q: keyword });
  const response = await request<unknown>(`/api/stocks?${query.toString()}`, { signal });
  if (
    !isRecord(response) ||
    !Array.isArray(response.stocks) ||
    !response.stocks.every(isSearchStock)
  ) {
    throw new Error('종목 검색 응답 형식이 올바르지 않습니다.');
  }
  return response.stocks.map((stock) => ({ ...stock, assetName: stock.name }));
}

export async function loadLearningStockPrice(assetId: number, signal?: AbortSignal) {
  const response = await request<unknown>(`/api/stocks/${assetId}/price`, { signal });
  if (!isStockPrice(response)) throw new Error('최근 종가 응답 형식이 올바르지 않습니다.');
  return response;
}
