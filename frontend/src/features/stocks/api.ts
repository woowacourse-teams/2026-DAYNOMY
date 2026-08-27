import type { StockCandidate, StockCandidatesResponse } from './types';

type StockCandidateApiResponse = {
  rank: number;
  code: string;
  name: string;
  rankChange?: number | null;
};

type StockCandidatesApiResponse = {
  baseDate: string | null;
  rankings: StockCandidateApiResponse[];
};

function normalizeStockCandidate(item: StockCandidateApiResponse): StockCandidate {
  return {
    rank: item.rank,
    code: item.code,
    name: item.name,
    rankChange: item.rankChange ?? null,
  };
}

function assertStockCandidatesResponse(data: StockCandidatesApiResponse) {
  if (!Array.isArray(data.rankings)) {
    throw new Error('종목 목록 API 응답 형식이 올바르지 않습니다.');
  }
}

export async function getKosdaqTopStocks(): Promise<StockCandidatesResponse> {
  const response = await fetch('/api/assets/kosdaq/top');

  if (!response.ok) {
    throw new Error('종목 목록을 불러오지 못했습니다.');
  }

  const contentType = response.headers.get('content-type') ?? '';

  if (!contentType.includes('application/json')) {
    throw new Error('종목 목록 API 응답 형식이 올바르지 않습니다.');
  }

  const data = (await response.json()) as StockCandidatesApiResponse;
  assertStockCandidatesResponse(data);

  return {
    baseDate: data.baseDate,
    rankings: data.rankings.map(normalizeStockCandidate),
  };
}
