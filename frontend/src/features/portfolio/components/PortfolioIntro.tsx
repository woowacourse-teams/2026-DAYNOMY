import { ArrowDownIcon } from '@phosphor-icons/react/dist/csr/ArrowDown';
import { PawPrintIcon } from '@phosphor-icons/react/dist/csr/PawPrint';
import { useEffect, useState } from 'react';
import analysisMascot from '../../../assets/portfolio-analysis-mascot.png';
import mascot from '../../../assets/portfolio-mascot.png';
import './portfolioIntro.css';

export function PortfolioIntro({ onRegister }: { onRegister: () => void }) {
  const [analysisVisible, setAnalysisVisible] = useState(false);

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
        <img
          className="portfolio-intro-start-mascot"
          src={mascot}
          alt=""
          width={200}
          height={174}
        />
        <h1 id="portfolio-intro-title">포트폴리오를 만들어 보세요</h1>
        <p className="portfolio-intro-description">
          보유 종목을 추가하면
          <br />
          <strong>어디에 얼마나 투자했는지</strong> 한눈에 볼 수 있어요.
        </p>
        <button type="button" className="portfolio-intro-register" onClick={onRegister}>
          포트폴리오 만들기
        </button>
        <a className="portfolio-intro-scroll" href="#portfolio-intro-demo">
          아래에서 예시 분석 보기
          <ArrowDownIcon size={16} aria-hidden="true" />
        </a>
      </section>
      <section
        id="portfolio-intro-demo"
        className="portfolio-intro-demo"
        aria-labelledby="portfolio-intro-demo-title"
        tabIndex={-1}
      >
        <div className="portfolio-intro-guide">
          <img
            className="portfolio-intro-mascot"
            src={analysisMascot}
            alt=""
            width={360}
            height={327}
          />
          <div className="portfolio-intro-bubble">
            <h2 id="portfolio-intro-demo-title">예시로 분석해 볼까요?</h2>
            <p className="portfolio-intro-description">
              삼성전자와 SK하이닉스가 이 예시의 80%를 차지해요. 반도체 수요가 회복되면 전체
              평가금액은 어떻게 달라질까요?
            </p>
          </div>
          <button
            type="button"
            className="portfolio-intro-analyze"
            aria-expanded={analysisVisible}
            aria-controls="portfolio-intro-analysis"
            onClick={() => setAnalysisVisible(!analysisVisible)}
          >
            <PawPrintIcon size={15} weight="fill" aria-hidden="true" />
            {analysisVisible ? '분석 결과 접기' : '분석해 보기'}
            <ArrowDownIcon className="portfolio-intro-analyze-arrow" size={14} aria-hidden="true" />
          </button>
        </div>
        <section
          className="portfolio-intro-example"
          aria-labelledby="portfolio-intro-example-title"
        >
          <h3 id="portfolio-intro-example-title">예시 포트폴리오</h3>
          <figure className="portfolio-intro-allocation">
            <div className="portfolio-intro-donut" aria-hidden="true">
              <span>자산 비중</span>
            </div>
            <figcaption>
              <ul aria-label="예시 자산 비중">
                <li>
                  <span className="portfolio-intro-swatch is-a" />
                  삼성전자 <strong>50%</strong>
                </li>
                <li>
                  <span className="portfolio-intro-swatch is-b" />
                  SK하이닉스 <strong>30%</strong>
                </li>
                <li>
                  <span className="portfolio-intro-swatch is-c" />
                  KODEX 200 <strong>20%</strong>
                </li>
              </ul>
            </figcaption>
          </figure>
        </section>
        <section
          id="portfolio-intro-analysis"
          className="portfolio-intro-result"
          aria-labelledby="portfolio-intro-result-title"
          hidden={!analysisVisible}
        >
          <h3 id="portfolio-intro-result-title">반도체 수요가 회복된다면</h3>
          <p>
            메모리 주문 증가가 실제 출하로 이어지고 판매 단가가 유지되면 삼성전자와 SK하이닉스의
            반도체 매출이 늘 수 있어요. 생산비가 함께 오르면 매출이 늘어도 이익 개선 폭은 작아질 수
            있어요.
          </p>
          <h4>삼성전자만 10% 오르면 전체는 5% 올라요</h4>
          <p>
            나머지 가격이 그대로라면 삼성전자의 비중 50%에 상승률 10%를 곱한 5%가 전체 평가금액에
            반영돼요. KODEX 200 가격이 그대로인 상태에서 두 반도체 종목이 함께 10% 오르면 전체는 8%
            올라요. 같은 조건에서 두 종목이 함께 10% 내리면 전체도 8% 내려요.
          </p>
          <h4>다음 실적에서 확인할 점</h4>
          <ul>
            <li>
              <strong>출하량과 반도체 매출</strong>이 함께 늘었는지 확인해 보세요.
            </li>
            <li>
              <strong>영업이익률</strong>을 보면 매출 증가가 이익으로 이어졌는지 알 수 있어요.
            </li>
            <li>
              <strong>재고</strong>가 계속 쌓인다면 출하 증가가 이어질지 지켜볼 필요가 있어요.
            </li>
          </ul>
        </section>
      </section>
    </main>
  );
}
