import { useEffect, useRef, useState, type FormEvent } from 'react';
import { createReview, getTransactions, recordDecision } from './api';
import { holdingPeriodLabels, transactionLabels } from './labels';
import type { SharedHolding } from './sharedPortfolioApi';
import type { HoldingPeriod, InvestmentDecisionInput, InvestmentTransaction } from './types';

const blankDecision = (): InvestmentDecisionInput => ({
  reason: '',
  expectedHoldingPeriod: null,
  expectedChange: '',
  invalidationCondition: '',
  maximumAcceptableLossRate: null,
});
const blankReview = () => ({ actualResult: '', differenceFromExpectation: '', nextAction: '' });
const messageOf = (error: unknown) =>
  error instanceof Error ? error.message : '저장하지 못했습니다. 다시 시도해 주세요.';

export function HoldingDecisions({
  holdings,
  portfolioBusy,
  publicationStatus,
  onVisibilityChange,
  onRecordsChange,
  refreshKey,
}: {
  holdings: SharedHolding[];
  portfolioBusy: boolean;
  publicationStatus: string;
  onVisibilityChange: (assetId: number, hidden: boolean) => void;
  onRecordsChange: (records: InvestmentTransaction[] | null) => void;
  refreshKey: number;
}) {
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
  }, [retry, refreshKey]);
  useEffect(() => {
    onRecordsChange(loading || loadError ? null : records);
  }, [records, loading, loadError, onRecordsChange]);
  const locked = busy || portfolioBusy;

  async function saveDecision(event: FormEvent) {
    event.preventDefault();
    if (locked || !records) return;
    const holding = holdings.find((h) => h.assetId === Number(assetId));
    if (
      !holding ||
      !decision.reason.trim() ||
      (decision.maximumAcceptableLossRate !== null &&
        (!Number.isFinite(decision.maximumAcceptableLossRate) ||
          decision.maximumAcceptableLossRate < 0 ||
          decision.maximumAcceptableLossRate > 100))
    ) {
      setError('보유 이유를 작성해 주세요. 선택 입력한 허용 하락률은 0~100%입니다.');
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
      setMessage('저장했습니다.');
    } catch (caught) {
      setError(messageOf(caught));
    } finally {
      setBusy(false);
    }
  }

  async function saveReview(event: FormEvent) {
    event.preventDefault();
    if (locked || reviewId === null || !records) return;
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

  function startDecision(id: number) {
    setAssetId(String(id));
    setDecision(blankDecision());
    requestKey.current = '';
    setWriting(true);
    setReviewId(null);
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
            autoFocus
            required
            maxLength={500}
            rows={3}
            value={decision.reason}
            placeholder="예: 실적이 좋아지고 있어 다음 분기까지 보유하려고 해요."
            onChange={(event) => setDecision({ ...decision, reason: event.target.value })}
          />
        </label>
      </fieldset>
      <fieldset disabled={locked}>
        <legend>추가 판단 정보 (선택)</legend>
        <label className="shared-reason">
          기대하는 변화
          <textarea
            maxLength={300}
            rows={2}
            value={decision.expectedChange}
            placeholder="예: 다음 분기 매출과 영업이익이 늘어날 것으로 기대해요."
            onChange={(event) => setDecision({ ...decision, expectedChange: event.target.value })}
          />
        </label>
        <label className="shared-reason">
          판단을 다시 확인할 조건
          <textarea
            maxLength={300}
            rows={2}
            value={decision.invalidationCondition}
            placeholder="예: 영업이익이 줄어들면 다시 판단해요."
            onChange={(event) =>
              setDecision({ ...decision, invalidationCondition: event.target.value })
            }
          />
        </label>
        <fieldset className="shared-choice-options shared-reason">
          <legend>예상 보유 기간</legend>
          <label>
            <input
              type="radio"
              name="holding-period"
              checked={decision.expectedHoldingPeriod === null}
              onChange={() => setDecision({ ...decision, expectedHoldingPeriod: null })}
            />
            <span>미정</span>
          </label>
          {Object.entries(holdingPeriodLabels).map(([value, label]) => (
            <label key={value}>
              <input
                type="radio"
                name="holding-period"
                checked={decision.expectedHoldingPeriod === value}
                onChange={() =>
                  setDecision({ ...decision, expectedHoldingPeriod: value as HoldingPeriod })
                }
              />
              <span>{label}</span>
            </label>
          ))}
        </fieldset>
        <label>
          허용 하락률 (%)
          <input
            type="number"
            min={0}
            max={100}
            step={0.01}
            value={decision.maximumAcceptableLossRate ?? ''}
            placeholder="선택 입력 · 0~100"
            onChange={(event) =>
              setDecision({
                ...decision,
                maximumAcceptableLossRate:
                  event.target.value === '' ? null : Number(event.target.value),
              })
            }
          />
        </label>
      </fieldset>
      {error ? (
        <p role="alert" className="league-form-error">
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
  function renderRecord(r: InvestmentTransaction) {
    return (
      <article className="shared-decision" key={r.id}>
        <h4>
          {r.assetName} · {transactionLabels[r.transactionType]}
        </h4>
        <small>
          {r.tradedOn} 기록 ·{' '}
          {holdings.some((h) => h.assetId === r.assetId && !h.hidden)
            ? '공유 범위에 따름'
            : '종목 숨김·삭제로 비공개'}
        </small>
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
          disabled={locked || writing || reviewId !== null}
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
            <fieldset disabled={locked}>
              <legend>{r.assetName} 결과 복기 (선택)</legend>
              <label className="shared-reason">
                실제 결과
                <textarea
                  required
                  maxLength={500}
                  rows={3}
                  value={review.actualResult}
                  placeholder="예: 매출은 늘었지만 주가는 기대보다 적게 올랐어요."
                  onChange={(event) => setReview({ ...review, actualResult: event.target.value })}
                />
              </label>
              <label className="shared-reason">
                예상과 달랐던 점
                <textarea
                  required
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
                  maxLength={500}
                  rows={3}
                  value={review.nextAction}
                  placeholder="예: 다음 실적에서 비용이 줄어드는지 확인해요."
                  onChange={(event) => setReview({ ...review, nextAction: event.target.value })}
                />
              </label>
            </fieldset>
            {error ? (
              <p role="alert" className="league-form-error">
                {error}
              </p>
            ) : null}
            <div className="shared-form-actions">
              <button className="shared-primary-button" disabled={locked}>
                {busy ? '저장 중…' : '복기 저장'}
              </button>
              <button
                type="button"
                disabled={locked}
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
    <section aria-label="종목별 투자 기록">
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
              <label className="shared-visibility-choice">
                <input
                  type="checkbox"
                  aria-label={`${h.assetName} 공개할 종목으로 선택`}
                  disabled={locked}
                  checked={!h.hidden}
                  onChange={(event) => onVisibilityChange(h.assetId, !event.target.checked)}
                />
                <span>공개 허용</span>
              </label>
            </header>
            <div
              className="shared-holding-details"
              aria-label={`${h.assetName} 보유 정보 · 비공개`}
            >
              <span className="shared-private-label">
                나만 보기 · {h.baseDate ? `${h.baseDate} 종가` : '가격 없음'}
              </span>
              <dl>
                <div>
                  <dt>보유수량</dt>
                  <dd>{h.quantity.toLocaleString()}주</dd>
                </div>
                <div>
                  <dt>평균 매수가</dt>
                  <dd>{h.averagePurchasePrice.toLocaleString()}원</dd>
                </div>
                <div>
                  <dt>평가금액</dt>
                  <dd>
                    {h.evaluationAmount === null
                      ? '가격 확인 중'
                      : `${h.evaluationAmount.toLocaleString()}원`}
                  </dd>
                </div>
                <div>
                  <dt>매수가 대비 수익률</dt>
                  <dd>
                    {h.returnRate === null
                      ? '집계 전'
                      : `${h.returnRate > 0 ? '+' : ''}${h.returnRate.toFixed(2)}%`}
                  </dd>
                </div>
              </dl>
            </div>
            <div className="shared-reason-heading">
              <strong className={completed ? 'shared-reason-complete' : 'shared-reason-required'}>
                {completed ? '작성 완료' : '보유 이유 필수'}
              </strong>
              <button
                type="button"
                aria-label={`${h.assetName} ${completed ? '투자 기록 추가' : '보유 이유 작성'}`}
                disabled={locked || loading || Boolean(loadError) || writing || reviewId !== null}
                onClick={() => startDecision(h.assetId)}
              >
                {completed ? '투자 기록 추가' : '보유 이유 작성'}
              </button>
            </div>
            {!ownRecords.length && h.reason ? (
              <p className="shared-reason-text">{h.reason}</p>
            ) : null}
            {writing && assetId === String(h.assetId) ? decisionForm : null}
            {ownRecords.map(renderRecord)}
          </article>
        );
      })}
      {(records ?? [])
        .filter((r) => !holdings.some((h) => h.assetId === r.assetId))
        .map(renderRecord)}
      {message ? (
        <p role="status" className="shared-feedback">
          {message}
        </p>
      ) : null}
    </section>
  );
}
