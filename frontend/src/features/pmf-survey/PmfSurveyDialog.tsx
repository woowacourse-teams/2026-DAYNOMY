import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import { CaretDownIcon, CaretUpIcon, XIcon } from '@phosphor-icons/react';
import { PMF_ANSWERS, PMF_QUESTION, PMF_REASON_MAX_LENGTH, type PmfAnswer } from './types';
import './pmfSurvey.css';

type Props = {
  onSubmit: (answer: PmfAnswer, reason: string | null) => Promise<void>;
  onHideToday: () => Promise<'saved' | 'local-only'>;
  onDismiss: () => void;
};
export function PmfSurveyDialog({ onSubmit, onHideToday, onDismiss }: Props) {
  const id = useId();
  const dialogRef = useRef<HTMLDialogElement>(null);
  const headingRef = useRef<HTMLHeadingElement>(null);
  const busyRef = useRef(false);
  const [complete, setComplete] = useState(false);
  const [expanded, setExpanded] = useState(false);
  const [answer, setAnswer] = useState<PmfAnswer | null>(null);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState<'submit' | 'snooze' | null>(null);
  const [error, setError] = useState('');
  useEffect(() => {
    const dialog = dialogRef.current;
    const trigger = document.activeElement;
    const previousOverflow = document.body.style.overflow;
    if (!dialog) return;
    dialog.showModal();
    document.body.style.overflow = 'hidden';
    return () => {
      dialog.close();
      document.body.style.overflow = previousOverflow;
      if (trigger instanceof HTMLElement && trigger.isConnected) trigger.focus();
    };
  }, []);
  useEffect(() => {
    headingRef.current?.focus();
    if (dialogRef.current) dialogRef.current.scrollTop = 0;
  }, [complete]);
  function dismiss() {
    if (!busyRef.current) onDismiss();
  }
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busyRef.current || !answer || reason.trim().length > PMF_REASON_MAX_LENGTH) return;
    busyRef.current = true;
    setBusy('submit');
    setError('');
    try {
      await onSubmit(answer, reason.trim() || null);
      setComplete(true);
    } catch {
      setError('응답을 저장하지 못했어요. 잠시 후 다시 시도해 주세요.');
    } finally {
      busyRef.current = false;
      setBusy(null);
    }
  }
  async function snooze() {
    if (busyRef.current) return;
    busyRef.current = true;
    setBusy('snooze');
    setError('');
    try {
      const result = await onHideToday();
      if (result === 'local-only')
        setError(
          '이 브라우저에서는 오늘 숨겼어요. 서버 저장에 실패했어요. 다시 시도하거나 닫아 주세요.',
        );
    } catch {
      setError('오늘 숨김을 저장하지 못했어요. 다시 시도하거나 닫아 주세요.');
    } finally {
      busyRef.current = false;
      setBusy(null);
    }
  }
  return (
    <dialog
      ref={dialogRef}
      className="pmf-dialog ph-no-capture"
      aria-labelledby={`${id}-title`}
      aria-busy={busy !== null}
      aria-describedby={complete ? undefined : `${id}-description`}
      onCancel={(event) => {
        event.preventDefault();
        dismiss();
      }}
      onKeyDown={(event) => {
        event.stopPropagation();
        if (event.key !== 'Tab') return;
        const controls = Array.from(
          event.currentTarget.querySelectorAll<HTMLElement>('button, input, textarea'),
        ).filter((element) => !element.matches(':disabled'));
        const first = controls[0],
          last = controls.at(-1);
        if (!first || !last) {
          event.preventDefault();
          headingRef.current?.focus();
        } else if (event.shiftKey && document.activeElement === first) {
          event.preventDefault();
          last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault();
          first.focus();
        }
      }}
    >
      <div className="pmf-dialog-panel">
        <header className="pmf-dialog-header">
          <p>DAYNOMY 사용 경험을 알려주세요</p>
          <button
            type="button"
            className="pmf-close"
            aria-label="설문 닫기"
            disabled={busy !== null}
            onClick={dismiss}
          >
            <XIcon size={20} aria-hidden="true" />
          </button>
        </header>
        <h2 ref={headingRef} id={`${id}-title`} tabIndex={-1} className="pmf-title">
          {complete ? '의견을 보내주셔서 감사합니다' : PMF_QUESTION}
        </h2>
        {complete ? (
          <>
            <p className="pmf-description" role="status">
              보내주신 의견은 서비스 개선에 참고하겠습니다.
            </p>
            <button type="button" className="pmf-submit" onClick={dismiss}>
              닫기
            </button>
          </>
        ) : (
          <form onSubmit={submit}>
            <p id={`${id}-description`} className="pmf-description">
              가장 가까운 답변 하나를 선택해 주세요.
            </p>
            <fieldset className="pmf-answers" disabled={busy !== null}>
              <legend className="pmf-sr-only">{PMF_QUESTION}</legend>
              {PMF_ANSWERS.map((option) => (
                <label key={option.value} className="pmf-answer">
                  <input
                    type="radio"
                    name={`${id}-answer`}
                    value={option.value}
                    checked={answer === option.value}
                    onChange={() => {
                      setAnswer(option.value);
                      setError('');
                    }}
                  />
                  <span>{option.label}</span>
                </label>
              ))}
            </fieldset>
            <div className="pmf-reason">
              <button
                type="button"
                className="pmf-reason-toggle"
                aria-expanded={expanded}
                aria-controls={`${id}-reason-panel`}
                disabled={busy !== null}
                onClick={() => setExpanded((value) => !value)}
              >
                <span>
                  그렇게 답하신 가장 큰 이유는 무엇인가요?{' '}
                  <span className="pmf-optional">(선택)</span>
                </span>
                {expanded ? (
                  <CaretUpIcon size={16} aria-hidden="true" />
                ) : (
                  <CaretDownIcon size={16} aria-hidden="true" />
                )}
              </button>
              <div id={`${id}-reason-panel`} hidden={!expanded}>
                <label className="pmf-sr-only" htmlFor={`${id}-reason`}>
                  그렇게 답하신 가장 큰 이유는 무엇인가요? (선택)
                </label>
                <textarea
                  id={`${id}-reason`}
                  placeholder="이유를 입력해 주세요."
                  maxLength={PMF_REASON_MAX_LENGTH}
                  disabled={busy !== null}
                  value={reason}
                  aria-describedby={`${id}-hint ${id}-count`}
                  onChange={(event) => {
                    setReason(event.target.value);
                    setError('');
                  }}
                />
                <div className="pmf-reason-meta">
                  <p id={`${id}-hint`}>의견 없이도 제출할 수 있어요.</p>
                  <span id={`${id}-count`}>
                    {reason.length} / {PMF_REASON_MAX_LENGTH}
                  </span>
                </div>
              </div>
            </div>
            {error ? (
              <p className="pmf-error" role="alert">
                {error}
              </p>
            ) : null}
            <button type="submit" className="pmf-submit" disabled={busy !== null || !answer}>
              {busy === 'submit' ? '저장 중…' : '응답 보내기'}
            </button>
            <footer className="pmf-dialog-footer">
              <button type="button" disabled={busy !== null} onClick={() => void snooze()}>
                {busy === 'snooze' ? '저장 중…' : '오늘 하루 그만 보기'}
              </button>
              <button type="button" disabled={busy !== null} onClick={dismiss}>
                닫기
              </button>
            </footer>
          </form>
        )}
      </div>
    </dialog>
  );
}
