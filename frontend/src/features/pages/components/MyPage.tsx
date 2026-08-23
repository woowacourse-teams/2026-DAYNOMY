import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Header } from '../../../components/Header';
import '../MyPage.css';
import {
  ApiError,
  deleteBookmark,
  getMyBookmarks,
  getMyProfile,
  logout,
  type BookmarkResponse,
  type MemberResponse,
  updateMyProfile,
  withdraw,
} from '../api';

function getErrorMessage(error: unknown, fallback: string) {
  return error instanceof ApiError ? error.message : fallback;
}

function MyPage() {
  const navigate = useNavigate();

  const [member, setMember] = useState<MemberResponse | null>(null);
  const [bookmarks, setBookmarks] = useState<BookmarkResponse[]>([]);
  const [draftNickname, setDraftNickname] = useState('');
  const [isEditing, setIsEditing] = useState(false);
  const [isWithdrawalOpen, setIsWithdrawalOpen] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [removingBookmarkId, setRemovingBookmarkId] = useState<number | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();

    Promise.all([getMyProfile(controller.signal), getMyBookmarks(controller.signal)])
      .then(([profile, myBookmarks]) => {
        setMember(profile);
        setDraftNickname(profile.nickname);
        setBookmarks(myBookmarks);
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

  const handleRemoveBookmark = async (targetId: number) => {
    setErrorMessage(null);
    setRemovingBookmarkId(targetId);

    try {
      await deleteBookmark(targetId);
      setBookmarks((currentBookmarks) =>
        currentBookmarks.filter((bookmark) => bookmark.targetId !== targetId),
      );
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        navigate('/login', { replace: true });
        return;
      }

      setErrorMessage(getErrorMessage(error, '관심자산을 삭제하지 못했습니다.'));
    } finally {
      setRemovingBookmarkId(null);
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

            <section className="bookmarked-assets" aria-labelledby="bookmarked-assets-title">
              <div className="bookmarked-assets-heading">
                <div>
                  <p className="section-eyebrow">MY ASSETS</p>
                  <h2 id="bookmarked-assets-title">관심자산</h2>
                </div>
                <span className="bookmark-count">{bookmarks.length}개</span>
              </div>

              {bookmarks.length > 0 ? (
                <div className="bookmark-grid">
                  {bookmarks.map((bookmark, index) => (
                    <article className="bookmark-card" key={bookmark.id}>
                      <div className="bookmark-card-topline">
                        <span className="bookmark-card-number">
                          {String(index + 1).padStart(2, '0')}
                        </span>
                        <button
                          className="bookmark-remove-button"
                          type="button"
                          onClick={() => handleRemoveBookmark(bookmark.targetId)}
                          disabled={removingBookmarkId === bookmark.targetId}
                          aria-label={`${bookmark.assetName} 관심자산에서 삭제`}
                        >
                          <svg viewBox="0 0 24 24" aria-hidden="true">
                            <path d="M6 4.5h12v15l-6-3.7-6 3.7z" />
                          </svg>
                        </button>
                      </div>
                      <h3>{bookmark.assetName}</h3>
                      <p>관심자산으로 저장됨</p>
                    </article>
                  ))}
                </div>
              ) : (
                <div className="bookmark-empty">
                  <span aria-hidden="true">☆</span>
                  <p>아직 저장한 관심자산이 없습니다.</p>
                </div>
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
