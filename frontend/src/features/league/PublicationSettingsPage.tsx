import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { trackEvent } from '../../analytics';
import { useAuth } from '../../hooks/useLoginStatus';
import {
  getMyInvestorProfile,
  getInvestmentDecisions,
  saveInvestorProfile,
  savePublication,
} from './api';
import { getSharedPortfolio } from './sharedPortfolioApi';
import { experienceLabels, riskLabels } from './labels';
import type { ExperienceLevel, InvestorProfile, RiskProfile } from './types';
import './league.css';
import './shared-portfolio.css';

export function PublicationSettingsPage() {
  const [searchParams] = useSearchParams();
  const joining = searchParams.get('join') === 'true';
  const navigate = useNavigate();
  const [profile, setProfile] = useState<InvestorProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadFailed, setLoadFailed] = useState(false);
  const [retry, setRetry] = useState(0);
  const [readiness, setReadiness] = useState({ ready: false, missing: 0 });
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setLoadFailed(false);
    Promise.all([
      getMyInvestorProfile(controller.signal),
      getSharedPortfolio(controller.signal),
      getInvestmentDecisions(controller.signal),
    ])
      .then(([saved, portfolio, records]) => {
        if (controller.signal.aborted) return;
        const missing = portfolio.holdings.filter(
          (holding) =>
            !holding.reason.trim() &&
            !records.some((record) => record.assetId === holding.assetId && record.reason.trim()),
        ).length;
        setProfile(saved);
        setReadiness({ ready: portfolio.holdings.length > 0 && missing === 0, missing });
      })
      .catch(() => {
        if (!controller.signal.aborted) setLoadFailed(true);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [retry]);
  return (
    <main className="league-page shared-page league-settings-page">
      <header className="league-hero">
        <h1>{joining ? '주간 리그 참여' : '프로필 설정'}</h1>
        <Link className="league-text-link" to={joining ? '/league/portfolio' : '/league/profile'}>
          {joining ? '← 종목 관리' : '← 내 프로필'}
        </Link>
      </header>
      <PortfolioSharingSettings
        profile={profile}
        loading={loading}
        loadFailed={loadFailed}
        onRetry={() => setRetry((value) => value + 1)}
        onSaved={() => navigate('/league/profile')}
        joining={joining}
        recordsReady={readiness.ready}
        missingCount={readiness.missing}
      />
    </main>
  );
}

