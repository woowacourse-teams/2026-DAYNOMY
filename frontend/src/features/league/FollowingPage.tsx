import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getFollowSummary } from './api';
import { leagueErrorType, trackLeagueEvent } from './analytics';
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
        if (!controller.signal.aborted) {
          setInvestors(value);
          trackLeagueEvent('league_load_result', {
            surface: 'following',
            status: 'success',
            result_count: value.length,
          });
        }
      })
      .catch((caught: unknown) => {
        if (!controller.signal.aborted) {
          setError(caught instanceof Error ? caught.message : '팔로잉 목록을 불러오지 못했습니다.');
          trackLeagueEvent('league_load_result', {
            surface: 'following',
            status: 'error',
            error_type: leagueErrorType(caught),
          });
        }
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
          팔로잉 목록을 불러오고 있습니다.
        </p>
      ) : null}
      {error ? (
        <div className="league-state" role="alert">
          <p>{error}</p>
          <button
            type="button"
            onClick={() => {
              trackLeagueEvent('league_action', {
                surface: 'following',
                action: 'retry',
                status: 'clicked',
              });
              setRetry((n) => n + 1);
            }}
          >
            다시 불러오기
          </button>
        </div>
      ) : null}
      {!loading && !error && investors.length === 0 ? (
        <section className="league-empty" role="status">
          <strong>팔로우한 투자자가 없습니다</strong>
        </section>
      ) : null}
      <ul className="league-follow-grid" aria-label="팔로우한 투자자">
        {investors.map((investor) => (
          <li key={investor.publicId}>
            <Link to={`/league/${investor.publicId}`} state={{ source: 'following' }}>
              <strong>{investor.displayName}</strong>
            </Link>
          </li>
        ))}
      </ul>
    </main>
  );
}
