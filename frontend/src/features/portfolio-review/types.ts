import type { PortfolioAnalysisResponse, PortfolioAssetImpactResponse } from '../portfolio/types';

export type PortfolioAnalysisComment = {
  id: number;
  authorNickname: string;
  content: string;
  createdAt: string;
  updatedAt: string;
  replies: PortfolioAnalysisCommentReply[];
};

export type PortfolioAnalysisCommentReply = {
  id: number;
  authorNickname: string;
  content: string;
  fromOwner: boolean;
  editable: boolean;
  createdAt: string;
  updatedAt: string;
};

export type PortfolioAnalysisShareStatus = 'ACTIVE' | 'EXPIRED' | 'REVOKED';

export type PortfolioAnalysisOwnerShare = {
  id: number;
  analysisId: number | null;
  question: string;
  status: PortfolioAnalysisShareStatus;
  expiresAt: string;
  createdAt: string;
  allAssetsShared: boolean;
  sharedAssets: { assetName: string; weight: number }[];
  comments: PortfolioAnalysisComment[];
};

export type PortfolioAnalysisShareCreated = {
  share: PortfolioAnalysisOwnerShare;
  token: string;
};

export type PortfolioAnalysisHoldingContext = {
  assetCode: string;
  market: string | null;
  firstPurchasedOn: string | null;
  returnRate: number | null;
};

export type PortfolioAnalysisPublicImpact = PortfolioAssetImpactResponse & {
  holding: PortfolioAnalysisHoldingContext | null;
};

export type PortfolioAnalysisPublicShare = {
  ownerNickname: string;
  question: string;
  expiresAt: string;
  analysis: Omit<PortfolioAnalysisResponse, 'impacts'> & {
    allAssetsShared: boolean;
    hasAnalysis: boolean;
    impacts: PortfolioAnalysisPublicImpact[];
  };
  canComment: boolean;
  myComments: PortfolioAnalysisComment[];
  ownerShare: PortfolioAnalysisOwnerShare | null;
};
