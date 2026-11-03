import { useEffect, useId, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import type { ReviewReminderEntryPoint } from './analytics';
import { InvestmentRecords } from './InvestmentRecords';
import { transactionLabels } from './labels';
import type { InvestmentDecisionRecord } from './types';
import './shared-portfolio.css';
import './floating-review-coach.css';

type DeferTarget = 'bubble' | 'minimize' | 'hide' | 'later';

export function FloatingReviewCoach({
  record,
  reminderCount,
  onSaved,
  onDismiss,
  entryPoint,
  onOpen,
  onDefer,
}: {
  record: InvestmentDecisionRecord;
  reminderCount?: number;
  onSaved: (record: InvestmentDecisionRecord) => void;
  onDismiss?: () => void;
  entryPoint?: ReviewReminderEntryPoint;
  onOpen?: (target: 'bubble' | 'cat' | 'entry') => void;
  onDefer?: (target: DeferTarget) => Promise<void>;
}) {
  const [open, setOpen] = useState(false);
  const [visible, setVisible] = useState(true);
  const [reminderVisible, setReminderVisible] = useState(Boolean(reminderCount));
  const [done, setDone] = useState(false);
  const saved = useRef(false);
  const deferring = useRef(false);
  const [deferFailure, setDeferFailure] = useState<DeferTarget | null>(null);
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
  function show(target: 'bubble' | 'cat' | 'entry') {
    onOpen?.(target);
    setReminderVisible(false);
    setVisible(true);
    setOpen(true);
  }
  async function defer(target: DeferTarget, close?: () => void) {
    close?.();
    setReminderVisible(false);
    if (!onDefer || done || deferring.current) return;
    deferring.current = true;
    setDeferFailure(null);
    try {
      await onDefer(target);
    } catch {
      setDeferFailure(target);
    } finally {
      deferring.current = false;
    }
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
  const deferNotice = deferFailure ? (
    <div className="floating-review-reminder" role="alert">
      <p>다음 알림 일정을 저장하지 못했어요. 다시 시도해 주세요.</p>
      <button type="button" className="shared-text-action" onClick={() => void defer(deferFailure)}>
        알림 미루기 다시 시도
      </button>
    </div>
  ) : null;
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
          void defer('minimize', minimize);
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
              <button
                type="button"
                onClick={() => void defer('hide', hide)}
                aria-label="복기 도우미 숨기기"
              >
                화면에서 숨기기
              </button>
            </div>
          </details>
          <button
            type="button"
            onClick={() => void defer('minimize', minimize)}
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
        {visible && open ? deferNotice : null}
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
            surface="review_coach"
            entryPoint={entryPoint}
            autoGuide={open}
            setReviewId={(value) => {
              if (value === null && !saved.current) void defer('later', minimize);
            }}
            onRecordChange={(value) => {
              saved.current = true;
              setDone(true);
              setDeferFailure(null);
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
        <>
          <button
            ref={entry}
            className="review-coach-entry"
            type="button"
            onClick={() => show('entry')}
            aria-haspopup="dialog"
          >
            {done ? '저장한 복기 확인' : '복기 이어서 작성'}
          </button>
          {deferNotice}
        </>
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
              {visible ? deferNotice : null}
              {reminderVisible && reminderCount && !done ? (
                <div className="floating-review-reminder">
                  <button
                    className="floating-review-reminder-close"
                    type="button"
                    aria-label="복기 알림 닫기"
                    title="닫으면 7일 뒤에 다시 알려드려요"
                    onClick={() =>
                      void defer('bubble', () => {
                        setReminderVisible(false);
                        trigger.current?.focus();
                      })
                    }
                  >
                    ×
                  </button>
                  <strong>복기할 기록이 {reminderCount}개 있어요</strong>
                  <p>{record.assetName}의 결과를 남겨볼까요?</p>
                  <p>열면 보유 이유와 관련 기사 일부를 외부 AI에 보내 근거를 준비해요.</p>
                  <button
                    className="shared-primary-button floating-review-primary"
                    type="button"
                    onClick={() => show('bubble')}
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
                onClick={() => show('cat')}
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
