import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { trackEvent } from '../../analytics';
import { useAuth } from '../../hooks/useLoginStatus';
import { getLeagueWeeks, getRankings } from './api';
import { experienceLabels, formatRate, leagueLabels, riskLabels } from './labels';
import type { LeagueType, LeagueWeek, RankingResponse } from './types';
import './league.css';

const leagueTypes: LeagueType[] = ['WEEKLY_RETURN', 'CONSISTENT', 'STABLE', 'BEGINNER'];

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

  useEffect(() => {
    trackEvent('view_investment_league');
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
    getRankings(leagueType, weekStart || undefined, controller.signal)
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
  }, [leagueType, retry, weekStart]);

  return (
    <main className="league-page">
      <section className="league-hero">
        <div>
          <h1>투자 리그</h1>
          <p>다른 투자자의 성과와 판단을 살펴보세요.</p>
        </div>
        <div className="league-hero-actions">
          <Link className="league-text-link" to="/league/following">
            관심 투자자
          </Link>
          {isLoggedIn ? (
            <Link className="league-primary-link" to="/league/portfolio">
              내 공유 관리
            </Link>
          ) : (
            <Link className="league-primary-link" to="/login?returnTo=/league">
              로그인하고 참여하기
            </Link>
          )}
        </div>
      </section>

      <section className="league-controls" aria-label="리그 조회 조건">
        <label>
          <span>리그 종류</span>
          <select
            value={leagueType}
            onChange={(event) => setLeagueType(event.target.value as LeagueType)}
          >
            {leagueTypes.map((type) => (
              <option key={type} value={type}>
                {leagueLabels[type]}
              </option>
            ))}
          </select>
        </label>
        <label>
          <span>조회 주차</span>
          <select value={weekStart} onChange={(event) => setWeekStart(event.target.value)}>
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
          {ranking.asOfDate ? `${ranking.asOfDate} 종가 기준` : '종가 수집 대기'} · 주간 누적 수익률
        </p>
      ) : null}

      {loading ? (
        <p className="league-state" role="status">
          리그 기록을 계산하고 있습니다.
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
          <p>다른 주차를 선택하거나 내 포트폴리오를 공유해 보세요.</p>
          {isLoggedIn ? <Link to="/league/portfolio">공유 자산 준비하기</Link> : null}
        </section>
      ) : null}

      {!loading && !error && ranking && ranking.rankings.length > 0 ? (
        <section className="league-board" aria-labelledby="league-board-title">
          <header>
            <div>
              <h2 id="league-board-title">{leagueLabels[ranking.leagueType]}</h2>
            </div>
            <p>
              {ranking.totalCount}명 · {ranking.confirmed ? '집계 완료' : '집계 중'}
            </p>
          </header>
          <ol>
            {ranking.rankings.map((entry) => (
              <li key={entry.publicId}>
                <span className="league-rank">{entry.rank}</span>
                <Link className="league-person" to={`/league/${entry.publicId}`}>
                  <strong>{entry.displayName}</strong>
                  <small>
                    {experienceLabels[entry.experienceLevel]} · {riskLabels[entry.riskProfile]}
                  </small>
                </Link>
                <div className="league-return">
                  <strong className={entry.weeklyReturnRate >= 0 ? 'positive' : 'negative'}>
                    {formatRate(entry.weeklyReturnRate)}
                  </strong>
                </div>
                <details className="league-row-details">
                  <summary>상세 지표</summary>
                  <dl className="league-risk-grid">
                    <div>
                      <dt>8주 누적</dt>
                      <dd>{formatRate(entry.eightWeekReturnRate)}</dd>
                    </div>
                    <div>
                      <dt>최대 하락</dt>
                      <dd>{formatRate(entry.maxDrawdownRate, false)}</dd>
                    </div>
                    <div>
                      <dt>변동성</dt>
                      <dd>{entry.volatilityRate.toFixed(2)}%</dd>
                    </div>
                    <div>
                      <dt>최대 집중도</dt>
                      <dd>{entry.maxHoldingWeight.toFixed(1)}%</dd>
                    </div>
                  </dl>
                  <div className="league-record-score">
                    <span>판단 {entry.decisionCount}개</span>
                    <span>복기 {entry.reviewCompletionRate.toFixed(0)}%</span>
                  </div>
                </details>
              </li>
            ))}
          </ol>
        </section>
      ) : null}

      <details className="league-onboarding">
        <summary>참여 방법</summary>
        <ol>
          <li>
            <Link to="/league/portfolio">자산 가져오기</Link>
            <p>원본 포트폴리오에서 가져옵니다.</p>
          </li>
          <li>
            <Link to="/league/portfolio">공개할 종목 선택</Link>
            <p>공개하지 않을 종목은 숨깁니다.</p>
          </li>
          <li>
            <Link to="/portfolio/publication">참여 설정하고 저장</Link>
            <p>공개 범위와 리그 참여를 선택합니다.</p>
          </li>
        </ol>
        <p>
          다음 완전한 거래 주부터 참여합니다. 이전 종가 평가금액 10만 원 이상과 거래일 종가가
          필요합니다. 숨긴 종목도 수익률에 포함되며, 실제 금액과 이메일은 공개하지 않습니다.
        </p>
      </details>

      <details className="league-metric-help">
        <summary>수익률·위험 지표 안내</summary>
        <dl>
          <div>
            <dt>8주 누적</dt>
            <dd>최근 8주의 성과를 이어서 계산한 수익률이에요.</dd>
          </div>
          <div>
            <dt>최대 하락률</dt>
            <dd>평가금액이 이전 고점에서 얼마나 내려갔는지 보여줘요.</dd>
          </div>
          <div>
            <dt>변동성</dt>
            <dd>수익률이 흔들린 정도예요. 높을수록 변화가 컸어요.</dd>
          </div>
          <div>
            <dt>최대 집중도</dt>
            <dd>한 종목이 차지한 최대 비중이에요. 높으면 특정 종목의 영향을 크게 받아요.</dd>
          </div>
        </dl>
      </details>

      <aside className="league-safety-note">
        <p>공유 자산으로 계산한 기록입니다. 실제 계좌 인증이나 미래 수익을 보장하지 않습니다.</p>
      </aside>
    </main>
  );
}
