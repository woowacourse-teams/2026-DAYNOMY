import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import { createReview, updateDecisionReason, updateReview } from './api';
import { leagueErrorType, trackLeagueEvent } from './analytics';
import { holdingPeriodLabels, transactionLabels } from './labels';
import type { InvestmentDecisionRecord } from './types';

const blankReview = () => ({ actualResult: '', differenceFromExpectation: '', nextAction: '' });
const messageOf = (error: unknown) =>
  error instanceof Error ? error.message : '저장하지 못했습니다. 다시 시도해 주세요.';

export function InvestmentRecords({
  records,
  onRecordChange,
  reviewId,
  setReviewId,
  disabled = false,
  surface = 'reviews',
}: {
  records: InvestmentDecisionRecord[];
  onRecordChange: (record: InvestmentDecisionRecord) => void;
  reviewId: number | null;
  setReviewId: (id: number | null) => void;
  disabled?: boolean;
  surface?: 'reviews' | 'decisions';
}) {
  const [editingReviewId, setEditingReviewId] = useState<number | null>(null);
  const [editingReason, setEditingReason] = useState(false);
  const [reason, setReason] = useState('');
  const [review, setReview] = useState(blankReview);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const errorId = useId();
  const editForm = useRef<HTMLFormElement>(null);
  const errorNotice = useRef<HTMLParagraphElement>(null);
  const editTrigger = useRef<HTMLButtonElement | null>(null);
  useEffect(() => {
    if (reviewId === null && !busy) {
      editTrigger.current?.focus();
      editTrigger.current = null;
    }
  }, [reviewId, busy]);
  useEffect(() => {
    if (!error || busy) return;
    const invalidInput = Array.from(editForm.current?.elements ?? []).find(
      (input) => input instanceof HTMLTextAreaElement && !input.value.trim(),
    );
    (invalidInput instanceof HTMLTextAreaElement ? invalidInput : errorNotice.current)?.focus();
  }, [error, busy]);
  async function saveReason(event: FormEvent) {
    event.preventDefault();
    if (busy || disabled || reviewId === null) return;
    if (!reason.trim() || reason.length > 500) {
      setError('보유 이유를 1~500자로 작성해 주세요.');
      return;
    }
    setBusy(true);
    setError('');
    setMessage('');
    trackLeagueEvent('league_action', { surface, action: 'edit_reason', status: 'submitted' });
    try {
      const saved = await updateDecisionReason(reviewId, reason);
      onRecordChange(saved);
      setReviewId(null);
      setEditingReason(false);
      setReason('');
      setMessage('보유 이유를 수정했습니다.');
    } catch (caught) {
      setError(messageOf(caught));
      trackLeagueEvent('league_action', {
        surface,
        action: 'edit_reason',
        status: 'error',
        error_type: leagueErrorType(caught),
      });
    } finally {
      setBusy(false);
    }
  }

  async function saveReview(event: FormEvent) {
    event.preventDefault();
    if (busy || disabled || reviewId === null) return;
    if (Object.values(review).some((value) => !value.trim())) {
      setError('결과와 달랐던 점, 다음 행동을 모두 작성해 주세요.');
      return;
    }
    setBusy(true);
    trackLeagueEvent('league_action', {
      surface,
      action: editingReviewId === null ? 'save_review' : 'edit_review',
      status: 'submitted',
    });
    setError('');
    setMessage('');
    try {
      const saved =
        editingReviewId === null
          ? await createReview(reviewId, review)
          : await updateReview(reviewId, editingReviewId, review);
      const record = records.find((record) => record.id === reviewId)!;
      onRecordChange({
        ...record,
        reviews:
          editingReviewId === null
            ? [...record.reviews, saved]
            : record.reviews.map((item) => (item.id === editingReviewId ? saved : item)),
      });
      setReviewId(null);
      setEditingReviewId(null);
      setReview(blankReview());
      setMessage(editingReviewId === null ? '복기를 저장했습니다.' : '복기를 수정했습니다.');
    } catch (caught) {
      setError(messageOf(caught));
      trackLeagueEvent('league_action', {
        surface,
        action: editingReviewId === null ? 'save_review' : 'edit_review',
        status: 'error',
        error_type: leagueErrorType(caught),
      });
    } finally {
      setBusy(false);
    }
  }

  function renderRecord(r: InvestmentDecisionRecord) {
    const editFields =
      reviewId === r.id ? (
        <form
          ref={editForm}
          className="shared-edit-form"
          onSubmit={(event) => void (editingReason ? saveReason(event) : saveReview(event))}
        >
          <fieldset disabled={disabled || busy}>
            <legend className="league-sr-only">
              {r.assetName}{' '}
              {editingReason
                ? '보유 이유 수정'
                : `복기 ${editingReviewId === null ? '추가' : '수정'}`}
            </legend>
            {editingReason ? (
              <label className="shared-reason">
                보유 이유
                <textarea
                  required
                  maxLength={500}
                  rows={3}
                  autoFocus
                  value={reason}
                  aria-invalid={
                    (Boolean(error) && (!reason.trim() || reason.length > 500)) || undefined
                  }
                  aria-describedby={error ? errorId : undefined}
                  onChange={(event) => {
                    setReason(event.target.value);
                    setError('');
                  }}
                />
              </label>
            ) : (
              <>
                <label className="shared-reason">
                  실제 결과
                  <textarea
                    required
                    aria-invalid={(Boolean(error) && !review.actualResult.trim()) || undefined}
                    aria-describedby={error ? errorId : undefined}
                    maxLength={500}
                    rows={3}
                    autoFocus
                    value={review.actualResult}
                    placeholder="예: 매출은 늘었지만 주가는 기대보다 적게 올랐어요."
                    onChange={(event) => setReview({ ...review, actualResult: event.target.value })}
                  />
                </label>
                <label className="shared-reason">
                  예상과 달랐던 점
                  <textarea
                    required
                    aria-invalid={
                      (Boolean(error) && !review.differenceFromExpectation.trim()) || undefined
                    }
                    aria-describedby={error ? errorId : undefined}
                    maxLength={500}
                    rows={3}
                    value={review.differenceFromExpectation}
                    placeholder="예: 비용 증가의 영향이 컸어요."
                    onChange={(event) =>
                      setReview({ ...review, differenceFromExpectation: event.target.value })
                    }
                  />
                </label>
                <label className="shared-reason">
                  다음 행동
                  <textarea
                    required
                    aria-invalid={(Boolean(error) && !review.nextAction.trim()) || undefined}
                    aria-describedby={error ? errorId : undefined}
                    maxLength={500}
                    rows={3}
                    value={review.nextAction}
                    placeholder="예: 다음 실적에서 비용이 줄어드는지 확인해요."
                    onChange={(event) => setReview({ ...review, nextAction: event.target.value })}
                  />
                </label>
              </>
            )}
          </fieldset>
          {error ? (
            <p
              id={errorId}
              ref={errorNotice}
              tabIndex={-1}
              role="alert"
              className="league-form-error"
            >
              {error}
            </p>
          ) : null}
          <div className="shared-form-actions">
            <button className="shared-primary-button" disabled={disabled || busy}>
              {busy
                ? '저장 중…'
                : editingReason
                  ? '보유 이유 수정 저장'
                  : editingReviewId === null
                    ? '복기 저장'
                    : '수정 저장'}
            </button>
            <button
              type="button"
              disabled={disabled || busy}
              onClick={() => {
                setReviewId(null);
                setEditingReason(false);
                setEditingReviewId(null);
                setError('');
              }}
            >
              취소
            </button>
          </div>
        </form>
      ) : null;
    return (
      <article className="shared-decision" key={r.id}>
        <h3>{r.assetName}</h3>
        <small>
          {r.tradedOn} · {transactionLabels[r.transactionType]}
        </small>
        <div className="shared-record-body">
          <div className="shared-record-reason">
            <div className="shared-record-heading">
              <strong>보유 이유</strong>
              <button
                className="shared-text-action"
                type="button"
                aria-label={`${r.assetName} ${r.tradedOn} ${transactionLabels[r.transactionType]} 보유 이유 수정`}
                aria-expanded={reviewId === r.id && editingReason}
                disabled={disabled || busy || reviewId !== null}
                onClick={(event) => {
                  trackLeagueEvent('league_action', {
                    surface,
                    action: 'open_reason',
                    status: 'clicked',
                  });
                  editTrigger.current = event.currentTarget;
                  setEditingReason(true);
                  setReason(r.reason);
                  setReviewId(r.id);
                  setError('');
                  setMessage('');
                }}
              >
                수정
              </button>
            </div>
            {reviewId === r.id && editingReason ? editFields : <p>{r.reason}</p>}
            {r.expectedHoldingPeriod ||
            r.expectedChange ||
            r.invalidationCondition ||
            r.maximumAcceptableLossRate !== null ? (
              <dl>
                {r.expectedHoldingPeriod ? (
                  <div>
                    <dt>예상 보유 기간</dt>
                    <dd>{holdingPeriodLabels[r.expectedHoldingPeriod]}</dd>
                  </div>
                ) : null}
                {r.expectedChange ? (
                  <div>
                    <dt>기대 변화</dt>
                    <dd>{r.expectedChange}</dd>
                  </div>
                ) : null}
                {r.invalidationCondition ? (
                  <div>
                    <dt>재확인 조건</dt>
                    <dd>{r.invalidationCondition}</dd>
                  </div>
                ) : null}
                {r.maximumAcceptableLossRate !== null ? (
                  <div>
                    <dt>허용 하락률</dt>
                    <dd>{r.maximumAcceptableLossRate}%</dd>
                  </div>
                ) : null}
              </dl>
            ) : null}
          </div>
          <div className="shared-record-reviews">
            {r.reviews.length === 0 ? (
              <p className="shared-review-empty">
                아직 복기가 없어요. 보유 후 달라진 생각을 남겨보세요.
              </p>
            ) : null}
            {r.reviews.map((item) => (
              <div className="shared-review" key={item.id}>
                <div className="shared-record-heading">
                  <strong>복기</strong>
                  <button
                    className="shared-text-action"
                    type="button"
                    aria-label={`${r.assetName} ${r.tradedOn} ${transactionLabels[r.transactionType]} 복기 수정`}
                    aria-expanded={
                      reviewId === r.id && !editingReason && editingReviewId === item.id
                    }
                    disabled={disabled || busy || reviewId !== null}
                    onClick={(event) => {
                      trackLeagueEvent('league_action', {
                        surface,
                        action: 'open_review',
                        status: 'clicked',
                      });
                      editTrigger.current = event.currentTarget;
                      setEditingReason(false);
                      setReviewId(r.id);
                      setEditingReviewId(item.id);
                      setReview({
                        actualResult: item.actualResult,
                        differenceFromExpectation: item.differenceFromExpectation,
                        nextAction: item.nextAction,
                      });
                      setError('');
                      setMessage('');
                    }}
                  >
                    수정
                  </button>
                </div>
                {reviewId === r.id && !editingReason && editingReviewId === item.id ? (
                  editFields
                ) : (
                  <>
                    <dl>
                      <div>
                        <dt>실제 결과</dt>
                        <dd>{item.actualResult}</dd>
                      </div>
                      <div>
                        <dt>예상과 달랐던 점</dt>
                        <dd>{item.differenceFromExpectation}</dd>
                      </div>
                      <div>
                        <dt>다음 행동</dt>
                        <dd>{item.nextAction}</dd>
                      </div>
                    </dl>
                    {item.updatedAt ? (
                      <small>
                        작성 {item.createdAt.slice(0, 10)} · 마지막 저장{' '}
                        {item.updatedAt.slice(0, 10)}
                      </small>
                    ) : null}
                  </>
                )}
              </div>
            ))}
            <div className="shared-holding-actions">
              <button
                className="shared-text-action"
                type="button"
                aria-label={`${r.assetName} ${r.tradedOn} ${transactionLabels[r.transactionType]} 복기 작성`}
                aria-expanded={reviewId === r.id && !editingReason && editingReviewId === null}
                disabled={disabled || busy || reviewId !== null}
                onClick={(event) => {
                  trackLeagueEvent('league_action', {
                    surface,
                    action: 'open_review',
                    status: 'clicked',
                  });
                  editTrigger.current = event.currentTarget;
                  setEditingReason(false);
                  setReviewId(r.id);
                  setEditingReviewId(null);
                  setReview(blankReview());
                  setError('');
                  setMessage('');
                }}
              >
                복기 작성
              </button>
            </div>

            {reviewId === r.id && !editingReason && editingReviewId === null ? editFields : null}
          </div>
        </div>
      </article>
    );
  }
  return (
    <>
      {records.map(renderRecord)}
      {message ? (
        <p role="status" className="shared-feedback">
          {message}
        </p>
      ) : null}
    </>
  );
}
