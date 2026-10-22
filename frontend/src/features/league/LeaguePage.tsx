import { useEffect, useRef, useState } from 'react';
import { InvestorAvatar } from './InvestorAvatar';
import { Link } from 'react-router-dom';
import { leagueErrorType, trackLeagueEvent } from './analytics';
import { useAuth } from '../../hooks/useLoginStatus';
import { getLeagueWeeks, getMyInvestorProfile, getRankings } from './api';
import {
  collectionLabels,
  concentrationLabel,
  experienceLabels,
  formatRate,
  riskLabels,
} from './labels';
import type { CollectionStatus, LeagueWeek, RankingEntry, RankingResponse } from './types';
import { recentLeagueWeeks } from './weeks';
import { LeagueWeekNavigation } from './LeagueWeekNavigation';
import { IssuePagination } from '../news/issue/IssuePagination';
import '../news/issue/issue.css';
import './league.css';
import { useLeaguePageHeading } from './useLeaguePageHeading';

const emptyCollectionStates: Partial<
  Record<CollectionStatus, { title: string; description: string }>
> = {
  COLLECTION_PENDING: {
    title: '종가 수집을 기다리고 있어요',
    description: '종가가 수집·집계되는 대로 순위가 반영됩니다.',
  },
  CALENDAR_PENDING: {
    title: '거래일 정보를 확인하고 있어요',
    description: '선택한 주차의 거래일이 확인되면 종가 수집과 순위 집계가 진행됩니다.',
  },
  NO_TRADING_DAYS: {
    title: '이 주에는 거래일이 없어요',
    description: '거래일이 있는 주에 성과를 집계합니다.',
  },
};

