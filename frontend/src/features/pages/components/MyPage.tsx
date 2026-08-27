import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { BookmarkIcon } from '../../stocks/components/BookmarkIcon';
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

function isAuthPreview() {
  const searchParams = new URLSearchParams(window.location.search);

  return import.meta.env.DEV && searchParams.get('authPreview') === '1';
}

const previewMember: MemberResponse = {
  id: 1,
  email: 'preview@daynomy.local',
  nickname: '쭈니',
  role: 'USER',
};

const bookmarkPreviewItems: BookmarkResponse[] = [
  { id: 1, targetId: 1001, assetName: '삼성전자' },
  { id: 2, targetId: 1002, assetName: '카카오' },
  { id: 3, targetId: 1003, assetName: 'KODEX 200' },
  { id: 4, targetId: 1004, assetName: '현대차' },
  { id: 5, targetId: 1005, assetName: '청년도약계좌' },
  { id: 6, targetId: 1006, assetName: '금 현물' },
  { id: 7, targetId: 1007, assetName: '달러·원' },
  { id: 8, targetId: 1008, assetName: '네이버' },
  { id: 9, targetId: 1009, assetName: '국고채 3년' },
];

const bookmarkCardMeta = [
  { label: '주식', color: '#4339C0', value: '247,540' },
  { label: '주식', color: '#4339C0', value: '005930' },
  { label: 'ETF', color: '#2563EB', value: '069500' },
  { label: '주식', color: '#4339C0', value: '247,540' },
  { label: '예금·적금', color: '#0F766E', value: '153,300' },
  { label: '금', color: '#B45309', value: '1,500' },
  { label: '외화·환율', color: '#635BDB', value: '1,384.20' },
  { label: '주식', color: '#4339C0', value: '005930' },
  { label: '채권', color: '#15803D', value: '3.16%' },
];

const BOOKMARKS_PER_PAGE = 9;

