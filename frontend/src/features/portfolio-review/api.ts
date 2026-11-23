import { request, requestWithCsrf } from '../../api/client';
import { isPortfolioAnalysisResponse } from '../portfolio/api';
import type { PortfolioAsset } from '../portfolio/types';
import { isSimulationCategory, type SimulationAsset } from './simulationAllocation';
import type {
  PortfolioAnalysisOwnerShare,
  PortfolioAnalysisPublicShare,
  PortfolioAnalysisComment,
  PortfolioAnalysisShareCreated,
  PortfolioReviewProposal,
  PortfolioReviewProposalAsset,
} from './types';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isDate(value: unknown): value is string {
  return typeof value === 'string' && Number.isFinite(Date.parse(value));
}

function isComment(value: unknown): value is PortfolioAnalysisComment {
  return (
    isRecord(value) &&
    Number.isSafeInteger(value.id) &&
    typeof value.authorNickname === 'string' &&
    typeof value.content === 'string' &&
    isDate(value.createdAt) &&
    isDate(value.updatedAt) &&
    Array.isArray(value.replies) &&
    value.replies.every(isReply)
  );
}

function isReply(value: unknown) {
  return (
    isRecord(value) &&
    Number.isSafeInteger(value.id) &&
    typeof value.authorNickname === 'string' &&
    typeof value.content === 'string' &&
    typeof value.fromOwner === 'boolean' &&
    typeof value.editable === 'boolean' &&
    isDate(value.createdAt) &&
    isDate(value.updatedAt)
  );
}

function isSharedAsset(value: unknown): value is PortfolioAsset {
  return (
    isRecord(value) &&
    typeof value.assetName === 'string' &&
    typeof value.weight === 'number' &&
    Number.isFinite(value.weight)
  );
}

function isNullableNumber(value: unknown) {
  return value === null || (typeof value === 'number' && Number.isFinite(value));
}

function isNullableDate(value: unknown) {
  return value === null || (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value));
}

function isHoldingContext(value: unknown) {
  return (
    isRecord(value) &&
    typeof value.assetCode === 'string' &&
    (value.market === null || typeof value.market === 'string') &&
    isNullableDate(value.firstPurchasedOn) &&
    isNullableNumber(value.returnRate)
  );
}

function hasHoldingContext(value: unknown) {
  return isRecord(value) && (value.holding === null || isHoldingContext(value.holding));
}

function hasValidExplanation(value: Record<string, unknown>) {
  return (
    (value.additionalExplanation === null && value.additionalExplanationUpdatedAt === null) ||
    (typeof value.additionalExplanation === 'string' &&
      value.additionalExplanation.trim().length > 0 &&
      value.additionalExplanation.length <= 2000 &&
      isDate(value.additionalExplanationUpdatedAt))
  );
}

function isOwnerShare(value: unknown): value is PortfolioAnalysisOwnerShare {
  return (
    isRecord(value) &&
    Number.isSafeInteger(value.id) &&
    (value.analysisId === null || Number.isSafeInteger(value.analysisId)) &&
    typeof value.question === 'string' &&
    hasValidExplanation(value) &&
    typeof value.status === 'string' &&
    ['ACTIVE', 'EXPIRED', 'REVOKED'].includes(value.status) &&
    isDate(value.expiresAt) &&
    isDate(value.createdAt) &&
    typeof value.allAssetsShared === 'boolean' &&
    Array.isArray(value.sharedAssets) &&
    value.sharedAssets.every(isSharedAsset) &&
    Array.isArray(value.comments) &&
    value.comments.every(isComment)
  );
}

function requireOwnerShares(value: unknown): PortfolioAnalysisOwnerShare[] {
  if (!Array.isArray(value) || !value.every(isOwnerShare)) {
    throw new Error('분석 의견 요청 응답 형식이 올바르지 않습니다.');
  }
  return value;
}

function requireCreated(value: unknown): PortfolioAnalysisShareCreated {
  if (!isRecord(value) || !isOwnerShare(value.share) || typeof value.token !== 'string') {
    throw new Error('공유 링크 응답 형식이 올바르지 않습니다.');
  }
  return { share: value.share, token: value.token };
}

function requirePublicShare(value: unknown): PortfolioAnalysisPublicShare {
  if (
    !isRecord(value) ||
    typeof value.ownerNickname !== 'string' ||
    typeof value.question !== 'string' ||
    !hasValidExplanation(value) ||
    !isDate(value.expiresAt) ||
    !isRecord(value.analysis) ||
    typeof value.analysis.allAssetsShared !== 'boolean' ||
    typeof value.analysis.hasAnalysis !== 'boolean' ||
    !isPortfolioAnalysisResponse(value.analysis) ||
    !Array.isArray(value.analysis.impacts) ||
    !value.analysis.impacts.every(hasHoldingContext) ||
    typeof value.canComment !== 'boolean' ||
    !Array.isArray(value.myComments) ||
    !value.myComments.every(isComment) ||
    !(value.ownerShare === null || isOwnerShare(value.ownerShare))
  ) {
    throw new Error('공유된 분석 응답 형식이 올바르지 않습니다.');
  }
  return value as PortfolioAnalysisPublicShare;
}

