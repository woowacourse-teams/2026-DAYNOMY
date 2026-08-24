import type { Category } from '../news/newslist/types';

export type PortfolioAssetCategory = Category | 'MOCK';
export type PortfolioImpactDirection = 'POSITIVE' | 'NEGATIVE';
export type PortfolioImpactLevel = 'HIGH' | 'MEDIUM' | 'LOW';

export type PortfolioAssetImpactResponse = {
  bookmarkId: number;
  assetId: number;
  name: string;
  category: PortfolioAssetCategory;
  assetCode: string;
  direction: PortfolioImpactDirection;
  impactLevel: PortfolioImpactLevel;
  expectedReaction: string;
  reason: string;
  sortOrder: number;
};

export type PortfolioAnalysisResponse = {
  impacts: PortfolioAssetImpactResponse[];
};
