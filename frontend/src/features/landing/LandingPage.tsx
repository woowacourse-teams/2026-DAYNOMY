import { Link } from 'react-router-dom';
import { useEffect, useRef, useState, type MouseEvent } from 'react';
import { LandingIcon } from './LandingIcon';
import './landing.css';
import { ProductMockup } from './ProductMockup';
import { GuideMockup } from './GuideMockup';

const topics = ['금리', '환율', '산업 동향', '기업 실적', '경제 정책'];
const assets = [
  '포트폴리오',
  '보유 종목',
  '거래 기록',
  '오늘의 소식',
  '통합 검색',
  '투자 리그',
  'AI 분석',
  '시장 분석',
];
const menuItems = [
  { label: '서비스 소개', href: '#product' },
  { label: '사용법', href: '#guide' },
  { label: '기술력', href: '#technology' },
  { label: '시작하기', href: '#start' },
];
const usageSteps = [
  {
    label: '거래 기록',
    title: '종목을 찾고,',
    emphasis: '거래를 기록하세요.',
    description: '매수·매도, 거래일, 수량과 단가를 입력하면 포트폴리오가 만들어져요.',
    screen: 'trade' as const,
  },
  {
    label: '자산 확인',
    title: '내 자산과 손익을',
    emphasis: '한눈에 확인하세요.',
    description: '최근 종가 기준 평가금액과 손익, 보유 종목별 비중을 확인해요.',
    screen: 'overview' as const,
  },
  {
    label: '관련 소식',
    title: '내 종목의 소식을',
    emphasis: '모아 보세요.',
    description: '「보유 자산 소식」에서 내 종목과 연결된 콘텐츠를 읽어보세요.',
    screen: 'news' as const,
  },
  {
    label: 'AI 분석',
    title: '내 투자에 미칠 영향,',
    emphasis: 'AI로 살펴보세요.',
    description: '종목별 영향과 분석 근거를 확인해요. 비회원도 1회 체험할 수 있어요.',
    screen: 'analysis' as const,
  },
];
export default function LandingPage({ onStart }: { onStart: () => void }) {
  function handleStart(event: MouseEvent<HTMLAnchorElement>) {
    if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey)
      return;
    event.preventDefault();
    onStart();
  }
  const menuRef = useRef<HTMLDialogElement>(null);
  const [reducedMotion, setReducedMotion] = useState(
    () => window.matchMedia('(prefers-reduced-motion: reduce)').matches,
  );
  const [motionPaused, setMotionPaused] = useState(false);
  const [introPhase, setIntroPhase] = useState<
    'hidden' | 'reveal' | 'economy' | 'combine' | 'seam' | 'open' | 'done' | 'skipped'
  >(reducedMotion ? 'skipped' : 'hidden');
  const introDone = introPhase === 'done' || introPhase === 'skipped';
  const paused = reducedMotion || motionPaused;

  useEffect(() => {
    const previousTitle = document.title;
    document.title = '서비스 소개 | DAYNOMY';
    const media = window.matchMedia('(prefers-reduced-motion: reduce)');
    function handleMotionChange(event: MediaQueryListEvent) {
      setReducedMotion(event.matches);
      if (event.matches) setIntroPhase('skipped');
    }
    media.addEventListener('change', handleMotionChange);
    return () => {
      document.title = previousTitle;
      media.removeEventListener('change', handleMotionChange);
    };
  }, []);

  useEffect(() => {
    if (introDone) return;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const timers = [
      window.setTimeout(() => setIntroPhase('reveal'), 250),
      window.setTimeout(() => setIntroPhase('economy'), 1050),
      window.setTimeout(() => setIntroPhase('combine'), 1600),
      window.setTimeout(() => setIntroPhase('seam'), 2800),
      window.setTimeout(() => setIntroPhase('open'), 3250),
      window.setTimeout(() => setIntroPhase('done'), 4200),
    ];
    return () => {
      timers.forEach(window.clearTimeout);
      document.body.style.overflow = previousOverflow;
    };
  }, [introDone]);

  return (
    <div
      className="landing-page page-shell"
      data-motion-paused={paused}
      data-intro-skipped={introPhase === 'skipped'}
      onFocusCapture={(event) => {
        if (!introDone && !(event.target instanceof Element && event.target.closest('.intro-skip')))
          setIntroPhase('skipped');
      }}
    >
      <a
        className="landing-skip-link"
        href="#landing-main"
        onClick={(event) => {
          event.preventDefault();
          document.getElementById('landing-main')?.focus();
        }}
      >
        본문 바로가기
      </a>
      {!introDone && (
        <button
          className="intro-skip"
          onClick={() => {
            setIntroPhase('skipped');
            document.getElementById('landing-main')?.focus();
          }}
        >
          인트로 건너뛰기
        </button>
      )}
      <header className="navbar">
        <div className="nav-inner">
          <a className="wordmark" href="#home" aria-label="DAYNOMY 홈">
            DAYNOMY
          </a>
          <div className="landing-nav-actions">
            <button
              className="motion-toggle"
              aria-pressed={paused}
              aria-describedby="motion-help"
              disabled={reducedMotion}
              onClick={() => {
                setMotionPaused((value) => !value);
                setIntroPhase('skipped');
              }}
            >
              모션 정지
            </button>
            <span id="motion-help" className="landing-sr-only">
              {reducedMotion
                ? '기기의 동작 줄이기 설정에 따라 애니메이션이 정지되어 있습니다.'
                : '자동으로 움직이는 화면을 정지하거나 다시 재생합니다.'}
            </span>
            <button
              className="menu-pill"
              onClick={() => menuRef.current?.showModal()}
              aria-label="메뉴 열기"
              aria-haspopup="dialog"
            >
              메뉴 <LandingIcon name="ChevronUp" size={16} />
            </button>
          </div>
        </div>
      </header>

      <dialog className="drawer" ref={menuRef} aria-label="전체 메뉴">
        <div className="drawer-top">
          <span className="wordmark">DAYNOMY</span>
          <button
            className="close-pill"
            onClick={() => menuRef.current?.close()}
            aria-label="메뉴 닫기"
          >
            닫기 <LandingIcon name="X" size={16} />
          </button>
        </div>
        <nav className="drawer-links" aria-label="전체 메뉴">
          {menuItems.map((item) => (
            <a key={item.label} href={item.href} onClick={() => menuRef.current?.close()}>
              {item.label}
              <LandingIcon name="ArrowUpRight" />
            </a>
          ))}
        </nav>
        <div className="drawer-footer">
          <span>매일의 경제를 더 선명하게</span>
          <span>© 2026 DAYNOMY</span>
        </div>
      </dialog>

      <main id="landing-main" tabIndex={-1}>
        <section
          className={`hero restored-hero ${introDone ? 'hero-enter' : 'hero-waiting'}`}
          id="home"
        >
          <div className="side-lines side-lines-left" aria-hidden="true">
            {Array.from({ length: 20 }, (_, i) => (
              <i key={i} style={{ width: `${60 + i * 10}px`, animationDelay: `${i * 0.25}s` }} />
            ))}
          </div>
          <div className="side-lines side-lines-right" aria-hidden="true">
            {Array.from({ length: 20 }, (_, i) => (
              <i key={i} style={{ width: `${60 + i * 10}px`, animationDelay: `${i * 0.25}s` }} />
            ))}
          </div>
          <div className="top-lines" aria-hidden="true">
            {Array.from({ length: 15 }, (_, i) => (
              <i key={i} style={{ height: `${48 + i * 9}px`, animationDelay: `${i * 0.25}s` }} />
            ))}
          </div>
          <div className="hero-content">
            <div className="topic-ticker">
              <ul className="ticker-track" aria-label="분석 대상 경제 이슈" role="list">
                {[...topics, ...topics, ...topics, ...topics].map((topic, i) => (
                  <li key={`${topic}-${i}`} aria-hidden={i >= topics.length ? true : undefined}>
                    {topic}
                  </li>
                ))}
              </ul>
            </div>
            <h1>
              <span className="hero-message">
                매일의 경제 이슈를,
                <br />내 포트폴리오와 연결하세요.
              </span>
              <span className="hero-brand-mask">
                <span className="hero-brand">DAYNOMY</span>
              </span>
            </h1>
            <p className="hero-subtitle">
              내 포트폴리오를 살펴보고, 오늘의 소식을 읽고,
              <br className="wide-only" /> 투자 리그에서 다른 투자자의 생각도 만나보세요.
            </p>
            <div className="cta-row">
              <a className="primary-cta" href="/" onClick={handleStart}>
                DAYNOMY 시작하기 <LandingIcon name="ArrowUpRight" size={18} />
              </a>
              <a className="secondary-cta" href="#product">
                서비스 둘러보기 <LandingIcon name="ChevronDown" size={16} />
              </a>
            </div>
          </div>
          <div className="progressive-blur" />

          {!introDone && (
            <div className={`brand-intro phase-${introPhase}`} aria-hidden="true">
              <div className="floating-copy float-one">
                <span>DAILY INSIGHT</span>
              </div>
              <div className="floating-copy float-two">
                <span>MARKET CONTEXT</span>
              </div>
              <div className="floating-copy float-three">
                <span>PERSONAL IMPACT</span>
              </div>
              <div
                className="combine-stage"
                aria-label="DAY ECONOMY에서 ECO가 사라지며 DAYNOMY로 결합됩니다"
              >
                <div className="day-word">
                  {'DAY'.split('').map((l, i) => (
                    <span className="intro-letter-mask" key={i}>
                      <i style={{ transitionDelay: `${i * 70}ms` }}>{l}</i>
                    </span>
                  ))}
                </div>
                <div className="economy-word">
                  <div className="eco-part">
                    {'ECO'.split('').map((l, i) => (
                      <span className="intro-letter-mask" key={i}>
                        <i style={{ transitionDelay: `${i * 70}ms` }}>{l}</i>
                      </span>
                    ))}
                  </div>
                  <div className="nomy-part">
                    {'NOMY'.split('').map((l, i) => (
                      <span className="intro-letter-mask" key={i}>
                        <i style={{ transitionDelay: `${210 + i * 70}ms` }}>{l}</i>
                      </span>
                    ))}
                  </div>
                </div>
              </div>
              <div className="seam" />
              <div className="curtain curtain-top">
                <span className="curtain-word">
                  {'DAYNOMY'.split('').map((letter, index) => (
                    <i key={index}>{letter}</i>
                  ))}
                </span>
              </div>
              <div className="curtain curtain-bottom">
                <span className="curtain-word">
                  {'DAYNOMY'.split('').map((letter, index) => (
                    <i key={index}>{letter}</i>
                  ))}
                </span>
              </div>
            </div>
          )}
        </section>

        <section className="asset-strip" aria-label="DAYNOMY 주요 기능">
          <p>
            내 자산을 이해하는 데 필요한
            <br />
            정보를 한곳에서
          </p>
          <div className="asset-marquee">
            <ul className="asset-track" aria-label="주요 기능 목록" role="list">
              {[...assets, ...assets, ...assets].map((asset, i) => (
                <li key={`${asset}-${i}`} aria-hidden={i >= assets.length ? true : undefined}>
                  {asset}
                  <i aria-hidden="true" />
                </li>
              ))}
            </ul>
          </div>
        </section>

        <section className="service-overview" id="product" aria-labelledby="product-title">
          <span className="story-kicker">하나의 흐름으로 만나는 경제</span>
          <h2 id="product-title">
            내 자산에서 시작해,
            <br />
            세상의 소식과 사람들의 생각까지.
          </h2>
          <p>DAYNOMY에서 매일의 경제를 나에게 가까운 순서로 살펴보세요.</p>
          <ol className="service-flow" aria-label="서비스 소개 순서" role="list">
            <li>내 포트폴리오</li>
            <li>소식</li>
            <li>투자 리그</li>
          </ol>
        </section>

        <section
          className="feature-section portfolio-story"
          id="portfolio"
          aria-labelledby="portfolio-title"
        >
          <div className="feature-inner">
            <div className="feature-copy">
              <span className="feature-number">내 포트폴리오</span>
              <h2 id="portfolio-title">
                흩어진 자산을 모으면,
                <br />
                <span>내 투자가 보입니다.</span>
              </h2>
              <p>
                국내·미국 주식과 ETF의 매수·매도를 기록하고, 평가금액과 손익, 자산 비중을 한곳에서
                확인해요.
              </p>
              <p className="feature-detail">
                관련 소식부터 내 자산에 미치는 AI 분석까지 함께 살펴보세요.
              </p>
              <a className="feature-link" href="/" onClick={handleStart}>
                내 포트폴리오 살펴보기 <LandingIcon name="ArrowUpRight" size={18} />
              </a>
            </div>
            <div className="feature-visual">
              <ProductMockup device="iphone" screen="portfolio" />
            </div>
          </div>
        </section>

        <section className="news-story" id="news" aria-labelledby="news-title">
          <div className="news-inner">
            <div className="feature-copy">
              <span className="feature-number">소식</span>
              <h2 id="news-title">
                오늘의 소식에서,
                <br />
                <span>내가 궁금한 맥락까지.</span>
              </h2>
              <p>
                투자 캘린더로 다가오는 경제 일정을 확인하고, 경제 이슈와 주목받는 인물의 글을
                한곳에서 살펴보세요.
              </p>
              <p className="feature-detail">
                궁금한 기업이나 주제는 통합 검색으로 찾아볼 수 있어요.
              </p>
              <Link className="feature-link" to="/news">
                소식 살펴보기 <LandingIcon name="ArrowUpRight" size={18} />
              </Link>
            </div>
            <ProductMockup device="macbook" screen="news" />
          </div>
        </section>

        <section
          className="feature-section league-story"
          id="league"
          aria-labelledby="league-title"
        >
          <div className="feature-inner">
            <div className="feature-copy">
              <span className="feature-number">투자 리그</span>
              <h2 id="league-title">
                나의 투자 기록이,
                <br />
                <span>다른 생각과 만날 때.</span>
              </h2>
              <p>
                주간 수익률 순위를 살펴보고, 공개된 투자자의 프로필과 기록을 만나보세요. 숫자 너머에
                있는 투자자의 생각을 읽어보세요.
              </p>
              <Link className="feature-link" to="/league">
                투자 리그 둘러보기 <LandingIcon name="ArrowUpRight" size={18} />
              </Link>
            </div>
            <div className="feature-visual">
              <ProductMockup device="iphone" screen="league" />
            </div>
          </div>
        </section>

        <section className="how-it-works" id="guide" aria-labelledby="guide-title">
          <div className="feature-copy">
            <span className="feature-number">사용법</span>
            <h2 id="guide-title">
              기록부터 이해까지,
              <br />
              <span>이 순서면 충분해요.</span>
            </h2>
          </div>
          <ol className="usage-steps" role="list" aria-label="DAYNOMY 사용 순서">
            {usageSteps.map((step, index) => (
              <li key={step.label} className="usage-scene">
                <div className="usage-step-copy">
                  <span className="feature-number">{step.label}</span>
                  <h3>
                    {step.title}
                    <br />
                    <span>{step.emphasis}</span>
                  </h3>
                  <p>{step.description}</p>
                </div>
                <div className="usage-device-stage">
                  <GuideMockup screen={step.screen} label={`${index + 1}단계 ${step.label}`} />
                </div>
              </li>
            ))}
          </ol>
          <p className="capture-note">운영 화면을 재구성했으며, 거래·보유 내역은 예시예요.</p>
        </section>

        <section className="technology-story" id="technology" aria-labelledby="technology-title">
          <div className="technology-heading feature-copy">
            <span className="feature-number">실제 자료에 연결하는 그라운딩</span>
            <h2 id="technology-title">
              근거로 설명하는
              <br />
              <span>AI 포트폴리오 분석.</span>
            </h2>
            <p>
              시장 뉴스와 공시 등 실제 자료를 바탕으로, 주요 이슈가 내 보유 종목에 미칠 영향을
              분석해요.
            </p>
          </div>
          <div className="grounding-scene">
            <div className="grounding-results">
              <h3>
                근거 부족 판단을
                <br />확 줄였어요.
              </h3>
              <div
                className="grounding-chart"
                role="img"
                aria-label="제공된 로컬 테스트의 근거 부족 판단 비율 50%에서 0%로의 감소를 표현한 개념도. 중간 곡선은 실측값이 아닙니다."
              >
                <svg viewBox="0 0 720 330" fill="none" aria-hidden="true">
                  <defs>
                    <linearGradient id="grounding-reveal">
                      <stop offset="0.22" stopColor="white" stopOpacity="0" />
                      <stop offset="0.48" stopColor="white" stopOpacity="0.3" />
                      <stop offset="0.85" stopColor="white" />
                    </linearGradient>
                    <mask id="grounding-fade">
                      <rect width="720" height="330" fill="url(#grounding-reveal)" />
                    </mask>
                    <linearGradient
                      id="grounding-area"
                      x1="0"
                      y1="40"
                      x2="0"
                      y2="320"
                      gradientUnits="userSpaceOnUse"
                    >
                      <stop stopColor="#2563eb" stopOpacity="0.06" />
                      <stop offset="1" stopColor="#2563eb" stopOpacity="0" />
                    </linearGradient>
                    <linearGradient
                      id="grounding-grid"
                      x1="0"
                      y1="220"
                      x2="0"
                      y2="330"
                      gradientUnits="userSpaceOnUse"
                    >
                      <stop stopColor="#2563eb" stopOpacity="0" />
                      <stop offset="0.6" stopColor="#2563eb" stopOpacity="0.06" />
                      <stop offset="1" stopColor="#2563eb" stopOpacity="0" />
                    </linearGradient>
                    <radialGradient id="grounding-glow">
                      <stop stopColor="#60a5fa" stopOpacity="0.3" />
                      <stop offset="1" stopColor="#60a5fa" stopOpacity="0" />
                    </radialGradient>
                    <filter id="grounding-soft-glow" x="-20%" y="-50%" width="140%" height="200%">
                      <feGaussianBlur stdDeviation="6" />
                    </filter>
                  </defs>
                  <g mask="url(#grounding-fade)">
                    <path
                      d="M0 230H720 M0 252H720 M0 282H720 M0 322H720 M180 220 40 330 M250 220 200 330 M320 220 340 330 M390 220 480 330 M460 220 640 330 M530 220 800 330"
                      stroke="url(#grounding-grid)"
                      strokeWidth="1.5"
                    />
                    <path
                      d="M44 46C140 46 130 118 220 118S300 214 400 214 480 258 620 258V330H44Z"
                      fill="url(#grounding-area)"
                    />
                    <path
                      d="M44 46C140 46 130 118 220 118S300 214 400 214 480 258 620 258"
                      stroke="#60a5fa"
                      strokeWidth="12"
                      strokeOpacity="0.22"
                      filter="url(#grounding-soft-glow)"
                    />
                    <path
                      className="grounding-curve"
                      d="M44 46C140 46 130 118 220 118S300 214 400 214 480 258 620 258"
                      pathLength="1"
                      stroke="#2563eb"
                      strokeWidth="4.5"
                      strokeLinecap="round"
                    />
                    <circle cx="44" cy="46" r="4.5" fill="#2563eb" />
                    <circle cx="620" cy="258" r="36" fill="url(#grounding-glow)" />
                    <circle cx="620" cy="258" r="8" fill="#2563eb" />
                    <circle cx="620" cy="258" r="3" fill="#bfdbfe" />
                  </g>
                  <text
                    x="620"
                    y="213"
                    textAnchor="middle"
                    fill="#2563eb"
                    fontSize="62"
                    fontWeight="800"
                    letterSpacing="-4"
                  >
                    0
                    <tspan fontSize="28" letterSpacing="0">
                      %
                    </tspan>
                  </text>
                </svg>
              </div>
            </div>
            <div className="technology-detail">
              <div className="technology-device-stage">
                <GuideMockup screen="sources" label="AI 분석과 출처 예시" />
              </div>
              <ol className="technology-benefits" role="list" aria-label="내 자산에 맞춘 분석 과정">
                <li>
                  <span className="feature-number">실제 자료에서 시작</span>
                  <h3>뉴스와 공시에서 근거를 찾아요.</h3>
                  <p>보유 종목과 관련된 실제 자료를 찾아, 주요 이슈와 예상 영향을 분석해요.</p>
                </li>
                <li>
                  <span className="feature-number">그라운딩으로 연결</span>
                  <h3>분석에 쓰인 출처를 확인해요.</h3>
                  <p>근거 URL을 검증하고, 근거가 부족한 종목은 추가 검색으로 보완해요.</p>
                </li>
                <li>
                  <span className="feature-number">근거까지 함께</span>
                  <h3>판단의 이유까지 살펴보세요.</h3>
                  <p>
                    분석 결과와 근거 자료를 함께 제공해요. 출처 원문을 읽고 내 판단을 점검할 수
                    있어요.
                  </p>
                </li>
              </ol>
            </div>
          </div>
          <p className="capture-note">
            실제 분석을 요약한 예시예요. 투자 판단을 위한 참고 정보로 활용하세요.
          </p>
          <article
            className="league-technology"
            id="league-technology"
            aria-labelledby="league-technology-title"
          >
            <div className="technology-heading feature-copy league-technology-heading">
              <span className="feature-number">투자 리그를 지탱하는 기술</span>
              <h3 id="league-technology-title">
                요청이 몰려도,
                <br />
                <span>순위는 빠르게.</span>
              </h3>
              <p>순위를 조회할 때마다 반복하던 계산과 DB 조회를 줄였어요.</p>
            </div>
            <div className="league-load-results">
              <span className="feature-number">순위 조회 · 개선 전 → 개선 후</span>
              <dl aria-label="순위 조회 성능 측정 결과">
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
            </div>
            <div className="technology-detail">
              <div className="technology-device-stage">
                <ProductMockup device="iphone" screen="league" />
              </div>
              <div>
                <ol
                  className="technology-benefits league-technology-benefits"
                  role="list"
                  aria-label="투자 리그의 장애 대응"
                >
                  <li>
                    <span className="feature-number">반복 계산을 줄여서</span>
                    <h4>계산한 순위를 함께 사용해요.</h4>
                    <p>같은 주차·리그의 계산 결과를 공유하고, 갱신은 백그라운드에서 처리해요.</p>
                  </li>
                  <li>
                    <span className="feature-number">DB 조회를 묶어서</span>
                    <h4>필요한 정보는 한 번에 가져와요.</h4>
                    <p>
                      참가자마다 반복하던 조회를 일괄 조회로 바꿔, 요청이 몰릴 때의 DB 부담을
                      줄였어요.
                    </p>
                  </li>
                  <li>
                    <span className="feature-number">확정은 신중하게</span>
                    <h4>오류가 남으면, 확정은 보류해요.</h4>
                    <p>
                      실패가 남은 실행에서는 순위를 확정하지 않아요. 화면에서도 잠정 순위와 확정
                      순위를 구분해요.
                    </p>
                  </li>
                </ol>
                <Link className="feature-link" to="/league">
                  투자 리그 살펴보기 <LandingIcon name="ArrowUpRight" size={18} />
                </Link>
              </div>
            </div>
          </article>
        </section>

        <section className="usage-guide" id="faq" aria-labelledby="faq-title">
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
                어떤 자산을 등록할 수 있나요?
                <LandingIcon name="ChevronDown" size={18} />
              </summary>
              <p>
                국내·미국 주식과 ETF를 검색해 매수·매도 거래를 기록할 수 있어요. 거래일, 수량, 거래
                단가를 입력하면 보유 수량과 평균 매입가를 계산해요. 평가금액과 자산 비중은 서비스가
                제공하는 최근 종가를 기준으로 확인할 수 있어요.
              </p>
            </details>
            <details>
              <summary>
                어떤 경제 이슈를 볼 수 있나요?
                <LandingIcon name="ChevronDown" size={18} />
              </summary>
              <p>
                경제 이슈와 X·Threads·YouTube 등 인물의 콘텐츠를 함께 볼 수 있어요. 투자 캘린더에서
                다가오는 경제 일정을 확인하고, 검색으로 관심 있는 기업과 주제를 찾아보세요.
              </p>
            </details>
            <details>
              <summary>
                회원가입 없이 이용할 수 있나요?
                <LandingIcon name="ChevronDown" size={18} />
              </summary>
              <p>
                로그인 없이 소식과 임시 포트폴리오를 이용할 수 있어요. 비회원의 보유 정보와 거래
                기록은 현재 브라우저 탭에 임시 보관됩니다. 기록을 계정에 저장하려면 로그인이
                필요해요. AI 분석은 비회원도 1회 체험할 수 있고, 자산 추이와 누적 실현손익은 로그인
                후 확인할 수 있어요.
              </p>
            </details>
            <details>
              <summary>
                투자 리그는 어떻게 이용하나요?
                <LandingIcon name="ChevronDown" size={18} />
              </summary>
              <p>
                「투자 리그」에서 주간 수익률 순위와 공개된 투자자의 프로필을 볼 수 있어요. 직접
                참여하려면 로그인한 뒤 「투자리그 참여하기」에서 안내를 확인해 주세요.
              </p>
            </details>
            <details>
              <summary>
                모바일에서도 사용할 수 있나요?
                <LandingIcon name="ChevronDown" size={18} />
              </summary>
              <p>
                네. 모바일 브라우저에서 DAYNOMY에 접속해 이용할 수 있어요. 별도의 앱 설치 없이
                시작하세요.
              </p>
            </details>
          </div>
        </section>
        <section className="closing-cta" id="start" aria-labelledby="start-title">
          <span>이제, 내 투자와 연결해 보세요.</span>
          <h2 id="start-title">
            오늘의 이슈가
            <br />내 자산의 이야기가 되는 곳.
          </h2>
          <a className="primary-cta" href="/" onClick={handleStart}>
            DAYNOMY 시작하기 <LandingIcon name="ArrowUpRight" size={18} />
          </a>
          <p className="start-note">로그인 없이 첫 거래부터 기록해 보세요.</p>
        </section>
      </main>
      <footer className="landing-footer" id="contact">
        <div>
          <a className="wordmark" href="#home">
            DAYNOMY
          </a>
          <p>경제 이슈와 내 포트폴리오를 한곳에서.</p>
        </div>
        <nav aria-label="서비스 안내">
          <Link to="/about">서비스 소개</Link>
          <Link to="/terms">이용약관</Link>
          <Link to="/privacy">개인정보처리방침</Link>
          <a href="mailto:paperchoigo@gmail.com">문의하기</a>
        </nav>
        <p className="footer-notice">
          뉴스·시세·AI 분석은 정보 확인을 위한 참고 자료이며 투자 결과를 보장하지 않습니다.
        </p>
        <small>© 2026 DAYNOMY</small>
      </footer>
    </div>
  );
}