function isReviewProposalAsset(value: unknown): value is PortfolioReviewProposalAsset {
  return (
    isRecord(value) &&
    Number.isSafeInteger(value.assetId) &&
    typeof value.assetId === 'number' &&
    value.assetId > 0 &&
    typeof value.assetName === 'string' &&
    value.assetName.trim().length > 0 &&
    value.assetName.length <= 100 &&
    isSimulationCategory(value.category) &&
    typeof value.weight === 'number' &&
    Number.isFinite(value.weight) &&
    value.weight > 0 &&
    value.weight <= 100
  );
}

function isReviewProposal(value: unknown): value is PortfolioReviewProposal {
  if (
    !isRecord(value) ||
    !Number.isSafeInteger(value.id) ||
    typeof value.id !== 'number' ||
    value.id <= 0 ||
    !Number.isSafeInteger(value.shareId) ||
    typeof value.shareId !== 'number' ||
    value.shareId <= 0 ||
    !Number.isSafeInteger(value.commentId) ||
    typeof value.commentId !== 'number' ||
    value.commentId <= 0 ||
    !Array.isArray(value.assets) ||
    value.assets.length === 0 ||
    value.assets.length > 50 ||
    !value.assets.every(isReviewProposalAsset) ||
    !isDate(value.createdAt) ||
    !isDate(value.updatedAt)
  )
    return false;
  const assetIds = new Set(value.assets.map((asset) => asset.assetId));
  const assetNames = new Set(value.assets.map((asset) => asset.assetName.trim().toLowerCase()));
  const weights = value.assets.map((asset) => Math.round(asset.weight * 100));
  const validPrecision = value.assets.every(
    (asset, index) => Math.abs(asset.weight * 100 - weights[index]) < 1e-8,
  );
  const total = weights.reduce((sum, weight) => sum + weight, 0);
  return (
    assetIds.size === value.assets.length &&
    assetNames.size === value.assets.length &&
    validPrecision &&
    total === 10_000
  );
}

function requireReviewProposal(value: unknown) {
  if (!isReviewProposal(value)) throw new Error('저장된 검토안 응답 형식이 올바르지 않습니다.');
  return value;
}

export async function getPortfolioAnalysisShares(signal?: AbortSignal) {
  return requireOwnerShares(
    await request<unknown>('/api/users/me/portfolio/analysis/shares', { signal }),
  );
}

export async function createPortfolioAnalysisShare(
  analysisId: number | null,
  question: string,
  assetNames: string[],
) {
  return requireCreated(
    await requestWithCsrf<unknown>('/api/users/me/portfolio/analysis/shares', {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ analysisId, question, assetNames }),
    }),
  );
}

export async function revokePortfolioAnalysisShare(shareId: number) {
  const response = await requestWithCsrf<unknown>(
    `/api/users/me/portfolio/analysis/shares/${shareId}/revoke`,
    { method: 'POST' },
  );
  if (!isOwnerShare(response)) throw new Error('공유 중단 응답 형식이 올바르지 않습니다.');
  return response;
}

export async function updatePortfolioShareExplanation(shareId: number, content: string) {
  const response = await requestWithCsrf<unknown>(
    `/api/users/me/portfolio/analysis/shares/${shareId}/explanation`,
    {
      method: 'PUT',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ content }),
    },
  );
  if (!isOwnerShare(response)) throw new Error('추가 설명 응답 형식이 올바르지 않습니다.');
  return response;
}

export function deletePortfolioAnalysisShare(shareId: number) {
  return requestWithCsrf<void>(`/api/users/me/portfolio/analysis/shares/${shareId}`, {
    method: 'DELETE',
  });
}

export async function getPublicPortfolioAnalysisShare(token: string, signal?: AbortSignal) {
  return requirePublicShare(
    await request<unknown>('/api/portfolio/analysis/shares', {
      signal,
      headers: { 'X-Portfolio-Share-Token': token },
    }),
  );
}

export async function createPortfolioAnalysisComment(token: string, content: string) {
  const response = await requestWithCsrf<unknown>('/api/portfolio/analysis/shares/comments', {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      'X-Portfolio-Share-Token': token,
    },
    body: JSON.stringify({ content }),
  });
  if (!isComment(response)) throw new Error('댓글 저장 응답 형식이 올바르지 않습니다.');
  return response;
}

