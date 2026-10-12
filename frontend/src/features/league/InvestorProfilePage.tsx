import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { trackEvent } from '../../analytics';
import { useAuth } from '../../hooks/useLoginStatus';
import { followInvestor, getInvestorDetail, getPublicInvestor, unfollowInvestor } from './api';
import {
  collectionLabels,
  concentrationLabel,
  experienceLabels,
  formatRate,
  holdingPeriodLabels,
  riskLabels,
  transactionLabels,
} from './labels';
import { DailyReturns } from './DailyReturns';
import { InvestorAvatar } from './InvestorAvatar';
import type { InvestorDetail, PublicInvestor } from './types';
import './league.css';

export function InvestorProfilePage() {
  const { publicId = '' } = useParams();
  const { pathname, search, hash, state } = useLocation();
  const viewSource = ['ranking', 'following'].includes(state?.source) ? state.source : 'direct';
  const viewRankBucket = ['top_3', 'top_10', 'other'].includes(state?.rankBucket)
    ? state.rankBucket
    : 'unknown';
  const { isLoggedIn } = useAuth();
  const [investor, setInvestor] = useState<PublicInvestor | null>(null);
  const [detail, setDetail] = useState<InvestorDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [followSaving, setFollowSaving] = useState(false);
  const [retry, setRetry] = useState(0);
  const [detailError, setDetailError] = useState('');
  const [weekStart, setWeekStart] = useState('');
  const [dailyOpen, setDailyOpen] = useState(false);

  const historyScroll = useRef<HTMLDivElement>(null);
  const dailyContent = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (dailyOpen) {
      dailyContent.current?.focus({ preventScroll: true });
      dailyContent.current?.scrollIntoView?.({ block: 'nearest' });
    }
  }, [dailyOpen]);

  useEffect(() => {
    const chart = historyScroll.current;
    if (chart) chart.scrollLeft = chart.scrollWidth;
  }, [investor?.publicId, hash, dailyOpen]);

  useEffect(() => {
    if (publicId)
      trackEvent('view_investor_profile', {
        source: viewSource,
        rank_bucket: viewRankBucket,
      });
  }, [publicId, viewSource, viewRankBucket]);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    setInvestor(null);
    setWeekStart('');
    setDailyOpen(false);
    setDetail(null);
    setDetailError('');
    setDetailLoading(false);
    getPublicInvestor(publicId, controller.signal)
      .then((value) => {
        if (controller.signal.aborted) return;
        setInvestor(value);
        if (!value.detailAvailable) return;
        setDetailLoading(true);
        void getInvestorDetail(publicId, controller.signal)
          .then((details) => {
            if (!controller.signal.aborted) setDetail(details);
          })
          .catch(() => {
            if (!controller.signal.aborted)
              setDetailError('상세 정보를 불러오지 못했습니다. 저장된 내용이 없는 것은 아니에요.');
          })
          .finally(() => {
            if (!controller.signal.aborted) setDetailLoading(false);
          });
      })
      .catch((caught: unknown) => {
        if (!controller.signal.aborted)
          setError(caught instanceof Error ? caught.message : '투자자를 불러오지 못했습니다.');
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [isLoggedIn, publicId, retry]);

  async function toggleFollow() {
    if (!investor || followSaving) return;
    setFollowSaving(true);
    try {
      if (investor.followed) await unfollowInvestor(investor.publicId);
      else await followInvestor(investor.publicId);
      setInvestor({ ...investor, followed: !investor.followed });
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : '팔로우 상태를 변경하지 못했습니다.');
    } finally {
      setFollowSaving(false);
    }
  }

  if (loading)
    return <main className="league-page league-state">투자 기록을 불러오고 있습니다.</main>;
  if (error && !investor)
    return (
      <main className="league-page league-state" role="alert">
        <p>{error}</p>
        <button type="button" onClick={() => setRetry((n) => n + 1)}>
          다시 불러오기
        </button>
      </main>
    );
  if (!investor) return null;

  const activeSection = !investor.detailAvailable
    ? 'performance'
    : hash === '#investment-details'
      ? 'holdings'
      : hash === '#investment-decisions'
        ? 'records'
        : 'performance';
  const history = [...investor.history].sort((a, b) => a.weekStart.localeCompare(b.weekStart));
  const historyRange = Math.max(
    1,
    ...history.map((point) => Math.abs(point.weeklyReturnRate ?? 0)),
  );
  function historyLabel(point: PublicInvestor['history'][number]) {
    if (
      point.collectionStatus === 'CALENDAR_PENDING' ||
      point.collectionStatus === 'NO_TRADING_DAYS'
    )
      return collectionLabels[point.collectionStatus];
    return point.weeklyReturnRate === null
      ? point.status === 'NOT_PARTICIPATING'
        ? '미참여'
        : '집계 대기'
      : formatRate(point.weeklyReturnRate);
  }
  function showDaily(week: string) {
    setWeekStart(week);
    setDailyOpen(true);
  }

  return (
    <main className="league-page league-investor-page">
      <section className="league-investor-header">
        <div>
          <InvestorAvatar name={investor.displayName} imageUrl={investor.profileImageUrl} />
          <div>
            <h1>{investor.displayName}</h1>
            {investor.bio ? <p>{investor.bio}</p> : null}
            <small>
              {experienceLabels[investor.experienceLevel]} · {riskLabels[investor.riskProfile]}
            </small>
          </div>
        </div>
        {isLoggedIn ? (
          <button type="button" disabled={followSaving} onClick={() => void toggleFollow()}>
            {investor.followed ? '팔로잉' : '팔로우'}
          </button>
        ) : (
          <Link to={`/login?returnTo=${encodeURIComponent(`${pathname}${search}${hash}`)}`}>
            로그인하고 팔로우
          </Link>
        )}
      </section>

      <nav className="league-section-nav" aria-label="투자자 상세 탐색">
        <Link
          to="#performance"
          state={state}
          aria-current={activeSection === 'performance' ? 'page' : undefined}
        >
          성과
        </Link>
        {investor.detailAvailable ? (
          <>
            <Link
              to="#investment-details"
              state={state}
              aria-current={activeSection === 'holdings' ? 'page' : undefined}
            >
              공개 종목
            </Link>
            <Link
              to="#investment-decisions"
              state={state}
              aria-current={activeSection === 'records' ? 'page' : undefined}
            >
              투자 기록
            </Link>
          </>
        ) : null}
      </nav>
      {error ? (
        <p className="league-inline-error" role="alert">
          {error}
        </p>
      ) : null}
      <div hidden={activeSection !== 'performance'}>
        {investor.performance.weeklyRank !== undefined ? (
          <p className="league-investor-rank">
            이번 주 순위 ·{' '}
            {investor.performance.weeklyRank === null
              ? '참여 순위 없음'
              : `${investor.performance.weeklyRank}위`}
          </p>
        ) : null}

        <section className="league-metric-cards" id="performance" aria-label="성과와 위험 지표">
          <div className="league-primary-metrics">
            <article>
              <span>이번 주 수익률</span>
              <strong
                className={
                  investor.performance.weeklyReturnRate === null
                    ? ''
                    : investor.performance.weeklyReturnRate >= 0
                      ? 'positive'
                      : 'negative'
                }
              >
                {investor.performance.weeklyReturnRate === null
                  ? '집계 대기'
                  : formatRate(investor.performance.weeklyReturnRate)}
              </strong>
            </article>
            <article>
              <span>전체 누적 수익률</span>
              <strong>{formatRate(investor.performance.cumulativeReturnRate)}</strong>
            </article>
            <article>
              <span>최대 하락률 · 8주</span>
              <strong>
                {investor.performance.maxDrawdownRate === null
                  ? '집계 대기'
                  : formatRate(investor.performance.maxDrawdownRate, false)}
              </strong>
            </article>
          </div>
        </section>

        <p className="league-calculation-note">
          {investor.performance.trackingStartedOn
            ? `성과 기록 시작 ${investor.performance.trackingStartedOn} · `
            : ''}
          참여 후 이번 주 수익률 {formatRate(investor.performance.participationReturnRate)}
          {investor.performance.eligibleFrom
            ? ` · 공식 순위 시작 ${investor.performance.eligibleFrom}`
            : ''}
        </p>
        <div className="league-chart-switch" aria-label="수익률 조회 단위">
          <button type="button" aria-pressed={!dailyOpen} onClick={() => setDailyOpen(false)}>
            주간
          </button>
          <button type="button" aria-pressed={dailyOpen} onClick={() => setDailyOpen(true)}>
            일별
          </button>
        </div>
        <section className="league-history-card" hidden={dailyOpen}>
          <header>
            <h2>주간 수익률</h2>
            <span>
              최근 8주 중 {history.filter((point) => point.weeklyReturnRate !== null).length}주 기록
            </span>
          </header>
          {history.length === 0 ? (
            <p className="league-history-empty">아직 집계된 주간 기록이 없어요.</p>
          ) : history.length === 1 ? (
            <button
              type="button"
              className="league-history-single"
              aria-label={`${history[0].weekStart} 주간 ${historyLabel(history[0])} 일별 기록 보기`}
              onClick={() => showDaily(history[0].weekStart)}
            >
              <span>
                <small>첫 주 기록</small>
                <time>{history[0].weekStart} 주</time>
              </span>
              <strong
                className={
                  (history[0].weeklyReturnRate ?? 0) > 0
                    ? 'positive'
                    : (history[0].weeklyReturnRate ?? 0) < 0
                      ? 'negative'
                      : ''
                }
              >
                {historyLabel(history[0])}
                {history[0].weeklyRank != null ? <small> · {history[0].weeklyRank}위</small> : null}
              </strong>
              <span className="league-history-action">일별 보기 →</span>
            </button>
          ) : (
            <div
              ref={historyScroll}
              className="league-history-scroll"
              tabIndex={0}
              aria-label="주간 수익률 그래프 탐색"
            >
              <div
                className="league-history-chart"
                aria-label="최근 8주 주간 수익률"
                style={{
                  gridTemplateColumns: `repeat(${history.length}, minmax(0, 1fr))`,
                  minWidth: `${history.length * 68}px`,
                }}
              >
                {history.map((point) => (
                  <button
                    type="button"
                    key={point.weekStart}
                    aria-label={`${point.weekStart} 주간 ${historyLabel(point)} 일별 기록 보기`}
                    aria-pressed={weekStart === point.weekStart}
                    aria-controls="league-daily-details"
                    onClick={() => showDaily(point.weekStart)}
                  >
                    <span className="league-week-label">
                      <strong
                        className={
                          point.weeklyReturnRate == null
                            ? ''
                            : point.weeklyReturnRate > 0
                              ? 'positive'
                              : point.weeklyReturnRate < 0
                                ? 'negative'
                                : ''
                        }
                      >
                        {historyLabel(point)}
                      </strong>
                      <span className="league-week-meta">
                        {point.weeklyRank != null ? <small>{point.weeklyRank}위</small> : null}
                        {point.weeklyReturnRate !== null && point.confirmed !== undefined ? (
                          <small>{point.confirmed ? '확정' : '집계 중'}</small>
                        ) : null}
                      </span>
                    </span>
                    <span className="league-week-track" aria-hidden="true">
                      {point.weeklyReturnRate !== null ? (
                        <span
                          className={`league-week-bar ${point.weeklyReturnRate < 0 ? 'negative' : point.weeklyReturnRate > 0 ? 'positive' : 'zero'}`}
                          style={{
                            height: `${(Math.abs(point.weeklyReturnRate ?? 0) / historyRange) * 50}%`,
                            [(point.weeklyReturnRate ?? 0) >= 0 ? 'bottom' : 'top']: '50%',
                          }}
                        />
                      ) : (
                        <span className="league-week-missing">—</span>
                      )}
                    </span>
                    <time>{point.weekStart.slice(5)}</time>
                  </button>
                ))}
              </div>
            </div>
          )}
        </section>

        <div id="league-daily-details" ref={dailyContent} tabIndex={-1} hidden={!dailyOpen}>
          <DailyReturns publicId={publicId} weekStart={weekStart} onWeekChange={setWeekStart} />
        </div>
        <section className="league-metric-cards" aria-label="기록과 위험 지표">
          <h2 className="league-subsection-title">위험·기록</h2>
          <div className="league-secondary-metrics">
            <article>
              <span>최근 8주 수익률</span>
              <strong>{formatRate(investor.performance.eightWeekReturnRate)}</strong>
            </article>
            <article>
              <span>연속 성과 기록</span>
              <strong>
                {investor.performance.consecutiveRecordedWeeks === undefined
                  ? '데이터 부족'
                  : `${investor.performance.consecutiveRecordedWeeks}주`}
              </strong>
            </article>
            <article>
              <span>현재 최대 종목 비중</span>
              <strong>{formatRate(investor.performance.currentHoldingWeight, false)}</strong>
            </article>
            <article>
              <span>일간 변동성 · 20거래일</span>
              <strong>
                {investor.performance.volatilityRate === null
                  ? '데이터 부족'
                  : `${investor.performance.volatilityRate.toFixed(2)}%`}
              </strong>
            </article>
            <article>
              <span>최대 종목 비중 · 8주</span>
              <strong>
                {investor.performance.maxHoldingWeight === null
                  ? '집계 대기'
                  : `${investor.performance.maxHoldingWeight.toFixed(1)}%`}
                {investor.performance.maxHoldingWeight != null ? (
                  <small>{concentrationLabel(investor.performance.maxHoldingWeight)}</small>
                ) : null}
              </strong>
            </article>
            <article>
              <span>투자 기록 복기율</span>
              <strong>{investor.reviewCompletionRate.toFixed(0)}%</strong>
            </article>
          </div>
        </section>
      </div>

      {investor.allocation ? (
        <section
          className="league-allocation-card"
          hidden={activeSection !== (investor.detailAvailable ? 'holdings' : 'performance')}
        >
          <div>
            <h2>자산군 비중</h2>
            <small>금액 비공개</small>
          </div>
          <div className="league-allocation-bar" aria-label="주식과 ETF 비중">
            <span style={{ width: `${investor.allocation.stockWeight}%` }} />
            <span style={{ width: `${investor.allocation.etfWeight}%` }} />
          </div>
          <dl>
            <div>
              <dt>주식</dt>
              <dd>{investor.allocation.stockWeight.toFixed(1)}%</dd>
            </div>
            <div>
              <dt>ETF</dt>
              <dd>{investor.allocation.etfWeight.toFixed(1)}%</dd>
            </div>
          </dl>
        </section>
      ) : null}

      {detailError ? (
        <div className="league-state" role="alert">
          <p>{detailError}</p>
          <button type="button" onClick={() => setRetry((n) => n + 1)}>
            상세 다시 불러오기
          </button>
        </div>
      ) : detailLoading ? (
        <p className="league-state" role="status">
          공개 투자 판단을 불러오고 있습니다.
        </p>
      ) : detail ? (
        <>
          <section
            className="league-detail-holdings"
            id="investment-details"
            hidden={activeSection !== 'holdings'}
          >
            <header>
              <div>
                <h2>공개 종목</h2>
              </div>
              <small>
                {detail.asOfDate ? `${detail.asOfDate} 기준` : '종가 수집 대기'} · 비중 / 수익
                기여도
              </small>
            </header>
            {detail.holdings.length === 0 ? (
              <p>해당 기준일에 공개 가능한 보유 종목이 없습니다.</p>
            ) : (
              <ol>
                {detail.holdings.map((holding) => (
                  <li key={`${holding.category}-${holding.assetName}`}>
                    <div>
                      <strong>{holding.assetName}</strong>
                      <small>{holding.category === 'ETF' ? 'ETF' : '주식'}</small>
                      {holding.reason && (
                        <p className="league-calculation-note">보유 이유: {holding.reason}</p>
                      )}
                    </div>
                    <span>평가 수익률 {formatRate(holding.returnRate)}</span>
                    <span>
                      {holding.weight === null
                        ? '비중 집계 대기'
                        : `비중 ${holding.weight.toFixed(1)}%`}
                    </span>
                    <strong
                      className={
                        holding.weeklyContributionRate === null
                          ? ''
                          : holding.weeklyContributionRate >= 0
                            ? 'positive'
                            : 'negative'
                      }
                    >
                      {holding.weeklyContributionRate === null
                        ? '기여도 집계 대기'
                        : `${formatRate(holding.weeklyContributionRate)} 기여`}
                    </strong>
                  </li>
                ))}
              </ol>
            )}
          </section>
          <section
            className="league-detail-decisions"
            id="investment-decisions"
            hidden={activeSection !== 'records'}
          >
            <header>
              <div>
                <h2>투자 기록</h2>
              </div>
              <small>{detail.decisions.length}개 기록</small>
            </header>
            {detail.decisions.length === 0 ? <p>공개된 투자 기록이 아직 없습니다.</p> : null}
            <ol>
              {detail.decisions.map((decision) => (
                <li key={decision.transactionId}>
                  <div>
                    <span>{transactionLabels[decision.transactionType]}</span>
                    <strong>{decision.assetName}</strong>
                    <time>{decision.tradedOn}</time>
                    {decision.writtenAfterTrade ? <small>사후 작성 기록</small> : null}
                  </div>
                  <p>{decision.reason}</p>
                  {decision.expectedHoldingPeriod ||
                  decision.expectedChange ||
                  decision.invalidationCondition ||
                  decision.maximumAcceptableLossRate !== null ? (
                    <dl>
                      {decision.expectedHoldingPeriod ? (
                        <div>
                          <dt>예상 보유 기간</dt>
                          <dd>{holdingPeriodLabels[decision.expectedHoldingPeriod]}</dd>
                        </div>
                      ) : null}
                      {decision.maximumAcceptableLossRate !== null ? (
                        <div>
                          <dt>허용 하락률</dt>
                          <dd>{decision.maximumAcceptableLossRate}%</dd>
                        </div>
                      ) : null}
                      {decision.expectedChange ? (
                        <div className="league-decision-wide">
                          <dt>기대 변화</dt>
                          <dd>{decision.expectedChange}</dd>
                        </div>
                      ) : null}
                      {decision.invalidationCondition ? (
                        <div className="league-decision-wide">
                          <dt>재확인 조건</dt>
                          <dd>{decision.invalidationCondition}</dd>
                        </div>
                      ) : null}
                    </dl>
                  ) : null}
                  {decision.reviews.map((review) => (
                    <blockquote key={review.id}>
                      <strong>결과 복기</strong>
                      <p>{review.actualResult}</p>
                      <p>예상과 달랐던 점: {review.differenceFromExpectation}</p>
                      <small>다음 행동: {review.nextAction}</small>
                    </blockquote>
                  ))}
                </li>
              ))}
            </ol>
          </section>
        </>
      ) : !investor.detailAvailable ? (
        <p className="league-calculation-note">종목·투자 기록 비공개</p>
      ) : null}
    </main>
  );
}
