import { useEffect, useMemo, useRef, useState } from 'react';
import { ChatCircleText, LinkBreak, LockKey, ShieldCheck, User } from '@phosphor-icons/react';
import { Link, useLocation, useParams } from 'react-router-dom';
import analysisMascot from '../../assets/portfolio-analysis-mascot.png';
import { trackEvent } from '../../analytics';
import { ApiError } from '../../api/client';
import { useAuth } from '../../hooks/useLoginStatus';
import {
  analyzeReviewSimulation,
  createPortfolioAnalysisComment,
  deletePortfolioReviewProposal,
  deletePortfolioAnalysisComment,
  getPortfolioReviewProposals,
  getOwnerPortfolioAnalysisShare,
  getPublicPortfolioAnalysisShare,
  savePortfolioAnalysisCommenterReply,
  savePortfolioAnalysisOwnerReply,
  updatePortfolioAnalysisComment,
} from './api';
import { PortfolioAnalysisReplyThread } from './PortfolioAnalysisReplyThread';
import type {
  PortfolioAnalysisComment,
  PortfolioAnalysisPublicShare,
  PortfolioReviewProposal,
} from './types';
import {
  PortfolioReviewResult,
  PortfolioReviewSimulation,
  type PortfolioReviewResultState,
} from './PortfolioReviewSimulation';
import { PortfolioReviewAnalysisResult } from './PortfolioReviewAnalysisResult';
import './portfolioAnalysisShare.css';
import './portfolioPublicDocument.css';

const ALLOCATION_COLORS = ['#3d6de0', '#78a5f2', '#bfd4f4', '#d7dce3', '#aeb8c6'];

function formatDate(value: string) {
  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value));
}

function formatExpiryDate(value: string) {
  return new Intl.DateTimeFormat('ko-KR', {
    month: 'long',
    day: 'numeric',
  }).format(new Date(value));
}

function displayOwnerNickname(value: string) {
  return /^회원_[0-9a-f-]+$/i.test(value) ? '익명 투자자' : value;
}

function errorMessage(error: unknown) {
  if (!(error instanceof ApiError)) return '공유된 분석을 불러오지 못했어요.';
  if (error.code === 'PORTFOLIO_ANALYSIS_SHARE_EXPIRED') return '공유 링크가 만료됐어요.';
  if (error.code === 'PORTFOLIO_ANALYSIS_SHARE_REVOKED') return '작성자가 공유를 중단했어요.';
  if (error.status === 404) return '공유된 분석을 찾을 수 없어요.';
  return error.message;
}

function getCommentValidationMessage(value: string) {
  const normalized = value.trim();
  if (!value) return '';
  if (normalized.length < 2) return '의견은 2자 이상 입력해 주세요.';
  if (normalized.length > 1000) return '의견은 1000자까지 입력할 수 있어요.';
  if (/[<>]/.test(value)) return '< 또는 > 문자는 사용할 수 없어요.';
  return '';
}

function savedProposalResult(proposal: PortfolioReviewProposal): PortfolioReviewResultState {
  return {
    proposal,
    base: null,
    draft: proposal.assets.map(({ assetId, assetName, category, weight }) => ({
      assetId,
      assetName,
      category,
      weight: weight.toFixed(2),
    })),
    analysis: null,
  };
}