function MyPage() {
  const navigate = useNavigate();

  const [member, setMember] = useState<MemberResponse | null>(null);
  const [bookmarks, setBookmarks] = useState<BookmarkResponse[]>([]);
  const [bookmarkPage, setBookmarkPage] = useState(1);
  const [draftNickname, setDraftNickname] = useState('');
  const [isEditing, setIsEditing] = useState(false);
  const [isWithdrawalOpen, setIsWithdrawalOpen] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [removingBookmarkId, setRemovingBookmarkId] = useState<number | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();

    Promise.allSettled([getMyProfile(controller.signal), getMyBookmarks(controller.signal)])
      .then(([profileResult, bookmarksResult]) => {
        if (controller.signal.aborted) {
          return;
        }

        if (profileResult.status === 'rejected') {
          if (isAuthPreview()) {
            setMember(previewMember);
            setDraftNickname(previewMember.nickname);
            const searchParams = new URLSearchParams(window.location.search);
            setBookmarks(searchParams.get('bookmarksPreview') === '1' ? bookmarkPreviewItems : []);
            setErrorMessage(null);
            return;
          }

          if (profileResult.reason instanceof ApiError && profileResult.reason.status === 401) {
            navigate('/login', { replace: true });
            return;
          }

          setErrorMessage(
            getErrorMessage(profileResult.reason, '회원 정보를 불러오지 못했습니다.'),
          );
          return;
        }

        setMember(profileResult.value);
        setDraftNickname(profileResult.value.nickname);

        if (bookmarksResult.status === 'fulfilled') {
          setBookmarks(bookmarksResult.value);
          setBookmarkPage(1);
          return;
        }

        if (bookmarksResult.reason instanceof ApiError && bookmarksResult.reason.status === 401) {
          navigate('/login', { replace: true });
          return;
        }

        setErrorMessage(getErrorMessage(bookmarksResult.reason, '관심자산을 불러오지 못했습니다.'));
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
      if (isAuthPreview()) {
        const updatedMember = { ...member, nickname: nextNickname };
        setMember(updatedMember);
        setDraftNickname(updatedMember.nickname);
        setIsEditing(false);
        return;
      }

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

    if (isAuthPreview()) {
      setBookmarks((currentBookmarks) =>
        currentBookmarks.filter((bookmark) => bookmark.targetId !== targetId),
      );
      setBookmarkPage((currentPage) => {
        const nextBookmarkCount = Math.max(0, bookmarks.length - 1);
        const nextTotalPages = Math.max(1, Math.ceil(nextBookmarkCount / BOOKMARKS_PER_PAGE));

        return Math.min(currentPage, nextTotalPages);
      });
      return;
    }

    setRemovingBookmarkId(targetId);

    try {
      await deleteBookmark(targetId);
      setBookmarks((currentBookmarks) =>
        currentBookmarks.filter((bookmark) => bookmark.targetId !== targetId),
      );
      setBookmarkPage(1);
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

  const totalBookmarkPages = Math.max(1, Math.ceil(bookmarks.length / BOOKMARKS_PER_PAGE));
  const visibleBookmarks = bookmarks.slice(
    (bookmarkPage - 1) * BOOKMARKS_PER_PAGE,
    bookmarkPage * BOOKMARKS_PER_PAGE,
  );

  return (
    <main className="my-page">
      <section className="mypage-content">
        <div className="mypage-heading">
          <h1>마이페이지</h1>
          <p>회원 정보를 확인하고 관심자산을 관리할 수 있습니다.</p>
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
                <svg viewBox="0 0 50 50" aria-hidden="true">
                  <circle cx="25" cy="25" r="24.5" fill="#F4F4F5" stroke="#DADDE3" />
                  <circle cx="25" cy="19" r="7.5" fill="#FFFFFF" />
                  <path
                    d="M10 41C13.1 33.8 18.1 30 24.5 30C30.9 30 35.9 33.8 39 41"
                    fill="#FFFFFF"
                    stroke="#DADDE3"
                    strokeLinecap="round"
                  />
                </svg>
              </div>

              <div className="profile-info">
                <strong>{member.nickname}</strong>

                <button
                  className="profile-edit-button"
                  type="button"
                  onClick={handleEditStart}
                  disabled={isSubmitting}
                >
                  회원 정보 수정
                  <svg viewBox="0 0 5 9" aria-hidden="true">
                    <path
                      d="M0 0L5 4.5L0 9"
                      fill="none"
                      stroke="#737B8C"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth="1.5"
                    />
                  </svg>
                </button>
              </div>

              <button
                className="logout-button"
                type="button"
                onClick={handleLogout}
                disabled={isSubmitting}
              >
                <svg viewBox="0 0 18 18" aria-hidden="true">
                  <path
                    d="M6 18H2C1.47 18 0.96 17.789 0.59 17.414C0.21 17.039 0 16.53 0 16V2C0 1.47 0.21 0.961 0.59 0.586C0.96 0.211 1.47 0 2 0H6M13 4L18 9L13 14M18 9H6"
                    fill="none"
                    stroke="currentColor"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth="2"
                  />
                </svg>
                로그아웃
              </button>
            </section>

            {isEditing ? (
              <div className="profile-edit-overlay">
                <section
                  className="profile-edit-dialog"
                  role="dialog"
                  aria-modal="true"
                  aria-labelledby="profile-edit-title"
                >
                  <div className="profile-edit-dialog-header">
                    <div className="profile-edit-avatar" aria-hidden="true">
                      <svg viewBox="0 0 68 68" aria-hidden="true">
                        <circle cx="34" cy="34" r="33.5" fill="#F4F4F5" stroke="#DADDE3" />
                        <circle cx="34" cy="25.8" r="10.2" fill="#FFFFFF" />
                        <path
                          d="M13.6 55.8C17.8 46 24.7 40.8 33.3 40.8C42 40.8 48.8 46 53 55.8"
                          fill="#FFFFFF"
                          stroke="#DADDE3"
                          strokeLinecap="round"
                          strokeWidth="1.4"
                        />
                      </svg>
                    </div>

                    <div className="profile-edit-dialog-title">
                      <h2 id="profile-edit-title">회원정보 수정</h2>
                      <p>내 정보를 수정할 수 있어요.</p>
                    </div>

                    <button
                      className="profile-withdrawal-pill"
                      type="button"
                      onClick={() => setIsWithdrawalOpen(true)}
                      disabled={isSubmitting}
                    >
                      회원 탈퇴
                    </button>
                  </div>

                  <div className="profile-edit-fields">
                    <label className="profile-edit-field">
                      <span>이메일</span>
                      <input value={member.email} disabled aria-label="이메일" />
                    </label>

                    <label className="profile-edit-field">
                      <span>닉네임</span>
                      <div className="nickname-field">
                        <input
                          value={draftNickname}
                          onChange={(event) => setDraftNickname(event.target.value)}
                          maxLength={20}
                          aria-label="닉네임"
                          disabled={isSubmitting}
                        />
                        <small>{draftNickname.length}-20자</small>
                      </div>
                    </label>
                  </div>

                  <div className="profile-edit-dialog-actions">
                    <button
                      className="edit-cancel-button"
                      type="button"
                      onClick={handleEditCancel}
                      disabled={isSubmitting}
                    >
                      취소
                    </button>

                    <button
                      className="edit-save-button"
                      type="button"
                      onClick={handleEditSave}
                      disabled={!draftNickname.trim() || isSubmitting}
                    >
                      {isSubmitting ? '저장 중...' : '저장'}
                    </button>
                  </div>
                </section>
              </div>
            ) : null}

            <section className="bookmarked-assets" aria-labelledby="bookmarked-assets-title">
              <div className="bookmarked-assets-heading">
                <div>
                  <p className="section-eyebrow">MY ASSETS</p>
                  <h2 id="bookmarked-assets-title">관심자산</h2>
                </div>
                <span className="bookmark-count">{bookmarks.length}개</span>
              </div>

              {bookmarks.length > 0 ? (
                <>
                  <div className="bookmark-grid">
                    {visibleBookmarks.map((bookmark, index) => {
                      const absoluteIndex = (bookmarkPage - 1) * BOOKMARKS_PER_PAGE + index;
                      const meta = bookmarkCardMeta[absoluteIndex % bookmarkCardMeta.length];

                      return (
                        <article className="bookmark-card" key={bookmark.id}>
                          <div className="bookmark-card-category" style={{ color: meta.color }}>
                            <span style={{ backgroundColor: meta.color }} aria-hidden="true" />
                            {meta.label}
                          </div>

                          <h3>{bookmark.assetName}</h3>
                          <p>{meta.value}</p>

                          <button
                            className="bookmark-remove-button"
                            type="button"
                            onClick={() => handleRemoveBookmark(bookmark.targetId)}
                            disabled={removingBookmarkId === bookmark.targetId}
                            aria-label={`${bookmark.assetName} 관심자산에서 삭제`}
                          >
                            <BookmarkIcon selected />
                          </button>
                        </article>
                      );
                    })}
                  </div>

                  {totalBookmarkPages > 1 ? (
                    <nav className="bookmark-pagination" aria-label="관심자산 페이지">
                      <button
                        type="button"
                        onClick={() => setBookmarkPage((page) => Math.max(1, page - 1))}
                        disabled={bookmarkPage === 1}
                        aria-label="이전 페이지"
                      >
                        &lt;
                      </button>

                      {Array.from({ length: totalBookmarkPages }, (_, index) => index + 1).map(
                        (pageNumber) => (
                          <button
                            className={pageNumber === bookmarkPage ? 'active' : undefined}
                            type="button"
                            key={pageNumber}
                            onClick={() => setBookmarkPage(pageNumber)}
                            aria-current={pageNumber === bookmarkPage ? 'page' : undefined}
                          >
                            {pageNumber}
                          </button>
                        ),
                      )}

                      <button
                        type="button"
                        onClick={() =>
                          setBookmarkPage((page) => Math.min(totalBookmarkPages, page + 1))
                        }
                        disabled={bookmarkPage === totalBookmarkPages}
                        aria-label="다음 페이지"
                      >
                        &gt;
                      </button>
                    </nav>
                  ) : null}
                </>
              ) : (
                <div className="bookmark-empty">
                  <span aria-hidden="true">
                    <BookmarkIcon selected />
                  </span>
                  <p>아직 저장한 관심자산이 없습니다.</p>
                </div>
              )}
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