export async function updatePortfolioAnalysisComment(
  token: string,
  commentId: number,
  content: string,
) {
  const response = await requestWithCsrf<unknown>(
    `/api/portfolio/analysis/shares/comments/${commentId}`,
    {
      method: 'PUT',
      headers: {
        'content-type': 'application/json',
        'X-Portfolio-Share-Token': token,
      },
      body: JSON.stringify({ content }),
    },
  );
  if (!isComment(response)) throw new Error('댓글 수정 응답 형식이 올바르지 않습니다.');
  return response;
}

export function deletePortfolioAnalysisComment(token: string, commentId: number) {
  return requestWithCsrf<void>(`/api/portfolio/analysis/shares/comments/${commentId}`, {
    method: 'DELETE',
    headers: { 'X-Portfolio-Share-Token': token },
  });
}

function requireComment(value: unknown) {
  if (!isComment(value)) throw new Error('댓글 대화 응답 형식이 올바르지 않습니다.');
  return value;
}

export async function savePortfolioAnalysisOwnerReply(
  shareId: number,
  commentId: number,
  content: string,
) {
  return requireComment(
    await requestWithCsrf<unknown>(
      `/api/users/me/portfolio/analysis/shares/${shareId}/comments/${commentId}/replies`,
      {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ content }),
      },
    ),
  );
}

export async function savePortfolioAnalysisCommenterReply(
  token: string,
  commentId: number,
  content: string,
) {
  return requireComment(
    await requestWithCsrf<unknown>(`/api/portfolio/analysis/shares/comments/${commentId}/replies`, {
      method: 'POST',
      headers: {
        'content-type': 'application/json',
        'X-Portfolio-Share-Token': token,
      },
      body: JSON.stringify({ content }),
    }),
  );
}

export async function updatePortfolioAnalysisReply(replyId: number, content: string) {
  return requireComment(
    await requestWithCsrf<unknown>(`/api/users/me/portfolio/analysis/comment-replies/${replyId}`, {
      method: 'PUT',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ content }),
    }),
  );
}

export async function deletePortfolioAnalysisReply(replyId: number) {
  return requireComment(
    await requestWithCsrf<unknown>(`/api/users/me/portfolio/analysis/comment-replies/${replyId}`, {
      method: 'DELETE',
    }),
  );
}

export async function getReviewSimulationAssets(
  shareId: number,
  commentId: number,
  signal?: AbortSignal,
) {
  const response = await request<unknown>(
    `/api/users/me/portfolio/analysis/shares/${shareId}/comments/${commentId}/simulation`,
    { signal },
  );
  if (
    !Array.isArray(response) ||
    response.length === 0 ||
    response.length > 50 ||
    !response.every(
      (asset): asset is SimulationAsset =>
        isRecord(asset) &&
        Number.isSafeInteger(asset.assetId) &&
        typeof asset.assetId === 'number' &&
        asset.assetId > 0 &&
        typeof asset.assetName === 'string' &&
        asset.assetName.trim().length > 0 &&
        asset.assetName.length <= 100 &&
        isSimulationCategory(asset.category) &&
        typeof asset.amount === 'number' &&
        Number.isFinite(asset.amount) &&
        asset.amount > 0 &&
        asset.amount <= 1_000_000_000_000,
    )
  )
    throw new Error('임시 포트폴리오 응답 형식이 올바르지 않습니다.');
  return response.map(({ assetId, assetName, category, amount }) => ({
    assetId,
    assetName,
    category,
    amount,
  }));
}

export async function analyzeReviewSimulation(
  shareId: number,
  commentId: number,
  assets: PortfolioAsset[],
) {
  const response = await requestWithCsrf<unknown>(
    `/api/users/me/portfolio/analysis/shares/${shareId}/comments/${commentId}/simulation`,
    {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ assets }),
    },
  );
  if (!isPortfolioAnalysisResponse(response))
    throw new Error('임시 포트폴리오 분석 응답 형식이 올바르지 않습니다.');
  return response;
}

export async function getPortfolioReviewProposals(shareId: number, signal?: AbortSignal) {
  const response = await request<unknown>(
    `/api/users/me/portfolio/analysis/shares/${shareId}/proposals`,
    { signal },
  );
  if (!Array.isArray(response) || !response.every(isReviewProposal))
    throw new Error('저장된 검토안 목록 응답 형식이 올바르지 않습니다.');
  return response;
}

export async function savePortfolioReviewProposal(
  shareId: number,
  commentId: number,
  assets: { assetId: number; weight: number }[],
) {
  return requireReviewProposal(
    await requestWithCsrf<unknown>(
      `/api/users/me/portfolio/analysis/shares/${shareId}/comments/${commentId}/proposal`,
      {
        method: 'PUT',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ assets }),
      },
    ),
  );
}

export function deletePortfolioReviewProposal(shareId: number, commentId: number) {
  return requestWithCsrf<void>(
    `/api/users/me/portfolio/analysis/shares/${shareId}/comments/${commentId}/proposal`,
    { method: 'DELETE' },
  );
}
