import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { trackEvent } from '../../analytics';
import { useAuth } from '../../hooks/useLoginStatus';
import { getMyInvestorProfile, getTransactions, saveInvestorProfile, savePublication } from './api';
import { getSharedPortfolio } from './sharedPortfolioApi';
import { experienceLabels, riskLabels } from './labels';
import type { ExperienceLevel, InvestorProfile, RiskProfile } from './types';
import './league.css';
import './shared-portfolio.css';

export function PublicationSettingsPage() {
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
      getTransactions(controller.signal),
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
        <h1>리그 설정</h1>
        <Link className="league-secondary-link" to="/league/portfolio">
          내 리그
        </Link>
      </header>
      <PortfolioSharingSettings
        profile={profile}
        loading={loading}
        loadFailed={loadFailed}
        onRetry={() => setRetry((value) => value + 1)}
        onSaved={setProfile}
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
}: {
  profile: InvestorProfile | null;
  loading: boolean;
  loadFailed: boolean;
  onRetry: () => void;
  onSaved: (profile: InvestorProfile) => void;
  recordsReady: boolean;
  missingCount: number;
}) {
  const { nickname, updateNickname } = useAuth();
  const [form, setForm] = useState({
    displayName: nickname || '',
    bio: '',
    experienceLevel: 'BEGINNER' as ExperienceLevel,
    riskProfile: 'BALANCED' as RiskProfile,
  });
  const [publication, setPublication] = useState({
    profilePublic: false,
    leagueEnabled: false,
    allocationPublic: true,
    detailPublic: false,
  });
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  useEffect(() => {
    if (!profile) return;
    setForm({
      displayName: profile.displayName,
      bio: profile.bio,
      experienceLevel: profile.experienceLevel,
      riskProfile: profile.riskProfile,
    });
    setPublication({
      profilePublic: profile.profilePublic,
      leagueEnabled: profile.leagueEnabled,
      allocationPublic: profile.allocationPublic,
      detailPublic: profile.detailPublic,
    });
  }, [profile]);
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (saving || !form.displayName.trim()) return;
    if (publication.leagueEnabled && !recordsReady) {
      setError('모든 종목의 보유 이유를 작성한 뒤 리그에 참여할 수 있어요.');
      return;
    }
    setSaving(true);
    setError('');
    setMessage('');
    try {
      const savedProfile = await saveInvestorProfile(form);
      setForm((value) => ({ ...value, displayName: savedProfile.displayName }));
      updateNickname?.(savedProfile.displayName);
      const saved = await savePublication({
        ...publication,
        leagueEnabled: publication.profilePublic && publication.leagueEnabled,
        detailPublic: publication.profilePublic && publication.detailPublic,
      });
      onSaved({ ...savedProfile, ...saved });
      trackEvent('publish_portfolio', {
        scope: !saved.profilePublic ? 'private' : saved.detailPublic ? 'details' : 'performance',
      });
      setMessage('저장했습니다.');
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
          <fieldset disabled={saving} className="shared-sharing-options">
            <legend>공개 범위</legend>
            {(
              [
                ['private', '나만 보기', '비공개'],
                ['performance', '성과만 공유', '닉네임·프로필·수익률'],
                ['details', '종목과 보유 이유도 공유', '성과 + 선택한 종목·투자 기록'],
              ] as const
            ).map(([value, title, description]) => (
              <label key={value}>
                <input
                  type="radio"
                  name="portfolio-sharing"
                  value={value}
                  checked={
                    value ===
                    (!publication.profilePublic
                      ? 'private'
                      : publication.detailPublic
                        ? 'details'
                        : 'performance')
                  }
                  onChange={() =>
                    setPublication({
                      ...publication,
                      profilePublic: value !== 'private',
                      detailPublic: value === 'details',
                      leagueEnabled: value === 'private' ? false : publication.leagueEnabled,
                    })
                  }
                />
                <span>
                  <strong>{title}</strong>
                  <small>{description}</small>
                </span>
              </label>
            ))}
          </fieldset>
          <label className="shared-visibility-choice">
            <input
              type="checkbox"
              checked={publication.allocationPublic}
              disabled={saving || !publication.profilePublic}
              onChange={(event) =>
                setPublication({ ...publication, allocationPublic: event.target.checked })
              }
            />
            주식·ETF의 비중도 보여주기
          </label>
          <p className="shared-privacy-note">금액·수량·계정 정보는 항상 비공개</p>
          <section className="shared-settings-section" aria-labelledby="public-profile-title">
            <h3 id="public-profile-title">공개 프로필</h3>
            <fieldset className="league-form-grid" disabled={saving}>
              <legend className="league-sr-only">공개 프로필 입력</legend>
              <label className="league-form-wide">
                <span>공개 닉네임</span>
                <input
                  required
                  maxLength={20}
                  value={form.displayName}
                  onChange={(event) => setForm({ ...form, displayName: event.target.value })}
                />
              </label>
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
          </section>
          <label className="shared-enrollment-choice">
            <input
              type="checkbox"
              aria-label="주간 순위에 참여하기"
              disabled={saving || (!recordsReady && !publication.leagueEnabled)}
              checked={publication.leagueEnabled}
              onChange={(event) =>
                setPublication({
                  ...publication,
                  leagueEnabled: event.target.checked,
                  profilePublic: event.target.checked || publication.profilePublic,
                })
              }
            />
            <span>
              <strong>주간 순위에 참여하기</strong>
              <small>닉네임·성과 공개에 동의 · 신규 참여는 다음 월요일부터</small>
            </span>
          </label>
          {!recordsReady ? (
            <p className="shared-required-note">
              {missingCount > 0
                ? `보유 이유 ${missingCount}개 미작성`
                : '참여할 종목과 보유 이유가 필요합니다.'}{' '}
              <Link to="/league/portfolio">내 리그에서 작성</Link>
            </p>
          ) : null}
          {error ? (
            <p role="alert" className="league-form-error">
              {error}
            </p>
          ) : null}
          {message ? (
            <p role="status" className="shared-feedback">
              {message}
            </p>
          ) : null}
          <div className="shared-form-actions shared-settings-actions">
            <button
              className="shared-primary-button"
              disabled={
                saving || !form.displayName.trim() || (publication.leagueEnabled && !recordsReady)
              }
            >
              {saving ? '저장 중…' : '저장'}
            </button>
            {profile?.profilePublic ? (
              <Link className="league-text-link" to={`/league/${profile.publicId}`}>
                공개 프로필 보기
              </Link>
            ) : null}
            {message && publication.leagueEnabled ? (
              <Link className="league-text-link" to="/league">
                주간 순위 보기 →
              </Link>
            ) : null}
          </div>
        </form>
      )}
    </section>
  );
}
