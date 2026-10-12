import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { useLocation } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { trackEvent } from '../../analytics';
import { createFeedback } from './api';
import './feedback.css';

function MessageIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M6.8 18.2 3.5 20l.9-3.8A8.2 8.2 0 1 1 6.8 18.2Z" />
      <path d="M8 10.8h8M8 14h5" />
    </svg>
  );
}

function getErrorMessage(error: unknown) {
  return error instanceof ApiError || error instanceof Error
    ? error.message
    : '피드백을 보내지 못했습니다. 잠시 후 다시 시도해 주세요.';
}

export function FeedbackWidget() {
  const location = useLocation();
  const triggerRef = useRef<HTMLButtonElement>(null);
  const dialogRef = useRef<HTMLDialogElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const [open, setOpen] = useState(false);
  const [content, setContent] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  function closeDialog() {
    if (!submitting) dialogRef.current?.close();
  }

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;

    if (open && !dialog.open) {
      dialog.showModal();
      textareaRef.current?.focus();
    } else if (!open && dialog.open) {
      dialog.close();
    }
  }, [open]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmedContent = content.trim();
    if (!trimmedContent) {
      setErrorMessage('피드백 내용을 입력해 주세요.');
      textareaRef.current?.focus();
      return;
    }

    setSubmitting(true);
    setErrorMessage(null);
    try {
      await createFeedback(trimmedContent, location.pathname.slice(0, 2048));
      setContent('');
      setSubmitted(true);
      trackEvent('submit_feedback');
    } catch (error) {
      setErrorMessage(getErrorMessage(error));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      <button
        ref={triggerRef}
        type="button"
        className="feedback-trigger"
        aria-label="피드백 보내기"
        aria-haspopup="dialog"
        aria-expanded={open}
        onClick={() => {
          setSubmitted(false);
          setErrorMessage(null);
          setOpen(true);
          trackEvent('click_feedback_open');
        }}
      >
        <MessageIcon />
        <span>피드백</span>
      </button>

      <dialog
        ref={dialogRef}
        className="feedback-dialog"
        aria-labelledby="feedback-title"
        onCancel={(event) => {
          event.preventDefault();
          closeDialog();
        }}
        onClose={() => {
          setOpen(false);
          setSubmitted(false);
          setErrorMessage(null);
          triggerRef.current?.focus();
        }}
        onClick={(event) => {
          if (event.target === event.currentTarget) closeDialog();
        }}
      >
        <div className="feedback-dialog-panel">
          {submitted ? (
            <div className="feedback-success" role="status" aria-live="polite">
              <span aria-hidden="true">✓</span>
              <h2 id="feedback-title">의견을 보내주셔서 감사합니다</h2>
              <p>보내주신 피드백은 서비스 개선에 꼼꼼히 반영하겠습니다.</p>
              <button type="button" onClick={closeDialog}>
                닫기
              </button>
            </div>
          ) : (
            <>
              <div className="feedback-dialog-heading">
                <div>
                  <h2 id="feedback-title">DAYNOMY에 의견 보내기</h2>
                  <p>불편한 점이나 필요한 기능을 자유롭게 알려주세요.</p>
                </div>
                <button
                  type="button"
                  className="feedback-close"
                  aria-label="피드백 창 닫기"
                  disabled={submitting}
                  onClick={closeDialog}
                >
                  <svg viewBox="0 0 24 24" aria-hidden="true">
                    <path d="m6 6 12 12M18 6 6 18" />
                  </svg>
                </button>
              </div>

              <form className="feedback-form" onSubmit={handleSubmit}>
                <label htmlFor="feedback-content">피드백 내용</label>
                <textarea
                  ref={textareaRef}
                  id="feedback-content"
                  value={content}
                  onChange={(event) => {
                    setContent(event.target.value);
                    if (errorMessage) setErrorMessage(null);
                  }}
                  placeholder="어떤 점을 개선하면 좋을까요?"
                  maxLength={1000}
                  required
                  disabled={submitting}
                  aria-describedby="feedback-count"
                />
                <div className="feedback-form-meta">
                  <span id="feedback-count">{content.length.toLocaleString('ko-KR')} / 1,000</span>
                  {errorMessage ? <p role="alert">{errorMessage}</p> : null}
                </div>
                <button type="submit" className="feedback-submit" disabled={submitting}>
                  {submitting ? '보내는 중…' : '피드백 보내기'}
                </button>
              </form>
            </>
          )}
        </div>
      </dialog>
    </>
  );
}
