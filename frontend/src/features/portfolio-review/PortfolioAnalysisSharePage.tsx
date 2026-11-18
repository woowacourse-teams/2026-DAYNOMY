import { useEffect, useMemo, useState } from 'react';
import { ChatCircleText, LinkBreak, ShieldCheck, User } from '@phosphor-icons/react';
import { Link, useLocation } from 'react-router-dom';
import { trackEvent } from '../../analytics';
import { ApiError } from '../../api/client';
import { useAuth } from '../../hooks/useLoginStatus';
import {
  createPortfolioAnalysisComment,
  deletePortfolioAnalysisComment,
  getPublicPortfolioAnalysisShare,
  savePortfolioAnalysisCommenterReply,
  savePortfolioAnalysisOwnerReply,
  updatePortfolioAnalysisComment,
} from './api';
import { PortfolioAnalysisReplyThread } from './PortfolioAnalysisReplyThread';
import type { PortfolioAnalysisPublicShare } from './types';
import { PortfolioReviewSimulation } from './PortfolioReviewSimulation';
import { PortfolioReviewAnalysisResult } from './PortfolioReviewAnalysisResult';
import './portfolioAnalysisShare.css';
import './portfolioPublicDocument.css';

const ALLOCATION_COLORS = ['#31588a', '#8b7bc8', '#2a8a72', '#b77c25', '#64748b'];

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

function formatDateOnly(value: string | null) {
  return value ? value.replaceAll('-', '.') : '기록 없음';
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
  const { isLoggedIn, loading: authLoading, memberId } = useAuth();
  const token = useMemo(() => location.hash.replace(/^#/, ''), [location.hash]);
  const [share, setShare] = useState<PortfolioAnalysisPublicShare | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [content, setContent] = useState('');
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editingContent, setEditingContent] = useState('');
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState('');

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
    if (!token || token.length > 100) {
      setError('공유된 분석을 찾을 수 없어요.');
      setLoading(false);
      return;
    }
    const controller = new AbortController();
    setLoading(true);
    setError('');
    getPublicPortfolioAnalysisShare(token, controller.signal)
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
  }, [authLoading, isLoggedIn, memberId, token]);

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
    <main className="portfolio-public-share-page">
      <header className="portfolio-public-share-hero">
        <div className="portfolio-public-share-owner">
          <span className="portfolio-public-share-avatar" aria-hidden="true">
            {ownerNickname.slice(0, 1)}
          </span>
          <div>
            <strong>{ownerNickname}</strong>
          </div>
          <time dateTime={share.expiresAt} className="portfolio-public-expiry">
            링크 공개 기한 · {formatExpiryDate(share.expiresAt)}까지
          </time>
        </div>
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
      </header>

      <div className="portfolio-public-content">
        <div className="portfolio-public-main">
          <section
            className="portfolio-public-assets"
            aria-labelledby="portfolio-public-assets-title"
          >
            <div className="portfolio-public-section-heading">
              <div>
                <h2 id="portfolio-public-assets-title">
                  공개한 종목 <span className="portfolio-public-count">{impacts.length}개</span>
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
            {impacts.length > 0 ? (
              <div className="portfolio-public-allocation">
                <p>공유 시점의 종목별 비중</p>
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
                    />
                  ))}
                </div>
                {!share.analysis.allAssetsShared ? (
                  <p>전체 포트폴리오 기준 비중이며, 선택한 종목만 표시해요.</p>
                ) : null}
              </div>
            ) : null}
            <div className="portfolio-public-asset-list">
              {impacts.map((impact, index) => {
                const holding = impact.holding;
                const returnRate = holding?.returnRate;
                return (
                  <article key={impact.assetName}>
                    <header>
                      <span
                        className="portfolio-public-asset-color"
                        aria-hidden="true"
                        style={{
                          backgroundColor: ALLOCATION_COLORS[index % ALLOCATION_COLORS.length],
                        }}
                      />
                      <div>
                        <h3>{impact.assetName}</h3>
                        <span>
                          {[holding?.assetCode, holding?.market].filter(Boolean).join(' · ') ||
                            '종목 정보'}
                        </span>
                      </div>
                    </header>
                    <dl className="portfolio-public-holding-metrics">
                      <div>
                        <dt>비중</dt>
                        <dd>{impact.weight}%</dd>
                      </div>
                      <div>
                        <dt>최초 매수일</dt>
                        <dd className={holding?.firstPurchasedOn ? undefined : 'is-muted'}>
                          {formatDateOnly(holding?.firstPurchasedOn ?? null)}
                        </dd>
                      </div>
                      <div
                        className={
                          returnRate == null
                            ? ''
                            : returnRate > 0
                              ? 'is-gain'
                              : returnRate < 0
                                ? 'is-loss'
                                : ''
                        }
                      >
                        <dt>수익률</dt>
                        <dd>
                          {returnRate == null
                            ? '확인 불가'
                            : `${returnRate > 0 ? '+' : ''}${returnRate.toFixed(2)}%`}
                        </dd>
                      </div>
                    </dl>
                  </article>
                );
              })}
            </div>
            {share.analysis.hasAnalysis && share.analysis.analyzedAt ? (
              <p className="portfolio-public-share-analyzed">
                AI 분석 기준{' '}
                <time dateTime={share.analysis.analyzedAt}>
                  {formatDate(share.analysis.analyzedAt)}
                </time>
              </p>
            ) : null}
            {share.analysis.excludedAssets?.length ? (
              <p className="portfolio-public-excluded">
                분석 예산에 따라 {share.analysis.excludedAssets.length}개 종목은 분석 대상에서
                제외됐어요.
              </p>
            ) : null}
          </section>

          {share.analysis.hasAnalysis ? (
            <PortfolioReviewAnalysisResult
              analysis={share.analysis}
              title={share.analysis.allAssetsShared ? 'AI 포트폴리오 분석' : '공개 종목 분석'}
              showOverall={share.analysis.allAssetsShared}
            />
          ) : null}
        </div>

        <section
          className={`portfolio-public-review${share.ownerShare ? ' is-owner' : ''}`}
          aria-labelledby="portfolio-public-review-title"
        >
          <header className="portfolio-public-review-heading">
            <h2 id="portfolio-public-review-title">
              {share.ownerShare ? `받은 의견 ${share.ownerShare.comments.length}개` : '의견 남기기'}
            </h2>
            {share.ownerShare ? (
              <p className="portfolio-public-owner-note">댓글 작성자와 나만 볼 수 있는 대화예요.</p>
            ) : null}
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
                    placeholder="비중이나 매수 시점에 대한 생각을 적어 주세요."
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
                      {saving ? '저장 중' : '의견 남기기'}
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
