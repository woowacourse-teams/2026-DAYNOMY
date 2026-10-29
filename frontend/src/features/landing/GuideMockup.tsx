import { LandingIcon } from './LandingIcon';
import './GuideMockup.css';

export function GuideMockup({
  screen,
  label,
}: {
  screen: 'trade' | 'overview' | 'news' | 'analysis' | 'sources';
  label: string;
}) {
  return (
    <figure className={`product-mockup guide-device iphone guide-${screen}`} aria-label={label}>
      <div className="mockup-hardware">
        <div className="mockup-display">
          <div className="mockup-status" aria-hidden="true">
            <b>9:41</b>
            <span>
              <LandingIcon name="Signal" size={13} />
              <LandingIcon name="Wifi" size={13} />
              <LandingIcon name="BatteryFull" size={17} />
            </span>
          </div>
          <div className="guide-viewport" aria-hidden="true">
            <div className="guide-screen">
              {screen === 'trade' ? (
                <>
                  <div className="guide-row">
                    <small>내 포트폴리오</small>
                    <span className="guide-close">×</span>
                  </div>
                  <h3>거래 기록</h3>
                  <p className="guide-muted">매수와 매도를 기록하고 자산의 흐름을 확인하세요.</p>
                  <div className="guide-stock guide-row">
                    <div>
                      <b>삼성전자</b>
                      <small>005930</small>
                    </div>
                    <span className="guide-change">종목 변경</span>
                  </div>
                  <div className="guide-segment">
                    <b>매수</b>
                    <span>매도</span>
                  </div>
                  <div className="guide-field">
                    <small>거래일</small>
                    <div>
                      2026. 10. 23.<span>▣</span>
                    </div>
                  </div>
                  <div className="guide-columns">
                    <div className="guide-field">
                      <small>수량 (주)</small>
                      <div>10</div>
                    </div>
                    <div className="guide-field">
                      <small>거래 단가 (원)</small>
                      <div>220000</div>
                    </div>
                  </div>
                  <p className="guide-muted">
                    2026-10-22 최근 종가 · KRW
                    <br />
                    실제 체결가와 다르면 수정해 주세요.
                  </p>
                  <div className="guide-rule guide-muted">▸ 수수료·세금　선택 입력</div>
                  <div className="guide-total">
                    <small>총 매수 지출액</small>
                    <strong>2,200,000원</strong>
                    <small>입력한 수수료와 세금을 반영한 금액</small>
                  </div>
                  <p className="guide-muted">
                    거래일을 기준으로 보유 수량과 평균 매입가가 계산됩니다.
                  </p>
                  <div className="guide-footer">
                    <span className="guide-pill">취소</span>
                    <b className="guide-pill">매수 기록하기</b>
                  </div>
                </>
              ) : (
                <>
                  <div className="guide-brand guide-row">
                    <b>DAYNOMY</b>
                    <span>로그인</span>
                  </div>
                  <div className="guide-tabs">
                    <b>포트폴리오</b>
                    <span>소식</span>
                    <span>투자 리그</span>
                  </div>
                  {screen === 'overview' && (
                    <>
                      <h3>내 포트폴리오</h3>
                      <p className="guide-muted">비회원 · 현재 탭에서만 보관돼요.</p>
                      <div className="guide-actions">
                        <span className="guide-pill">소식 ‹</span>
                        <span className="guide-pill">＋ 거래 기록</span>
                      </div>
                      <div className="guide-row">
                        <small>전체 자산 평가금액</small>
                        <small className="guide-pill">금액 숨기기</small>
                      </div>
                      <strong className="guide-balance">2,145,000원</strong>
                      <p className="guide-muted">
                        최근 거래일 손익 <b className="guide-negative">−90,000원　−4.03%</b>
                      </p>
                      <div className="guide-columns guide-rule guide-metrics">
                        <div>
                          <small>보유자산 매입금액</small>
                          <strong>2,200,000원</strong>
                          <small>현재 보유 수량 기준</small>
                        </div>
                        <div>
                          <small>평가손익</small>
                          <strong className="guide-negative">−55,000원</strong>
                          <small className="guide-negative">−2.50%</small>
                        </div>
                      </div>
                      <div className="guide-realized">
                        <small>누적 실현손익</small>
                        <p>로그인 후 확인할 수 있어요</p>
                        <small>매도로 확정된 손익</small>
                      </div>
                      <div className="guide-rule">
                        <h4>보유 종목</h4>
                        <div className="guide-row guide-holding">
                          <div>
                            <b>삼성전자</b>
                            <small>005930 · 10주</small>
                          </div>
                          <div>
                            <b>2,145,000원</b>
                            <small className="guide-negative">−2.50%</small>
                          </div>
                        </div>
                      </div>
                    </>
                  )}
                  {screen === 'news' && (
                    <>
                      <div className="guide-row">
                        <h3>보유 자산 소식</h3>
                        <small>최신순　›</small>
                      </div>
                      <p className="guide-muted">내 종목과 연결된 이야기</p>
                      {[
                        {
                          id: 'eyd5f1cJAnY',
                          title: '삼성전자 엑시노스2700 “퀄컴 넘어섰다” 성능 테스트 결과',
                          date: '10.09',
                        },
                        {
                          id: 'lXaCjxZCUYc',
                          title: '삼성전자, 테슬라에 22.8조 원 반도체 계약 수주',
                          date: '07.28',
                        },
                      ].map((item) => (
                        <div className="guide-news-item" key={item.id}>
                          <img
                            src={`https://i.ytimg.com/vi/${item.id}/hqdefault.jpg`}
                            alt=""
                            loading="lazy"
                            width="120"
                            height="90"
                          />
                          <div>
                            <small>삼성전자 · YouTube</small>
                            <h4>{item.title}</h4>
                            <small>{item.date} 발행</small>
                          </div>
                        </div>
                      ))}
                      <div className="guide-pill guide-more">보유 종목 소식 더 보기　→</div>
                    </>
                  )}
                  {(screen === 'analysis' || screen === 'sources') && (
                    <>
                      <div className="guide-row guide-analysis-title">
                        <h3>AI 분석</h3>
                        <span className="guide-pill">다시 분석하기</span>
                      </div>
                      {screen === 'analysis' ? (
                        <>
                          <h4>자산별 영향</h4>
                          <div className="guide-donut">
                            <div>
                              <b>전체 포트폴리오</b>
                              <small>전체 분석</small>
                            </div>
                          </div>
                          <div className="guide-impact guide-row">
                            <b>● 전체 포트폴리오</b>
                            <b>+100.00점</b>
                          </div>
                          <div className="guide-row guide-stock-row">
                            <span>● 삼성전자</span>
                            <b>TOP 1</b>
                          </div>
                          <small>색상은 각 자산에 예상되는 영향 방향을 나타내요.</small>
                        </>
                      ) : (
                        <div className="guide-impact guide-row">
                          <b>삼성전자</b>
                          <span>보유 비중 100%</span>
                        </div>
                      )}
                      <div className="guide-result">
                        <div className="guide-row">
                          <small>전체 포트폴리오 예상 영향</small>
                          <b className="guide-positive">↑ 긍정 영향</b>
                        </div>
                        <h3>전체 포트폴리오</h3>
                        <p>
                          현재 보유한 1개 종목 중 삼성전자 비중은 100%예요. 한 종목에 자산이
                          집중되어 있어요.
                        </p>
                        {screen === 'sources' && (
                          <p>
                            실적 개선 기대를 주는 소식이에요. 확정 실적과 감사 결과, 컨퍼런스콜에서
                            달라질 여지도 함께 확인하세요.
                          </p>
                        )}
                      </div>
                      {screen === 'sources' ? (
                        <div className="guide-source-list">
                          <b>분석 출처 접기 −</b>
                          <div>
                            <b>www.samsung.com ↗</b>
                            <small>발행일 2026-10-08</small>
                            <p>2026년 3분기 실적 사전 가이던스</p>
                          </div>
                          <div>
                            <b>www.samsung.com ↗</b>
                            <small>발행일 2026-10-08</small>
                            <p>2026년 3분기 실적발표 일정 안내</p>
                          </div>
                        </div>
                      ) : (
                        <div className="guide-source-toggle">분석 출처 모두 보기 ＋</div>
                      )}
                    </>
                  )}
                </>
              )}
            </div>
          </div>
          <div className="guide-home" aria-hidden="true" />
        </div>
      </div>
      <figcaption>운영 화면을 재구성한 예시</figcaption>
    </figure>
  );
}
