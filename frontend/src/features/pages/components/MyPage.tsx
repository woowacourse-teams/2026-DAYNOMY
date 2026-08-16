import { Header } from "../../../components/Header";
import "../MyPage.css";

const mockUser = {
  email: "daynomy@example.com",
  nickname: "데이노미",
};

function MyPage() {
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
            {mockUser.nickname.slice(0, 1)}
          </div>

          <dl className="profile-info">
            <div>
              <dt>이메일</dt>
              <dd>{mockUser.email}</dd>
            </div>

            <div>
              <dt>닉네임</dt>
              <dd>{mockUser.nickname}</dd>
            </div>
          </dl>

          <button className="profile-edit-button" type="button">
            회원 정보 수정
          </button>
        </section>

        <section className="account-actions" aria-label="계정 관리">
          <button className="logout-button" type="button">
            로그아웃
          </button>

          <button className="withdrawal-button" type="button">
            회원 탈퇴
          </button>
        </section>
      </section>
    </main>
  );
}

export default MyPage;