function SavedProposalSelector({
  proposals,
  comments,
  selectedProposalId,
  disabled,
  onSelect,
}: {
  proposals: PortfolioReviewProposal[];
  comments: PortfolioAnalysisComment[];
  selectedProposalId: number | null;
  disabled: boolean;
  onSelect: (proposal: PortfolioReviewProposal) => void;
}) {
  if (!proposals.length) return null;
  const ordered = [...proposals].sort(
    (left, right) =>
      new Date(left.createdAt).getTime() - new Date(right.createdAt).getTime() ||
      left.id - right.id,
  );
  const selectedId = selectedProposalId ?? ordered.at(-1)!.id;
  const selectedIndex = Math.max(
    0,
    ordered.findIndex((proposal) => proposal.id === selectedId),
  );

  if (ordered.length === 1) return null;

  return (
    <label className="portfolio-proposal-selector">
      <span className="portfolio-proposal-selector-count">저장한 개선안 {ordered.length}개</span>
      <select
        aria-label="저장한 개선안"
        disabled={disabled}
        value={String(ordered[selectedIndex].id)}
        onChange={(event) => {
          const selected = ordered.find((proposal) => String(proposal.id) === event.target.value);
          if (selected) onSelect(selected);
        }}
      >
        {ordered.map((proposal, index) => {
          const comment = comments.find((value) => value.id === proposal.commentId);
          const excerpt = comment?.content.trim() || '원댓글을 확인할 수 없음';
          return (
            <option key={proposal.id} value={proposal.id}>
              {`${index + 1}. ${excerpt}`}
            </option>
          );
        })}
      </select>
    </label>
  );
}

function installPrivatePageMeta() {
  const created: HTMLMetaElement[] = [];
  const ensure = (name: string, content: string) => {
    const existing = document.head.querySelector<HTMLMetaElement>(`meta[name="${name}"]`);
    if (existing) {
      const previous = existing.content;
      existing.content = content;
      return () => {
        existing.content = previous;
      };
    }
    const meta = document.createElement('meta');
    meta.name = name;
    meta.content = content;
    document.head.append(meta);
    created.push(meta);
    return () => {};
  };
  const restoreRobots = ensure('robots', 'noindex, nofollow');
  const restoreReferrer = ensure('referrer', 'no-referrer');
  return () => {
    restoreRobots();
    restoreReferrer();
    created.forEach((meta) => meta.remove());
  };
}