export function PortfolioSharingSettings({
  profile,
  loading,
  loadFailed,
  onRetry,
  onSaved,
  recordsReady,
  missingCount,
  joining = false,
}: {
  profile: InvestorProfile | null;
  loading: boolean;
  loadFailed: boolean;
  onRetry: () => void;
  onSaved: (profile: InvestorProfile) => void;
  recordsReady: boolean;
  missingCount: number;
  joining?: boolean;
}) {
  const { nickname, updateNickname } = useAuth();
  const [form, setForm] = useState({
    displayName: nickname || '',
    bio: '',
    experienceLevel: 'BEGINNER' as ExperienceLevel,
    riskProfile: 'BALANCED' as RiskProfile,
  });
  const [leagueEnabled, setLeagueEnabled] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  useEffect(() => {
    if (!profile) return;
    setForm({
      displayName: profile.displayName,
      bio: profile.bio,
      experienceLevel: profile.experienceLevel,
      riskProfile: profile.riskProfile,
    });
    setLeagueEnabled(profile.leagueEnabled);
  }, [profile]);
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (saving || !form.displayName.trim()) return;
    if ((joining || leagueEnabled) && !recordsReady) {
      setError('모든 종목의 보유 이유를 작성한 뒤 리그에 참여할 수 있어요.');
      return;
    }
    setSaving(true);
    setError('');
    try {
      const savedProfile = await saveInvestorProfile(form);
      setForm((value) => ({ ...value, displayName: savedProfile.displayName }));
      updateNickname?.(savedProfile.displayName);
      const participating = joining || leagueEnabled;
      const saved = await savePublication({
        profilePublic: participating,
        leagueEnabled: participating,
        detailPublic: participating,
        allocationPublic: participating,
      });
      onSaved({ ...savedProfile, ...saved });
      trackEvent('publish_portfolio', {
        scope: saved.profilePublic ? 'details' : 'private',
      });
    } catch (caught) {
      setError(
        caught instanceof Error
          ? caught.message
          : '설정을 저장하지 못했습니다. 다시 시도해 주세요.',
      );
    } finally {
      setSaving(false);
    }
  }
  return (
    <section
      id="league-settings"
      className="shared-panel shared-settings"
      aria-labelledby="sharing-settings-title"
    >
      <h2 id="sharing-settings-title" className="league-sr-only">
        공유 방법과 리그 참여
      </h2>
      {loading ? (
        <p role="status">현재 공유 범위를 불러오는 중이에요.</p>
      ) : loadFailed ? (
        <div role="alert">
          <p>설정을 불러오지 못했습니다.</p>
          <button onClick={onRetry}>공유 범위 다시 불러오기</button>
        </div>
      ) : (
        <form onSubmit={(event) => void submit(event)}>
          {joining ? (
            <p className="shared-join-notice">
              첫 종가 집계에서 0%로 시작하고, 매일 이번 주 순위에 반영됩니다.
            </p>
          ) : null}
          <label className="shared-nickname">
            공개 닉네임
            <input
              required
              maxLength={20}
              disabled={saving}
              value={form.displayName}
              onChange={(event) => setForm({ ...form, displayName: event.target.value })}
            />
          </label>
          {!joining ? (
            <fieldset disabled={saving} className="shared-sharing-options">
              <legend>참여 및 공개</legend>
              {(
                [
                  ['private', '비공개', '나만 보기'],
                  ['league', '주간 리그 참여', '닉네임·수익률 공개 · 첫 집계부터 순위 반영'],
                ] as const
              ).map(([value, title, description]) => (
                <label key={value}>
                  <input
                    type="radio"
                    name="portfolio-sharing"
                    value={value}
                    disabled={value === 'league' && !recordsReady && !leagueEnabled}
                    checked={value === (leagueEnabled ? 'league' : 'private')}
                    onChange={() => setLeagueEnabled(value === 'league')}
                  />
                  <span>
                    <strong>{title}</strong>
                    <small>{description}</small>
                  </span>
                </label>
              ))}
            </fieldset>
          ) : null}
          {!joining && profile?.profilePublic && !profile.leagueEnabled ? (
            <p className="shared-required-note">
              기존 프로필 공개는 저장 시 선택한 방식으로 바뀝니다.
            </p>
          ) : null}
          {!recordsReady ? (
            <p className="shared-required-note">
              {missingCount > 0
                ? `보유 이유 ${missingCount}개 미작성`
                : '참여할 종목과 보유 이유가 필요합니다.'}{' '}
              <Link to="/league/portfolio">종목 준비하기</Link>
            </p>
          ) : null}
          {joining || leagueEnabled ? (
            <div className="shared-publication-notice">
              <strong>함께 공개됩니다</strong>
              <p>닉네임 · 수익률 · 종목 · 비중 · 보유 이유 · 복기</p>
              <small>숨긴 종목과 금액·수량·계정 정보는 공개되지 않습니다.</small>
            </div>
          ) : null}
          {!joining ? (
            <details className="shared-settings-section" open={!profile || undefined}>
              <summary>소개·투자 성향</summary>
              <fieldset className="league-form-grid" disabled={saving}>
                <legend className="league-sr-only">공개 프로필 입력</legend>
                <label className="league-form-wide">
                  <span>한 줄 소개 (선택)</span>
                  <textarea
                    maxLength={120}
                    value={form.bio}
                    onChange={(event) => setForm({ ...form, bio: event.target.value })}
                    placeholder="어떤 원칙으로 투자하는지 알려주세요"
                  />
                </label>
                <fieldset className="shared-choice-options league-form-wide">
                  <legend>투자 경력</legend>
                  {Object.entries(experienceLabels).map(([value, label]) => (
                    <label key={value}>
                      <input
                        type="radio"
                        name="experienceLevel"
                        checked={form.experienceLevel === value}
                        onChange={() =>
                          setForm({ ...form, experienceLevel: value as ExperienceLevel })
                        }
                      />
                      <span>{label}</span>
                    </label>
                  ))}
                </fieldset>
                <fieldset className="shared-choice-options league-form-wide">
                  <legend>투자 성향</legend>
                  {Object.entries(riskLabels).map(([value, label]) => (
                    <label key={value}>
                      <input
                        type="radio"
                        name="riskProfile"
                        checked={form.riskProfile === value}
                        onChange={() => setForm({ ...form, riskProfile: value as RiskProfile })}
                      />
                      <span>{label}</span>
                    </label>
                  ))}
                </fieldset>
              </fieldset>
            </details>
          ) : null}
          {error ? (
            <p role="alert" className="league-form-error">
              {error}
            </p>
          ) : null}
          <div className="shared-form-actions shared-settings-actions">
            <button
              className="shared-primary-button"
              disabled={
                saving || !form.displayName.trim() || ((joining || leagueEnabled) && !recordsReady)
              }
            >
              {saving
                ? '저장 중…'
                : joining
                  ? '공개에 동의하고 참여'
                  : leagueEnabled
                    ? '공개에 동의하고 저장'
                    : '저장'}
            </button>
          </div>
        </form>
      )}
    </section>
  );
}
