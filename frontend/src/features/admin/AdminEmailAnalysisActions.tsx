import { useEffect, useRef, useState, type FormEvent } from 'react';
import {
  CORRECTION_REVIEW_PAGE_SIZE,
  getAdminEmailCorrectionReviewIds,
  invalidateAdminEmailAnalysis,
  type AnalysisInvalidationResponse,
  type EmailAnalysisStatus,
} from './emailAnalysisApi';

type Props = {
  analysisId: number;
  status: EmailAnalysisStatus;
  onInvalidated: (result: AnalysisInvalidationResponse, signal: AbortSignal) => Promise<void>;
};

export function AdminEmailAnalysisActions({ analysisId, status, onInvalidated }: Props) {
  const [editing, setEditing] = useState(false);
  const [reason, setReason] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [syncError, setSyncError] = useState(false);
  const [result, setResult] = useState<AnalysisInvalidationResponse | null>(null);
  const [ids, setIds] = useState<string[]>([]);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [reviewError, setReviewError] = useState('');
  const [reload, setReload] = useState(0);
  const lifetime = useRef<AbortController | null>(null);
  const submitting = useRef(false);
  const reasonInput = useRef<HTMLTextAreaElement>(null);
  const invalidated = status === 'INVALIDATED' || result !== null;

  useEffect(() => {
    const controller = new AbortController();
    lifetime.current = controller;
    return () => controller.abort();
  }, []);
  useEffect(() => {
    if (editing) reasonInput.current?.focus();
  }, [editing]);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setReviewError('');
    setIds([]);
    getAdminEmailCorrectionReviewIds(analysisId, page, controller.signal)
      .then((value) => {
        if (!controller.signal.aborted) setIds(value);
      })
      .catch(() => {
        if (!controller.signal.aborted)
          setReviewError('정정 검토 대상을 불러오지 못했어요. 다시 조회해 주세요.');
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [analysisId, page, reload]);

  async function synchronize(value: AnalysisInvalidationResponse, signal: AbortSignal) {
    setSyncError(false);
    try {
      await onInvalidated(value, signal);
    } catch {
      if (!signal.aborted) setSyncError(true);
    }
  }
  async function submit(event: FormEvent) {
    event.preventDefault();
    const controller = lifetime.current;
    if (submitting.current || invalidated || !controller || controller.signal.aborted) return;
    const trimmed = reason.trim();
    if (!trimmed || trimmed.length > 500) {
      setError('무효화 사유는 1~500자로 입력해 주세요.');
      reasonInput.current?.focus();
      return;
    }
    submitting.current = true;
    setSaving(true);
    setError('');
    try {
      const response = await invalidateAdminEmailAnalysis(analysisId, trimmed, controller.signal);
      if (controller.signal.aborted) return;
      setResult(response);
      setEditing(false);
      setReason('');
      setPage(0);
      setReload((value) => value + 1);
      await synchronize(response, controller.signal);
    } catch {
      if (!controller.signal.aborted)
        setError('무효화 요청 결과를 확인하지 못했어요. 다시 시도해 주세요.');
    } finally {
      submitting.current = false;
      if (!controller.signal.aborted) setSaving(false);
    }
  }
  async function retrySynchronization() {
    const controller = lifetime.current;
    if (!result || submitting.current || !controller || controller.signal.aborted) return;
    submitting.current = true;
    setSaving(true);
    await synchronize(result, controller.signal);
    submitting.current = false;
    if (!controller.signal.aborted) setSaving(false);
  }
  function reloadReview() {
    setPage(0);
    setReload((value) => value + 1);
  }

  return (
    <>
      <section
        className="admin-email-detail-panel"
        aria-labelledby="email-analysis-invalidation"
        aria-busy={saving}
      >
        <h2 id="email-analysis-invalidation">분석 무효화</h2>
        <p>
          이 분석을 사용하는 대기·재시도 메일을 취소합니다. 이미 전송 중이거나 접수된 메일은
          회수되지 않습니다.
        </p>
        {result ? (
          <p role="status">
            무효화 처리가 완료됐습니다. 이번 요청에서 취소된 대기·재시도 메일은{' '}
            {result.cancelledDeliveries.toLocaleString('ko-KR')}건입니다.
          </p>
        ) : invalidated ? (
          <p>이미 무효화된 분석입니다. 최초 사유와 시각은 분석 정보에서 확인할 수 있습니다.</p>
        ) : editing ? (
          <form className="admin-email-invalidation-form" onSubmit={submit} noValidate>
            <label className="admin-field" htmlFor="email-analysis-invalidation-reason">
              <span>무효화 사유</span>
              <textarea
                ref={reasonInput}
                id="email-analysis-invalidation-reason"
                value={reason}
                maxLength={500}
                rows={3}
                disabled={saving}
                aria-invalid={!!error}
                aria-describedby={`email-analysis-reason-hint${error ? ' email-analysis-invalidation-error' : ''}`}
                onChange={(event) => {
                  setReason(event.target.value);
                  setError('');
                }}
              />
            </label>
            <p id="email-analysis-reason-hint" className="admin-email-source-dates">
              1~500자 · 최초 저장된 사유는 이후 변경되지 않습니다.
            </p>
            {error && (
              <p id="email-analysis-invalidation-error" className="admin-alert" role="alert">
                {error}
              </p>
            )}
            <div className="admin-email-action-buttons">
              <button
                type="submit"
                className="admin-secondary-button admin-email-danger"
                disabled={saving}
              >
                {saving ? '무효화 처리 중…' : '무효화 확정'}
              </button>
              <button
                type="button"
                className="admin-secondary-button"
                disabled={saving}
                onClick={() => {
                  setEditing(false);
                  setReason('');
                  setError('');
                }}
              >
                취소
              </button>
            </div>
          </form>
        ) : (
          <button
            type="button"
            className="admin-secondary-button admin-email-danger"
            onClick={() => setEditing(true)}
          >
            분석 무효화
          </button>
        )}
        {syncError && (
          <section className="admin-alert" role="alert">
            <strong>무효화는 완료됐지만 최신 분석 정보를 불러오지 못했어요.</strong>
            <button type="button" disabled={saving} onClick={() => void retrySynchronization()}>
              분석 정보 다시 확인
            </button>
          </section>
        )}
      </section>
      <section
        className="admin-email-detail-panel"
        aria-labelledby="email-analysis-correction-review"
        aria-busy={loading}
      >
        <div className="admin-email-review-heading">
          <h2 id="email-analysis-correction-review">정정 검토 대상</h2>
          <button
            type="button"
            className="admin-secondary-button"
            disabled={loading || saving}
            onClick={reloadReview}
          >
            다시 조회
          </button>
        </div>
        <p>
          이 분석을 포함한 접수 완료·전송 중·결과 불명확 메일의 발송 ID입니다. 수신함 도착이나 정정
          필요 여부가 확정된 목록은 아닙니다.
        </p>
        <p className="admin-email-source-dates">
          전송 중인 메일은 상태가 바뀔 수 있으니 다시 확인해 주세요.
        </p>
        {loading ? (
          <p role="status">정정 검토 대상을 불러오는 중입니다.</p>
        ) : reviewError ? (
          <p className="admin-alert" role="alert">
            {reviewError}
          </p>
        ) : (
          <>
            <p className="admin-email-source-dates">
              {page + 1}페이지 · 현재 페이지 {ids.length}건
            </p>
            {ids.length ? (
              <ul className="admin-email-delivery-ids" aria-label="정정 검토 대상 발송 ID">
                {ids.map((id) => (
                  <li key={id}>
                    <code>{id}</code>
                  </li>
                ))}
              </ul>
            ) : (
              <p>
                {page === 0
                  ? '조회된 정정 검토 대상이 없습니다.'
                  : '이 페이지에는 조회된 대상이 없습니다. 이전 페이지를 확인해 주세요.'}
              </p>
            )}
          </>
        )}
        <nav className="admin-pagination" aria-label="정정 검토 대상 페이지">
          <button
            type="button"
            aria-label="이전 정정 검토 페이지"
            disabled={loading || saving || page === 0}
            onClick={() => setPage((value) => value - 1)}
          >
            이전
          </button>
          <span className="admin-email-page-count">{page + 1}페이지</span>
          <button
            type="button"
            aria-label="다음 정정 검토 페이지"
            disabled={
              loading || saving || !!reviewError || ids.length < CORRECTION_REVIEW_PAGE_SIZE
            }
            onClick={() => setPage((value) => value + 1)}
          >
            다음
          </button>
        </nav>
      </section>
    </>
  );
}
