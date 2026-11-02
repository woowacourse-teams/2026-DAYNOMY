import {
  memo,
  useCallback,
  useEffect,
  useId,
  useRef,
  useState,
  type FormEvent,
  type ReactNode,
} from 'react';
import {
  createReview,
  getInvestmentDecisions,
  getReviewGuide,
  updateDecisionReason,
  updateReview,
} from './api';
import { ApiError } from '../../api/client';
import { leagueErrorType, trackLeagueEvent } from './analytics';
import { holdingPeriodLabels, reviewDateLabel, transactionLabels } from './labels';
import type {
  InvestmentDecisionRecord,
  InvestmentReview,
  ReviewGuide,
  ReviewGuideQuestion,
} from './types';

const blankReview = () => ({ actualResult: '', differenceFromExpectation: '', nextAction: '' });
const messageOf = (error: unknown) =>
  error instanceof Error ? error.message : '저장하지 못했습니다. 다시 시도해 주세요.';

function GuideQuestion({
  question,
  evidence,
  evidencePrefix,
}: {
  question: ReviewGuideQuestion;
  evidence: ReviewGuide['evidence'];
  evidencePrefix: string;
}) {
  return (
    <>
      {question.text}{' '}
      {question.evidenceIds.map((id) => {
        const index = evidence.findIndex((item) => item.id === id);
        return (
          <a
            key={id}
            href={`#${evidencePrefix}-review-evidence-${id}`}
            aria-label={`근거 ${index + 1} 보기`}
            onClick={(event) => {
              const target = document.getElementById(`${evidencePrefix}-review-evidence-${id}`);
              const disclosure = target?.closest('details');
              if (!disclosure) return;
              event.preventDefault();
              disclosure.open = true;
              target?.scrollIntoView?.({ block: 'nearest' });
            }}
          >
            [{index + 1}]
          </a>
        );
      })}
    </>
  );
}

