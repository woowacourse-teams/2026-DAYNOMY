import type { PortfolioAnalysisResponse, PortfolioAssetImpactResponse } from '../portfolio/types';
import type { SimulationCategory } from './simulationAllocation';

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
  additionalExplanation: string | null;
  additionalExplanationUpdatedAt: string | null;
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

export type PortfolioReviewProposalAsset = {
  assetId: number;
  assetName: string;
  category: SimulationCategory;
  weight: number;
};

export type PortfolioReviewProposal = {
  id: number;
  shareId: number;
  commentId: number;
  assets: PortfolioReviewProposalAsset[];
  createdAt: string;
  updatedAt: string;
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
  additionalExplanation: string | null;
  additionalExplanationUpdatedAt: string | null;
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
