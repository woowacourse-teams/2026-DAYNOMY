import { CopyIcon } from '@phosphor-icons/react/dist/csr/Copy';
import { LinkSimpleIcon } from '@phosphor-icons/react/dist/csr/LinkSimple';
import { ShieldCheckIcon } from '@phosphor-icons/react/dist/csr/ShieldCheck';
import { XIcon } from '@phosphor-icons/react/dist/csr/X';
import { UserIcon } from '@phosphor-icons/react/dist/csr/User';
import { useEffect, useRef, useState } from 'react';
import { trackEvent } from '../../analytics';
import { ApiError } from '../../api/client';
import { PortfolioDialog } from '../portfolio/components/PortfolioDialog';
import type { PortfolioAsset, PortfolioHoldingResult } from '../portfolio/types';
import {
  createPortfolioAnalysisShare,
  deletePortfolioAnalysisShare,
  getPortfolioAnalysisShares,
  revokePortfolioAnalysisShare,
  savePortfolioAnalysisOwnerReply,
} from './api';
import { PortfolioAnalysisReplyThread } from './PortfolioAnalysisReplyThread';
import { PortfolioShareExplanation } from './PortfolioShareExplanation';
import type { PortfolioAnalysisOwnerShare } from './types';
import './portfolioAnalysisShare.css';
import './portfolioPublicDocument.css';

const MAX_QUESTION_LENGTH = 200;
const PREVIEW_COLORS = ['#ef464d', '#31588a', '#e9a23b', '#6f63b7', '#2a8a72'];

function formatDate(value: string) {
  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value));
}

function messageFrom(error: unknown, fallback: string) {
  return error instanceof ApiError ? error.message : fallback;
}

function previewGradient(assets: PortfolioAsset[]) {
  if (assets.length === 0) return '#e7ebf1';
  let start = 0;
  const segments = assets.map((asset, index) => {
    const end = start + asset.weight;
    const segment = `${PREVIEW_COLORS[index % PREVIEW_COLORS.length]} ${start}% ${end}%`;
    start = end;
    return segment;
  });
  if (start < 100) segments.push(`#e7ebf1 ${start}% 100%`);
  return `conic-gradient(${segments.join(', ')})`;
}

function replaceShare(
  values: PortfolioAnalysisOwnerShare[] | null,
  next: PortfolioAnalysisOwnerShare,
) {
  return [next, ...(values ?? []).filter((value) => value.id !== next.id)];
}

function replaceComment(
  values: PortfolioAnalysisOwnerShare[] | null,
  shareId: number,
  nextComment: PortfolioAnalysisOwnerShare['comments'][number],
) {
  return (values ?? []).map((share) =>
    share.id === shareId
      ? {
          ...share,
          comments: share.comments.map((comment) =>
            comment.id === nextComment.id ? nextComment : comment,
          ),
        }
      : share,
  );
}

export function PortfolioAnalysisSharePanel({
  analysisId,
  assets,
  holdings = [],
}: {
  analysisId: number | null;
  assets: PortfolioAsset[];
  holdings?: PortfolioHoldingResult[];
}) {
  const [open, setOpen] = useState(false);

  return (
    <>
      <button
        className="portfolio-analysis-button portfolio-analysis-share-trigger"
        type="button"
        onClick={() => {
          trackEvent('portfolio_analysis_share_opened', { surface: 'portfolio_analysis' });
          setOpen(true);
        }}
      >
        <LinkSimpleIcon size={16} aria-hidden="true" />
        의견 받기
      </button>
      {open ? (
        <ShareDialog
          analysisId={analysisId}
          assets={assets}
          holdings={holdings}
          onClose={() => setOpen(false)}
        />
      ) : null}
    </>
  );
}

