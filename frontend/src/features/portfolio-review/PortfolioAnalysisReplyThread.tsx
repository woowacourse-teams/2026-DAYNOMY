import { useId, useRef, useState, type ReactNode } from 'react';
import { CaretDownIcon } from '@phosphor-icons/react/dist/csr/CaretDown';
import { DotsThreeIcon } from '@phosphor-icons/react/dist/csr/DotsThree';
import { UserIcon } from '@phosphor-icons/react/dist/csr/User';
import { ApiError } from '../../api/client';
import { deletePortfolioAnalysisReply, updatePortfolioAnalysisReply } from './api';
import type { PortfolioAnalysisComment } from './types';

const MIN_REPLY_LENGTH = 2;
const MAX_REPLY_LENGTH = 1000;

function formatDate(value: string) {
  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value));
}

function invalid(content: string) {
  const normalized = content.trim();
  return (
    normalized.length < MIN_REPLY_LENGTH ||
    normalized.length > MAX_REPLY_LENGTH ||
    /[<>]/.test(normalized)
  );
}

export function PortfolioAnalysisReplyThread({
  comment,
  canReply,
  createReply,
  onChange,
  reviewAction,
  initiallyExpanded = true,
}: {
  comment: PortfolioAnalysisComment;
  canReply: boolean;
  createReply: (content: string) => Promise<PortfolioAnalysisComment>;
  onChange: (comment: PortfolioAnalysisComment) => void;
  reviewAction?: ReactNode;
  initiallyExpanded?: boolean;
}) {
  const composeId = useId();
  const repliesId = useId();
  const toggleRef = useRef<HTMLButtonElement>(null);
  const [composing, setComposing] = useState(false);
  const [expanded, setExpanded] = useState(initiallyExpanded);
  const [draft, setDraft] = useState('');
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editingContent, setEditingContent] = useState('');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');

  async function submit() {
    if (pending || invalid(draft)) return;
    setPending(true);
    setError('');
    try {
      onChange(await createReply(draft.trim()));
      setExpanded(true);
      setDraft('');
      setComposing(false);
      toggleRef.current?.focus();
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : '답글을 저장하지 못했어요.');
    } finally {
      setPending(false);
    }
  }

  async function update() {
    if (editingId === null || pending || invalid(editingContent)) return;
    setPending(true);
    setError('');
    try {
      onChange(await updatePortfolioAnalysisReply(editingId, editingContent.trim()));
      setEditingId(null);
      setEditingContent('');
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : '답글을 수정하지 못했어요.');
    } finally {
      setPending(false);
    }
  }

  async function remove(replyId: number) {
    if (pending || !window.confirm('이 답글을 삭제할까요?')) return;
    setPending(true);
    setError('');
    try {
      onChange(await deletePortfolioAnalysisReply(replyId));
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : '답글을 삭제하지 못했어요.');
    } finally {
      setPending(false);
    }
  }

  return (
    <div className="portfolio-reply-thread">
      <div className="portfolio-reply-actions">
        <div className="portfolio-reply-primary-actions">
          {comment.replies.length ? (
            <button
              type="button"
              className="portfolio-reply-disclosure"
              aria-expanded={expanded}
              aria-controls={repliesId}
              onClick={() => setExpanded((current) => !current)}
            >
              <CaretDownIcon className="portfolio-reply-caret" size={16} weight="bold" />
              답글 {comment.replies.length}개
            </button>
          ) : null}
          {canReply ? (
            <button
              ref={toggleRef}
              type="button"
              className="portfolio-reply-toggle"
              aria-expanded={composing}
              aria-controls={composing ? composeId : undefined}
              onClick={() => {
                setComposing((current) => !current);
                setExpanded(true);
              }}
            >
              {composing ? '답글 접기' : '답글 달기'}
            </button>
          ) : null}
        </div>
      </div>

      {comment.replies.length && expanded ? (
        <ul id={repliesId} aria-label="답글 목록">
          {comment.replies.map((reply) => (
            <li key={reply.id}>
              <span className="portfolio-reply-avatar" aria-hidden="true">
                <UserIcon size={16} />
              </span>
              <div className="portfolio-reply-meta">
                <strong>
                  <span className="portfolio-reply-author-name">{reply.authorNickname}</span>
                  {reply.fromOwner ? (
                    <span className="portfolio-reply-owner-badge">작성자</span>
                  ) : null}
                </strong>
                <time dateTime={reply.updatedAt}>{formatDate(reply.updatedAt)}</time>
              </div>
              {editingId === reply.id ? (
                <div className="portfolio-reply-editor">
                  <textarea
                    aria-label={`${reply.authorNickname} 답글 수정`}
                    rows={3}
                    maxLength={MAX_REPLY_LENGTH}
                    value={editingContent}
                    onChange={(event) => setEditingContent(event.target.value)}
                  />
                  <div className="portfolio-share-actions">
                    <button
                      type="button"
                      disabled={pending || invalid(editingContent)}
                      onClick={() => void update()}
                    >
                      수정 저장
                    </button>
                    <button
                      className="is-secondary"
                      type="button"
                      disabled={pending}
                      onClick={() => setEditingId(null)}
                    >
                      취소
                    </button>
                  </div>
                </div>
              ) : (
                <>
                  <p>{reply.content}</p>
                  {reply.editable ? (
                    <details className="portfolio-reply-menu">
                      <summary role="button">
                        <DotsThreeIcon size={20} weight="bold" />
                        <span className="portfolio-reply-menu-label">
                          {reply.authorNickname} 답글 관리
                        </span>
                      </summary>
                      <div className="portfolio-reply-controls">
                        <button
                          type="button"
                          disabled={pending}
                          onClick={() => {
                            setEditingId(reply.id);
                            setEditingContent(reply.content);
                          }}
                        >
                          수정
                        </button>
                        <button
                          type="button"
                          disabled={pending}
                          onClick={() => void remove(reply.id)}
                        >
                          삭제
                        </button>
                      </div>
                    </details>
                  ) : null}
                </>
              )}
            </li>
          ))}
        </ul>
      ) : null}

      {canReply && composing ? (
        <div id={composeId} className="portfolio-reply-compose">
          <label>
            <span>답글 남기기</span>
            <textarea
              autoFocus
              aria-label={`${comment.authorNickname}님 댓글에 답글`}
              rows={2}
              maxLength={MAX_REPLY_LENGTH}
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              placeholder="의견에 대한 생각을 남겨 주세요."
            />
          </label>
          <div className="portfolio-reply-compose-footer">
            <small>작성자와 이 의견을 남긴 사람만 볼 수 있어요.</small>
            <button
              type="button"
              disabled={pending || invalid(draft)}
              onClick={() => void submit()}
            >
              {pending ? '저장 중' : '답글 등록'}
            </button>
          </div>
        </div>
      ) : null}
      {reviewAction ? <div className="portfolio-reply-secondary-action">{reviewAction}</div> : null}
      {error ? (
        <p className="portfolio-share-error" role="alert">
          {error}
        </p>
      ) : null}
    </div>
  );
}
