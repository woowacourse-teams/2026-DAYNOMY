import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { leagueErrorType, trackLeagueEvent } from './analytics';
import { useAuth } from '../../hooks/useLoginStatus';
import { followInvestor, getInvestorDetail, getPublicInvestor, unfollowInvestor } from './api';
import { collectionLabels, formatRate, transactionLabels } from './labels';
import { ReturnLine } from './ReturnLine';
import { DailyReturns } from './DailyReturns';
import { InvestorAvatar } from './InvestorAvatar';
import type { InvestorDetail, PublicInvestor, InvestorProfile } from './types';
import { LEAGUE_FIRST_WEEK } from './weeks';
import './league.css';
import { useLeaguePageHeading } from './useLeaguePageHeading';

export function InvestorProfilePage({ ownProfile }: { ownProfile?: InvestorProfile } = {}) {
  const params = useParams();
  const publicId = ownProfile?.publicId ?? params.publicId ?? '';
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
  const [dailyOpen, setDailyOpen] = useState(true);
  const heading = useLeaguePageHeading(
    investor ? `${investor.displayName} 공개 프로필` : '투자자 프로필',
    !loading,
  );

  const dailyContent = useRef<HTMLDivElement>(null);
  const viewed = useRef('');
  useEffect(() => {
    if (dailyOpen) {
      dailyContent.current?.focus({ preventScroll: true });
      dailyContent.current?.scrollIntoView?.({ block: 'nearest' });
    }
  }, [dailyOpen]);

  useEffect(() => {
    const key = JSON.stringify([publicId, viewSource, viewRankBucket]);
    if (publicId && viewed.current !== key) {
      viewed.current = key;
      trackLeagueEvent('view_investor_profile', {
        source: viewSource,
        rank_bucket: viewRankBucket,
      });
    }
  }, [publicId, viewSource, viewRankBucket]);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    setInvestor(null);
    setWeekStart('');
    setDailyOpen(true);
    setDetail(null);
    setDetailError('');
    setDetailLoading(false);
    getPublicInvestor(publicId, controller.signal)
      .then((value) => {
        if (controller.signal.aborted) return;
        setInvestor(value);
        trackLeagueEvent('league_load_result', { surface: 'investor', status: 'success' });
        if (!value.detailAvailable) return;
        setDetailLoading(true);
        void getInvestorDetail(publicId, controller.signal)
          .then((details) => {
            if (!controller.signal.aborted) {
              setDetail(details);
              trackLeagueEvent('league_load_result', {
                surface: 'investor_details',
                status: 'success',
              });
            }
          })
          .catch((caught: unknown) => {
            if (!controller.signal.aborted) {
              setDetailError('상세 정보를 불러오지 못했습니다. 저장된 내용이 없는 것은 아니에요.');
              trackLeagueEvent('league_load_result', {
                surface: 'investor_details',
                status: 'error',
                error_type: leagueErrorType(caught),
              });
            }
          })
          .finally(() => {
            if (!controller.signal.aborted) setDetailLoading(false);
          });
      })
      .catch((caught: unknown) => {
        if (!controller.signal.aborted) {
          setError(caught instanceof Error ? caught.message : '투자자를 불러오지 못했습니다.');
          trackLeagueEvent('league_load_result', {
            surface: 'investor',
            status: 'error',
            error_type: leagueErrorType(caught),
          });
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [isLoggedIn, publicId, retry]);

  async function toggleFollow() {
    if (!investor || followSaving) return;
    const action = investor.followed ? 'unfollow' : 'follow';
    trackLeagueEvent('league_action', { surface: 'investor', action, status: 'submitted' });
    setFollowSaving(true);
    try {
      if (investor.followed) await unfollowInvestor(investor.publicId);
      else await followInvestor(investor.publicId);
      setInvestor({ ...investor, followed: !investor.followed });
      trackLeagueEvent('league_action', { surface: 'investor', action, status: 'success' });
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : '팔로우 상태를 변경하지 못했습니다.');
      trackLeagueEvent('league_action', {
        surface: 'investor',
        action,
        status: 'error',
        error_type: leagueErrorType(caught),
      });
    } finally {
      setFollowSaving(false);
    }
  }

  if (loading)
    return (
      <main className="league-page league-state">
        <h1 ref={heading} tabIndex={-1}>
          투자자 프로필
        </h1>
        <p role="status">투자 기록을 불러오고 있습니다.</p>
      </main>
    );
  if (error && !investor)
    return (
      <main className="league-page league-state" role="alert">
        <h1 ref={heading} tabIndex={-1}>
          투자자 프로필
        </h1>
        <p>{error}</p>
        <button
          type="button"
          onClick={() => {
            trackLeagueEvent('league_action', {
              surface: 'investor',
              action: 'retry',
              status: 'clicked',
            });
            setRetry((n) => n + 1);
          }}
        >
          다시 불러오기
        </button>
      </main>
    );
  if (!investor) return null;

  const history = investor.history
    .filter((point) => point.weekStart >= LEAGUE_FIRST_WEEK)
    .sort((a, b) => a.weekStart.localeCompare(b.weekStart))
    .slice(-3);
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
    trackLeagueEvent('league_action', {
      surface: 'investor',
      action: 'change_daily_week',
      status: 'clicked',
    });
    setWeekStart(week);
    setDailyOpen(true);
  }

  return (
    <main className="league-page league-investor-page">
      <section className="league-investor-header">
        <div>
          <InvestorAvatar name={investor.displayName} imageUrl={investor.profileImageUrl} />
          <div>
            <h1 ref={heading} tabIndex={-1}>
              {investor.displayName}
            </h1>
            {investor.bio ? <p>{investor.bio}</p> : null}
          </div>
        </div>
        {ownProfile ? (
          <Link className="league-text-link" to="/portfolio/publication">
            프로필 설정
          </Link>
        ) : isLoggedIn ? (
          <button
            type="button"
            disabled={followSaving}
            aria-pressed={investor.followed}
            aria-label={`${investor.displayName} ${investor.followed ? '팔로잉 취소' : '팔로우'}`}
            onClick={() => void toggleFollow()}
          >
            {investor.followed ? '팔로잉' : '팔로우'}
          </button>
        ) : (
          <Link to={`/login?returnTo=${encodeURIComponent(`${pathname}${search}${hash}`)}`}>
            로그인하고 팔로우
          </Link>
        )}
      </section>

      {error ? (
        <p className="league-inline-error" role="alert">
          {error}
        </p>
      ) : null}
      <div>
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
              <span>이번 주 순위</span>
              <strong>
                {investor.performance.weeklyRank == null
                  ? '집계 대기'
                  : `${investor.performance.weeklyRank}위`}
              </strong>
            </article>
          </div>
        </section>

        <div className="league-chart-switch" role="group" aria-label="수익률 조회 단위">
          <button
            type="button"
            aria-pressed={dailyOpen}
            onClick={() => {
              if (!dailyOpen)
                trackLeagueEvent('league_action', {
                  surface: 'investor',
                  action: 'toggle_daily_history',
                  status: 'clicked',
                  view: 'daily',
                });
              setDailyOpen(true);
            }}
          >
            일별
          </button>
          <button
            type="button"
            aria-pressed={!dailyOpen}
            onClick={() => {
              if (dailyOpen)
                trackLeagueEvent('league_action', {
                  surface: 'investor',
                  action: 'toggle_daily_history',
                  status: 'clicked',
                  view: 'weekly',
                });
              setDailyOpen(false);
            }}
          >
            주간
          </button>
        </div>
        <section className="league-history-card" hidden={dailyOpen}>
          <header>
            <h2>주간 수익률</h2>
            <span>{history.length <= 1 ? '이번 주' : `최근 ${history.length}주`}</span>
          </header>
          {history.length === 0 ? (
            <p className="league-history-empty">아직 집계된 주간 기록이 없어요.</p>
          ) : (
            <div className="league-history-scroll" aria-label="주간 수익률 그래프 탐색">
              <div
                className="league-history-chart"
                role="group"
                aria-label={
                  history.length === 1 ? '이번 주 주간 수익률 그래프' : '최근 3주 주간 수익률'
                }
                style={{
                  gridTemplateColumns: `repeat(${history.length}, minmax(0, 1fr))`,
                  minWidth: `${history.length * 68}px`,
                }}
              >
                <ReturnLine rates={history.map((point) => point.weeklyReturnRate)} />
                {history.map((point) => (
                  <button
                    type="button"
                    key={point.weekStart}
                    aria-label={`${point.weekStart} 주간 ${historyLabel(point)} 일별 기록 보기`}
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
                        {history.length === 1 ? <small>첫 주 기록</small> : null}
                        {point.weeklyRank != null ? <small>{point.weeklyRank}위</small> : null}
                        {point.weeklyReturnRate !== null && point.confirmed !== undefined ? (
                          <small>{point.confirmed ? '확정' : '집계 중'}</small>
                        ) : null}
                      </span>
                    </span>
                    <span aria-hidden="true" />
                    <time>{point.weekStart.slice(5)}</time>
                  </button>
                ))}
              </div>
            </div>
          )}
        </section>

        <div id="league-daily-details" ref={dailyContent} tabIndex={-1} hidden={!dailyOpen}>
          {dailyOpen ? (
            <DailyReturns publicId={publicId} weekStart={weekStart} onWeekChange={setWeekStart} />
          ) : null}
        </div>
      </div>

      {detailError ? (
        <div className="league-state" role="alert">
          <p>{detailError}</p>
          <button type="button" onClick={() => setRetry((n) => n + 1)}>
            상세 다시 불러오기
          </button>
        </div>
      ) : detailLoading ? (
        <p className="league-state" role="status">
          공개 투자 기록을 불러오고 있습니다.
        </p>
      ) : detail ? (
        <>
          <section className="league-detail-holdings" id="investment-details">
            <header>
              <div>
                <h2>공개 종목</h2>
              </div>
              <small>{detail.asOfDate ? `${detail.asOfDate} 기준` : '종가 수집 대기'}</small>
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
                  </li>
                ))}
              </ol>
            )}
          </section>
          <section className="league-detail-decisions" id="investment-decisions">
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
                  </div>
                  <p>{decision.reason}</p>
                  {decision.reviews.map((review) => (
                    <blockquote key={review.id}>
                      <strong>복기</strong>
                      <p>{review.actualResult}</p>
                      <p>예상과 달랐던 점: {review.differenceFromExpectation}</p>
                      <small>다음 행동: {review.nextAction}</small>
                      {review.updatedAt ? (
                        <small>
                          {' '}
                          · 작성 {review.createdAt.slice(0, 10)} · 마지막 저장{' '}
                          {review.updatedAt.slice(0, 10)}
                        </small>
                      ) : null}
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
