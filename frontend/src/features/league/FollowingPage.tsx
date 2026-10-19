import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getFollowSummary } from './api';
import { formatRate } from './labels';
import type { FollowSummary } from './types';
import './league.css';
import { useLeaguePageHeading } from './useLeaguePageHeading';

export function FollowingPage() {
  const heading = useLeaguePageHeading('팔로잉');
  const [investors, setInvestors] = useState<FollowSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    setInvestors([]);
    getFollowSummary(controller.signal)
      .then((value) => {
        if (!controller.signal.aborted) setInvestors(value);
      })
      .catch((caught: unknown) => {
        if (!controller.signal.aborted)
          setError(caught instanceof Error ? caught.message : '팔로우 요약을 불러오지 못했습니다.');
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [retry]);

  return (
    <main className="league-page league-following-page">
      <header className="league-page-heading">
        <div>
          <h1 ref={heading} tabIndex={-1}>
            팔로잉
          </h1>
        </div>
        <Link to="/league">투자자 찾기</Link>
      </header>
      {loading ? (
        <p className="league-state" role="status">
          주간 요약을 불러오고 있습니다.
        </p>
      ) : null}
      {error ? (
        <div className="league-state" role="alert">
          <p>{error}</p>
          <button type="button" onClick={() => setRetry((n) => n + 1)}>
            다시 불러오기
          </button>
        </div>
      ) : null}
      {!loading && !error && investors.length === 0 ? (
        <section className="league-empty" role="status">
          <strong>팔로우한 투자자가 없습니다</strong>
        </section>
      ) : null}
      <div className="league-follow-grid">
        {investors.map((investor) => (
          <Link
            key={investor.publicId}
            to={`/league/${investor.publicId}`}
            state={{ source: 'following' }}
          >
            <span>{investor.rank ? `${investor.rank}위` : '집계 전'}</span>
            <div className="league-follow-person">
              <strong>{investor.displayName}</strong>
              <small>
                {investor.rankChange == null
                  ? '비교 기록 없음'
                  : investor.rankChange === 0
                    ? '지난주와 동일'
                    : `지난주 대비 ${Math.abs(investor.rankChange)}위 ${investor.rankChange > 0 ? '상승' : '하락'}`}
              </small>
            </div>
            <p>
              이번 주 수익률{' '}
              {investor.weeklyReturnRate === null
                ? '기록 없음'
                : formatRate(investor.weeklyReturnRate)}
            </p>
            <small>이번 주 복기 {investor.newReviewCount}개</small>
          </Link>
        ))}
      </div>
    </main>
  );
}
