import { LandingIcon } from './LandingIcon';
import './ProductMockup.css';
import mockupNews from '../../assets/news-home-banner.jpg';

export function ProductMockup({
  device,
  screen,
}: {
  device: 'iphone' | 'macbook';
  screen: 'portfolio' | 'news' | 'league';
}) {
  const labels = { portfolio: '내 포트폴리오', news: '소식', league: '주간 리그' };
  return (
    <figure
      className={`product-mockup ${device} mockup-${screen}`}
      aria-label={`${device === 'iphone' ? '아이폰' : '맥북'}으로 소개하는 ${labels[screen]} 예시 화면`}
    >
      <div className="mockup-hardware">
        <div className="mockup-display">
          {device === 'iphone' && (
            <div className="mockup-status" aria-hidden="true">
              <b>9:41</b>
              <span>
                <LandingIcon name="Signal" size={13} />
                <LandingIcon name="Wifi" size={13} />
                <LandingIcon name="BatteryFull" size={17} />
              </span>
            </div>
          )}
          <div className="mockup-app" aria-hidden="true">
            <div className="mockup-app-header">
              <b>DAYNOMY</b>
              <div className="mockup-header-actions">
                <LandingIcon name="Search" size={14} />
                <span>로그인</span>
              </div>
              <div className="mockup-service-tabs">
                <span className={screen === 'portfolio' ? 'active' : ''}>포트폴리오</span>
                <span className={screen === 'news' ? 'active' : ''}>소식</span>
                <span className={screen === 'league' ? 'active' : ''}>투자 리그</span>
              </div>
            </div>
            <div className="mockup-viewport">
              <div className="mockup-content">
                {screen === 'portfolio' && (
                  <>
                    <h3>내 포트폴리오</h3>
                    <div className="mockup-actions">
                      <span>소식 ‹</span>
                      <b>＋ 거래 기록</b>
                    </div>
                    <div className="mockup-balance mockup-reveal">
                      <span>전체 자산 평가금액</span>
                      <strong>
                        12,840,000<small>원</small>
                      </strong>
                      <div className="mockup-daily">
                        최근 거래일 손익{' '}
                        <em>
                          +120,000원 <small>+0.94%</small>
                        </em>
                      </div>
                    </div>
                    <div className="service-metrics mockup-reveal delay-one">
                      <div>
                        <span>보유자산 매입금액</span>
                        <b>12,000,000원</b>
                        <small>현재 보유 수량 기준</small>
                      </div>
                      <div>
                        <span>평가손익</span>
                        <b className="service-positive">+840,000원</b>
                        <small className="service-positive">+7.00%</small>
                      </div>
                    </div>
                    <div className="service-chart mockup-reveal delay-one">
                      <div className="service-section-title">
                        <b>자산 추이</b>
                        <span>1개월 ⌄</span>
                      </div>
                      <div className="service-chart-tabs">
                        <b>평가금액</b>
                        <span>평가손익</span>
                        <span>평가수익률</span>
                      </div>
                      <svg className="mockup-chart" viewBox="0 0 300 100" fill="none">
                        <path d="M0 25H300 M0 55H300 M0 85H300" stroke="#eceeef" />
                        <path
                          className="chart-area"
                          d="M0 88 L25 80 L48 87 L73 60 L95 67 L120 45 L142 51 L168 28 L194 35 L217 17 L240 25 L272 10 L300 5 V100 H0Z"
                          fill="#fbefed"
                        />
                        <path
                          className="chart-line"
                          d="M0 88 L25 80 L48 87 L73 60 L95 67 L120 45 L142 51 L168 28 L194 35 L217 17 L240 25 L272 10 L300 5"
                          stroke="#d24755"
                          strokeWidth="2.5"
                        />
                        <path d="M0 85H300" stroke="#8998ac" strokeDasharray="4 4" />
                      </svg>
                      <div className="service-chart-dates">
                        <span>09.14</span>
                        <span>09.28</span>
                        <span>10.14</span>
                      </div>
                    </div>
                    <div className="service-composition mockup-reveal delay-two">
                      <b>자산 구성</b>
                      <div>
                        <div className="service-donut">
                          <span>
                            2<small>보유 자산</small>
                          </span>
                        </div>
                        <dl>
                          <div>
                            <dt>● 국내 주식</dt>
                            <dd>65%</dd>
                          </div>
                          <div>
                            <dt>● ETF</dt>
                            <dd>35%</dd>
                          </div>
                        </dl>
                      </div>
                    </div>
                    <div className="service-holdings mockup-reveal delay-three">
                      <div className="service-section-title">
                        <b>
                          보유 자산 <small>2</small>
                        </b>
                        <span>거래 내역</span>
                      </div>
                      <div>
                        <b>삼성전자</b>
                        <span className="service-positive">+8.20%</span>
                      </div>
                      <div>
                        <b>KODEX 200</b>
                        <span className="service-positive">+4.77%</span>
                      </div>
                    </div>
                  </>
                )}
                {screen === 'news' && (
                  <>
                    <div className="mockup-news-grid">
                      <section className="service-story mockup-reveal">
                        <h3>오늘의 이슈</h3>
                        <img src={mockupNews} width={2048} height={1109} alt="" />
                        <h4>금리 변화, 우리 경제에는 어떤 영향을 줄까요?</h4>
                        <span>01 / 03</span>
                      </section>
                      <section className="service-people">
                        <div className="service-section-title">
                          <h3>주목받는 인물의 글</h3>
                          <span>전체 보기</span>
                        </div>
                        {[
                          'AI 산업의 변화와 다음 성장의 기회',
                          '오늘의 시장을 바라보는 새로운 시선',
                        ].map((title, i) => (
                          <div
                            className={`service-person mockup-reveal delay-${i ? 'two' : 'one'}`}
                            key={title}
                          >
                            <i>
                              <LandingIcon name="UserRound" />
                            </i>
                            <div>
                              <small>인물의 글 · {i ? 'YouTube' : 'X'}</small>
                              <h4>{title}</h4>
                              <span>10월 14일 등록</span>
                            </div>
                          </div>
                        ))}
                      </section>
                    </div>
                    <section className="service-feed mockup-reveal delay-two">
                      <h3>최근 올라온 글</h3>
                      <div className="service-filters">
                        <b>전체</b>
                        <span>DAYNOMY Issue</span>
                        <span>X</span>
                        <span>Threads</span>
                        <span>YouTube</span>
                      </div>
                      {['기업 실적 발표, 숫자 너머의 흐름을 읽다'].map((title, i) => (
                        <div
                          className={`service-feed-row mockup-reveal delay-${i ? 'three' : 'two'}`}
                          key={title}
                        >
                          <span className="service-platform">{i ? 'X' : '▶'}</span>
                          <div>
                            <small>{i ? 'X' : 'YouTube'}　10월 14일</small>
                            <h4>{title}</h4>
                          </div>
                          <span>원문 보기 +</span>
                        </div>
                      ))}
                    </section>
                  </>
                )}
                {screen === 'league' && (
                  <>
                    <div className="service-league-heading">
                      <h3>주간 리그</h3>
                      <b>투자리그 참여하기</b>
                    </div>
                    <div className="service-league-controls">
                      <span>이번 주　⌄</span>
                      <small>10.12 – 10.16</small>
                    </div>
                    <div className="service-league-notice">
                      거래일 종가를 기준으로 수익률을 비교해요.
                    </div>
                    <div className="service-section-title service-ranking-title">
                      <b>주간 순위</b>
                      <span>3명 · 주간 수익률순</span>
                    </div>
                    {[
                      { name: '꾸준한 하루', rate: '+8.40%' },
                      { name: '시장을 읽는 사람', rate: '+6.20%' },
                      { name: '내일의 투자자', rate: '+4.70%' },
                    ].map((investor, i) => (
                      <div
                        className={`service-rank mockup-reveal delay-${['one', 'two', 'three'][i]}`}
                        key={investor.name}
                      >
                        <b>{i + 1}</b>
                        <i>
                          <LandingIcon name="UserRound" size={17} />
                        </i>
                        <div>
                          <b>{investor.name}</b>
                          <small>투자 기록 보기 →</small>
                        </div>
                        <strong>
                          {investor.rate}
                          <small>주간 수익률</small>
                        </strong>
                      </div>
                    ))}
                    <div className="service-record mockup-reveal delay-three">
                      <div className="service-section-title">
                        <b>투자 기록</b>
                        <span>보유 이유</span>
                      </div>
                      <h4>삼성전자</h4>
                      <p>
                        실적의 변화와 산업의 흐름을 살펴보며
                        <br />
                        나의 투자 판단을 기록했어요.
                      </p>
                      <span>판단과 복기를 함께 살펴보세요.</span>
                    </div>
                    <div className="service-pagination">
                      <span>‹</span>
                      <b>1</b>
                      <span>›</span>
                    </div>
                  </>
                )}
              </div>
            </div>
          </div>
          {device === 'iphone' && <div className="mockup-home-indicator" />}
        </div>
      </div>
      {device === 'macbook' && (
        <div className="macbook-base">
          <i />
        </div>
      )}
      <figcaption>
        {screen === 'league'
          ? '투자 리그 디자인을 반영한 소개용 화면입니다.'
          : 'DAYNOMY 디자인을 반영한 소개용 화면입니다.'}{' '}
        데이터는 예시입니다.
      </figcaption>
    </figure>
  );
}
