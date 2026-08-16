import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Header } from '../../../components/Header';
import '../MyPage.css';

const mockUser = {
  email: 'daynomy@example.com',
  nickname: '데이노미',
};

function MyPage() {
  const navigate = useNavigate();

  const [nickname, setNickname] = useState(mockUser.nickname);
  const [draftNickname, setDraftNickname] = useState(mockUser.nickname);
  const [isEditing, setIsEditing] = useState(false);
  const [isWithdrawalOpen, setIsWithdrawalOpen] = useState(false);

  const handleEditStart = () => {
    setDraftNickname(nickname);
    setIsEditing(true);
  };

  const handleEditCancel = () => {
    setDraftNickname(nickname);
    setIsEditing(false);
  };

  const handleEditSave = () => {
    const nextNickname = draftNickname.trim();

    if (!nextNickname) {
      return;
    }

    setNickname(nextNickname);
    setIsEditing(false);
  };

  const handleLogout = () => {
    // TODO: 로그아웃 API 연결 후 인증 상태 초기화
    navigate('/login');
  };

  const handleWithdrawal = () => {
    // TODO: 회원 탈퇴 API 연결
    setIsWithdrawalOpen(false);
    navigate('/login');
  };

  return (
    <main className="my-page">
      <Header />

      <section className="mypage-content">
        <div className="mypage-heading">
          <h1>마이페이지</h1>
          <p>회원 정보를 확인하고 관리할 수 있습니다.</p>
        </div>

        <section className="profile-card">
          <div className="profile-avatar" aria-hidden="true">
            {nickname.slice(0, 1)}
          </div>

          <dl className="profile-info">
            <div>
              <dt>이메일</dt>
              <dd>{mockUser.email}</dd>
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
                  />
                ) : (
                  nickname
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
                disabled={!draftNickname.trim()}
              >
                저장
              </button>

              <button className="edit-cancel-button" type="button" onClick={handleEditCancel}>
                취소
              </button>
            </div>
          ) : (
            <button className="profile-edit-button" type="button" onClick={handleEditStart}>
              회원 정보 수정
            </button>
          )}
        </section>

        <section className="account-actions" aria-label="계정 관리">
          <button className="logout-button" type="button" onClick={handleLogout}>
            로그아웃
          </button>

          <button
            className="withdrawal-button"
            type="button"
            onClick={() => setIsWithdrawalOpen(true)}
          >
            회원 탈퇴
          </button>
        </section>
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
              >
                취소
              </button>

              <button
                className="withdrawal-confirm-button"
                type="button"
                onClick={handleWithdrawal}
              >
                탈퇴
              </button>
            </div>
          </section>
        </div>
      )}
    </main>
  );
}

export default MyPage;
