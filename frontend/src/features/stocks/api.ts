import type { StockCandidate, StockCandidatesResponse } from './types';

type AssetCandidateResponse = {
  rank: number;
  code: string;
  name: string;
};

type AssetCandidatesResponse = {
  baseDate: string | null;
  rankings: AssetCandidateResponse[];
  page: number;
  size: number;
  totalPages: number;
  totalElements: number;
  hasNext: boolean;
};

function normalizeStockCandidate(item: AssetCandidateResponse): StockCandidate {
  return {
    rank: item.rank,
    code: item.code,
    name: item.name,
  };
}

function assertStockCandidatesResponse(data: AssetCandidatesResponse) {
  if (
    !Array.isArray(data.rankings) ||
    typeof data.page !== 'number' ||
    typeof data.size !== 'number' ||
    typeof data.totalPages !== 'number' ||
    typeof data.totalElements !== 'number' ||
    typeof data.hasNext !== 'boolean'
  ) {
    throw new Error('종목 목록 API 응답 형식이 올바르지 않습니다.');
  }
}

async function requestKosdaqTopStocks(url: string): Promise<StockCandidatesResponse> {
  const response = await fetch(url);

  if (!response.ok) {
    throw new Error('종목 목록을 불러오지 못했습니다.');
  }

  const contentType = response.headers.get('content-type') ?? '';

  if (!contentType.includes('application/json')) {
    throw new Error('종목 목록 API 응답 형식이 올바르지 않습니다.');
  }

  const data = (await response.json()) as AssetCandidatesResponse;
  assertStockCandidatesResponse(data);

  return {
    baseDate: data.baseDate,
    rankings: data.rankings.map(normalizeStockCandidate),
    page: data.page,
    size: data.size,
    totalPages: data.totalPages,
    totalElements: data.totalElements,
    hasNext: data.hasNext,
  };
}

export function getKosdaqTopStocks(page = 1, size = 10): Promise<StockCandidatesResponse> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  return requestKosdaqTopStocks(`/api/assets/kosdaq/top?${params}`);
}

export function searchKosdaqTopStocks(keyword: string): Promise<StockCandidatesResponse> {
  const params = new URLSearchParams({ q: keyword, page: '1', size: '100' });
  return requestKosdaqTopStocks(`/api/assets/kosdaq/top?${params}`);
}
