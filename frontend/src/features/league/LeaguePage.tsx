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
          <span className="league-eyebrow">성과를 비교하고 판단을 배우는 곳</span>
          <h1>이번 주 투자 리그</h1>
          <p>투자자를 선택해 성과와 위험을 확인하세요. 순위는 로그인 없이 볼 수 있어요.</p>
          <p className="league-calculation-note">
            거래일마다 종가로 갱신하고, 월요일~일요일의 누적 수익률로 비교해요.
          </p>
        </div>
        <div className="league-hero-actions">
          {isLoggedIn ? (
            <>
              <Link className="league-primary-link" to="/portfolio/publication">
                참여 설정하기
              </Link>
              <Link className="league-secondary-link" to="/league/portfolio">
                공유 자산 관리
              </Link>
            </>
          ) : (
            <Link className="league-primary-link" to="/login?returnTo=/league">
              로그인하고 참여하기
            </Link>
          )}
        </div>
      </section>

      <details className="league-onboarding">
        <summary>처음 참여하나요? 참여 순서 보기</summary>
        <ol>
          <li>
            <Link to="/league/portfolio">공유용 자산 준비</Link>
            <p>원본 포트폴리오에서 원하는 종목만 가져오세요. 원본은 바뀌지 않아요.</p>
          </li>
          <li>
            <Link to="/portfolio/publication">공개 범위 선택</Link>
            <p>프로필 공개와 주간 리그 참여를 직접 켜세요.</p>
          </li>
          <li>
            <Link to="/league/portfolio">투자 판단 남기기</Link>
            <p>가져온 자산의 판단과 복기를 남겨요. 자산 변경은 다음 거래일부터 집계됩니다.</p>
          </li>
        </ol>
        <p>실제 투자금액과 이메일은 공개하지 않습니다.</p>
      </details>

      <section className="league-controls" aria-label="리그 조회 조건">
        <div className="league-tabs" role="tablist" aria-label="리그 종류">
          {leagueTypes.map((type) => (
            <button
              key={type}
              type="button"
              role="tab"
              aria-selected={leagueType === type}
              onClick={() => setLeagueType(type)}
            >
              {leagueLabels[type]}
            </button>
          ))}
        </div>
        <label>
          <span>조회 주차</span>
          <select value={weekStart} onChange={(event) => setWeekStart(event.target.value)}>
            {weeks.length === 0 ? <option value="">이번 주</option> : null}
            {weeks.map((week) => (
              <option key={week.weekStart} value={week.weekStart}>
                {week.weekStart} ~ {week.weekEnd} {week.confirmed ? '지난 주' : '집계 중'}
              </option>
            ))}
          </select>
        </label>
      </section>

      {!loading && !error && ranking ? (
        <p className="league-calculation-note">
          {ranking.asOfDate ? `${ranking.asOfDate} 종가 기준` : '이 주차의 종가 수집 대기'} · 주간
          누적 수익률 · 종가 제공 시점에 따라 갱신이 늦어질 수 있어요.
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
          <p>공개 설정을 완료하면 다음 완전한 거래 주부터 리그에 참여할 수 있어요.</p>
          <p>
            공유 자산의 이전 종가 평가가 10만 원 이상이고 해당 거래일의 종가가 있어야 집계됩니다.
          </p>
          {isLoggedIn ? <Link to="/portfolio/publication">내 공개 설정 확인</Link> : null}
        </section>
      ) : null}

      {!loading && !error && ranking && ranking.rankings.length > 0 ? (
        <section className="league-board" aria-labelledby="league-board-title">
          <header>
            <div>
              <span>{ranking.confirmed ? '확정된 기록' : '집계 중인 기록'}</span>
              <h2 id="league-board-title">{leagueLabels[ranking.leagueType]}</h2>
            </div>
            <p>{ranking.totalCount}명이 같은 기준으로 비교되고 있어요.</p>
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
                  <span className="league-person-action">성과·기록 보기 →</span>
                </Link>
                <div className="league-return">
                  <strong className={entry.weeklyReturnRate >= 0 ? 'positive' : 'negative'}>
                    {formatRate(entry.weeklyReturnRate)}
                  </strong>
                  <small>주간 누적</small>
                </div>
                <details className="league-row-details">
                  <summary>위험·기록 보기</summary>
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

      <details className="league-metric-help">
        <summary>수익률·위험 지표는 어떻게 읽나요?</summary>
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
        <strong>순위는 추천이 아닙니다</strong>
        <p>
          공유 포트폴리오 수익률은 사용자가 입력한 공유용 자산을 DAYNOMY가 종가로 계산한 기록이며,
          실제 증권계좌 인증이나 미래 수익을 의미하지 않습니다.
        </p>
      </aside>
    </main>
  );
}
