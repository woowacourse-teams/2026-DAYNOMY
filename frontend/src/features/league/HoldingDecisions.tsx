import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import { getInvestmentDecisions, recordDecision } from './api';
import type { SharedHolding } from './sharedPortfolioApi';
import type { InvestmentDecisionRecord } from './types';
const messageOf = (error: unknown) =>
  error instanceof Error ? error.message : '저장하지 못했습니다. 다시 시도해 주세요.';

export function HoldingDecisions({
  holdings,
  portfolioBusy,
  publicationStatus,
  onVisibilityChange,
  onRemove,
  onRecordsChange,
  refreshKey,
}: {
  holdings: SharedHolding[];
  portfolioBusy: boolean;
  publicationStatus: string;
  onVisibilityChange: (assetId: number, hidden: boolean) => void;
  onRemove: (assetId: number) => Promise<boolean>;
  onRecordsChange: (records: InvestmentDecisionRecord[] | null) => void;
  refreshKey: number;
}) {
  const [removing, setRemoving] = useState<number | null>(null);
  const [records, setRecords] = useState<InvestmentDecisionRecord[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [retry, setRetry] = useState(0);
  const [writing, setWriting] = useState(false);
  const [assetId, setAssetId] = useState('');
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const requestKey = useRef('');
  const errorId = useId();
  const reasonInput = useRef<HTMLTextAreaElement>(null);
  const errorNotice = useRef<HTMLParagraphElement>(null);
  const decisionTrigger = useRef<HTMLButtonElement>(null);
  const removeTrigger = useRef<HTMLButtonElement>(null);
  const removeCancel = useRef<HTMLButtonElement>(null);
  const recordsSection = useRef<HTMLElement>(null);

  useEffect(() => {
    if (!writing) decisionTrigger.current?.focus();
  }, [writing]);
  useEffect(() => {
    if (removing !== null) removeCancel.current?.focus();
    else if (removeTrigger.current) {
      (removeTrigger.current.isConnected ? removeTrigger.current : recordsSection.current)?.focus();
      removeTrigger.current = null;
    }
  }, [removing]);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setLoadError('');
    getInvestmentDecisions(controller.signal)
      .then((values) => {
        if (!controller.signal.aborted) setRecords(values);
      })
      .catch(() => {
        if (!controller.signal.aborted)
          setLoadError('판단 기록을 불러오지 못했습니다. 기록이 없는 것은 아니에요.');
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [retry, refreshKey]);
  useEffect(() => {
    onRecordsChange(loading || loadError ? null : records);
  }, [records, loading, loadError, onRecordsChange]);
  const locked = busy || portfolioBusy;
  useEffect(() => {
    if (error && !locked) {
      (!reasonInput.current?.value.trim() ? reasonInput.current : errorNotice.current)?.focus();
    }
  }, [error, locked]);

  async function saveDecision(event: FormEvent) {
    event.preventDefault();
    if (locked || !records) return;
    const holding = holdings.find((h) => h.assetId === Number(assetId));
    if (!holding || !reason.trim()) {
      setError('보유 이유를 작성해 주세요.');
      return;
    }
    if (!requestKey.current) requestKey.current = crypto.randomUUID();
    setBusy(true);
    setError('');
    setMessage('');
    try {
      const saved = await recordDecision({
        requestKey: requestKey.current,
        assetId: holding.assetId,
        decision: {
          reason,
          expectedHoldingPeriod: null,
          expectedChange: '',
          invalidationCondition: '',
          maximumAcceptableLossRate: null,
        },
      });
      setRecords((values) => [saved, ...(values ?? []).filter((r) => r.id !== saved.id)]);
      requestKey.current = '';
      setWriting(false);
      setReason('');
      setMessage('저장했습니다.');
    } catch (caught) {
      setError(messageOf(caught));
    } finally {
      setBusy(false);
    }
  }

  function startDecision(id: number) {
    setAssetId(String(id));
    setReason('');
    requestKey.current = '';
    setWriting(true);
    setError('');
    setMessage('');
  }
  const decisionForm = (
    <form
      className="shared-edit-form"
      aria-label="종목 투자 기록 작성"
      onSubmit={(event) => void saveDecision(event)}
    >
      <fieldset disabled={locked}>
        <legend className="league-sr-only">보유 이유 작성</legend>
        <label className="shared-reason">
          보유하는 이유
          <textarea
            ref={reasonInput}
            autoFocus
            required
            aria-invalid={(Boolean(error) && !reason.trim()) || undefined}
            aria-describedby={error ? errorId : undefined}
            maxLength={500}
            rows={3}
            value={reason}
            placeholder="예: 실적이 좋아지고 있어 다음 분기까지 보유하려고 해요."
            onChange={(event) => {
              setReason(event.target.value);
              setError('');
            }}
          />
        </label>
      </fieldset>
      {error ? (
        <p id={errorId} ref={errorNotice} tabIndex={-1} role="alert" className="league-form-error">
          {error}
        </p>
      ) : null}
      <div className="shared-form-actions">
        <button className="shared-primary-button" disabled={locked}>
          {busy ? '저장 중…' : '보유 이유 저장'}
        </button>
        <button
          type="button"
          disabled={locked}
          onClick={() => {
            setWriting(false);
            setError('');
          }}
        >
          취소
        </button>
      </div>
    </form>
  );
  return (
    <section ref={recordsSection} tabIndex={-1} aria-label="종목별 투자 기록">
      {loading ? (
        <p role="status">투자 기록을 불러오는 중이에요.</p>
      ) : loadError ? (
        <div role="alert">
          <p>{loadError}</p>
          <button onClick={() => setRetry((value) => value + 1)}>판단 기록 다시 불러오기</button>
        </div>
      ) : null}
      {holdings.map((h) => {
        const ownRecords = (records ?? []).filter((r) => r.assetId === h.assetId);
        const completed = Boolean(h.reason.trim() || ownRecords.some((r) => r.reason.trim()));
        return (
          <article key={h.assetId} className="shared-holding">
            <header>
              <div>
                <h3>{h.assetName}</h3>
                <small>
                  {h.category === 'ETF' ? 'ETF' : '주식'} · {h.assetCode} ·{' '}
                  {h.hidden ? '공개 제외' : publicationStatus}
                </small>
              </div>
              {!completed ? (
                <strong className="shared-reason-required">보유 이유 미작성</strong>
              ) : null}
            </header>
            <p className="shared-holding-meta">
              {h.quantity.toLocaleString()}주 · 평균 매수가{' '}
              {h.averagePurchasePrice.toLocaleString()}원
            </p>
            <p className="shared-reason-preview">
              {ownRecords.find((r) => r.reason.trim())?.reason || h.reason}
            </p>
            {!completed ? (
              <button
                ref={assetId === String(h.assetId) ? decisionTrigger : undefined}
                type="button"
                aria-label={`${h.assetName} 보유 이유 작성`}
                disabled={locked || loading || Boolean(loadError) || writing}
                onClick={() => startDecision(h.assetId)}
              >
                보유 이유 작성
              </button>
            ) : null}
            {writing && assetId === String(h.assetId) ? decisionForm : null}
            <div className="shared-holding-actions">
              {completed ? (
                <button
                  ref={assetId === String(h.assetId) ? decisionTrigger : undefined}
                  type="button"
                  aria-label={`${h.assetName} 투자 기록 추가`}
                  disabled={locked || loading || Boolean(loadError) || writing}
                  onClick={() => startDecision(h.assetId)}
                >
                  기록 추가
                </button>
              ) : null}
              <label className="shared-visibility-choice">
                <input
                  type="checkbox"
                  aria-label={`${h.assetName} 공개할 종목으로 선택`}
                  disabled={locked}
                  checked={!h.hidden}
                  onChange={(event) => onVisibilityChange(h.assetId, !event.target.checked)}
                />
                공개
              </label>
              <button
                type="button"
                className="shared-remove-button"
                aria-label={`${h.assetName} 리그에서 삭제`}
                disabled={locked || writing}
                onClick={(event) => {
                  removeTrigger.current = event.currentTarget;
                  setRemoving(h.assetId);
                }}
              >
                삭제
              </button>
            </div>
            {removing === h.assetId ? (
              <div
                className="shared-remove-confirm"
                role="group"
                aria-label={`${h.assetName} 삭제 확인`}
              >
                <p>
                  {holdings.length === 1
                    ? '마지막 종목을 삭제하면 리그 참여가 종료됩니다.'
                    : '이 종목을 리그에서 삭제할까요?'}{' '}
                  원본과 투자 기록은 유지됩니다.
                </p>
                <button
                  disabled={locked}
                  onClick={async () => {
                    if (await onRemove(h.assetId)) setRemoving(null);
                  }}
                >
                  삭제 확인
                </button>
                <button ref={removeCancel} disabled={locked} onClick={() => setRemoving(null)}>
                  취소
                </button>
              </div>
            ) : null}
          </article>
        );
      })}
      {message ? (
        <p role="status" className="shared-feedback">
          {message}
        </p>
      ) : null}
    </section>
  );
}
