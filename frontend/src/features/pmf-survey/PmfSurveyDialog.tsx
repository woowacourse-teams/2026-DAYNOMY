import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import { XIcon } from '@phosphor-icons/react';
import { PMF_ANSWERS, PMF_QUESTION, PMF_REASON_MAX_LENGTH, type PmfAnswer } from './types';
import './pmfSurvey.css';

type Props = {
  onSubmitAnswer: (answer: PmfAnswer) => Promise<void>;
  onSubmitReason: (reason: string) => Promise<void>;
  onDismiss: () => void;
};

export function PmfSurveyDialog({ onSubmitAnswer, onSubmitReason, onDismiss }: Props) {
  const id = useId();
  const dialogRef = useRef<HTMLDialogElement>(null);
  const headingRef = useRef<HTMLHeadingElement>(null);
  const submittingRef = useRef(false);
  const [step, setStep] = useState<'answer' | 'reason' | 'complete'>('answer');
  const [answer, setAnswer] = useState<PmfAnswer | null>(null);
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);
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
  }, [step]);

  function dismiss() {
    if (!submittingRef.current) onDismiss();
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submittingRef.current) return;
    if (step === 'answer' && !answer) return;
    const trimmedReason = reason.trim();
    if (step === 'reason' && (!trimmedReason || trimmedReason.length > PMF_REASON_MAX_LENGTH))
      return;
    submittingRef.current = true;
    setSubmitting(true);
    setError('');
    try {
      if (step === 'answer' && answer) {
        await onSubmitAnswer(answer);
        setStep('reason');
      } else if (step === 'reason') {
        await onSubmitReason(trimmedReason);
        setStep('complete');
      }
    } catch {
      setError(
        step === 'answer'
          ? '응답을 저장하지 못했어요. 잠시 후 다시 시도해 주세요.'
          : '추가 의견을 보내지 못했어요. 먼저 제출한 응답은 저장되어 있어요.',
      );
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  }

  return (
    <dialog
      ref={dialogRef}
      className="pmf-dialog ph-no-capture"
      aria-labelledby={`${id}-title`}
      aria-describedby={step === 'complete' ? undefined : `${id}-description`}
      aria-busy={submitting}
      onKeyDown={(event) => {
        event.stopPropagation();
        if (event.key !== 'Tab') return;
        const controls = Array.from(
          event.currentTarget.querySelectorAll<HTMLElement>('button, input, textarea'),
        ).filter((element) => !element.matches(':disabled'));
        const first = controls[0];
        const last = controls.at(-1);
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
      onCancel={(event) => {
        event.preventDefault();
        dismiss();
      }}
    >
      <div className="pmf-dialog-panel">
        <header className="pmf-dialog-header">
          <p>
            {step === 'answer'
              ? 'DAYNOMY 사용 경험을 알려주세요'
              : step === 'reason'
                ? '응답이 저장되었어요'
                : 'DAYNOMY'}
          </p>
          <button
            className="pmf-close"
            type="button"
            aria-label="설문 닫기"
            disabled={submitting}
            onClick={dismiss}
          >
            <XIcon size={24} aria-hidden="true" />
          </button>
        </header>

        <h2 ref={headingRef} id={`${id}-title`} tabIndex={-1} className="pmf-title">
          {step === 'answer' ? (
            PMF_QUESTION
          ) : step === 'reason' ? (
            <>
              <span className="pmf-reason-question-lead">그렇게 답하신 가장 큰 이유는</span>{' '}
              무엇인가요? <span className="pmf-optional">(선택)</span>
            </>
          ) : (
            '의견을 보내주셔서 감사합니다'
          )}
        </h2>

        {step === 'complete' ? (
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
              {step === 'answer'
                ? '가장 가까운 답변 하나를 선택해 주세요.'
                : '떠오르는 생각을 자유롭게 적어 주세요.'}
            </p>

            {step === 'answer' ? (
              <fieldset className="pmf-answers" disabled={submitting}>
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
            ) : (
              <div className="pmf-reason">
                <label className="pmf-sr-only" htmlFor={`${id}-reason`}>
                  그렇게 답하신 가장 큰 이유는 무엇인가요? (선택)
                </label>
                <textarea
                  id={`${id}-reason`}
                  placeholder="이유를 입력해 주세요."
                  value={reason}
                  maxLength={PMF_REASON_MAX_LENGTH}
                  disabled={submitting}
                  aria-describedby={`${id}-hint ${id}-count`}
                  onChange={(event) => {
                    setReason(event.target.value);
                    setError('');
                  }}
                />
                <div className="pmf-reason-meta">
                  <p id={`${id}-hint`}>추가 의견을 남기지 않아도 괜찮아요.</p>
                  <span id={`${id}-count`}>
                    {reason.length} / {PMF_REASON_MAX_LENGTH}
                  </span>
                </div>
              </div>
            )}

            {error ? (
              <p className="pmf-error" role="alert">
                {error}
              </p>
            ) : null}
            <button
              type="submit"
              className="pmf-submit"
              disabled={submitting || (step === 'answer' ? !answer : !reason.trim())}
            >
              {submitting ? '저장 중…' : step === 'answer' ? '응답 제출' : '의견 보내기'}
            </button>
            <button type="button" className="pmf-skip" disabled={submitting} onClick={dismiss}>
              {step === 'answer' ? '나중에' : '건너뛰기'}
            </button>
          </form>
        )}
      </div>
    </dialog>
  );
}