export default function PortfolioAnalysisSharePage() {
  const location = useLocation();
  const { shareId: ownerShareIdParam } = useParams();
  const { isLoggedIn, loading: authLoading, memberId } = useAuth();
  const token = useMemo(() => location.hash.replace(/^#/, ''), [location.hash]);
  const ownerPreviewShareId = ownerShareIdParam ? Number(ownerShareIdParam) : null;
  const [share, setShare] = useState<PortfolioAnalysisPublicShare | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [content, setContent] = useState('');
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editingContent, setEditingContent] = useState('');
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState('');
  const [proposals, setProposals] = useState<PortfolioReviewProposal[]>([]);
  const [proposalResult, setProposalResult] = useState<PortfolioReviewResultState | null>(null);
  const [proposalError, setProposalError] = useState('');
  const [proposalLoading, setProposalLoading] = useState(false);
  const [proposalAttempt, setProposalAttempt] = useState(0);
  const [deletingProposal, setDeletingProposal] = useState(false);
  const [analyzingProposal, setAnalyzingProposal] = useState(false);
  const proposalResultRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const previousTitle = document.title;
    document.title = '포트폴리오 분석 의견 요청 | DAYNOMY';
    const restoreMeta = installPrivatePageMeta();
    return () => {
      document.title = previousTitle;
      restoreMeta();
    };
  }, []);

  useEffect(() => {
    if (authLoading) return;
    const ownerPreview = ownerShareIdParam !== undefined;
    if (
      (ownerPreview && (!Number.isSafeInteger(ownerPreviewShareId) || ownerPreviewShareId! <= 0)) ||
      (!ownerPreview && (!token || token.length > 100))
    ) {
      setError('공유된 분석을 찾을 수 없어요.');
      setLoading(false);
      return;
    }
    const controller = new AbortController();
    setLoading(true);
    setError('');
    const request = ownerPreview
      ? getOwnerPortfolioAnalysisShare(ownerPreviewShareId!, controller.signal)
      : getPublicPortfolioAnalysisShare(token, controller.signal);
    request
      .then((value) => {
        if (controller.signal.aborted) return;
        setShare(value);
        setContent('');
        trackEvent('portfolio_analysis_share_viewed', {
          status: 'success',
          is_logged_in: isLoggedIn,
        });
      })
      .catch((caught: unknown) => {
        if (!controller.signal.aborted) {
          setError(errorMessage(caught));
          setShare(null);
          trackEvent('portfolio_analysis_share_viewed', {
            status: 'error',
            is_logged_in: isLoggedIn,
          });
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [authLoading, isLoggedIn, memberId, ownerPreviewShareId, ownerShareIdParam, token]);

  const ownerShareId = share?.ownerShare?.id;

  useEffect(() => {
    if (!ownerShareId) {
      setProposals([]);
      setProposalResult(null);
      setProposalError('');
      setProposalLoading(false);
      return;
    }
    const controller = new AbortController();
    setProposals([]);
    setProposalResult(null);
    setProposalError('');
    setProposalLoading(true);
    getPortfolioReviewProposals(ownerShareId, controller.signal)
      .then((values) => {
        if (controller.signal.aborted) return;
        setProposals(values);
        const latest = values[0];
        setProposalResult(latest ? savedProposalResult(latest) : null);
      })
      .catch((caught: unknown) => {
        if (!controller.signal.aborted) {
          setProposalError(
            caught instanceof ApiError ? caught.message : '저장한 개선안을 불러오지 못했어요.',
          );
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setProposalLoading(false);
      });
    return () => controller.abort();
  }, [ownerShareId, proposalAttempt]);

  const normalizedContent = content.trim();
  const contentValidationMessage = getCommentValidationMessage(content);
  const contentInvalid = !normalizedContent || Boolean(contentValidationMessage);
  const editingValidationMessage = getCommentValidationMessage(editingContent);
  const editingInvalid = !editingContent.trim() || Boolean(editingValidationMessage);

  async function createComment() {
    if (!share || contentInvalid || saving) return;
    setSaving(true);
    setError('');
    setNotice('');
    try {
      const comment = await createPortfolioAnalysisComment(token, normalizedContent);
      setShare({ ...share, myComments: [...share.myComments, comment] });
      setContent('');
      setNotice('댓글을 남겼어요.');
      trackEvent('portfolio_analysis_comment_saved', {
        status: 'success',
        action: 'create',
      });
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : '댓글을 저장하지 못했어요.');
    } finally {
      setSaving(false);
    }
  }

  async function updateComment(commentId: number) {
    const normalized = editingContent.trim();
    if (!share || editingInvalid) return;
    setSaving(true);
    setError('');
    try {
      const comment = await updatePortfolioAnalysisComment(token, commentId, normalized);
      setShare({
        ...share,
        myComments: share.myComments.map((value) => (value.id === comment.id ? comment : value)),
      });
      setEditingId(null);
      setEditingContent('');
      setNotice('댓글을 수정했어요.');
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : '댓글을 수정하지 못했어요.');
    } finally {
      setSaving(false);
    }
  }

  async function removeComment(commentId: number) {
    if (!share || saving || !window.confirm('이 댓글을 삭제할까요?')) return;
    setSaving(true);
    setError('');
    try {
      await deletePortfolioAnalysisComment(token, commentId);
      setShare({
        ...share,
        myComments: share.myComments.filter((comment) => comment.id !== commentId),
      });
      setNotice('댓글을 삭제했어요.');
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : '댓글을 삭제하지 못했어요.');
    } finally {
      setSaving(false);
    }
  }

  function rememberProposal(proposal: PortfolioReviewProposal) {
    setProposals((current) => [
      proposal,
      ...current.filter((value) => value.commentId !== proposal.commentId),
    ]);
    setProposalResult(savedProposalResult(proposal));
  }

  function selectProposal(proposal: PortfolioReviewProposal) {
    if (analyzingProposal) return;
    setProposalError('');
    if (proposalResult?.proposal.id !== proposal.id) {
      setProposalResult(savedProposalResult(proposal));
    }
    window.requestAnimationFrame(() =>
      proposalResultRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' }),
    );
  }

  function showAnalysisResult(result: PortfolioReviewResultState) {
    rememberProposal(result.proposal);
    setProposalResult(result);
    setProposalError('');
    window.requestAnimationFrame(() =>
      proposalResultRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' }),
    );
  }

  async function analyzeSavedProposal() {
    if (!ownerShareId || !proposalResult || analyzingProposal) return;
    const proposal = proposalResult.proposal;
    setAnalyzingProposal(true);
    setProposalError('');
    try {
      const analysis = await analyzeReviewSimulation(
        ownerShareId,
        proposal.commentId,
        proposal.assets.map((asset) => ({
          assetName: asset.assetName,
          weight: asset.weight,
        })),
      );
      setProposalResult((current) =>
        current?.proposal.id === proposal.id ? { ...current, analysis } : current,
      );
    } catch (caught) {
      setProposalError(
        caught instanceof ApiError
          ? caught.message
          : '개선안을 분석하지 못했어요. 다시 시도해 주세요.',
      );
    } finally {
      setAnalyzingProposal(false);
    }
  }

  async function removeProposal() {
    if (
      !ownerShareId ||
      !proposalResult ||
      deletingProposal ||
      analyzingProposal ||
      !window.confirm('저장한 개선안을 삭제할까요? 분석 결과도 화면에서 사라집니다.')
    )
      return;
    setDeletingProposal(true);
    setProposalError('');
    try {
      await deletePortfolioReviewProposal(ownerShareId, proposalResult.proposal.commentId);
      const remaining = proposals.filter(
        (value) => value.commentId !== proposalResult.proposal.commentId,
      );
      setProposals(remaining);
      const latest = remaining[0];
      setProposalResult(latest ? savedProposalResult(latest) : null);
    } catch (caught) {
      setProposalError(
        caught instanceof ApiError ? caught.message : '저장한 개선안을 삭제하지 못했어요.',
      );
    } finally {
      setDeletingProposal(false);
    }
  }

  if (loading) {
    return (
      <main className="portfolio-public-share-page" aria-busy="true">
        <section className="portfolio-public-share-state">
          <ChatCircleText size={32} aria-hidden="true" />
          <p role="status">공유된 분석을 불러오고 있어요.</p>
        </section>
      </main>
    );
  }

  if (!share) {
    return (
      <main className="portfolio-public-share-page">
        <section className="portfolio-public-share-state" role="alert">
          <LinkBreak size={32} aria-hidden="true" />
          <h1>{error || '공유된 분석을 찾을 수 없어요.'}</h1>
          <p>링크가 정확한지 확인하거나 작성자에게 새 링크를 요청해 주세요.</p>
          <Link to="/">내 포트폴리오 분석하기</Link>
        </section>
      </main>
    );
  }

  const impacts = [...share.analysis.impacts].sort((left, right) => left.rank - right.rank);
  const returnTo = `${location.pathname}${location.search}${location.hash}`;
  const ownerNickname = displayOwnerNickname(share.ownerNickname);

  return (
    <main
      className={`portfolio-public-share-page ${share.ownerShare ? 'is-owner-view' : 'is-reviewer-view'}`}
    >
      <div className="portfolio-public-content">
        <div className="portfolio-public-main">
          <section className="portfolio-public-share-hero" id="portfolio-public-question">
            <div className="portfolio-public-share-owner">
              <strong>{ownerNickname}</strong>
              <span>님의 질문</span>
            </div>
            <time dateTime={share.expiresAt} className="portfolio-public-question-expiry">
              <LockKey size={18} aria-hidden="true" />
              링크 공개 기한 · {formatExpiryDate(share.expiresAt)}까지
            </time>
            <h1>{share.question}</h1>
            {share.additionalExplanation ? (
              <section
                className="portfolio-public-explanation"
                aria-labelledby="portfolio-public-explanation-title"
              >
                <div>
                  <h2 id="portfolio-public-explanation-title">작성자 추가 설명</h2>
                  {share.additionalExplanationUpdatedAt ? (
                    <time dateTime={share.additionalExplanationUpdatedAt}>
                      {formatDate(share.additionalExplanationUpdatedAt)} 수정
                    </time>
                  ) : null}
                </div>
                <p>{share.additionalExplanation}</p>
              </section>
            ) : null}
          </section>

          <section
            className={`portfolio-public-assets${share.analysis.hasAnalysis ? ' is-summary' : ''}`}
            aria-labelledby="portfolio-public-assets-title"
          >
            <div className="portfolio-public-section-heading">
              <div>
                <h2 id="portfolio-public-assets-title">
                  {share.analysis.hasAnalysis
                    ? share.analysis.allAssetsShared
                      ? '포트폴리오 구성'
                      : '공개한 종목 구성'
                    : '종목별 구성'}{' '}
                  <span className="portfolio-public-count">{impacts.length}개</span>
                </h2>
              </div>
            </div>
            <p className="portfolio-public-privacy-note">
              <ShieldCheck size={18} aria-hidden="true" />
              <span>
                공유 시점의 종목과 비중이에요. 매수가·공유 당시 가격·수량·평가금액·손익 금액은
                비공개입니다.
              </span>
            </p>
            {share.analysis.hasAnalysis && !share.analysis.allAssetsShared ? (
              <p className="portfolio-public-partial-note">
                전체 포트폴리오 기준 비중이며, 선택한 종목만 표시해요.
              </p>
            ) : null}
            {!share.analysis.hasAnalysis && impacts.length > 0 ? (
              <div className="portfolio-public-allocation">
                <div
                  className="portfolio-public-allocation-bar"
                  role="img"
                  aria-label={`전체 포트폴리오 기준 공개 종목 비중: ${impacts.map((impact) => `${impact.assetName} ${impact.weight}%`).join(', ')}`}
                >
                  {impacts.map((impact, index) => (
                    <span
                      key={impact.assetName}
                      style={{
                        flexBasis: `${impact.weight}%`,
                        backgroundColor: ALLOCATION_COLORS[index % ALLOCATION_COLORS.length],
                      }}
                    >
                      {impact.weight >= 8 ? <span aria-hidden="true">{impact.weight}%</span> : null}
                    </span>
                  ))}
                </div>
                {!share.analysis.allAssetsShared ? (
                  <p>전체 포트폴리오 기준 비중이며, 선택한 종목만 표시해요.</p>
                ) : null}
              </div>
            ) : null}
            <div
              className={share.analysis.hasAnalysis ? 'portfolio-public-asset-table' : undefined}
              role={share.analysis.hasAnalysis ? 'table' : undefined}
              aria-label={share.analysis.hasAnalysis ? '공개 포트폴리오 구성' : undefined}
            >
              {share.analysis.hasAnalysis ? (
                <div className="portfolio-public-asset-table-head" role="row">
                  <span role="columnheader">종목</span>
                  <span role="columnheader">시장</span>
                  <span role="columnheader">비중</span>
                </div>
              ) : null}
              <div
                className="portfolio-public-asset-list"
                role={share.analysis.hasAnalysis ? 'rowgroup' : undefined}
              >
                {impacts.map((impact, index) => {
                  const holding = impact.holding;
                  const returnRate = holding?.returnRate;
                  const clampedWeight = Math.min(100, Math.max(0, impact.weight));
                  return (
                    <article
                      key={impact.assetName}
                      role={share.analysis.hasAnalysis ? 'row' : undefined}
                    >
                      <header role={share.analysis.hasAnalysis ? 'rowheader' : undefined}>
                        {!share.analysis.hasAnalysis ? (
                          <span
                            className="portfolio-public-asset-color"
                            aria-hidden="true"
                            style={{
                              backgroundColor: ALLOCATION_COLORS[index % ALLOCATION_COLORS.length],
                            }}
                          />
                        ) : null}
                        <div>
                          <h3>{impact.assetName}</h3>
                          <span>
                            {share.analysis.hasAnalysis
                              ? holding?.assetCode || '종목 정보'
                              : [holding?.assetCode, holding?.market].filter(Boolean).join(' · ') ||
                                '종목 정보'}
                          </span>
                        </div>
                      </header>
                      {share.analysis.hasAnalysis ? (
                        <>
                          <span className="portfolio-public-asset-market" role="cell">
                            {holding?.market || '—'}
                          </span>
                          <div className="portfolio-public-weight-cell" role="cell">
                            <span className="portfolio-public-weight-line" aria-hidden="true">
                              <span style={{ width: `${clampedWeight}%` }} />
                            </span>
                            <strong className="portfolio-public-weight">{impact.weight}%</strong>
                          </div>
                        </>
                      ) : (
                        <>
                          <strong className="portfolio-public-weight">{impact.weight}%</strong>
                          <div
                            className="portfolio-public-weight-bar"
                            role="img"
                            aria-label={`${impact.assetName} 비중 ${impact.weight}%`}
                          >
                            <span
                              style={{
                                width: `${clampedWeight}%`,
                                backgroundColor:
                                  ALLOCATION_COLORS[index % ALLOCATION_COLORS.length],
                              }}
                            />
                          </div>
                          <dl
                            className={`portfolio-public-return${
                              returnRate == null
                                ? ''
                                : returnRate > 0
                                  ? ' is-gain'
                                  : returnRate < 0
                                    ? ' is-loss'
                                    : ''
                            }`}
                          >
                            <dt>수익률</dt>
                            <dd>
                              {returnRate == null
                                ? '확인 불가'
                                : `${returnRate > 0 ? '+' : ''}${returnRate.toFixed(2)}%`}
                            </dd>
                          </dl>
                        </>
                      )}
                    </article>
                  );
                })}
              </div>
            </div>
            {!share.analysis.hasAnalysis && share.analysis.overallImpact ? (
              <p className="portfolio-public-summary">{share.analysis.overallImpact}</p>
            ) : null}
            {!share.analysis.hasAnalysis && share.analysis.excludedAssets?.length ? (
              <p className="portfolio-public-excluded">
                분석 예산에 따라 {share.analysis.excludedAssets.length}개 종목은 분석 대상에서
                제외됐어요.
              </p>
            ) : null}
          </section>

          <a className="portfolio-public-mobile-review-link" href="#portfolio-public-review">
            {share.ownerShare
              ? `받은 의견 ${share.ownerShare.comments.length}개 보기`
              : '의견 남기기'}
          </a>

          {share.analysis.hasAnalysis ? (
            <PortfolioReviewAnalysisResult
              analysis={share.analysis}
              title={share.analysis.allAssetsShared ? 'AI 포트폴리오 분석' : '공개 종목 분석'}
              showOverall={share.analysis.allAssetsShared}
              fullLayout
            />
          ) : null}
          {proposalError ? (
            <div className="portfolio-public-proposal-error" role="alert">
              <p>{proposalError}</p>
              <button type="button" onClick={() => setProposalAttempt((current) => current + 1)}>
                다시 불러오기
              </button>
            </div>
          ) : proposalLoading ? (
            <p className="portfolio-public-proposal-loading" role="status">
              저장한 개선안을 확인하고 있어요.
            </p>
          ) : null}
          {proposalResult ? (
            <div ref={proposalResultRef}>
              <PortfolioReviewResult
                {...proposalResult}
                sourceComment={
                  share.ownerShare?.comments.find(
                    (comment) => comment.id === proposalResult.proposal.commentId,
                  )?.content ?? null
                }
                proposalSelector={
                  share.ownerShare ? (
                    <SavedProposalSelector
                      proposals={proposals}
                      comments={share.ownerShare.comments}
                      selectedProposalId={proposalResult.proposal.id}
                      disabled={analyzingProposal}
                      onSelect={selectProposal}
                    />
                  ) : null
                }
                baselineAnalysis={
                  share.analysis.hasAnalysis && share.analysis.allAssetsShared
                    ? share.analysis
                    : null
                }
                deleting={deletingProposal}
                analyzing={analyzingProposal}
                onDelete={() => void removeProposal()}
                onAnalyze={() => void analyzeSavedProposal()}
                onReanalyze={() => {
                  const trigger = document.getElementById(
                    `portfolio-review-simulation-${proposalResult.proposal.commentId}`,
                  );
                  if (trigger instanceof HTMLButtonElement) {
                    trigger.scrollIntoView({ behavior: 'smooth', block: 'center' });
                    trigger.click();
                  }
                }}
              />
            </div>
          ) : null}
        </div>

        <section
          id="portfolio-public-review"
          className={`portfolio-public-review${share.ownerShare ? ' is-owner' : ''}`}
          aria-labelledby="portfolio-public-review-title"
        >
          <header className="portfolio-public-review-heading">
            <h2 id="portfolio-public-review-title">
              {share.ownerShare ? `받은 의견 ${share.ownerShare.comments.length}개` : '의견 남기기'}
            </h2>
            {share.ownerShare ? (
              <p className="portfolio-public-owner-note">댓글 작성자와 나만 볼 수 있는 대화예요.</p>
            ) : (
              <p className="portfolio-public-review-prompt">이 구성에서 보완할 점을 알려주세요.</p>
            )}
          </header>
          {!isLoggedIn ? (
            <div className="portfolio-public-login">
              <p>로그인하고 의견을 나눠보세요.</p>
              <Link to={`/login?returnTo=${encodeURIComponent(returnTo)}`}>
                로그인하고 의견 남기기
              </Link>
            </div>
          ) : share.canComment ? (
            <>
              <div className="portfolio-public-comment-form">
                <label className="portfolio-public-comment-compose">
                  <textarea
                    aria-label="새 댓글"
                    aria-describedby="portfolio-public-comment-hint portfolio-public-comment-count"
                    value={content}
                    rows={3}
                    maxLength={1000}
                    onChange={(event) => {
                      setContent(event.target.value);
                      setNotice('');
                    }}
                    aria-invalid={Boolean(contentValidationMessage)}
                    placeholder="의견을 입력해 주세요."
                  />
                  <span className="portfolio-public-comment-meta">
                    <small
                      id="portfolio-public-comment-hint"
                      className={contentValidationMessage ? 'is-error' : undefined}
                    >
                      {contentValidationMessage || '작성자와 나만 볼 수 있어요.'}
                    </small>
                    <small id="portfolio-public-comment-count">
                      {normalizedContent.length}/1000자
                    </small>
                  </span>
                </label>
                {error ? (
                  <p className="portfolio-share-error" role="alert">
                    {error}
                  </p>
                ) : null}
                <div className="portfolio-public-comment-footer">
                  <p className="portfolio-public-comment-privacy">
                    <img src={analysisMascot} alt="" />
                    작성자에게만 공개됩니다
                  </p>
                  {notice ? (
                    <p className="portfolio-share-notice" role="status">
                      {notice}
                    </p>
                  ) : null}
                  <div className="portfolio-share-actions">
                    <button
                      type="button"
                      onClick={() => void createComment()}
                      disabled={contentInvalid || saving}
                    >
                      {saving ? '저장 중' : '의견 보내기'}
                    </button>
                  </div>
                </div>
              </div>
              {share.myComments.length ? (
                <ul className="portfolio-share-reviews" aria-label="내가 남긴 의견">
                  {share.myComments.map((comment) => (
                    <li key={comment.id}>
                      <div className="portfolio-review-meta">
                        <span className="portfolio-comment-avatar" aria-hidden="true">
                          <User size={18} />
                        </span>
                        <strong>{comment.authorNickname}</strong>
                        <time dateTime={comment.updatedAt}>{formatDate(comment.updatedAt)}</time>
                      </div>
                      {editingId === comment.id ? (
                        <div className="portfolio-reply-editor">
                          <textarea
                            aria-label="댓글 수정"
                            aria-describedby={`portfolio-comment-edit-hint-${comment.id}`}
                            aria-invalid={Boolean(editingValidationMessage)}
                            rows={4}
                            maxLength={1000}
                            value={editingContent}
                            onChange={(event) => setEditingContent(event.target.value)}
                          />
                          <small
                            id={`portfolio-comment-edit-hint-${comment.id}`}
                            className={editingValidationMessage ? 'is-error' : undefined}
                          >
                            {editingValidationMessage || '2자 이상 입력해 주세요.'}
                          </small>
                          <div className="portfolio-share-actions">
                            <button
                              type="button"
                              disabled={saving || editingInvalid}
                              onClick={() => void updateComment(comment.id)}
                            >
                              수정 저장
                            </button>
                            <button
                              className="is-secondary"
                              type="button"
                              disabled={saving}
                              onClick={() => setEditingId(null)}
                            >
                              취소
                            </button>
                          </div>
                        </div>
                      ) : (
                        <>
                          <p>{comment.content}</p>
                          <div className="portfolio-reply-controls">
                            <button
                              type="button"
                              disabled={saving}
                              onClick={() => {
                                setEditingId(comment.id);
                                setEditingContent(comment.content);
                              }}
                            >
                              수정
                            </button>
                            <button
                              type="button"
                              disabled={saving}
                              onClick={() => void removeComment(comment.id)}
                            >
                              삭제
                            </button>
                          </div>
                        </>
                      )}
                      <PortfolioAnalysisReplyThread
                        comment={comment}
                        canReply
                        createReply={(replyContent) =>
                          savePortfolioAnalysisCommenterReply(token, comment.id, replyContent)
                        }
                        onChange={(nextComment) =>
                          setShare((current) =>
                            current
                              ? {
                                  ...current,
                                  myComments: current.myComments.map((value) =>
                                    value.id === nextComment.id ? nextComment : value,
                                  ),
                                }
                              : current,
                          )
                        }
                      />
                    </li>
                  ))}
                </ul>
              ) : null}
            </>
          ) : share.ownerShare ? (
            <>
              {share.ownerShare.comments.length ? (
                <ul className="portfolio-share-reviews" aria-label="받은 의견 목록">
                  {share.ownerShare.comments.map((comment) => (
                    <li key={comment.id}>
                      <div className="portfolio-review-meta">
                        <span className="portfolio-comment-avatar" aria-hidden="true">
                          <User size={18} />
                        </span>
                        <strong>{comment.authorNickname}</strong>
                        <time dateTime={comment.updatedAt}>{formatDate(comment.updatedAt)}</time>
                      </div>
                      <p>{comment.content}</p>
                      <PortfolioAnalysisReplyThread
                        comment={comment}
                        canReply
                        reviewAction={
                          <PortfolioReviewSimulation
                            shareId={share.ownerShare!.id}
                            comment={comment}
                            proposal={
                              proposals.find((proposal) => proposal.commentId === comment.id) ??
                              null
                            }
                            onProposalSaved={rememberProposal}
                            onAnalysisComplete={showAnalysisResult}
                          />
                        }
                        createReply={(replyContent) =>
                          savePortfolioAnalysisOwnerReply(
                            share.ownerShare!.id,
                            comment.id,
                            replyContent,
                          )
                        }
                        onChange={(nextComment) =>
                          setShare((current) =>
                            current?.ownerShare
                              ? {
                                  ...current,
                                  ownerShare: {
                                    ...current.ownerShare,
                                    comments: current.ownerShare.comments.map((value) =>
                                      value.id === nextComment.id ? nextComment : value,
                                    ),
                                  },
                                }
                              : current,
                          )
                        }
                      />
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="portfolio-share-empty">
                  아직 받은 의견이 없어요. 의견이 도착하면 이곳에서 답글을 달 수 있어요.
                </p>
              )}
            </>
          ) : (
            <p className="portfolio-public-owner-note">
              이 링크에서는 의견을 남길 수 없어요. 공유된 분석은 계속 확인할 수 있어요.
            </p>
          )}
        </section>
      </div>
    </main>
  );
}
