import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getMyInvestorProfile } from './api';
import { InvestorProfilePage } from './InvestorProfilePage';
import { useAuth } from '../../hooks/useLoginStatus';
import type { InvestorProfile } from './types';
import './league.css';

export function LeagueProfilePage() {
  const { nickname } = useAuth();
  const [profile, setProfile] = useState<InvestorProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setFailed(false);
    getMyInvestorProfile(controller.signal)
      .then((saved) => {
        if (!controller.signal.aborted) setProfile(saved);
      })
      .catch(() => {
        if (!controller.signal.aborted) setFailed(true);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [retry]);
  if (!loading && !failed && profile?.profilePublic)
    return <InvestorProfilePage ownProfile={profile} />;
  return (
    <main className="league-page league-profile-page">
      {loading ? (
        <p role="status">불러오는 중…</p>
      ) : failed ? (
        <div role="alert">
          <p>프로필을 불러오지 못했습니다.</p>
          <button onClick={() => setRetry((value) => value + 1)}>다시 불러오기</button>
        </div>
      ) : (
        <>
          <header className="league-hero">
            <h1>{profile?.displayName || nickname || '내 프로필'}</h1>
            <Link className="league-text-link" to="/portfolio/publication">
              프로필 설정
            </Link>
          </header>
          <p className="league-state">현재 비공개입니다.</p>
        </>
      )}
    </main>
  );
}