const InvestmentRecordCard = memo(function InvestmentRecordCard({
  r,
  reviewId,
  editingReason,
  editingReviewId,
  editFields,
  blocked,
  reviewPage,
  onOpenReason,
  onOpenReview,
}: {
  r: InvestmentDecisionRecord;
  reviewId: number | null;
  editingReason: boolean;
  editingReviewId: number | null;
  editFields: ReactNode;
  blocked: boolean;
  reviewPage: boolean;
  onOpenReason: (record: InvestmentDecisionRecord, trigger: HTMLButtonElement) => void;
  onOpenReview: (
    record: InvestmentDecisionRecord,
    review: InvestmentReview | null,
    trigger: HTMLButtonElement,
  ) => void;
}) {
  const criteria =
    r.expectedHoldingPeriod ||
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
    ) : null;
  const newReviewAction = (
    <button
      className={reviewPage ? 'shared-primary-button' : 'shared-text-action'}
      type="button"
      aria-label={`${r.assetName} ${r.tradedOn} ${transactionLabels[r.transactionType]} 복기 작성`}
      hidden={reviewId === r.id && !editingReason && (reviewPage || editingReviewId === null)}
      aria-expanded={reviewId === r.id && !editingReason && editingReviewId === null}
      disabled={blocked}
      onClick={(event) => onOpenReview(r, null, event.currentTarget)}
    >
      복기 작성
    </button>
  );
  return (
    <article className={`shared-decision${reviewPage ? ' review-page-record' : ''}`}>
      {reviewPage ? (
        <header className="review-page-record-header">
          <div>
            <h3>{r.assetName}</h3>
            <small>
              {r.tradedOn} · {transactionLabels[r.transactionType]}
            </small>
          </div>
          <div className="review-page-record-actions">
            <span className="review-page-record-state">
              {reviewId === r.id
                ? editingReason
                  ? '판단 수정 중'
                  : editingReviewId === null
                    ? '복기 작성 중'
                    : '복기 수정 중'
                : r.reviews.length
                  ? `복기 ${r.reviews.length}건`
                  : '복기 대기'}
            </span>
            {newReviewAction}
          </div>
        </header>
      ) : (
        <>
          <h3>{r.assetName}</h3>
          <small>
            {r.tradedOn} · {transactionLabels[r.transactionType]}
          </small>
        </>
      )}
      <div className="shared-record-body">
        <div className="shared-record-reason">
          <div className="shared-record-heading">
            <strong>보유 이유</strong>
            <button
              className="shared-text-action"
              type="button"
              aria-label={`${r.assetName} ${r.tradedOn} ${transactionLabels[r.transactionType]} 보유 이유 수정`}
              aria-expanded={reviewId === r.id && editingReason}
              disabled={blocked}
              onClick={(event) => onOpenReason(r, event.currentTarget)}
            >
              수정
            </button>
          </div>
          {reviewId === r.id && editingReason ? editFields : <p>{r.reason}</p>}
          {reviewPage && criteria ? (
            <details className="review-page-criteria">
              <summary>판단 기준 보기</summary>
              {criteria}
            </details>
          ) : (
            criteria
          )}
        </div>
        <div className="shared-record-reviews">
          {r.reviews.length === 0 && reviewId !== r.id ? (
            reviewPage ? (
              <div className="review-page-empty">
                <strong>그때의 판단, 지금은 어떤가요?</strong>
                <p>실제 결과와 달라진 생각을 남겨보세요. 관련 기사와 AI 질문이 작성을 도와줘요.</p>
              </div>
            ) : (
              <p className="shared-review-empty">
                아직 복기가 없어요. 보유 후 달라진 생각을 남겨보세요.
              </p>
            )
          ) : null}
          {r.reviews.map((item) => (
            <div className="shared-review" key={item.id}>
              <div className="shared-record-heading">
                <strong>복기</strong>
                <button
                  className="shared-text-action"
                  type="button"
                  aria-label={`${r.assetName} ${r.tradedOn} ${transactionLabels[r.transactionType]} 복기 수정`}
                  aria-expanded={reviewId === r.id && !editingReason && editingReviewId === item.id}
                  disabled={blocked}
                  onClick={(event) => onOpenReview(r, item, event.currentTarget)}
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
                  <small>{reviewDateLabel(item)}</small>
                </>
              )}
            </div>
          ))}
          {!reviewPage ? <div className="shared-holding-actions">{newReviewAction}</div> : null}

          {reviewId === r.id && !editingReason && editingReviewId === null ? editFields : null}
        </div>
      </div>
    </article>
  );
});