function ShareDialog({
  analysisId,
  assets,
  holdings,
  onClose,
}: {
  analysisId: number | null;
  assets: PortfolioAsset[];
  holdings: PortfolioHoldingResult[];
  onClose: () => void;
}) {
  const [shares, setShares] = useState<PortfolioAnalysisOwnerShare[] | null>(null);
  const [question, setQuestion] = useState('');
  const [selectedAssetNames, setSelectedAssetNames] = useState(() =>
    assets.map((asset) => asset.assetName),
  );
  const [shareUrl, setShareUrl] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const linkRef = useRef<HTMLInputElement>(null);
  const current = shares?.find((share) => share.analysisId === analysisId) ?? null;
  const selectedAssets = assets.filter((asset) => selectedAssetNames.includes(asset.assetName));
  const holdingByName = new Map(holdings.map((holding) => [holding.name, holding]));

  useEffect(() => {
    const controller = new AbortController();
    getPortfolioAnalysisShares(controller.signal)
      .then((values) => {
        if (controller.signal.aborted) return;
        setShares(values);
        const existing = values.find((share) => share.analysisId === analysisId);
        setQuestion(existing?.question ?? '');
        setSelectedAssetNames(
          existing?.sharedAssets.map((asset) => asset.assetName) ??
            assets.map((asset) => asset.assetName),
        );
      })
      .catch((caught: unknown) => {
        if (!controller.signal.aborted)
          setError(messageFrom(caught, '의견 요청 내역을 불러오지 못했어요.'));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [analysisId, assets]);

  const normalizedQuestion = question.trim();
  const questionInvalid =
    normalizedQuestion.length === 0 ||
    normalizedQuestion.length > MAX_QUESTION_LENGTH ||
    /[<>]/.test(normalizedQuestion);

  async function issueLink() {
    if (questionInvalid || selectedAssetNames.length === 0 || saving) return;
    setSaving(true);
    setError('');
    setNotice('');
    try {
      const created = await createPortfolioAnalysisShare(
        analysisId,
        normalizedQuestion,
        selectedAssetNames,
      );
      const url = `${window.location.origin}/portfolio/analysis/share#${created.token}`;
      setShares((values) => replaceShare(values, created.share));
      setShareUrl(url);
      setNotice('7일 동안 사용할 수 있는 링크를 발급했어요.');
      trackEvent('portfolio_analysis_share_created', { surface: 'portfolio_analysis' });
    } catch (caught) {
      setError(messageFrom(caught, '공유 링크를 발급하지 못했어요.'));
    } finally {
      setSaving(false);
    }
  }

  async function copyLink() {
    if (!shareUrl) return;
    try {
      if (navigator.clipboard?.writeText) await navigator.clipboard.writeText(shareUrl);
      else {
        linkRef.current?.select();
        document.execCommand('copy');
      }
      setNotice('공유 링크를 복사했어요.');
      trackEvent('portfolio_analysis_share_copied', { surface: 'portfolio_analysis' });
    } catch {
      setError('링크를 복사하지 못했어요. 직접 선택해 복사해 주세요.');
    }
  }

  async function revoke(share: PortfolioAnalysisOwnerShare) {
    if (saving) return;
    setSaving(true);
    setError('');
    try {
      const revoked = await revokePortfolioAnalysisShare(share.id);
      setShares((values) => replaceShare(values, revoked));
      if (share.id === current?.id) setShareUrl('');
      setNotice('공유를 중단했어요. 받은 의견은 유지됩니다.');
    } catch (caught) {
      setError(messageFrom(caught, '공유를 중단하지 못했어요.'));
    } finally {
      setSaving(false);
    }
  }

  async function remove(share: PortfolioAnalysisOwnerShare) {
    if (saving) return;
    if (!window.confirm('의견 요청과 받은 의견을 모두 삭제할까요? 이 작업은 되돌릴 수 없어요.'))
      return;
    setSaving(true);
    setError('');
    try {
      await deletePortfolioAnalysisShare(share.id);
      setShares((values) => (values ?? []).filter((value) => value.id !== share.id));
      if (share.id === current?.id) {
        setQuestion('');
        setShareUrl('');
      }
      setNotice('의견 요청을 삭제했어요.');
    } catch (caught) {
      setError(messageFrom(caught, '의견 요청을 삭제하지 못했어요.'));
    } finally {
      setSaving(false);
    }
  }

  return (
    <PortfolioDialog
      titleId="portfolio-analysis-share-title"
      onClose={onClose}
      busy={saving}
      className="portfolio-analysis-share-dialog"
    >
      <header className="portfolio-share-dialog-header">
        <div>
          <h2 id="portfolio-analysis-share-title">내 포트폴리오 의견 받기</h2>
          <p>보여줄 종목을 고르고 궁금한 점을 적으면 7일 링크가 만들어져요.</p>
        </div>
        <button
          className="portfolio-share-close"
          type="button"
          onClick={onClose}
          disabled={saving}
          aria-label="닫기"
        >
          <XIcon size={22} aria-hidden="true" />
        </button>
      </header>

      {loading ? <p role="status">의견 요청 내역을 불러오고 있어요.</p> : null}
      {error ? (
        <p className="portfolio-share-error" role="alert">
          {error}
        </p>
      ) : null}
      {notice ? (
        <p className="portfolio-share-notice" role="status">
          {notice}
        </p>
      ) : null}

      {!loading ? (
        <div className="portfolio-share-dialog-body">
          <div className="portfolio-share-builder">
            <aside className="portfolio-share-preview" aria-label="공개 화면 미리보기">
              <div className="portfolio-share-preview-heading">
                <strong>링크 미리보기</strong>
                <span>{selectedAssets.length}개 종목</span>
              </div>
              <div className="portfolio-share-preview-chart-row">
                <div
                  className="portfolio-share-preview-chart"
                  style={{ background: previewGradient(selectedAssets) }}
                  role="img"
                  aria-label={`선택한 종목 비중 ${selectedAssets.map((asset) => `${asset.assetName} ${asset.weight}%`).join(', ')}`}
                >
                  <span>
                    {selectedAssets.reduce((sum, asset) => sum + asset.weight, 0).toFixed(0)}%
                  </span>
                </div>
                <ul>
                  {selectedAssets.slice(0, 5).map((asset, index) => (
                    <li key={asset.assetName}>
                      <i style={{ background: PREVIEW_COLORS[index % PREVIEW_COLORS.length] }} />
                      <span>{asset.assetName}</span>
                      <strong>{asset.weight}%</strong>
                    </li>
                  ))}
                </ul>
              </div>
              {selectedAssets[0] ? (
                <div className="portfolio-share-preview-holding">
                  <div>
                    <strong>{selectedAssets[0].assetName}</strong>
                    <span>{holdingByName.get(selectedAssets[0].assetName)?.assetCode}</span>
                  </div>
                  <dl>
                    <div>
                      <dt>비중</dt>
                      <dd>{selectedAssets[0].weight}%</dd>
                    </div>
                  </dl>
                </div>
              ) : (
                <p className="portfolio-share-preview-empty">공개할 종목을 선택해 주세요.</p>
              )}
              <div className="portfolio-share-privacy">
                <ShieldCheckIcon size={20} weight="fill" aria-hidden="true" />
                <p>
                  <strong>링크에 공개되는 정보</strong>
                  비중, 매수일, 수익률만 담아요. 매수가와 가격, 수량과 총 투자금은 숨겨져요.
                </p>
              </div>
            </aside>

            <div className="portfolio-share-controls">
              <label className="portfolio-share-question">
                <span>어떤 점이 궁금한가요?</span>
                <textarea
                  data-autofocus
                  aria-label="어떤 점이 궁금한가요?"
                  value={question}
                  maxLength={MAX_QUESTION_LENGTH}
                  rows={3}
                  onChange={(event) => setQuestion(event.target.value)}
                  aria-invalid={Boolean(question) && questionInvalid}
                  placeholder="예: 지금 비중을 그대로 유지해도 괜찮을까?"
                />
                <small>
                  {normalizedQuestion.length}/{MAX_QUESTION_LENGTH}자
                </small>
              </label>

              <fieldset className="portfolio-share-assets">
                <legend>공개할 종목</legend>
                <div className="portfolio-share-assets-heading">
                  <span>
                    {selectedAssetNames.length}/{assets.length}개 선택
                  </span>
                  <button
                    type="button"
                    onClick={() =>
                      setSelectedAssetNames(
                        selectedAssetNames.length === assets.length
                          ? []
                          : assets.map((asset) => asset.assetName),
                      )
                    }
                  >
                    {selectedAssetNames.length === assets.length ? '전체 해제' : '전체 선택'}
                  </button>
                </div>
                <div className="portfolio-share-asset-options">
                  {assets.map((asset) => (
                    <label key={asset.assetName}>
                      <input
                        type="checkbox"
                        checked={selectedAssetNames.includes(asset.assetName)}
                        onChange={() =>
                          setSelectedAssetNames((currentNames) =>
                            currentNames.includes(asset.assetName)
                              ? currentNames.filter((name) => name !== asset.assetName)
                              : [...currentNames, asset.assetName],
                          )
                        }
                      />
                      <span>{asset.assetName}</span>
                      <small>{asset.weight}%</small>
                    </label>
                  ))}
                </div>
                {selectedAssetNames.length === 0 ? (
                  <p className="portfolio-share-error">공개할 종목을 한 개 이상 선택해 주세요.</p>
                ) : analysisId !== null && selectedAssetNames.length < assets.length ? (
                  <p className="portfolio-share-hint">
                    일부 종목만 공개하면 전체 포트폴리오 점수와 종합 요약은 숨겨져요.
                  </p>
                ) : null}
              </fieldset>

              <div className="portfolio-share-actions portfolio-share-primary-action">
                <button
                  type="button"
                  onClick={() => void issueLink()}
                  disabled={questionInvalid || selectedAssetNames.length === 0 || saving}
                >
                  <LinkSimpleIcon size={18} weight="bold" aria-hidden="true" />
                  {saving
                    ? '링크 만드는 중'
                    : current?.status === 'ACTIVE'
                      ? '새 링크 만들기'
                      : '7일 링크 만들기'}
                </button>
                {current?.status === 'ACTIVE' && !shareUrl ? (
                  <p className="portfolio-share-hint">
                    새 링크를 발급하면 이전 링크는 바로 사용할 수 없어요.
                  </p>
                ) : null}
              </div>

              {shareUrl ? (
                <div className="portfolio-share-link">
                  <label htmlFor="portfolio-share-url">공유 링크</label>
                  <div>
                    <input ref={linkRef} id="portfolio-share-url" readOnly value={shareUrl} />
                    <button type="button" onClick={() => void copyLink()}>
                      <CopyIcon size={18} weight="bold" aria-hidden="true" />
                      링크 복사
                    </button>
                  </div>
                </div>
              ) : null}
            </div>
          </div>

          {shares?.length ? (
            <details className="portfolio-share-history">
              <summary id="portfolio-share-history-title">
                이전 의견 요청 <span>{shares.length}</span>
              </summary>
              <ul>
                {shares.map((share) => (
                  <li key={share.id}>
                    <div className={`portfolio-share-status is-${share.status.toLowerCase()}`}>
                      <strong>
                        {share.status === 'ACTIVE'
                          ? '공유 중'
                          : share.status === 'EXPIRED'
                            ? '공유 만료'
                            : '공유 중단'}
                      </strong>
                      <span>만료 {formatDate(share.expiresAt)}</span>
                      <span>받은 댓글 {share.comments.length}건</span>
                    </div>
                    <p className="portfolio-share-history-question">{share.question}</p>
                    <PortfolioShareExplanation
                      share={share}
                      disabled={saving}
                      onChange={(updated) => setShares((values) => replaceShare(values, updated))}
                    />
                    <p className="portfolio-share-shared-assets">
                      공개 종목: {share.sharedAssets.map((asset) => asset.assetName).join(', ')}
                    </p>
                    <div className="portfolio-share-actions">
                      {share.status === 'ACTIVE' ? (
                        <button
                          className="is-secondary"
                          type="button"
                          onClick={() => void revoke(share)}
                          disabled={saving}
                          aria-label={`${share.question} 공유 중단`}
                        >
                          공유 중단
                        </button>
                      ) : null}
                      <button
                        className="is-danger"
                        type="button"
                        onClick={() => void remove(share)}
                        disabled={saving}
                        aria-label={`${share.question} 영구 삭제`}
                      >
                        영구 삭제
                      </button>
                    </div>
                    {share.comments.length ? (
                      <section
                        className="portfolio-public-review is-owner"
                        aria-labelledby={`portfolio-share-comments-${share.id}`}
                      >
                        <header className="portfolio-public-review-heading">
                          <h3 id={`portfolio-share-comments-${share.id}`}>
                            받은 의견 {share.comments.length}개
                          </h3>
                          <p className="portfolio-public-owner-note">
                            댓글 작성자와 나만 볼 수 있는 대화예요.
                          </p>
                        </header>
                        <ul className="portfolio-share-reviews" aria-label="받은 의견 목록">
                          {share.comments.map((comment) => (
                            <li key={comment.id}>
                              <div className="portfolio-review-meta">
                                <span className="portfolio-comment-avatar" aria-hidden="true">
                                  <UserIcon size={18} />
                                </span>
                                <strong>{comment.authorNickname}</strong>
                                <time dateTime={comment.updatedAt}>
                                  {formatDate(comment.updatedAt)}
                                </time>
                              </div>
                              <p>{comment.content}</p>
                              <PortfolioAnalysisReplyThread
                                comment={comment}
                                canReply={share.status === 'ACTIVE'}
                                createReply={(content) =>
                                  savePortfolioAnalysisOwnerReply(share.id, comment.id, content)
                                }
                                onChange={(nextComment) =>
                                  setShares((values) =>
                                    replaceComment(values, share.id, nextComment),
                                  )
                                }
                              />
                            </li>
                          ))}
                        </ul>
                      </section>
                    ) : (
                      <p className="portfolio-share-empty">아직 받은 댓글이 없어요.</p>
                    )}
                  </li>
                ))}
              </ul>
            </details>
          ) : null}
        </div>
      ) : null}
    </PortfolioDialog>
  );
}
