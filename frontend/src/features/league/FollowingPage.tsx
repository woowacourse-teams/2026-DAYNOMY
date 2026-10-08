import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getFollowSummary } from './api';
import { formatRate } from './labels';
import type { FollowSummary } from './types';
import './league.css';

export function FollowingPage() {
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
          <h1>관심 투자자</h1>
          <p>팔로우한 투자자의 이번 주 성과와 새 복기예요.</p>
        </div>
        <Link to="/league">투자자 찾기</Link>
      </header>
      {loading ? <p className="league-state">주간 요약을 불러오고 있습니다.</p> : null}
      {error ? (
        <div className="league-state" role="alert">
          <p>{error}</p>
          <button type="button" onClick={() => setRetry((n) => n + 1)}>
            다시 불러오기
          </button>
        </div>
      ) : null}
      {!loading && !error && investors.length === 0 ? (
        <section className="league-empty">
          <strong>팔로우한 투자자가 없습니다</strong>
          <p>리그에서 투자 과정을 계속 보고 싶은 사람을 팔로우해 보세요.</p>
          <Link to="/league">투자 리그 둘러보기</Link>
        </section>
      ) : null}
      <div className="league-follow-grid">
        {investors.map((investor) => (
          <Link key={investor.publicId} to={`/league/${investor.publicId}`}>
            <span>{investor.rank ? `${investor.rank}위` : '집계 전'}</span>
            <strong>{investor.displayName}</strong>
            <p>
              이번 주{' '}
              {investor.weeklyReturnRate === null
                ? '기록 없음'
                : formatRate(investor.weeklyReturnRate)}
            </p>
            <small>새 복기 {investor.newReviewCount}개</small>
          </Link>
        ))}
      </div>
    </main>
  );
}
