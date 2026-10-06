import { useEffect, useRef, useState, type FormEvent } from 'react';
import { createReview, getTransactions, recordDecision } from './api';
import { holdingPeriodLabels } from './labels';
import type { SharedHolding } from './sharedPortfolioApi';
import type { HoldingPeriod, InvestmentDecisionInput, InvestmentTransaction } from './types';

const blankDecision = (): InvestmentDecisionInput => ({
  reason: '',
  expectedHoldingPeriod: 'OVER_SIX_MONTHS',
  expectedChange: '',
  invalidationCondition: '',
  maximumAcceptableLossRate: 10,
});
const blankReview = () => ({ actualResult: '', differenceFromExpectation: '', nextAction: '' });
const messageOf = (error: unknown) =>
  error instanceof Error ? error.message : '저장하지 못했습니다. 다시 시도해 주세요.';

export function HoldingDecisions({ holdings }: { holdings: SharedHolding[] }) {
  const [records, setRecords] = useState<InvestmentTransaction[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [retry, setRetry] = useState(0);
  const [writing, setWriting] = useState(false);
  const [assetId, setAssetId] = useState('');
  const [decision, setDecision] = useState(blankDecision);
  const [reviewId, setReviewId] = useState<number | null>(null);
  const [review, setReview] = useState(blankReview);
  const [busy, setBusy] = useState(false);
  const requestKey = useRef('');

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setLoadError('');
    getTransactions(controller.signal)
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
  }, [retry]);

  async function saveDecision(event: FormEvent) {
    event.preventDefault();
    if (busy || !records) return;
    const holding = holdings.find((h) => h.assetId === Number(assetId));
    if (
      !holding ||
      !decision.reason.trim() ||
      !decision.expectedChange.trim() ||
      !decision.invalidationCondition.trim() ||
      !Number.isFinite(decision.maximumAcceptableLossRate) ||
      decision.maximumAcceptableLossRate < 0 ||
      decision.maximumAcceptableLossRate > 100
    ) {
      setError('종목과 판단 내용을 모두 확인해 주세요. 허용 하락률은 0~100%입니다.');
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
        decision,
      });
      setRecords((values) => [saved, ...(values ?? []).filter((r) => r.id !== saved.id)]);
      requestKey.current = '';
      setWriting(false);
      setDecision(blankDecision());
      setMessage('판단을 기록했어요. 보유수량과 매수가는 변경하지 않았습니다.');
    } catch (caught) {
      setError(messageOf(caught));
    } finally {
      setBusy(false);
    }
  }

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
      setMessage('복기를 추가했어요. 처음 작성한 판단은 그대로 유지됩니다.');
    } catch (caught) {
      setError(messageOf(caught));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="shared-panel" aria-labelledby="holding-decisions-title">
      <h2 id="holding-decisions-title">판단과 복기</h2>
      <p>
        왜 보유하는지 기록하고, 나중에 결과를 돌아보세요. 기록만 추가하며 보유수량은 바꾸지
        않습니다.
      </p>
      <p>
        최초 판단은 수정하지 않습니다. 상세 공개에 동의한 기록은 누구나 바로 볼 수 있으며, 숨긴
        종목의 판단과 복기는 공개하지 않습니다.
      </p>
      {loading ? (
        <p role="status">판단 기록을 불러오는 중입니다.</p>
      ) : loadError ? (
        <div role="alert">
          <p>{loadError}</p>
          <button type="button" onClick={() => setRetry((v) => v + 1)}>
            판단 기록 다시 불러오기
          </button>
        </div>
      ) : (
        <>
          <button
            type="button"
            disabled={busy || writing || reviewId !== null || holdings.length === 0}
            onClick={() => {
              setAssetId(String(holdings[0]?.assetId ?? ''));
              setDecision(blankDecision());
              requestKey.current = '';
              setWriting(true);
              setReviewId(null);
              setError('');
              setMessage('');
            }}
          >
            보유 판단 남기기
          </button>
          {holdings.length === 0 ? <p>원본 포트폴리오에서 공유용 자산을 먼저 가져오세요.</p> : null}
          {writing ? (
            <form className="shared-edit-form" onSubmit={(event) => void saveDecision(event)}>
              <fieldset disabled={busy}>
                <legend>보유 판단 작성</legend>
                <label>
                  기록할 종목
                  <select required value={assetId} onChange={(e) => setAssetId(e.target.value)}>
                    {holdings.map((h) => (
                      <option key={h.assetId} value={h.assetId}>
                        {h.assetName}
                        {h.hidden ? ' · 숨김' : ''}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  예상 보유 기간
                  <select
                    value={decision.expectedHoldingPeriod}
                    onChange={(e) =>
                      setDecision({
                        ...decision,
                        expectedHoldingPeriod: e.target.value as HoldingPeriod,
                      })
                    }
                  >
                    {Object.entries(holdingPeriodLabels).map(([key, value]) => (
                      <option key={key} value={key}>
                        {value}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="shared-reason">
                  보유하는 이유
                  <textarea
                    required
                    maxLength={500}
                    rows={3}
                    value={decision.reason}
                    onChange={(e) => setDecision({ ...decision, reason: e.target.value })}
                  />
                </label>
                <label>
                  기대하는 변화
                  <textarea
                    required
                    maxLength={300}
                    rows={3}
                    value={decision.expectedChange}
                    onChange={(e) => setDecision({ ...decision, expectedChange: e.target.value })}
                  />
                </label>
                <label>
                  판단을 다시 확인할 조건
                  <textarea
                    required
                    maxLength={300}
                    rows={3}
                    value={decision.invalidationCondition}
                    onChange={(e) =>
                      setDecision({ ...decision, invalidationCondition: e.target.value })
                    }
                  />
                </label>
                <label>
                  허용 하락률 (%)
                  <input
                    required
                    type="number"
                    min={0}
                    max={100}
                    step={0.01}
                    value={decision.maximumAcceptableLossRate}
                    onChange={(e) =>
                      setDecision({
                        ...decision,
                        maximumAcceptableLossRate: Number(e.target.value),
                      })
                    }
                  />
                </label>
              </fieldset>
              <button disabled={busy}>{busy ? '저장 중…' : '판단 기록 저장'}</button>
              <button type="button" disabled={busy} onClick={() => setWriting(false)}>
                취소
              </button>
            </form>
          ) : null}
          {records?.length === 0 ? (
            <p>아직 판단 기록이 없어요. 첫 보유 판단을 남겨 보세요.</p>
          ) : null}
          {records?.map((r) => {
            const holding = holdings.find((h) => h.assetId === r.assetId);
            return (
              <article className="shared-decision" key={r.id}>
                <h3>
                  {r.assetName} ·{' '}
                  {r.transactionType === 'HOLD'
                    ? '보유 판단'
                    : r.transactionType === 'BUY'
                      ? '매수 판단'
                      : '매도 판단'}
                </h3>
                <small>
                  {r.tradedOn} 기록 ·{' '}
                  {!holding || holding.hidden ? '종목 숨김·삭제로 비공개' : '공개 설정에 따름'}
                </small>
                <p>{r.reason}</p>
                <details>
                  <summary>예상과 확인 조건</summary>
                  <dl>
                    <div>
                      <dt>보유 기간</dt>
                      <dd>{holdingPeriodLabels[r.expectedHoldingPeriod]}</dd>
                    </div>
                    <div>
                      <dt>기대 변화</dt>
                      <dd>{r.expectedChange}</dd>
                    </div>
                    <div>
                      <dt>재확인 조건</dt>
                      <dd>{r.invalidationCondition}</dd>
                    </div>
                    <div>
                      <dt>허용 하락률</dt>
                      <dd>{r.maximumAcceptableLossRate}%</dd>
                    </div>
                  </dl>
                </details>
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
                  disabled={busy}
                  onClick={() => {
                    setReviewId(r.id);
                    setReview(blankReview());
                    setWriting(false);
                    setError('');
                    setMessage('');
                  }}
                >
                  결과 복기 추가
                </button>
                {reviewId === r.id ? (
                  <form className="shared-edit-form" onSubmit={(event) => void saveReview(event)}>
                    <fieldset disabled={busy}>
                      <legend>{r.assetName} 결과 복기</legend>
                      <label className="shared-reason">
                        실제 결과
                        <textarea
                          required
                          maxLength={500}
                          rows={3}
                          value={review.actualResult}
                          onChange={(e) => setReview({ ...review, actualResult: e.target.value })}
                        />
                      </label>
                      <label>
                        예상과 달랐던 점
                        <textarea
                          required
                          maxLength={500}
                          rows={3}
                          value={review.differenceFromExpectation}
                          onChange={(e) =>
                            setReview({ ...review, differenceFromExpectation: e.target.value })
                          }
                        />
                      </label>
                      <label>
                        다음 행동
                        <textarea
                          required
                          maxLength={500}
                          rows={3}
                          value={review.nextAction}
                          onChange={(e) => setReview({ ...review, nextAction: e.target.value })}
                        />
                      </label>
                    </fieldset>
                    <button disabled={busy}>{busy ? '저장 중…' : '복기 저장'}</button>
                    <button type="button" disabled={busy} onClick={() => setReviewId(null)}>
                      취소
                    </button>
                  </form>
                ) : null}
              </article>
            );
          })}
        </>
      )}
      {error ? <p role="alert">{error}</p> : null}
      {message ? <p role="status">{message}</p> : null}
    </section>
  );
}
