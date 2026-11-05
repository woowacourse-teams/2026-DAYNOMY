import type { MouseEvent } from 'react';
import { Link } from 'react-router-dom';
import { ChartDonutIcon, FilesIcon, LinkSimpleIcon } from '@phosphor-icons/react';
import { LandingIcon } from './LandingIcon';

export function WelcomeDetails({
  onStart,
}: {
  onStart: (event: MouseEvent<HTMLAnchorElement>) => void;
}) {
  return (
    <div className="welcome-details">
      <section
        className="technology-story welcome-technology"
        id="technology"
        aria-labelledby="technology-title"
      >
        <div className="technology-heading feature-copy">
          <span className="feature-number">기술력</span>
          <h2 id="technology-title" tabIndex={-1}>
            근거가 보이는 <span>AI 분석.</span>
          </h2>
        </div>
        <ol className="welcome-analysis-flow" aria-label="AI 분석 과정">
          <li>
            <div className="welcome-tech-icon" aria-hidden="true">
              <FilesIcon size={32} weight="duotone" />
            </div>
            <div>
              <span className="welcome-tech-number" aria-hidden="true">
                01
              </span>
              <h3>실제 자료 연결</h3>
              <p>뉴스·공시에서 근거를 찾아요.</p>
            </div>
          </li>
          <li>
            <div className="welcome-tech-icon" aria-hidden="true">
              <ChartDonutIcon size={32} weight="duotone" />
            </div>
            <div>
              <span className="welcome-tech-number" aria-hidden="true">
                02
              </span>
              <h3>보유 비중 반영</h3>
              <p>종목별 영향을 내 자산에 맞춰요.</p>
            </div>
          </li>
          <li>
            <div className="welcome-tech-icon" aria-hidden="true">
              <LinkSimpleIcon size={32} weight="duotone" />
            </div>
            <div>
              <span className="welcome-tech-number" aria-hidden="true">
                03
              </span>
              <h3>출처까지 제공</h3>
              <p>분석에 쓰인 자료를 바로 확인해요.</p>
            </div>
          </li>
        </ol>
        <p className="capture-note">AI 분석은 투자 참고 정보예요.</p>
      </section>

      <section
        className="welcome-league-technology"
        id="league-technology"
        aria-labelledby="league-technology-title"
      >
        <div className="technology-heading feature-copy">
          <span className="feature-number">투자 리그 성능 개선</span>
          <h2 id="league-technology-title">
            요청이 몰려도, <span>순위는 빠르게.</span>
          </h2>
        </div>
        <dl className="welcome-league-results" aria-label="순위 조회 성능 측정 결과">
          <div>
            <dt>초당 실제 요청</dt>
            <dd>
              <small>5.05건 →</small>100<span>건</span>
            </dd>
          </div>
          <div>
            <dt>응답 시간 · p95</dt>
            <dd>
              <small>약 10초 →</small>21.9<span>ms</span>
            </dd>
          </div>
          <div>
            <dt>요청 오류</dt>
            <dd>
              <small>타임아웃 →</small>0<span>건</span>
            </dd>
          </div>
        </dl>
        <ul className="welcome-league-methods" aria-label="투자 리그 성능과 안정성 개선">
          <li>
            <h3>순위는 캐시로</h3>
            <p>계산한 순위를 공유하고, 갱신은 백그라운드에서 처리해요.</p>
          </li>
          <li>
            <h3>조회는 한 번에</h3>
            <p>참가자별 반복 조회를 일괄 조회로 바꿔 DB 부담을 줄였어요.</p>
          </li>
          <li>
            <h3>확정은 신중하게</h3>
            <p>오류가 남으면 확정을 보류하고, 잠정·확정 순위를 구분해요.</p>
          </li>
        </ul>
        <div className="welcome-league-footer">
          <p>기존 순위 조회 부하 테스트 · 개선 전 → 개선 후</p>
          <Link className="feature-link" to="/league">
            투자 리그 살펴보기 <LandingIcon name="ArrowUpRight" size={18} />
          </Link>
        </div>
      </section>

      <section className="usage-guide welcome-faq" aria-labelledby="faq-title">
        <div>
          <span className="story-kicker">시작하기 전에</span>
          <h2 id="faq-title">
            궁금한 점을
            <br />
            먼저 확인하세요.
          </h2>
        </div>
        <div className="guide-questions">
          <details>
            <summary>
              회원가입 없이 이용할 수 있나요?
              <LandingIcon name="ChevronDown" size={18} />
            </summary>
            <p>
              소식과 임시 포트폴리오는 로그인 없이 이용할 수 있어요. AI 분석은 비회원도 1회 체험할
              수 있고, 기록을 계정에 저장하려면 로그인이 필요해요.
            </p>
          </details>
          <details>
            <summary>
              어떤 자산을 등록할 수 있나요?
              <LandingIcon name="ChevronDown" size={18} />
            </summary>
            <p>
              국내·미국 주식과 ETF의 매수·매도를 기록할 수 있어요. 평가금액은 서비스가 제공하는 최근
              종가를 기준으로 보여줘요.
            </p>
          </details>
          <details>
            <summary>
              모바일에서도 사용할 수 있나요?
              <LandingIcon name="ChevronDown" size={18} />
            </summary>
            <p>별도의 앱 설치 없이 모바일 브라우저에서 이용할 수 있어요.</p>
          </details>
        </div>
      </section>
      <section className="closing-cta welcome-start" aria-labelledby="start-title">
        <h2 id="start-title">
          이제, 내 투자와
          <br />
          연결해 보세요.
        </h2>
        <a className="primary-cta" href="/" onClick={onStart}>
          DAYNOMY 시작하기 <LandingIcon name="ArrowUpRight" size={18} />
        </a>
      </section>
    </div>
  );
}
