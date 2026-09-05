export type StockCandidate = {
  rank: number;
  code: string;
  name: string;
  rankChange?: number | null;
};

export type StockCandidatesResponse = {
  baseDate: string | null;
  rankings: StockCandidate[];
  page: number;
  size: number;
  totalPages: number;
  totalElements: number;
  hasNext: boolean;
};
