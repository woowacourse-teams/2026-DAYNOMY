import { useEffect } from 'react';
import mascot from '../../../assets/portfolio-mascot.png';
import './portfolioIntro.css';

export function PortfolioIntro({
  onRegister,
  onBrowse,
}: {
  onRegister: () => void;
  onBrowse: () => void;
}) {
  useEffect(() => {
    const previousTitle = document.title;
    document.title = '포트폴리오 만들기 | DAYNOMY';
    return () => {
      document.title = previousTitle;
    };
  }, []);

  return (
    <main className="portfolio-intro">
      <section className="portfolio-intro-start" aria-labelledby="portfolio-intro-title">
        <img className="portfolio-intro-mascot" src={mascot} alt="" width={200} height={174} />
        <h1 id="portfolio-intro-title">포트폴리오를 만들어 보세요</h1>
        <p className="portfolio-intro-description">
          보유 종목을 추가하면
          <br />
          <strong>어디에 얼마나 투자했는지</strong> 한눈에 볼 수 있어요.
        </p>
        <button type="button" className="portfolio-intro-register" onClick={onRegister}>
          포트폴리오 만들기
        </button>
        <button type="button" className="portfolio-intro-browse" onClick={onBrowse}>
          포트폴리오 바로 보기
        </button>
      </section>
      <div className="portfolio-intro-example">
        <figure className="portfolio-intro-allocation">
          <div className="portfolio-intro-donut" aria-hidden="true">
            <span>자산 비중</span>
          </div>
          <figcaption>
            <ul aria-label="예시 자산 비중">
              <li>
                <span className="portfolio-intro-swatch is-a" />
                예시 자산 A <strong>50%</strong>
              </li>
              <li>
                <span className="portfolio-intro-swatch is-b" />
                예시 자산 B <strong>30%</strong>
              </li>
              <li>
                <span className="portfolio-intro-swatch is-c" />
                예시 자산 C <strong>20%</strong>
              </li>
            </ul>
          </figcaption>
        </figure>
        <section
          className="portfolio-intro-analysis"
          aria-labelledby="portfolio-intro-example-title"
        >
          <h2 id="portfolio-intro-example-title">반도체 수요가 회복된다면</h2>
          <p>
            예시 자산 A는 포트폴리오의 50%를 차지해요. 주문 증가가 출하와 매출 증가로 이어지면 이
            자산에 긍정적으로 작용할 수 있어요.
          </p>
          <dl>
            <dt>전체 평가액에 미치는 영향</dt>
            <dd>다른 자산의 가격이 그대로이고 A만 10% 오르면 전체 평가액은 약 5% 올라요.</dd>
            <dt>다음 실적에서 확인할 점</dt>
            <dd>
              출하량과 반도체 부문 매출이 함께 늘었는지 살펴보세요. 생산비가 오르면 매출이 늘어도
              이익 개선 폭은 작아질 수 있어요.
            </dd>
          </dl>
        </section>
      </div>
    </main>
  );
}