export function InvestmentRecords({
  records,
  onRecordChange,
  reviewId,
  setReviewId,
  disabled = false,
  surface = 'reviews',
  editorOnly = false,
  autoGuide = false,
}: {
  records: InvestmentDecisionRecord[];
  onRecordChange: (record: InvestmentDecisionRecord) => void;
  reviewId: number | null;
  setReviewId: (id: number | null) => void;
  disabled?: boolean;
  surface?: 'reviews' | 'decisions';
  editorOnly?: boolean;
  autoGuide?: boolean;
}) {
  const reviewPage = surface === 'reviews' && !editorOnly;
  const compactGuide = editorOnly || reviewPage;
  const [editingReviewId, setEditingReviewId] = useState<number | null>(null);
  const [editingReason, setEditingReason] = useState(false);
  const [reason, setReason] = useState('');
  const [review, setReview] = useState(blankReview);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [guide, setGuide] = useState<ReviewGuide | null>(null);
  const [guideLoading, setGuideLoading] = useState(false);
  const [guideError, setGuideError] = useState('');
  const [guideConflictId, setGuideConflictId] = useState<number | null>(null);
  const [guideUsed, setGuideUsed] = useState(false);
  const guideAbort = useRef<AbortController | null>(null);
  const automaticGuideId = useRef<number | null>(null);
  const errorId = useId();
  const guideId = useId();
  const editForm = useRef<HTMLFormElement>(null);
  const reviewHeading = useRef<HTMLHeadingElement>(null);
  const errorNotice = useRef<HTMLParagraphElement>(null);
  const editTrigger = useRef<HTMLButtonElement | null>(null);
  useEffect(() => () => guideAbort.current?.abort(), [reviewId]);

  const resetGuide = useCallback(() => {
    guideAbort.current?.abort();
    guideAbort.current = null;
    automaticGuideId.current = null;
    setGuide(null);
    setGuideLoading(false);
    setGuideError('');
    setGuideConflictId(null);
    setGuideUsed(false);
  }, []);

  const requestGuide = useCallback(
    async (transactionId: number) => {
      if (guideAbort.current || disabled || busy) return;
      const controller = new AbortController();
      guideAbort.current = controller;
      setGuideLoading(true);
      setGuideError('');
      const startedAt = performance.now();
      trackLeagueEvent('league_action', { surface, action: 'review_guide', status: 'submitted' });
      try {
        const result = await getReviewGuide(transactionId, controller.signal);
        if (controller.signal.aborted) return;
        setGuide(result);
        setGuideUsed(true);
        trackLeagueEvent('league_action', {
          surface,
          action: 'review_guide',
          status: 'success',
          retrieval_status: result.retrievalStatus,
          duration_ms: Math.round(performance.now() - startedAt),
        });
      } catch (caught) {
        if (controller.signal.aborted) return;
        if (caught instanceof ApiError && caught.code === 'REVIEW_GUIDE_ALREADY_REVIEWED') {
          setGuideConflictId(transactionId);
          try {
            const recordsNow = await getInvestmentDecisions(controller.signal);
            if (!controller.signal.aborted) {
              const current = recordsNow.find((record) => record.id === transactionId);
              if (current) onRecordChange(current);
            }
          } catch {
            // 충돌 시 버튼은 숨기고 기존 초안은 유지한다.
          }
        }
        if (controller.signal.aborted) return;
        setGuideError(
          caught instanceof ApiError && caught.status === 409
            ? '이미 복기가 저장된 기록입니다. 작성 중인 내용은 유지됩니다.'
            : 'AI 질문을 만들지 못했습니다. 직접 복기를 작성하거나 다시 시도해 주세요.',
        );
        trackLeagueEvent('league_action', {
          surface,
          action: 'review_guide',
          status: 'error',
          error_type: leagueErrorType(caught),
          duration_ms: Math.round(performance.now() - startedAt),
        });
      } finally {
        if (!controller.signal.aborted) {
          setGuideLoading(false);
          guideAbort.current = null;
        }
      }
    },
    [disabled, busy, onRecordChange, surface],
  );

  useEffect(() => {
    if (
      !autoGuide ||
      reviewId === null ||
      editingReason ||
      editingReviewId !== null ||
      disabled ||
      busy ||
      automaticGuideId.current === reviewId ||
      guideAbort.current ||
      guide?.transactionId === reviewId ||
      !records.some((record) => record.id === reviewId && record.reviews.length === 0)
    )
      return;
    automaticGuideId.current = reviewId;
    void requestGuide(reviewId);
  }, [
    autoGuide,
    reviewId,
    editingReason,
    editingReviewId,
    disabled,
    busy,
    guide,
    records,
    requestGuide,
  ]);
  useEffect(() => {
    if (!reviewPage || reviewId === null || editingReason) return;
    reviewHeading.current?.focus({ preventScroll: true });
    if (window.innerWidth <= 760) reviewHeading.current?.scrollIntoView?.({ block: 'start' });
  }, [reviewPage, reviewId, editingReason, editingReviewId]);
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
    if (Object.values(review).some((value) => !value.trim() || value.length > 500)) {
      setError('결과와 달랐던 점, 다음 행동을 모두 작성해 주세요.');
      return;
    }
    setBusy(true);
    trackLeagueEvent('league_action', {
      surface,
      action: editingReviewId === null ? 'save_review' : 'edit_review',
      status: 'submitted',
      guide_used: guideUsed || undefined,
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
      resetGuide();
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

  const openReason = useCallback(
    (record: InvestmentDecisionRecord, trigger: HTMLButtonElement) => {
      trackLeagueEvent('league_action', { surface, action: 'open_reason', status: 'clicked' });
      editTrigger.current = trigger;
      setEditingReason(true);
      setReason(record.reason);
      setReviewId(record.id);
      setError('');
      setMessage('');
      resetGuide();
    },
    [setReviewId, surface, resetGuide],
  );
  const openReview = useCallback(
    (
      record: InvestmentDecisionRecord,
      item: InvestmentReview | null,
      trigger: HTMLButtonElement,
    ) => {
      trackLeagueEvent('league_action', { surface, action: 'open_review', status: 'clicked' });
      editTrigger.current = trigger;
      setEditingReason(false);
      setReviewId(record.id);
      setEditingReviewId(item?.id ?? null);
      setReview(
        item
          ? {
              actualResult: item.actualResult,
              differenceFromExpectation: item.differenceFromExpectation,
              nextAction: item.nextAction,
            }
          : blankReview(),
      );
      setError('');
      setMessage('');
      resetGuide();
    },
    [setReviewId, surface, resetGuide],
  );

  function renderRecord(r: InvestmentDecisionRecord) {
    const activeGuide = guide?.transactionId === r.id ? guide : null;
    const guideEvidence = activeGuide ? (
      <div className="shared-review-evidence">
        {!compactGuide ? <strong>확인한 기사</strong> : null}
        <ol>
          {activeGuide.evidence.map((item) => (
            <li key={item.id} id={`${guideId}-review-evidence-${item.id}`}>
              <a href={item.href} target="_blank" rel="noopener noreferrer">
                {item.title}
              </a>
              <time dateTime={item.publishedAt}>
                {new Date(item.publishedAt).toLocaleDateString('ko-KR')}
              </time>
              <p>{item.excerpt}</p>
            </li>
          ))}
        </ol>
      </div>
    ) : null;
    const guideBasis = activeGuide?.basis ? (
      <p className="shared-review-guide-basis">
        {activeGuide.basis.weekStart} 주 · {activeGuide.basis.asOfDate} 종가 기준 · 포트폴리오 전체
        주간 수익률 {activeGuide.basis.portfolioWeeklyReturnRate}%
      </p>
    ) : !compactGuide ? (
      <p>확정된 주간 성과가 없습니다.</p>
    ) : null;
    const guidePanel =
      !editingReason &&
      editingReviewId === null &&
      (r.reviews.length === 0 || guideConflictId === r.id) ? (
        <div className={editorOnly ? 'review-coach-guide' : 'shared-review-guide'}>
          {autoGuide && guideLoading ? (
            <p role="status" className="review-coach-guide-status">
              기사와 AI 질문을 준비하고 있어요. 먼저 작성해도 괜찮아요.
            </p>
          ) : null}
          {activeGuide ? (
            <>
              {!compactGuide ? <strong>기사 근거와 AI 복기 질문</strong> : null}
              {activeGuide.retrievalStatus === 'NO_EVIDENCE' ? (
                <>
                  <p>
                    기록 이후 확인할 수 있는 관련 발행 기사가 없습니다. 아래 질문은 투자 기록만
                    바탕으로 만들었어요.
                  </p>
                  {guideBasis}
                </>
              ) : compactGuide ? (
                <details className={editorOnly ? 'review-coach-evidence' : 'review-page-evidence'}>
                  <summary>
                    <span>AI가 찾은 참고 기사 · {activeGuide.evidence.length}개</span>
                    <span className="review-coach-resource-preview">
                      {activeGuide.evidence[0]?.title}
                    </span>
                  </summary>
                  {guideEvidence}
                  {guideBasis}
                </details>
              ) : (
                <>
                  {guideEvidence}
                  {guideBasis}
                </>
              )}
              {!autoGuide ? (
                <button
                  className={reviewPage ? 'shared-text-action' : undefined}
                  type="button"
                  onClick={() => setGuide(null)}
                >
                  질문 닫기
                </button>
              ) : null}
            </>
          ) : (
            <>
              <p className={editorOnly ? 'review-coach-ai-notice' : undefined}>
                {compactGuide
                  ? '보유 이유·기대 내용과 기사 일부를 외부 AI에 보내 참고 질문을 만들어요.'
                  : '보유 이유와 기대 내용, 검색된 DAYNOMY 기사 일부를 외부 AI 처리 서비스에 보내 근거가 연결된 복기 질문을 만듭니다. 복기 내용은 직접 작성해 주세요.'}
              </p>
              {r.reviews.length === 0 && guideConflictId !== r.id && (!autoGuide || guideError) ? (
                <button
                  type="button"
                  disabled={disabled || busy || guideLoading}
                  onClick={() => void requestGuide(r.id)}
                >
                  {guideLoading
                    ? '근거 찾는 중…'
                    : autoGuide
                      ? '근거 다시 찾기'
                      : '근거 찾아 복기하기'}
                </button>
              ) : null}
            </>
          )}
          {guideLoading && !autoGuide ? (
            <p role="status">기사를 찾고 AI 복기 질문을 만들고 있습니다.</p>
          ) : null}
          {guideError ? (
            <p role="alert" className="league-form-error">
              {guideError}
            </p>
          ) : null}
        </div>
      ) : null;
    const editFields =
      reviewId === r.id ? (
        <form
          ref={editForm}
          className="shared-edit-form"
          onSubmit={(event) => void (editingReason ? saveReason(event) : saveReview(event))}
        >
          <div className={editorOnly ? 'review-coach-scroll' : undefined}>
            {reviewPage && !editingReason ? (
              <>
                <div className="review-page-compose-heading">
                  <h4 ref={reviewHeading} tabIndex={-1}>
                    {editingReviewId === null ? '나의 복기' : '복기 수정'}
                  </h4>
                  <span>{Object.values(review).filter((value) => value.trim()).length}/3 작성</span>
                </div>
                {guidePanel}
              </>
            ) : null}
            {editorOnly ? (
              <>
                <details className="review-coach-context">
                  <summary>
                    <span>당시의 판단</span>
                    <span className="review-coach-resource-preview">{r.reason}</span>
                  </summary>
                  <dl>
                    <div>
                      <dt>보유 이유</dt>
                      <dd>{r.reason}</dd>
                    </div>
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
                  </dl>
                </details>
                {guidePanel}
                <div className="review-coach-compose-heading">
                  <h3>나의 복기</h3>
                  <span>{Object.values(review).filter((value) => value.trim()).length}/3 작성</span>
                </div>
              </>
            ) : null}
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
                    autoFocus={!editorOnly}
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
                  <div className="shared-reason">
                    <label htmlFor={`${guideId}-actual`}>실제 결과</label>
                    {activeGuide ? (
                      <p id={`${guideId}-actual-question`} className="shared-review-question">
                        <GuideQuestion
                          question={activeGuide.questions.actualResult}
                          evidence={activeGuide.evidence}
                          evidencePrefix={guideId}
                        />
                      </p>
                    ) : null}
                    <textarea
                      id={`${guideId}-actual`}
                      required
                      aria-invalid={(Boolean(error) && !review.actualResult.trim()) || undefined}
                      aria-describedby={
                        [activeGuide ? `${guideId}-actual-question` : '', error ? errorId : '']
                          .filter(Boolean)
                          .join(' ') || undefined
                      }
                      maxLength={500}
                      rows={compactGuide ? 2 : 3}
                      autoFocus={!editorOnly && !reviewPage}
                      value={review.actualResult}
                      placeholder="예: 매출은 늘었지만 주가는 기대보다 적게 올랐어요."
                      onChange={(event) =>
                        setReview({ ...review, actualResult: event.target.value })
                      }
                    />
                  </div>
                  <div className="shared-reason">
                    <label htmlFor={`${guideId}-difference`}>예상과 달랐던 점</label>
                    {activeGuide ? (
                      <p id={`${guideId}-difference-question`} className="shared-review-question">
                        <GuideQuestion
                          question={activeGuide.questions.differenceFromExpectation}
                          evidence={activeGuide.evidence}
                          evidencePrefix={guideId}
                        />
                      </p>
                    ) : null}
                    <textarea
                      id={`${guideId}-difference`}
                      required
                      aria-invalid={
                        (Boolean(error) && !review.differenceFromExpectation.trim()) || undefined
                      }
                      aria-describedby={
                        [activeGuide ? `${guideId}-difference-question` : '', error ? errorId : '']
                          .filter(Boolean)
                          .join(' ') || undefined
                      }
                      maxLength={500}
                      rows={compactGuide ? 2 : 3}
                      value={review.differenceFromExpectation}
                      placeholder="예: 비용 증가의 영향이 컸어요."
                      onChange={(event) =>
                        setReview({ ...review, differenceFromExpectation: event.target.value })
                      }
                    />
                  </div>
                  <div className="shared-reason">
                    <label htmlFor={`${guideId}-next`}>다음 행동</label>
                    {activeGuide ? (
                      <p id={`${guideId}-next-question`} className="shared-review-question">
                        <GuideQuestion
                          question={activeGuide.questions.nextAction}
                          evidence={activeGuide.evidence}
                          evidencePrefix={guideId}
                        />
                      </p>
                    ) : null}
                    <textarea
                      id={`${guideId}-next`}
                      required
                      aria-invalid={(Boolean(error) && !review.nextAction.trim()) || undefined}
                      aria-describedby={
                        [activeGuide ? `${guideId}-next-question` : '', error ? errorId : '']
                          .filter(Boolean)
                          .join(' ') || undefined
                      }
                      maxLength={500}
                      rows={compactGuide ? 2 : 3}
                      value={review.nextAction}
                      placeholder="예: 다음 실적에서 비용이 줄어드는지 확인해요."
                      onChange={(event) => setReview({ ...review, nextAction: event.target.value })}
                    />
                  </div>
                </>
              )}
            </fieldset>
            {!compactGuide ? guidePanel : null}
          </div>
          <div
            className={
              editorOnly ? 'review-coach-footer' : reviewPage ? 'review-page-footer' : undefined
            }
          >
            {!editingReason ? (
              <p className="shared-privacy-note">
                {editorOnly
                  ? '공개 설정에 따라 다른 투자자에게도 보일 수 있어요.'
                  : '저장한 복기는 현재 공개 설정에 따라 다른 사람에게 보일 수 있습니다.'}
              </p>
            ) : null}
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
                  if (editorOnly) return;
                  setEditingReason(false);
                  setEditingReviewId(null);
                  setError('');
                  resetGuide();
                }}
              >
                {editorOnly ? '나중에' : '취소'}
              </button>
            </div>
          </div>
        </form>
      ) : null;
    if (editorOnly) return editFields;
    return (
      <InvestmentRecordCard
        key={r.id}
        r={r}
        reviewId={reviewId}
        editingReason={editingReason}
        editingReviewId={editingReviewId}
        editFields={editFields}
        blocked={disabled || busy || reviewId !== null}
        reviewPage={reviewPage}
        onOpenReason={openReason}
        onOpenReview={openReview}
      />
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
