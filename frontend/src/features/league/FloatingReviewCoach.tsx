import { useEffect, useId, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { InvestmentRecords } from './InvestmentRecords';
import { transactionLabels } from './labels';
import type { InvestmentDecisionRecord } from './types';
import './shared-portfolio.css';
import './floating-review-coach.css';

export function FloatingReviewCoach({
  record,
  reminderCount,
  onSaved,
  onDismiss,
}: {
  record: InvestmentDecisionRecord;
  reminderCount?: number;
  onSaved: (record: InvestmentDecisionRecord) => void;
  onDismiss?: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [visible, setVisible] = useState(true);
  const [reminderVisible, setReminderVisible] = useState(Boolean(reminderCount));
  const [done, setDone] = useState(false);
  const saved = useRef(false);
  const floating = useRef<HTMLElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const entry = useRef<HTMLButtonElement>(null);
  const heading = useRef<HTMLHeadingElement>(null);
  const restoreFocus = useRef<'entry' | 'trigger' | null>(null);
  const id = useId();
  useEffect(() => {
    const viewport = window.visualViewport;
    if (!open || !viewport) return;
    let frame = 0;
    const resize = () => {
      floating.current?.style.setProperty('--coach-viewport-height', `${viewport.height}px`);
      floating.current?.style.setProperty(
        '--coach-viewport-offset',
        `${Math.max(0, window.innerHeight - viewport.height - viewport.offsetTop)}px`,
      );
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => {
        const active = document.activeElement;
        if (active?.tagName === 'TEXTAREA' && floating.current?.contains(active))
          active.scrollIntoView({ block: 'nearest' });
      });
    };
    resize();
    viewport.addEventListener('resize', resize);
    viewport.addEventListener('scroll', resize);
    return () => {
      cancelAnimationFrame(frame);
      viewport.removeEventListener('resize', resize);
      viewport.removeEventListener('scroll', resize);
    };
  }, [open]);
  useEffect(() => {
    if (open) heading.current?.focus({ preventScroll: true });
    else if (restoreFocus.current) {
      (restoreFocus.current === 'entry' ? entry : trigger).current?.focus();
      restoreFocus.current = null;
    }
  }, [open, visible, done]);
  function show() {
    setReminderVisible(false);
    setVisible(true);
    setOpen(true);
  }
  function minimize() {
    if (done && onDismiss) {
      onDismiss();
      return;
    }
    restoreFocus.current = 'trigger';
    setOpen(false);
  }
  function hide() {
    restoreFocus.current = 'entry';
    setOpen(false);
    setVisible(false);
    onDismiss?.();
  }
  const panel = (
    <section
      id={`${id}-panel`}
      className="floating-review-panel"
      data-stage={done ? 'done' : 'writing'}
      hidden={!open}
      role="dialog"
      aria-labelledby={`${id}-title`}
      onKeyDown={(event) => {
        if (event.key === 'Escape') {
          event.stopPropagation();
          minimize();
        }
      }}
    >
      <header>
        <div className="floating-review-identity">
          <span className="review-coach-avatar" aria-hidden="true" />
          <div>
            <h2 id={`${id}-title`} ref={heading} tabIndex={-1}>
              {record.assetName} 복기
            </h2>
            <p>
              {record.tradedOn} · {transactionLabels[record.transactionType]}
            </p>
          </div>
        </div>
        <div className="floating-review-header-actions">
          <details className="floating-review-menu">
            <summary aria-label="복기 도우미 메뉴">⋯</summary>
            <div>
              <button type="button" onClick={hide} aria-label="복기 도우미 숨기기">
                화면에서 숨기기
              </button>
            </div>
          </details>
          <button
            type="button"
            onClick={minimize}
            aria-label="복기 도우미 접기"
            title="접어도 작성한 답변은 유지돼요"
          >
            −
          </button>
        </div>
      </header>
      <div
        className="floating-review-body"
        onFocus={(event) => {
          if (event.target instanceof HTMLTextAreaElement)
            event.target.scrollIntoView?.({ block: 'nearest' });
        }}
      >
        {done ? (
          <>
            <p className="shared-feedback" role="status">
              복기를 남겼어요.
            </p>
            <button className="shared-primary-button" type="button" onClick={hide}>
              기록으로 돌아가기
            </button>
          </>
        ) : (
          <InvestmentRecords
            records={[record]}
            reviewId={record.id}
            editorOnly
            autoGuide={open}
            setReviewId={(value) => {
              if (value === null && !saved.current) minimize();
            }}
            onRecordChange={(value) => {
              saved.current = true;
              setDone(true);
              onSaved(value);
            }}
          />
        )}
      </div>
    </section>
  );
  return (
    <>
      {!visible ? (
        <button
          ref={entry}
          className="review-coach-entry"
          type="button"
          onClick={show}
          aria-haspopup="dialog"
        >
          {done ? '저장한 복기 확인' : '복기 이어서 작성'}
        </button>
      ) : null}
      {createPortal(
        <aside
          ref={floating}
          hidden={!visible}
          className={`floating-review${open ? ' floating-review--open' : ''}`}
          aria-label="투자 복기 도우미"
        >
          {panel}
          {!open ? (
            <>
              {reminderVisible && reminderCount && !done ? (
                <div className="floating-review-reminder">
                  <button
                    className="floating-review-reminder-close"
                    type="button"
                    aria-label="복기 알림 닫기"
                    onClick={() => setReminderVisible(false)}
                  >
                    ×
                  </button>
                  <strong>복기할 기록이 {reminderCount}개 있어요</strong>
                  <p>{record.assetName}의 결과를 남겨볼까요?</p>
                  <p>열면 보유 이유와 관련 기사 일부를 외부 AI에 보내 근거를 준비해요.</p>
                  <button
                    className="shared-primary-button floating-review-primary"
                    type="button"
                    onClick={show}
                  >
                    바로 작성하기
                  </button>
                </div>
              ) : null}
              <button
                ref={trigger}
                className="floating-review-trigger"
                type="button"
                aria-label="복기 도우미 열기"
                aria-expanded={false}
                aria-haspopup="dialog"
                title="투자 복기 열기"
                onClick={show}
              >
                <span
                  className="review-coach-avatar review-coach-avatar--floating"
                  aria-hidden="true"
                />
              </button>
            </>
          ) : null}
        </aside>,
        document.body,
      )}
    </>
  );
}