export function LeaguePage() {
  const heading = useLeaguePageHeading('주간 리그');
  const { isLoggedIn, memberId } = useAuth();
  const [leagueEnabled, setLeagueEnabled] = useState<boolean | null>(null);
  const [profileError, setProfileError] = useState(false);
  const [profileRetry, setProfileRetry] = useState(0);
  const [weeks, setWeeks] = useState<LeagueWeek[]>([]);
  const [weeksReady, setWeeksReady] = useState(false);
  const [weekStart, setWeekStart] = useState('');
  const [ranking, setRanking] = useState<RankingResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [weekError, setWeekError] = useState('');
  const [retry, setRetry] = useState(0);
  const [page, setPage] = useState(0);
  const [selectedEntry, setSelectedEntry] = useState<RankingEntry | null>(null);
  const metricsDialog = useRef<HTMLDialogElement>(null);
  const pageStatus = useRef<HTMLSpanElement>(null);
  const changingPage = useRef(false);
  const viewed = useRef(false);

  function retryLoad() {
    trackLeagueEvent('league_action', { surface: 'ranking', action: 'retry', status: 'clicked' });
    setRetry((value) => value + 1);
  }

  useEffect(() => {
    if (!loading && changingPage.current) {
      (pageStatus.current ?? heading.current)?.focus();
      changingPage.current = false;
    }
  }, [loading, heading]);
  const emptyCollectionState = emptyCollectionStates[ranking?.collectionStatus ?? 'IN_PROGRESS'];
  const isCurrentWeek = !weekStart || weekStart === weeks[0]?.weekStart;
  const participationPath = isLoggedIn
    ? leagueEnabled
      ? '/league/portfolio'
      : '/league/portfolio#select'
    : `/login?returnTo=${encodeURIComponent('/league/portfolio#select')}`;

  useEffect(() => {
    function refreshWhenVisible() {
      if (document.visibilityState === 'visible') setRetry((value) => value + 1);
    }
    document.addEventListener('visibilitychange', refreshWhenVisible);
    return () => document.removeEventListener('visibilitychange', refreshWhenVisible);
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    setLeagueEnabled(null);
    setProfileError(false);
    if (isLoggedIn) {
      getMyInvestorProfile(controller.signal)
        .then((profile) => {
          if (!controller.signal.aborted) setLeagueEnabled(profile?.leagueEnabled ?? false);
        })
        .catch(() => {
          if (!controller.signal.aborted) setProfileError(true);
        });
    }
    return () => controller.abort();
  }, [isLoggedIn, memberId, profileRetry]);

  useEffect(() => {
    const dialog = metricsDialog.current;
    if (!dialog) return;
    if (selectedEntry && !dialog.open) dialog.showModal();
    else if (!selectedEntry && dialog.open) dialog.close();
  }, [selectedEntry]);

  useEffect(() => {
    if (viewed.current) return;
    viewed.current = true;
    trackLeagueEvent('view_league', { league_type: 'WEEKLY_RETURN', week_state: 'current' });
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    setWeekError('');
    getLeagueWeeks(controller.signal)
      .then((availableWeeks) => {
        if (controller.signal.aborted) return;
        const recentWeeks = recentLeagueWeeks(availableWeeks);
        setWeeks(recentWeeks);
        setWeekStart((current) =>
          recentWeeks.some((week) => week.weekStart === current)
            ? current
            : recentWeeks[0]?.weekStart || '',
        );
        setWeeksReady(true);
        trackLeagueEvent('league_load_result', {
          surface: 'ranking_weeks',
          status: 'success',
          result_count: recentWeeks.length,
        });
      })
      .catch((caught: unknown) => {
        if (!controller.signal.aborted) {
          setWeekError('조회 주차를 불러오지 못했습니다. 다시 불러와 주세요.');
          setWeeksReady(true);
          trackLeagueEvent('league_load_result', {
            surface: 'ranking_weeks',
            status: 'error',
            error_type: leagueErrorType(caught),
          });
        }
      });
    return () => controller.abort();
  }, [retry]);

  useEffect(() => {
    if (!weeksReady) return;
    const controller = new AbortController();
    setLoading(true);
    setError('');
    getRankings('WEEKLY_RETURN', weekStart || undefined, controller.signal, page)
      .then((value) => {
        if (controller.signal.aborted) return;
        setRanking(value);
        trackLeagueEvent('league_load_result', {
          surface: 'ranking',
          status: 'success',
          page,
          result_count: value.rankings.length,
          confirmed: value.confirmed,
          collection_status: value.collectionStatus,
        });
      })
      .catch((caught: unknown) => {
        if (!controller.signal.aborted) {
          setError(caught instanceof Error ? caught.message : '리그를 불러오지 못했습니다.');
          trackLeagueEvent('league_load_result', {
            surface: 'ranking',
            status: 'error',
            page,
            error_type: leagueErrorType(caught),
          });
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [retry, weekStart, page, weeksReady]);

  return (
    <main className="league-page">
      <header className="league-hero">
        <h1 ref={heading} tabIndex={-1}>
          주간 리그
        </h1>
        <div className="league-hero-actions">
          {isLoggedIn && profileError ? (
            <button type="button" onClick={() => setProfileRetry((value) => value + 1)}>
              참여 상태 다시 확인
            </button>
          ) : isLoggedIn && leagueEnabled === null ? (
            <span role="status">참여 상태 확인 중</span>
          ) : (
            <Link
              className="league-primary-link"
              to={participationPath}
              onClick={() =>
                trackLeagueEvent('league_action', {
                  surface: 'ranking',
                  action: leagueEnabled ? 'manage_holdings' : 'participate',
                  status: 'clicked',
                })
              }
            >
              {isLoggedIn && leagueEnabled ? '종목 관리하기' : '투자리그 참여하기'}
            </Link>
          )}
          {isLoggedIn ? (
            <nav className="league-personal-links" aria-label="내 리그">
              <Link
                className="league-text-link"
                to="/league/profile"
                onClick={() =>
                  trackLeagueEvent('league_action', {
                    surface: 'ranking',
                    action: 'open_profile',
                    status: 'clicked',
                  })
                }
              >
                내 프로필
              </Link>
              <Link
                className="league-text-link"
                to="/league/following"
                onClick={() =>
                  trackLeagueEvent('league_action', {
                    surface: 'ranking',
                    action: 'open_following',
                    status: 'clicked',
                  })
                }
              >
                팔로잉
              </Link>
            </nav>
          ) : null}
        </div>
      </header>
      <section className="league-controls" aria-label="리그 조회 조건">
        <LeagueWeekNavigation
          weeks={weeks}
          selected={weekStart}
          onChange={(value) => {
            setWeekStart(value);
            setPage(0);
            const index = weeks.findIndex((week) => week.weekStart === value);
            trackLeagueEvent('change_league_week', {
              relative_week: -index,
              week_state: weeks[index]?.confirmed ? 'confirmed' : 'in_progress',
            });
          }}
        />
        {!loading && !error && ranking && (ranking.asOfDate || ranking.rankings.length > 0) ? (
          <div className="league-ranking-meta">
            {ranking.asOfDate ? <span>{ranking.asOfDate} 종가 기준</span> : null}
            <span className={`league-status-badge${ranking.confirmed ? ' is-confirmed' : ''}`}>
              {ranking.confirmed ? '순위 확정' : '잠정 순위'}
            </span>
            {!ranking.confirmed ? (
              <span>{collectionLabels[ranking.collectionStatus ?? 'IN_PROGRESS']}</span>
            ) : null}
          </div>
        ) : null}
      </section>

      {!loading && !error && ranking ? (
        <aside className="league-update-notice" aria-label="순위 갱신 안내">
          {!ranking.confirmed && ranking.rankings.length > 0 ? (
            <p className="league-provisional-note">
              집계된 종가 기준의 잠정 순위예요. 최종 집계 후 달라질 수 있어요.
            </p>
          ) : null}
          <details className="league-calculation-details">
            <summary>집계 기준 안내</summary>
            <p>종가는 다음 영업일 오후 2시 이후 제공되며, 수집·집계 후 반영됩니다.</p>
            <p>같은 순위 기준 점수는 공동 순위로 표시합니다.</p>
            <p>사용자 등록 자산의 종가 기준 성과로, 실제 계좌 수익률과 다를 수 있습니다.</p>
          </details>
        </aside>
      ) : null}

      {loading ? (
        <p className="league-state" role="status">
          순위를 불러오는 중입니다.
        </p>
      ) : null}
      {weekError ? (
        <div className="league-state" role="alert">
          <p>{weekError}</p>
          <button type="button" onClick={retryLoad}>
            주차 다시 불러오기
          </button>
        </div>
      ) : null}
      {!loading && error ? (
        <div className="league-state" role="alert">
          <p>{error}</p>
          <button type="button" onClick={retryLoad}>
            다시 시도
          </button>
        </div>
      ) : null}
      {!loading && !error && ranking?.rankings.length === 0 ? (
        <section
          className="league-empty league-ranking-empty"
          role="status"
          aria-label="순위 조회 결과"
        >
          <strong>
            {emptyCollectionState?.title ?? '아직 이 주차에 집계된 투자자가 없습니다'}
          </strong>
          <p>
            {emptyCollectionState?.description ??
              (isCurrentWeek
                ? '종가 수집과 참여자 집계가 끝나면 순위를 확인할 수 있어요.'
                : '이 주차에 표시할 순위가 없어요. 다른 주차를 선택해 보세요.')}
          </p>
          {ranking.collectionStatus === 'NO_TRADING_DAYS' ? null : emptyCollectionState ? (
            <button type="button" onClick={retryLoad}>
              순위 다시 확인
            </button>
          ) : null}
        </section>
      ) : null}

      {!loading && !error && ranking && ranking.rankings.length > 0 ? (
        <section className="league-board" aria-labelledby="league-board-title">
          <header>
            <div>
              <h2 id="league-board-title">주간 순위</h2>
            </div>
            <p>{ranking.totalCount}명 · 주간 수익률순</p>
          </header>
          <ol start={page * 20 + 1}>
            {ranking.rankings.map((entry) => (
              <li key={entry.publicId}>
                <span className="league-rank">{entry.rank}</span>
                <Link
                  className="league-person"
                  to={`/league/${entry.publicId}`}
                  state={{
                    source: 'ranking',
                    rankBucket: entry.rank <= 3 ? 'top_3' : entry.rank <= 10 ? 'top_10' : 'other',
                  }}
                >
                  <InvestorAvatar name={entry.displayName} imageUrl={entry.profileImageUrl} />
                  <strong>{entry.displayName}</strong>
                  <small>
                    {experienceLabels[entry.experienceLevel]} · {riskLabels[entry.riskProfile]}
                  </small>
                </Link>
                <div className="league-return">
                  <small>주간 수익률</small>
                  <strong className={entry.weeklyReturnRate >= 0 ? 'positive' : 'negative'}>
                    {formatRate(entry.weeklyReturnRate)}
                  </strong>
                </div>
                <div className="league-row-actions">
                  <button
                    type="button"
                    className="league-metrics-trigger"
                    aria-label={`${entry.displayName} 상세 지표`}
                    aria-haspopup="dialog"
                    aria-expanded={selectedEntry?.publicId === entry.publicId}
                    onClick={() => {
                      setSelectedEntry(entry);
                      trackLeagueEvent('league_action', {
                        surface: 'ranking',
                        action: 'open_metrics',
                        status: 'clicked',
                        rank_bucket:
                          entry.rank <= 3 ? 'top_3' : entry.rank <= 10 ? 'top_10' : 'other',
                      });
                    }}
                  >
                    상세 지표
                  </button>
                  <Link
                    className="league-row-records"
                    to={`/league/${entry.publicId}`}
                    aria-label={`${entry.displayName} 공개 프로필 보기`}
                    state={{
                      source: 'ranking',
                      rankBucket: entry.rank <= 3 ? 'top_3' : entry.rank <= 10 ? 'top_10' : 'other',
                    }}
                  >
                    프로필 보기 <span aria-hidden="true">→</span>
                  </Link>
                </div>
              </li>
            ))}
          </ol>
          {ranking.totalCount > 20 ? (
            <div className="league-pagination">
              <IssuePagination
                page={page + 1}
                totalPages={Math.ceil(ranking.totalCount / 20)}
                ariaLabel="순위 페이지"
                onChange={(nextPage) => {
                  changingPage.current = true;
                  trackLeagueEvent('league_action', {
                    surface: 'ranking',
                    action: 'change_page',
                    status: 'clicked',
                    page: nextPage - 1,
                  });
                  setPage(nextPage - 1);
                }}
              />
              <span
                ref={pageStatus}
                tabIndex={-1}
                role="status"
                className="league-sr-only"
                aria-label={`전체 ${Math.ceil(ranking.totalCount / 20)}페이지 중 ${page + 1}페이지`}
              >
                {page + 1} / {Math.ceil(ranking.totalCount / 20)}
              </span>
            </div>
          ) : null}
        </section>
      ) : null}

      <dialog
        ref={metricsDialog}
        className="league-metrics-dialog"
        aria-labelledby="league-metrics-title"
        onClose={() => setSelectedEntry(null)}
        onCancel={(event) => {
          event.preventDefault();
          metricsDialog.current?.close();
        }}
        onClick={(event) => {
          if (event.target === event.currentTarget) metricsDialog.current?.close();
        }}
      >
        {selectedEntry ? (
          <div className="league-metrics-panel">
            <header>
              <div>
                <p>
                  순위 {selectedEntry.rank}위 · {ranking?.weekStart.slice(5)} 주
                </p>
                <h2 id="league-metrics-title">{selectedEntry.displayName} 상세 지표</h2>
              </div>
              <button
                type="button"
                aria-label="상세 지표 닫기"
                onClick={() => metricsDialog.current?.close()}
              >
                ×
              </button>
            </header>
            <div className="league-metrics-highlight">
              <span>주간 수익률</span>
              <strong className={selectedEntry.weeklyReturnRate >= 0 ? 'positive' : 'negative'}>
                {formatRate(selectedEntry.weeklyReturnRate)}
              </strong>
              <small>
                {ranking?.asOfDate ? `${ranking.asOfDate} 종가 기준` : '종가 수집 대기'}
              </small>
            </div>
            <section className="league-metrics-group" aria-label="성과 요약">
              <h3>성과</h3>
              <dl className="league-risk-grid">
                <div>
                  <dt>연속 성과 기록</dt>
                  <dd>
                    {selectedEntry.consecutiveRecordedWeeks === undefined
                      ? '데이터 부족'
                      : `${selectedEntry.consecutiveRecordedWeeks}주`}
                  </dd>
                </div>
                <div>
                  <dt>최근 3주 수익률</dt>
                  <dd>{formatRate(selectedEntry.threeWeekReturnRate)}</dd>
                </div>
              </dl>
            </section>
            <section className="league-metrics-group" aria-label="위험 지표">
              <h3>위험 지표</h3>
              <dl className="league-risk-grid">
                <div>
                  <dt>최대 하락률 · 3주</dt>
                  <dd>{formatRate(selectedEntry.maxDrawdownRate, false)}</dd>
                </div>
                <div>
                  <dt>일간 변동성 · 20거래일</dt>
                  <dd>{formatRate(selectedEntry.volatilityRate, false)}</dd>
                </div>
                <div>
                  <dt>최대 종목 비중 · 3주</dt>
                  <dd>
                    {formatRate(selectedEntry.maxHoldingWeight, false)}
                    {selectedEntry.maxHoldingWeight != null ? (
                      <small>{concentrationLabel(selectedEntry.maxHoldingWeight)}</small>
                    ) : null}
                  </dd>
                </div>
                <div>
                  <dt>현재 최대 종목 비중</dt>
                  <dd>{formatRate(selectedEntry.currentHoldingWeight, false)}</dd>
                </div>
              </dl>
            </section>
            {selectedEntry.trackingStartedOn ? (
              <p className="league-calculation-note">
                성과 기록 시작 {selectedEntry.trackingStartedOn}
              </p>
            ) : null}
            <div className="league-record-score">
              <span>투자 기록 {selectedEntry.decisionCount}개</span>
              <span>투자 기록 복기율 {selectedEntry.reviewCompletionRate.toFixed(0)}%</span>
            </div>
            <footer className="league-metrics-actions">
              <Link
                className="league-primary-link"
                to={`/league/${selectedEntry.publicId}`}
                state={{
                  source: 'ranking',
                  rankBucket:
                    selectedEntry.rank <= 3
                      ? 'top_3'
                      : selectedEntry.rank <= 10
                        ? 'top_10'
                        : 'other',
                }}
                onClick={() => metricsDialog.current?.close()}
              >
                공개 프로필 보기
              </Link>
            </footer>
          </div>
        ) : null}
      </dialog>
    </main>
  );
}
