import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { trackEvent } from '../../analytics';
import { useAuth } from '../../hooks/useLoginStatus';
import { getMyInvestorProfile, saveInvestorProfile, savePublication } from './api';
import { experienceLabels, riskLabels } from './labels';
import type { ExperienceLevel, InvestorProfile, RiskProfile } from './types';
import './league.css';

const emptyProfile = {
  displayName: '',
  bio: '',
  experienceLevel: 'BEGINNER' as ExperienceLevel,
  riskProfile: 'BALANCED' as RiskProfile,
};

export function PublicationSettingsPage() {
  const { nickname, updateNickname } = useAuth();
  const [profile, setProfile] = useState<InvestorProfile | null>(null);
  const [form, setForm] = useState(() => ({ ...emptyProfile, displayName: nickname || '' }));
  const [publication, setPublication] = useState({
    profilePublic: false,
    leagueEnabled: false,
    allocationPublic: true,
    detailPublic: false,
  });
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [loadFailed, setLoadFailed] = useState(false);
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setLoadFailed(false);
    setError('');
    getMyInvestorProfile(controller.signal)
      .then((saved) => {
        if (controller.signal.aborted) return;
        if (!saved) return;
        setProfile(saved);
        setForm({
          displayName: saved.displayName,
          bio: saved.bio,
          experienceLevel: saved.experienceLevel,
          riskProfile: saved.riskProfile,
        });
        setPublication({
          profilePublic: saved.profilePublic,
          leagueEnabled: saved.leagueEnabled,
          allocationPublic: saved.allocationPublic,
          detailPublic: saved.detailPublic,
        });
      })
      .catch((caught: unknown) => {
        if (controller.signal.aborted) return;
        setLoadFailed(true);
        setError(caught instanceof Error ? caught.message : '공개 설정을 불러오지 못했습니다.');
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [retry]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!form.displayName.trim() || saving) return;
    setSaving(true);
    setMessage('');
    setError('');
    try {
      const savedProfile = await saveInvestorProfile(form);
      setForm((value) => ({ ...value, displayName: savedProfile.displayName }));
      updateNickname?.(savedProfile.displayName);
      const savedPublication = await savePublication({
        ...publication,
        leagueEnabled: publication.profilePublic && publication.leagueEnabled,
        detailPublic: publication.profilePublic && publication.detailPublic,
      });
      setProfile({ ...savedProfile, ...savedPublication });
      setPublication({
        profilePublic: savedPublication.profilePublic,
        leagueEnabled: savedPublication.leagueEnabled,
        allocationPublic: savedPublication.allocationPublic,
        detailPublic: savedPublication.detailPublic,
      });
      trackEvent('save_portfolio_publication', {
        profile_public: savedPublication.profilePublic,
        league_enabled: savedPublication.leagueEnabled,
      });
      setMessage('공개 설정을 저장했습니다.');
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : '공개 설정을 저장하지 못했습니다.');
    } finally {
      setSaving(false);
    }
  }

  if (loading)
    return <main className="league-page league-state">공개 설정을 불러오고 있습니다.</main>;
  if (loadFailed)
    return (
      <main className="league-page league-state" role="alert">
        <h1>공개 설정을 불러오지 못했어요</h1>
        <p>{error}</p>
        <button type="button" onClick={() => setRetry((value) => value + 1)}>
          다시 불러오기
        </button>
        <Link to="/league">투자 리그로 돌아가기</Link>
      </main>
    );

  return (
    <main className="league-page league-settings-page">
      <header className="league-page-heading">
        <div>
          <h1>공개 설정</h1>
          <p>다른 사람에게 보여줄 항목을 선택하세요.</p>
        </div>
        <Link to="/league/portfolio">← 공유 자산 확인</Link>
      </header>

      <form className="league-settings-form" onSubmit={submit}>
        <section>
          <h2>프로필</h2>
          <div className="league-form-grid">
            <label>
              <span>공개 닉네임</span>
              <input
                required
                maxLength={20}
                value={form.displayName}
                onChange={(event) => setForm({ ...form, displayName: event.target.value })}
              />
            </label>
            <label>
              <span>투자 경력</span>
              <select
                value={form.experienceLevel}
                onChange={(event) =>
                  setForm({ ...form, experienceLevel: event.target.value as ExperienceLevel })
                }
              >
                {Object.entries(experienceLabels).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </label>
            <label>
              <span>투자 성향</span>
              <select
                value={form.riskProfile}
                onChange={(event) =>
                  setForm({ ...form, riskProfile: event.target.value as RiskProfile })
                }
              >
                {Object.entries(riskLabels).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </label>
            <label className="league-form-wide">
              <span>한 줄 소개</span>
              <textarea
                maxLength={120}
                value={form.bio}
                onChange={(event) => setForm({ ...form, bio: event.target.value })}
                placeholder="어떤 원칙으로 투자하는지 알려주세요"
              />
            </label>
          </div>
        </section>

        <section>
          <h2>공개 범위</h2>
          <p className="league-form-hint">실제 금액·이름·이메일은 항상 비공개예요.</p>
          <div className="league-toggle-list">
            {[
              ['profilePublic', '프로필 공개', '닉네임과 소개를 보여줍니다.'],
              ['leagueEnabled', '주간 리그 참여', '다음 완전한 거래 주부터 참여합니다.'],
              ['allocationPublic', '자산군 비중 공개', '주식·ETF 비중을 보여줍니다.'],
              [
                'detailPublic',
                '종목·판단 기록 공개',
                '숨기지 않은 종목·판단·복기를 공개합니다. 금액·수량은 비공개입니다.',
              ],
            ].map(([key, title, description]) => (
              <label key={key}>
                <span>
                  <strong>{title}</strong>
                  <small>{description}</small>
                </span>
                <input
                  type="checkbox"
                  checked={publication[key as keyof typeof publication]}
                  disabled={key !== 'profilePublic' && !publication.profilePublic}
                  onChange={(event) =>
                    setPublication({ ...publication, [key]: event.target.checked })
                  }
                />
              </label>
            ))}
          </div>
        </section>

        <details className="league-publication-preview">
          <summary>공개 모습 미리보기</summary>
          <aside className="league-preview-card">
            <span>{publication.profilePublic ? '저장 후 공개될 프로필' : '현재 선택: 비공개'}</span>
            <strong>{form.displayName || '공개 닉네임'}</strong>
            <p>{form.bio || '투자 원칙을 소개해 주세요.'}</p>
            <small>
              {experienceLabels[form.experienceLevel]} · {riskLabels[form.riskProfile]} · 실제 금액
              비공개
            </small>
            <p className="league-preview-status">
              {!publication.profilePublic
                ? '프로필을 공개하지 않으면 다른 사용자는 볼 수 없어요.'
                : publication.leagueEnabled
                  ? '주간 리그 참여 켜짐 · 다음 완전한 거래 주부터 집계'
                  : '프로필만 공개 · 리그에는 참여하지 않음'}
            </p>
          </aside>
        </details>
        <div className="league-form-actions">
          <button type="submit" disabled={saving || !form.displayName.trim()}>
            {saving ? '저장 중…' : profile ? '설정 저장' : '프로필 만들기'}
          </button>
          <p className={error ? 'error' : ''} aria-live="polite">
            {error || message}
          </p>
          {message ? (
            <Link to={publication.leagueEnabled ? '/league' : '/portfolio/records'}>
              {publication.leagueEnabled ? '주간 순위 보기 →' : '공유 자산 준비하기 →'}
            </Link>
          ) : null}
        </div>
      </form>
    </main>
  );
}
