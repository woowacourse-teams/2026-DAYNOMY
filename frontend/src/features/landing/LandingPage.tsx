import { Link } from 'react-router-dom';
import { useEffect, useRef, useState, type MouseEvent } from 'react';
import { LandingIcon } from './LandingIcon';
import { GuideMockup } from './GuideMockup';
import { WelcomeGuide } from './WelcomeGuide';
import { WelcomeDetails } from './WelcomeDetails';
import './landing.css';
import './ProductMockup.css';
import './welcomeShowcase.css';

const steps = [
  { id: 'portfolio', label: '내 포트폴리오', screen: 'overview', title: '내 자산에서 시작해요.' },
  { id: 'news', label: '관련 소식', screen: 'news', title: '내 종목의 소식을 모아요.' },
  { id: 'analysis', label: 'AI 영향 분석', screen: 'analysis', title: 'AI가 예상 영향을 읽어요.' },
  { id: 'sources', label: '분석 근거', screen: 'sources', title: '판단의 근거까지 확인해요.' },
  { id: 'league', label: '투자 리그', screen: 'league', title: '다른 투자자의 기록도 살펴봐요.' },
] as const;
const scrollStep = 180;

export default function LandingPage({ onStart }: { onStart: () => void }) {
  const stageRef = useRef<HTMLDivElement>(null);
  const progressRef = useRef<HTMLSpanElement>(null);
  const rootRef = useRef<HTMLDivElement>(null);
  const [reducedMotion, setReducedMotion] = useState(
    () => window.matchMedia('(prefers-reduced-motion: reduce)').matches,
  );
  const [introPhase, setIntroPhase] = useState<
    'hidden' | 'reveal' | 'economy' | 'combine' | 'seam' | 'open' | 'done' | 'skipped'
  >(reducedMotion ? 'skipped' : 'hidden');
  const introDone = introPhase === 'done' || introPhase === 'skipped';
  const [step, setStep] = useState(0);
  const stepRef = useRef(0);
  const previousStep = useRef(0);

  function chooseStep(index: number) {
    const root = rootRef.current;
    root?.scrollTo({
      top: index * scrollStep,
      behavior: reducedMotion ? 'instant' : 'smooth',
    });
  }
  function handleScroll() {
    const root = rootRef.current;
    if (!root) return;
    root.style.scrollSnapType =
      root.scrollTop >= (steps.length - 1) * scrollStep ? 'none' : 'y mandatory';
    const position = Math.max(0, Math.min(steps.length - 1, root.scrollTop / scrollStep));
    if (progressRef.current)
      progressRef.current.style.transform = `scaleX(${position / (steps.length - 1)})`;
    const next = Math.round(position);
    if (next !== stepRef.current) {
      stepRef.current = next;
      setStep(next);
    }
  }
  function showGuide() {
    if (rootRef.current) rootRef.current.style.scrollSnapType = 'none';
    const heading = document.getElementById('guide-title');
    document.getElementById('guide')?.scrollIntoView({ behavior: 'instant', block: 'start' });
    heading?.focus({ preventScroll: true });
  }
  function handleStart(event: MouseEvent<HTMLAnchorElement>) {
    if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey)
      return;
    event.preventDefault();
    onStart();
  }

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
    const root = rootRef.current;
    if (introDone || !root) return;
    const previousOverflow = root.style.overflow;
    root.style.overflow = 'hidden';
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
      root.style.overflow = previousOverflow;
    };
  }, [introDone]);

  useEffect(() => {
    const root = rootRef.current;
    const stage = stageRef.current;
    if (!root || !stage) return;
    const observer = new ResizeObserver((entries) => {
      for (const entry of entries) {
        if (entry.target === root) {
          if (root.scrollTop <= (steps.length - 1) * scrollStep)
            root.scrollTo({ top: stepRef.current * scrollStep, behavior: 'instant' });
        } else {
          const { width, height } = entry.contentRect;
          const deviceWidth = Math.max(0, Math.min(330, width - 32, ((height - 34) * 390) / 844));
          stage.style.setProperty('--film-device-width', `${deviceWidth}px`);
        }
      }
    });
    observer.observe(root);
    observer.observe(stage);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    const direction = step >= previousStep.current ? 1 : -1;
    previousStep.current = step;
    if (reducedMotion) return;
    const animation = stageRef.current?.querySelector('.guide-screen')?.animate(
      [
        { opacity: 0.25, transform: `translateY(${direction * 12}px)` },
        { opacity: 1, transform: 'translateY(0)' },
      ],
      { duration: 320, easing: 'ease-out' },
    );
    return () => animation?.cancel();
  }, [step, reducedMotion]);

  const current = steps[step];
  return (
    <div
      ref={rootRef}
      className="landing-page page-shell welcome-film"
      data-scene={current.id}
      data-motion-paused={introDone}
      data-intro-skipped={introPhase === 'skipped'}
      onScroll={handleScroll}
      onFocusCapture={(event) => {
        if (!introDone && !event.target.closest('.intro-skip')) setIntroPhase('skipped');
      }}
    >
      <a
        className="landing-skip-link"
        href="#landing-main"
        onClick={(event) => {
          event.preventDefault();
          document.getElementById('landing-main')?.focus({ preventScroll: true });
        }}
      >
        본문 바로가기
      </a>
      {!introDone && (
        <button
          className="intro-skip"
          onClick={() => {
            setIntroPhase('skipped');
            document.getElementById('landing-main')?.focus({ preventScroll: true });
          }}
        >
          인트로 건너뛰기
        </button>
      )}
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
      <header className="navbar">
        <div className="nav-inner">
          <a
            className="wordmark"
            href="#home"
            aria-label="DAYNOMY 홈"
            onClick={(event) => {
              event.preventDefault();
              chooseStep(0);
            }}
          >
            DAYNOMY
          </a>
        </div>
      </header>
      <main
        id="landing-main"
        tabIndex={-1}
        onKeyDown={(event) => {
          if (
            event.target !== event.currentTarget ||
            (rootRef.current?.scrollTop ?? 0) > (steps.length - 1) * scrollStep
          )
            return;
          const direction =
            event.key === 'PageDown' || event.key === 'ArrowDown'
              ? 1
              : event.key === 'PageUp' || event.key === 'ArrowUp'
                ? -1
                : 0;
          if (!direction) return;
          event.preventDefault();
          if (step === steps.length - 1 && direction > 0) showGuide();
          else chooseStep(Math.max(0, Math.min(steps.length - 1, step + direction)));
        }}
      >
        <div
          className="film-scroll-track"
          style={{
            height: `calc(var(--welcome-height) - var(--welcome-nav-height) + ${(steps.length - 1) * scrollStep}px)`,
          }}
        >
          <div
            className="film-scroll-stops"
            aria-hidden="true"
            style={{ gridTemplateRows: `repeat(${steps.length - 1}, ${scrollStep}px) 1fr` }}
          >
            {steps.map((item) => (
              <div key={item.id} />
            ))}
          </div>
          <div className="film-shell">
            <section className="hero restored-hero welcome-hero hero-enter" id="home">
              <div className="hero-content">
                <span className="film-kicker">DAYNOMY AI</span>
                <h1>
                  <span className="hero-message">
                    내 투자에 미칠 영향,
                    <br />
                    <span className="film-accent">AI로 한눈에.</span>
                  </span>
                </h1>
                <p className="film-step-copy" role="status">
                  <span>
                    {String(step + 1).padStart(2, '0')} / {String(steps.length).padStart(2, '0')}
                  </span>
                  {current.title}
                </p>
              </div>
              <div className="film-stage" ref={stageRef} aria-label="DAYNOMY 서비스 소개">
                <div className="film-device">
                  <GuideMockup screen={current.screen} label={`${current.label} 소개용 목업`} />
                </div>
                <p className="film-example-note">소개용 예시 · 투자 참고 자료</p>
              </div>
              <div className="cta-row film-actions">
                <a className="primary-cta" href="/" onClick={handleStart}>
                  DAYNOMY 시작하기 <LandingIcon name="ArrowUpRight" size={18} />
                </a>
                <button
                  className="secondary-cta film-scroll-hint"
                  type="button"
                  aria-label={step === steps.length - 1 ? '사용법 살펴보기' : '다음 소개 장면으로'}
                  onClick={() => (step === steps.length - 1 ? showGuide() : chooseStep(step + 1))}
                >
                  {step === steps.length - 1 ? '사용법 살펴보기' : '스크롤해서 살펴보기'}{' '}
                  <span aria-hidden="true">↓</span>
                </button>
              </div>
              <div className="film-controls">
                <div className="film-steps" role="group" aria-label="소개 장면 선택">
                  {steps.map((item, index) => (
                    <button
                      type="button"
                      key={item.id}
                      aria-current={step === index ? 'step' : undefined}
                      onClick={() => chooseStep(index)}
                    >
                      <span>{String(index + 1).padStart(2, '0')}</span>
                      {item.label}
                    </button>
                  ))}
                </div>
                <div className="film-progress" aria-hidden="true">
                  <span ref={progressRef} />
                </div>
              </div>
            </section>
          </div>
        </div>
        <WelcomeGuide />
        <WelcomeDetails onStart={handleStart} />
      </main>
      <footer className="landing-footer film-footer">
        <small>© 2026 DAYNOMY</small>
        <nav aria-label="서비스 안내">
          <Link to="/terms">이용약관</Link>
          <Link to="/privacy">개인정보처리방침</Link>
          <a href="mailto:paperchoigo@gmail.com">문의하기</a>
        </nav>
      </footer>
    </div>
  );
}
