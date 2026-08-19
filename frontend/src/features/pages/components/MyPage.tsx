import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Header } from '../../../components/Header';
import '../MyPage.css';
import { ApiError, getMyProfile, logout, type Member, updateMyProfile, withdraw } from '../api';

function getErrorMessage(error: unknown, fallback: string) {
  return error instanceof ApiError ? error.message : fallback;
}

function MyPage() {
  const navigate = useNavigate();

  const [member, setMember] = useState<Member | null>(null);
  const [draftNickname, setDraftNickname] = useState('');
  const [isEditing, setIsEditing] = useState(false);
  const [isWithdrawalOpen, setIsWithdrawalOpen] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();

    getMyProfile(controller.signal)
      .then((profile) => {
        setMember(profile);
        setDraftNickname(profile.nickname);
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) {
          return;
        }

        if (error instanceof ApiError && error.status === 401) {
          navigate('/login', { replace: true });
          return;
        }

        setErrorMessage(getErrorMessage(error, '회원 정보를 불러오지 못했습니다.'));
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setIsLoading(false);
        }
      });

    return () => controller.abort();
  }, [navigate]);

  const handleEditStart = () => {
    if (!member) {
      return;
    }

    setErrorMessage(null);
    setDraftNickname(member.nickname);
    setIsEditing(true);
  };

  const handleEditCancel = () => {
    setDraftNickname(member?.nickname ?? '');
    setIsEditing(false);
  };

  const handleEditSave = async () => {
    const nextNickname = draftNickname.trim();

    if (!nextNickname || !member) {
      return;
    }

    setErrorMessage(null);
    setIsSubmitting(true);

    try {
      const updatedMember = await updateMyProfile(nextNickname);
      setMember(updatedMember);
      setDraftNickname(updatedMember.nickname);
      setIsEditing(false);
    } catch (error) {
      setErrorMessage(getErrorMessage(error, '회원 정보를 수정하지 못했습니다.'));
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleLogout = async () => {
    setErrorMessage(null);
    setIsSubmitting(true);

    try {
      await logout();
      window.location.replace('/');
    } catch (error) {
      setErrorMessage(getErrorMessage(error, '로그아웃하지 못했습니다.'));
      setIsSubmitting(false);
    }
  };

  const handleWithdrawal = async () => {
    setErrorMessage(null);
    setIsSubmitting(true);

    try {
      await withdraw();
      setIsWithdrawalOpen(false);
      navigate('/login', { replace: true });
    } catch (error) {
      setErrorMessage(getErrorMessage(error, '회원 탈퇴를 처리하지 못했습니다.'));
      setIsSubmitting(false);
    }
  };

  return (
    <main className="my-page">
      <Header />

      <section className="mypage-content">
        <div className="mypage-heading">
          <h1>마이페이지</h1>
          <p>회원 정보를 확인하고 관리할 수 있습니다.</p>
        </div>

        <div className="mypage-message" aria-live="polite">
          {isLoading && <p className="mypage-status">회원 정보를 불러오는 중입니다.</p>}
          {errorMessage && (
            <p className="mypage-error" role="alert">
              {errorMessage}
            </p>
          )}
        </div>

        {member && (
          <>
            <section className="profile-card">
              <div className="profile-avatar" aria-hidden="true">
                {member.nickname.slice(0, 1)}
              </div>

              <dl className="profile-info">
                <div>
                  <dt>이메일</dt>
                  <dd>{member.email}</dd>
                </div>

                <div>
                  <dt>닉네임</dt>
                  <dd>
                    {isEditing ? (
                      <input
                        className="nickname-input"
                        value={draftNickname}
                        onChange={(event) => setDraftNickname(event.target.value)}
                        maxLength={20}
                        aria-label="닉네임"
                        disabled={isSubmitting}
                      />
                    ) : (
                      member.nickname
                    )}
                  </dd>
                </div>
              </dl>

              {isEditing ? (
                <div className="profile-edit-actions">
                  <button
                    className="edit-save-button"
                    type="button"
                    onClick={handleEditSave}
                    disabled={!draftNickname.trim() || isSubmitting}
                  >
                    {isSubmitting ? '저장 중...' : '저장'}
                  </button>

                  <button
                    className="edit-cancel-button"
                    type="button"
                    onClick={handleEditCancel}
                    disabled={isSubmitting}
                  >
                    취소
                  </button>
                </div>
              ) : (
                <button
                  className="profile-edit-button"
                  type="button"
                  onClick={handleEditStart}
                  disabled={isSubmitting}
                >
                  회원 정보 수정
                </button>
              )}
            </section>

            <section className="account-actions" aria-label="계정 관리">
              <button
                className="logout-button"
                type="button"
                onClick={handleLogout}
                disabled={isSubmitting}
              >
                로그아웃
              </button>

              <button
                className="withdrawal-button"
                type="button"
                onClick={() => setIsWithdrawalOpen(true)}
                disabled={isSubmitting}
              >
                회원 탈퇴
              </button>
            </section>
          </>
        )}
      </section>

      {isWithdrawalOpen && (
        <div className="withdrawal-overlay">
          <section
            className="withdrawal-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="withdrawal-title"
          >
            <h2 id="withdrawal-title">회원 탈퇴</h2>
            <p>
              탈퇴한 계정은 복구할 수 없습니다.
              <br />
              정말 탈퇴하시겠습니까?
            </p>

            <div className="withdrawal-dialog-actions">
              <button
                className="edit-cancel-button"
                type="button"
                onClick={() => setIsWithdrawalOpen(false)}
                disabled={isSubmitting}
              >
                취소
              </button>

              <button
                className="withdrawal-confirm-button"
                type="button"
                onClick={handleWithdrawal}
                disabled={isSubmitting}
              >
                {isSubmitting ? '처리 중...' : '탈퇴'}
              </button>
            </div>
          </section>
        </div>
      )}
    </main>
  );
}

export default MyPage;
