import { useEffect, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { trackEvent } from '../../analytics';
import { useAuth } from '../../hooks/useLoginStatus';
import { followInvestor, getInvestorDetail, getPublicInvestor, unfollowInvestor } from './api';
import { experienceLabels, formatRate, holdingPeriodLabels, riskLabels } from './labels';
import { DailyReturns } from './DailyReturns';
import type { InvestorDetail, PublicInvestor } from './types';
import './league.css';

export function InvestorProfilePage() {
  const { publicId = '' } = useParams();
  const { hash } = useLocation();
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

  useEffect(() => {
    if (publicId) trackEvent('view_public_investor', { investor_id: publicId });
  }, [publicId]);

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

  useEffect(() => {
    if (detail && hash === '#investment-details') {
      document.getElementById('investment-details')?.scrollIntoView?.({ block: 'start' });
    }
  }, [detail, hash]);

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

  const history = [...investor.history].sort((a, b) => a.weekStart.localeCompare(b.weekStart));
  const historyRange = Math.max(1, ...history.map((point) => Math.abs(point.weeklyReturnRate)));
  function showDaily(week: string) {
    setWeekStart(week);
    setDailyOpen(true);
  }

  return (
    <main className="league-page league-investor-page">
      <section className="league-investor-header">
        <div>
          <span className="league-avatar" aria-hidden="true">
            {investor.displayName.slice(0, 1)}
          </span>
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
          <Link to={`/login?returnTo=/league/${investor.publicId}`}>로그인하고 팔로우</Link>
        )}
      </section>

      {error ? <p className="league-inline-error">{error}</p> : null}

      <section className="league-metric-cards" aria-label="성과와 위험 지표">
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
            <span>최근 8주 누적</span>
            <strong>
              {investor.performance.eightWeekReturnRate === null
                ? '집계 대기'
                : formatRate(investor.performance.eightWeekReturnRate)}
            </strong>
          </article>
          <article>
            <span>최대 하락률</span>
            <strong>
              {investor.performance.maxDrawdownRate === null
                ? '집계 대기'
                : formatRate(investor.performance.maxDrawdownRate, false)}
            </strong>
          </article>
        </div>
        <details className="league-metric-more">
          <summary>위험·기록 지표</summary>
          <div className="league-secondary-metrics">
            <article>
              <span>변동성</span>
              <strong>
                {investor.performance.volatilityRate === null
                  ? '집계 대기'
                  : `${investor.performance.volatilityRate.toFixed(2)}%`}
              </strong>
            </article>
            <article>
              <span>최대 종목 비중</span>
              <strong>
                {investor.performance.maxHoldingWeight === null
                  ? '집계 대기'
                  : `${investor.performance.maxHoldingWeight.toFixed(1)}%`}
              </strong>
            </article>
            <article>
              <span>판단 복기율</span>
              <strong>{investor.reviewCompletionRate.toFixed(0)}%</strong>
            </article>
          </div>
        </details>
      </section>

      <section className="league-history-card">
        <header>
          <h2>주간 수익률</h2>
          <span>최근 8주 중 {history.length}주 기록</span>
        </header>
        {history.length === 0 ? (
          <p className="league-history-empty">아직 집계된 주간 기록이 없어요.</p>
        ) : history.length === 1 ? (
          <button
            type="button"
            className="league-history-single"
            aria-label={`${history[0].weekStart} 주간 ${formatRate(history[0].weeklyReturnRate)} 일별 기록 보기`}
            onClick={() => showDaily(history[0].weekStart)}
          >
            <span>
              <small>첫 주 기록</small>
              <time>{history[0].weekStart} 주</time>
            </span>
            <strong
              className={
                history[0].weeklyReturnRate > 0
                  ? 'positive'
                  : history[0].weeklyReturnRate < 0
                    ? 'negative'
                    : ''
              }
            >
              {formatRate(history[0].weeklyReturnRate)}
            </strong>
            <span className="league-history-action">일별 보기 →</span>
          </button>
        ) : (
          <div
            className="league-history-chart"
            aria-label="최근 8주 주간 수익률"
            style={{ gridTemplateColumns: `repeat(${history.length}, minmax(0, 1fr))` }}
          >
            {history.map((point) => (
              <button
                type="button"
                key={point.weekStart}
                aria-label={`${point.weekStart} 주간 ${formatRate(point.weeklyReturnRate)} 일별 기록 보기`}
                aria-pressed={weekStart === point.weekStart}
                onClick={() => showDaily(point.weekStart)}
              >
                <strong>{formatRate(point.weeklyReturnRate)}</strong>
                <span className="league-week-track">
                  <span
                    className={`league-week-bar ${point.weeklyReturnRate < 0 ? 'negative' : 'positive'}`}
                    style={{
                      height: `${(Math.abs(point.weeklyReturnRate) / historyRange) * 50}%`,
                      [point.weeklyReturnRate >= 0 ? 'bottom' : 'top']: '50%',
                    }}
                  />
                </span>
                <time>{point.weekStart.slice(5)}</time>
              </button>
            ))}
          </div>
        )}
      </section>

      <details
        className="league-daily-disclosure"
        open={dailyOpen}
        onToggle={(event) => setDailyOpen(event.currentTarget.open)}
      >
        <summary>일별 수익률 보기</summary>
        <DailyReturns publicId={publicId} weekStart={weekStart} onWeekChange={setWeekStart} />
      </details>

      {investor.allocation ? (
        <section className="league-allocation-card">
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
          <section className="league-detail-holdings" id="investment-details">
            <header>
              <div>
                <h2>공개 종목</h2>
              </div>
              <small>{detail.asOfDate} 기준 · 비중 / 수익 기여도</small>
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
                        <p className="league-calculation-note">판단 근거: {holding.reason}</p>
                      )}
                    </div>
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
            <p className="league-calculation-note">
              일별 기여도를 합산한 표시값으로 전체 수익률과 반올림 차이가 날 수 있습니다.
            </p>
          </section>
          <section className="league-detail-decisions">
            <header>
              <div>
                <h2>판단과 복기</h2>
              </div>
              <small>{detail.decisions.length}개 기록</small>
            </header>
            {detail.decisions.length === 0 ? <p>공개 가능한 판단 기록이 아직 없습니다.</p> : null}
            <ol>
              {detail.decisions.map((decision) => (
                <li key={decision.transactionId}>
                  <div>
                    <span>
                      {decision.transactionType === 'HOLD'
                        ? '보유 판단'
                        : decision.transactionType === 'BUY'
                          ? '매수'
                          : '매도'}
                    </span>
                    <strong>{decision.assetName}</strong>
                    <time>{decision.tradedOn}</time>
                  </div>
                  <p>{decision.reason}</p>
                  <details className="league-decision-more">
                    <summary>판단 자세히</summary>
                    <dl>
                      <div>
                        <dt>예상 기간</dt>
                        <dd>{holdingPeriodLabels[decision.expectedHoldingPeriod]}</dd>
                      </div>
                      <div>
                        <dt>기대한 변화</dt>
                        <dd>{decision.expectedChange}</dd>
                      </div>
                      <div>
                        <dt>무효 조건</dt>
                        <dd>{decision.invalidationCondition}</dd>
                      </div>
                    </dl>
                  </details>
                  {decision.reviews.map((review) => (
                    <blockquote key={review.id}>
                      <strong>결과 복기</strong>
                      <p>{review.actualResult}</p>
                      <small>{review.nextAction}</small>
                    </blockquote>
                  ))}
                </li>
              ))}
            </ol>
          </section>
        </>
      ) : !investor.detailAvailable ? (
        <p className="league-calculation-note">
          이 투자자는 종목과 판단 기록을 비공개로 설정했습니다.
        </p>
      ) : null}

      <aside className="league-safety-note">
        <p>공유 자산으로 계산한 과거 성과입니다. 미래 수익을 보장하지 않습니다.</p>
      </aside>
    </main>
  );
}
