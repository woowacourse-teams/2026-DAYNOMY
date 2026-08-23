export type StockCandidate = {
  rank: number;
  code: string;
  name: string;
};

export type StockCandidatesResponse = {
  baseDate: string | null;
  rankings: StockCandidate[];
};
