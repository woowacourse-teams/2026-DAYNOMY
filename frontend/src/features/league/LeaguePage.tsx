import { useEffect, useRef, useState } from 'react';
import { InvestorAvatar } from './InvestorAvatar';
import { Link } from 'react-router-dom';
import { trackEvent } from '../../analytics';
import { useAuth } from '../../hooks/useLoginStatus';
import { getLeagueWeeks, getRankings } from './api';
import {
  collectionLabels,
  concentrationLabel,
  experienceLabels,
  formatRate,
  leagueCriteria,
  leagueLabels,
  riskLabels,
} from './labels';
import type { LeagueType, LeagueWeek, RankingEntry, RankingResponse } from './types';
import './league.css';

const leagueTypes: LeagueType[] = ['WEEKLY_RETURN', 'STABLE', 'BEGINNER'];

export function LeaguePage() {
  const { isLoggedIn } = useAuth();
  const [leagueType, setLeagueType] = useState<LeagueType>('WEEKLY_RETURN');
  const [weeks, setWeeks] = useState<LeagueWeek[]>([]);
  const [weekStart, setWeekStart] = useState('');
  const [ranking, setRanking] = useState<RankingResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [weekError, setWeekError] = useState('');
  const [retry, setRetry] = useState(0);
  const [page, setPage] = useState(0);
  const [selectedEntry, setSelectedEntry] = useState<RankingEntry | null>(null);
  const metricsDialog = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = metricsDialog.current;
    if (!dialog) return;
    if (selectedEntry && !dialog.open) dialog.showModal();
    else if (!selectedEntry && dialog.open) dialog.close();
  }, [selectedEntry]);

  useEffect(() => {
    trackEvent('view_league', { league_type: 'WEEKLY_RETURN', week_state: 'current' });
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    setWeekError('');
    getLeagueWeeks(controller.signal)
      .then((availableWeeks) => {
        if (controller.signal.aborted) return;
        setWeeks(availableWeeks);
        setWeekStart((current) => current || availableWeeks[0]?.weekStart || '');
      })
      .catch(() => {
        if (!controller.signal.aborted)
          setWeekError('조회 주차를 불러오지 못했습니다. 다시 불러와 주세요.');
      });
    return () => controller.abort();
  }, [retry]);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    getRankings(leagueType, weekStart || undefined, controller.signal, page)
      .then((value) => {
        if (!controller.signal.aborted) setRanking(value);
      })
      .catch((caught: unknown) => {
        if (!controller.signal.aborted) {
          setError(caught instanceof Error ? caught.message : '리그를 불러오지 못했습니다.');
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [leagueType, retry, weekStart, page]);

  return (
    <main className="league-page">
      <section className="league-hero">
        <div>
          <h1>투자 리그</h1>
        </div>
        <div className="league-hero-actions">
          <Link className="league-text-link" to="/league/following">
            관심 투자자
          </Link>
          {isLoggedIn ? (
            <>
              <Link className="league-primary-link" to="/league/portfolio">
                내 리그
              </Link>
              <Link className="league-secondary-link" to="/portfolio/publication">
                리그 설정
              </Link>
            </>
          ) : (
            <Link className="league-primary-link" to="/login?returnTo=/league">
              로그인하고 참여하기
            </Link>
          )}
        </div>
      </section>

      <section className="league-controls" aria-label="리그 조회 조건">
        <fieldset className="league-type-options">
          <legend className="league-sr-only">리그 종류</legend>
          <div>
            {leagueTypes.map((type) => (
              <label key={type}>
                <input
                  type="radio"
                  name="league-type"
                  value={type}
                  checked={leagueType === type}
                  onChange={() => {
                    setLeagueType(type);
                    setPage(0);
                    trackEvent('change_league_filter', { filter_type: type });
                  }}
                />
                <span>{leagueLabels[type]}</span>
              </label>
            ))}
          </div>
        </fieldset>
        <label className="league-week-filter">
          <span>조회 주차</span>
          <select
            value={weekStart}
            onChange={(event) => {
              setWeekStart(event.target.value);
              setPage(0);
              const index = weeks.findIndex((week) => week.weekStart === event.target.value);
              trackEvent('change_league_week', {
                relative_week: -index,
                week_state: weeks[index]?.confirmed ? 'confirmed' : 'in_progress',
              });
            }}
          >
            {weeks.length === 0 ? <option value="">이번 주</option> : null}
            {weeks.map((week) => (
              <option key={week.weekStart} value={week.weekStart}>
                {week.weekStart.slice(5)} ~ {week.weekEnd.slice(5)}{' '}
                {week.confirmed ? '' : '· 집계 중'}
              </option>
            ))}
          </select>
        </label>
      </section>

      {!loading && !error && ranking ? (
        <p className="league-calculation-note">
          {ranking.asOfDate ? `${ranking.asOfDate} 종가 기준` : '종가 수집 대기'} ·{' '}
          {leagueCriteria[ranking.leagueType]}
        </p>
      ) : null}

      {loading ? (
        <p className="league-state" role="status">
          순위를 불러오는 중입니다.
        </p>
      ) : null}
      {weekError ? (
        <div className="league-state" role="alert">
          <p>{weekError}</p>
          <button type="button" onClick={() => setRetry((v) => v + 1)}>
            주차 다시 불러오기
          </button>
        </div>
      ) : null}
      {!loading && error ? (
        <div className="league-state" role="alert">
          <p>{error}</p>
          <button type="button" onClick={() => setRetry((value) => value + 1)}>
            다시 시도
          </button>
        </div>
      ) : null}
      {!loading && !error && ranking?.rankings.length === 0 ? (
        <section className="league-empty">
          <strong>아직 이 주차에 집계된 투자자가 없습니다</strong>
          {isLoggedIn ? <Link to="/league/portfolio">종목 가져오기</Link> : null}
        </section>
      ) : null}

      {!loading && !error && ranking && ranking.rankings.length > 0 ? (
        <section className="league-board" aria-labelledby="league-board-title">
          <header>
            <div>
              <h2 id="league-board-title">{leagueLabels[ranking.leagueType]}</h2>
            </div>
            <p>
              {ranking.totalCount}명 ·{' '}
              {ranking.confirmed
                ? '순위 확정'
                : collectionLabels[ranking.collectionStatus ?? 'IN_PROGRESS']}
            </p>
          </header>
          <ol>
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
                  <small>8주 수익률 {formatRate(entry.eightWeekReturnRate)}</small>
                </div>
                <div className="league-row-actions">
                  <button
                    type="button"
                    className="league-metrics-trigger"
                    aria-label={`${entry.displayName} 상세 지표`}
                    aria-haspopup="dialog"
                    aria-expanded={selectedEntry?.publicId === entry.publicId}
                    onClick={() => setSelectedEntry(entry)}
                  >
                    상세 지표
                  </button>
                  <Link
                    className="league-row-records"
                    to={`/league/${entry.publicId}#investment-decisions`}
                    aria-label={`${entry.displayName} 투자 기록`}
                    state={{
                      source: 'ranking',
                      rankBucket: entry.rank <= 3 ? 'top_3' : entry.rank <= 10 ? 'top_10' : 'other',
                    }}
                  >
                    투자 기록 <span aria-hidden="true">→</span>
                  </Link>
                </div>
              </li>
            ))}
          </ol>
          {ranking.totalCount > 20 ? (
            <nav className="league-pagination" aria-label="순위 페이지">
              <button
                type="button"
                disabled={page === 0}
                onClick={() => setPage((value) => value - 1)}
              >
                이전
              </button>
              <span>
                {page + 1} / {Math.ceil(ranking.totalCount / 20)}
              </span>
              <button
                type="button"
                disabled={(page + 1) * 20 >= ranking.totalCount}
                onClick={() => setPage((value) => value + 1)}
              >
                다음
              </button>
            </nav>
          ) : null}
          {ranking.finalizedAt ? (
            <p className="league-calculation-note">
              최종 확정 · 계산 버전 {ranking.calculationVersion}
            </p>
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
                  <dt>전체 누적 수익률</dt>
                  <dd>{formatRate(selectedEntry.cumulativeReturnRate)}</dd>
                </div>
                <div>
                  <dt>연속 성과 기록</dt>
                  <dd>
                    {selectedEntry.consecutiveRecordedWeeks === undefined
                      ? '데이터 부족'
                      : `${selectedEntry.consecutiveRecordedWeeks}주`}
                  </dd>
                </div>
                <div>
                  <dt>최근 8주 수익률</dt>
                  <dd>{formatRate(selectedEntry.eightWeekReturnRate)}</dd>
                </div>
              </dl>
            </section>
            <section className="league-metrics-group" aria-label="위험 지표">
              <h3>위험 지표</h3>
              <dl className="league-risk-grid">
                <div>
                  <dt>최대 하락률 · 8주</dt>
                  <dd>{formatRate(selectedEntry.maxDrawdownRate, false)}</dd>
                </div>
                <div>
                  <dt>일간 변동성 · 20거래일</dt>
                  <dd>{formatRate(selectedEntry.volatilityRate, false)}</dd>
                </div>
                <div>
                  <dt>최대 종목 비중 · 8주</dt>
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
                성과 보기
              </Link>
              <Link
                className="league-secondary-link"
                to={`/league/${selectedEntry.publicId}#investment-decisions`}
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
                투자 기록
              </Link>
            </footer>
          </div>
        ) : null}
      </dialog>
    </main>
  );
}
