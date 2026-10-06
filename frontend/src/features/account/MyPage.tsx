import { startTransition, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../hooks/useLoginStatus';
import { ApiError, getMyProfile, logout, updateMyProfile, type MemberResponse } from '../pages/api';
import './MyPage.css';

export function MyPage() {
  const { clearSession, updateNickname } = useAuth();
  const navigate = useNavigate();
  const [profile, setProfile] = useState<MemberResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);
  const [loggingOut, setLoggingOut] = useState(false);
  const [editingNickname, setEditingNickname] = useState(false);
  const [nicknameDraft, setNicknameDraft] = useState('');
  const [savingNickname, setSavingNickname] = useState(false);
  const [nicknameError, setNicknameError] = useState('');
  const [nicknameNotice, setNicknameNotice] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    getMyProfile(controller.signal)
      .then((value) => {
        if (!controller.signal.aborted) setProfile(value);
      })
      .catch((caught: unknown) => {
        if (!controller.signal.aborted)
          setError(caught instanceof Error ? caught.message : '계정 정보를 불러오지 못했습니다.');
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [retry]);

  async function handleSaveNickname() {
    if (savingNickname || loggingOut) return;
    const nickname = nicknameDraft.trim();
    setNicknameError('');
    setNicknameNotice('');
    if (!nickname || nickname.length > 20) {
      setNicknameError('닉네임은 1~20자로 입력해 주세요.');
      return;
    }
    setSavingNickname(true);
    try {
      const updated = await updateMyProfile(nickname);
      setProfile(updated);
      updateNickname?.(updated.nickname);
      setEditingNickname(false);
      setNicknameNotice('닉네임을 변경했어요.');
    } catch (caught: unknown) {
      setNicknameError(
        caught instanceof ApiError
          ? caught.status === 401
            ? '로그인이 만료되었습니다. 다시 로그인해 주세요.'
            : caught.message
          : '닉네임을 저장하지 못했습니다. 연결을 확인하고 다시 시도해 주세요.',
      );
    } finally {
      setSavingNickname(false);
    }
  }

  async function handleLogout() {
    if (loggingOut || savingNickname) return;
    setLoggingOut(true);
    setError('');
    try {
      await logout();
      // 인증 해제와 이동을 함께 반영해 보호 경로의 로그인 리다이렉트와 경합하지 않는다.
      startTransition(() => {
        clearSession?.();
        navigate('/', { replace: true });
      });
    } catch {
      setError('로그아웃하지 못했습니다. 연결을 확인하고 다시 시도해 주세요.');
      setLoggingOut(false);
    }
  }

  const links = [
    { to: '/', title: '내 포트폴리오', description: '입력한 원본 자산과 평가금액 확인' },
    {
      to: '/league/portfolio',
      title: '공유 포트폴리오',
      description: '원본 자산 불러오기와 공개 종목 숨김 설정',
    },
    {
      to: '/league/following',
      title: '팔로우한 투자자',
      description: '이번 주 성과와 새 복기 확인',
    },
  ];

  return (
    <main className="mypage">
      <header>
        <h1>마이페이지</h1>
        <p>내 계정과 공유 설정을 한곳에서 관리하세요.</p>
      </header>
      <section aria-labelledby="mypage-account-title">
        <h2 id="mypage-account-title">내 계정</h2>
        {loading ? (
          <p role="status">계정 정보를 불러오고 있습니다.</p>
        ) : profile ? (
          <dl>
            <div>
              <dt>
                이메일<small className="mypage-visibility">비공개</small>
              </dt>
              <dd>{profile.email}</dd>
            </div>
            <div>
              <dt>
                이름<small className="mypage-visibility">비공개</small>
              </dt>
              <dd>
                <span>{profile.name || '다시 Google 로그인하면 이름을 불러옵니다.'}</span>
                <small className="mypage-note">Google 계정 이름 · 수정 불가</small>
              </dd>
            </div>
            <div>
              <dt>
                닉네임<small className="mypage-visibility">공개용</small>
              </dt>
              <dd>
                {editingNickname ? (
                  <form
                    className="mypage-nickname-form"
                    noValidate
                    aria-busy={savingNickname}
                    onSubmit={(event) => {
                      event.preventDefault();
                      void handleSaveNickname();
                    }}
                  >
                    <label htmlFor="mypage-nickname">새 닉네임</label>
                    <input
                      id="mypage-nickname"
                      name="nickname"
                      autoComplete="nickname"
                      required
                      maxLength={20}
                      value={nicknameDraft}
                      disabled={savingNickname}
                      aria-invalid={Boolean(nicknameError)}
                      aria-describedby={`mypage-nickname-hint${nicknameError ? ' mypage-nickname-error' : ''}`}
                      onChange={(event) => {
                        setNicknameDraft(event.target.value);
                        setNicknameError('');
                      }}
                    />
                    <p id="mypage-nickname-hint" className="mypage-note">
                      1~20자 · 앞뒤 공백 제외 · 다른 회원과 중복 불가
                    </p>
                    {nicknameError ? (
                      <p id="mypage-nickname-error" role="alert">
                        {nicknameError}
                      </p>
                    ) : null}
                    <div className="mypage-nickname-actions">
                      <button type="submit" disabled={savingNickname}>
                        {savingNickname ? '저장 중…' : '닉네임 저장'}
                      </button>
                      <button
                        type="button"
                        disabled={savingNickname}
                        onClick={() => {
                          setEditingNickname(false);
                          setNicknameError('');
                        }}
                      >
                        취소
                      </button>
                    </div>
                  </form>
                ) : (
                  <div className="mypage-nickname-actions">
                    <span>{profile.nickname}</span>
                    <button
                      type="button"
                      disabled={loggingOut}
                      onClick={() => {
                        setNicknameDraft(profile.nickname);
                        setNicknameError('');
                        setNicknameNotice('');
                        setEditingNickname(true);
                      }}
                    >
                      닉네임 변경
                    </button>
                  </div>
                )}
              </dd>
            </div>
          </dl>
        ) : (
          <button type="button" onClick={() => setRetry((value) => value + 1)}>
            계정 정보 다시 불러오기
          </button>
        )}
        {nicknameNotice ? <p role="status">{nicknameNotice}</p> : null}
        <p className="mypage-note">
          이름과 이메일은 본인에게만 표시됩니다. 닉네임은 리그에서도 동일하게 사용하며, 프로필
          공개에 동의한 경우 다른 사용자에게 표시됩니다.
        </p>
        <p className="mypage-note">이메일은 다른 투자자에게 공개되지 않습니다.</p>
      </section>
      <section aria-labelledby="mypage-activity-title">
        <h2 id="mypage-activity-title">내 활동</h2>
        <nav aria-label="내 활동">
          {links.map((link) => (
            <Link key={link.to} to={link.to}>
              <strong>{link.title}</strong>
              <span>{link.description}</span>
            </Link>
          ))}
        </nav>
      </section>
      <section aria-labelledby="mypage-settings-title">
        <h2 id="mypage-settings-title">설정</h2>
        <nav aria-label="계정·공개 설정">
          <Link to="/portfolio/publication">
            <strong>리그 참여 설정</strong>
            <span>공개 프로필과 공개 범위 관리</span>
          </Link>
        </nav>
      </section>
      {error ? <p role="alert">{error}</p> : null}
      <button
        type="button"
        className="mypage-logout"
        disabled={loggingOut || savingNickname}
        onClick={() => void handleLogout()}
      >
        {loggingOut ? '로그아웃 중…' : '로그아웃'}
      </button>
    </main>
  );
}
