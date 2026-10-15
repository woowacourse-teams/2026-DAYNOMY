import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { createReview, getTransactions } from './api';
import { holdingPeriodLabels, transactionLabels } from './labels';
import type { InvestmentTransaction } from './types';
import './league.css';
import './shared-portfolio.css';
import { useLeaguePageHeading } from './useLeaguePageHeading';

const blankReview = () => ({ actualResult: '', differenceFromExpectation: '', nextAction: '' });
const messageOf = (error: unknown) =>
  error instanceof Error ? error.message : '저장하지 못했습니다. 다시 시도해 주세요.';

export function InvestmentReviewsPage() {
  const heading = useLeaguePageHeading('투자 복기');
  const [records, setRecords] = useState<InvestmentTransaction[] | null>(null);
  const [loadError, setLoadError] = useState('');
  const [retry, setRetry] = useState(0);
  const [reviewId, setReviewId] = useState<number | null>(null);
  const [review, setReview] = useState(blankReview);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const errorId = useId();
  const reviewForm = useRef<HTMLFormElement>(null);
  const errorNotice = useRef<HTMLParagraphElement>(null);
  const reviewTrigger = useRef<HTMLButtonElement | null>(null);
  useEffect(() => {
    if (reviewId === null && !busy) {
      reviewTrigger.current?.focus();
      reviewTrigger.current = null;
    }
  }, [reviewId, busy]);
  useEffect(() => {
    if (!error || busy) return;
    const invalidInput = Array.from(reviewForm.current?.elements ?? []).find(
      (input) => input instanceof HTMLTextAreaElement && !input.value.trim(),
    );
    (invalidInput instanceof HTMLTextAreaElement ? invalidInput : errorNotice.current)?.focus();
  }, [error, busy]);
  useEffect(() => {
    const controller = new AbortController();
    setLoadError('');
    getTransactions(controller.signal)
      .then((values) => {
        if (!controller.signal.aborted) setRecords(values);
      })
      .catch(() => {
        if (!controller.signal.aborted) setLoadError('투자 기록을 불러오지 못했습니다.');
      });
    return () => controller.abort();
  }, [retry]);
  async function saveReview(event: FormEvent) {
    event.preventDefault();
    if (busy || reviewId === null || !records) return;
    if (Object.values(review).some((value) => !value.trim())) {
      setError('결과와 달랐던 점, 다음 행동을 모두 작성해 주세요.');
      return;
    }
    setBusy(true);
    setError('');
    setMessage('');
    try {
      const saved = await createReview(reviewId, review);
      setRecords((values) =>
        (values ?? []).map((r) =>
          r.id === reviewId ? { ...r, reviews: [...r.reviews, saved] } : r,
        ),
      );
      setReviewId(null);
      setReview(blankReview());
      setMessage('복기를 저장했습니다.');
    } catch (caught) {
      setError(messageOf(caught));
    } finally {
      setBusy(false);
    }
  }

  function renderRecord(r: InvestmentTransaction) {
    return (
      <article className="shared-decision" key={r.id}>
        <h2>
          {r.assetName} · {transactionLabels[r.transactionType]}
        </h2>
        <small>{r.tradedOn}</small>
        <p>{r.reason}</p>
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
        {r.reviews.map((item) => (
          <div className="shared-review" key={item.id}>
            <strong>결과 복기</strong>
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
          </div>
        ))}
        <button
          type="button"
          aria-label={`${r.assetName} ${r.tradedOn} ${transactionLabels[r.transactionType]} 결과 복기 추가`}
          aria-expanded={reviewId === r.id}
          disabled={busy || reviewId !== null}
          onClick={(event) => {
            reviewTrigger.current = event.currentTarget;
            setReviewId(r.id);
            setReview(blankReview());
            setError('');
            setMessage('');
          }}
        >
          결과 복기 추가
        </button>
        {reviewId === r.id ? (
          <form
            ref={reviewForm}
            className="shared-edit-form"
            onSubmit={(event) => void saveReview(event)}
          >
            <fieldset disabled={busy}>
              <legend>{r.assetName} 결과 복기</legend>
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
              <button className="shared-primary-button" disabled={busy}>
                {busy ? '저장 중…' : '복기 저장'}
              </button>
              <button
                type="button"
                disabled={busy}
                onClick={() => {
                  setReviewId(null);
                  setError('');
                }}
              >
                취소
              </button>
            </div>
          </form>
        ) : null}
      </article>
    );
  }
  return (
    <main className="league-page shared-page league-reviews-page">
      <header className="league-hero">
        <h1 ref={heading} tabIndex={-1}>
          투자 복기
        </h1>
        <Link className="league-text-link" to="/league/portfolio">
          {records?.length === 0 ? '보유 이유 작성하기' : '종목 관리'}
        </Link>
      </header>
      {loadError ? (
        <div className="league-state" role="alert">
          <p>{loadError}</p>
          <button onClick={() => setRetry((value) => value + 1)}>다시 불러오기</button>
        </div>
      ) : records === null ? (
        <p className="league-state" role="status">
          투자 기록을 불러오는 중입니다.
        </p>
      ) : records.length === 0 ? (
        <section className="league-empty" role="status">
          <strong>아직 투자 기록이 없습니다</strong>
        </section>
      ) : (
        <section className="league-review-list" aria-label="복기할 투자 기록">
          {records.map(renderRecord)}
        </section>
      )}
      {message ? (
        <p role="status" className="shared-feedback">
          {message}
        </p>
      ) : null}
    </main>
  );
}
