import { trackPortfolioEvent } from '../analytics';
import { useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import './portfolioTour.css';

const STORAGE_KEY = 'daynomy:portfolio-tour:v1';
const target = (name: string) => `[data-portfolio-tour="${name}"]`;
const steps = [
  {
    id: 'intro',
    group: 0,
    demoStep: 0,
    selector: target('trade'),
    title: '예시로 자산 등록을 시작해요',
    description:
      '다음을 누르면 거래 기록 창이 열리고 예시 종목인 삼성전자가 검색돼요. 실제 자산에는 저장되지 않아요.',
  },
  {
    id: 'stock_search',
    group: 0,
    demoStep: 2,
    selector: target('search-results'),
    title: '검색한 종목을 선택해요',
    description:
      '종목 이름과 코드를 확인하세요. 다음을 누르면 삼성전자를 선택하고 매수 날짜·수량·가격을 한 번에 채워 드려요.',
  },
  {
    id: 'trade_details',
    group: 0,
    demoStep: 6,
    selector: target('trade-fields'),
    title: '매수 내역을 한 번에 확인해요',
    description:
      '오늘 날짜, 10주, 1주당 70,000원을 입력했어요. 총 매수 금액은 700,000원이에요. 다음을 누르면 예시 포트폴리오에 반영돼요.',
  },
  {
    id: 'asset_summary',
    group: 0,
    demoStep: 8,
    selector: target('summary'),
    title: '예시 자산이 반영된 모습이에요',
    description:
      '예시 시세로 계산한 평가금액은 720,000원, 매입금액은 700,000원이에요. 차이인 +20,000원이 평가손익이에요. 이제 관련 소식을 살펴봐요.',
  },
  {
    id: 'asset_news',
    group: 1,
    demoStep: 10,
    selector: '#portfolio-news-panel',
    title: '내 종목의 소식을 읽어요',
    description:
      '보유 종목의 소식과 내용을 함께 볼 수 있어요. 이 가상 소식에서는 신제품 수요가 늘었다고 가정했어요. 다음에는 내 자산에 줄 영향을 살펴봐요.',
  },
  {
    id: 'analysis_start',
    group: 2,
    demoStep: 11,
    selector: '.portfolio-analysis-button',
    title: '이제 포트폴리오를 분석해요',
    description:
      '실제 이용할 때는 이 버튼에서 분석을 시작해요. 다음을 누르면 준비된 예시 결과를 보여드려요. 무료 분석 횟수는 사용하지 않아요.',
  },
  {
    id: 'overall_analysis',
    group: 2,
    demoStep: 12,
    selector: '.portfolio-impact-detail',
    title: '전체 자산에 대한 영향을 읽어요',
    description:
      '전체 포트폴리오의 영향 방향과 설명을 확인해요. 다음에는 종목을 선택한 모습과 분석 근거를 함께 볼게요.',
  },
  {
    id: 'asset_analysis',
    group: 2,
    demoStep: 14,
    selector: '.portfolio-impact-detail',
    title: '종목별 영향과 근거를 확인해요',
    description:
      '삼성전자의 예시 분석과 근거를 함께 확인했어요. 실제 분석에서는 원문과 날짜도 살펴보세요. ‘내 자산 등록하기’를 누르면 빈 입력창에서 직접 시작할 수 있어요.',
  },
];
const groups = ['자산 등록', '소식 확인', '포폴 분석'];
const groupIds = ['registration', 'news', 'analysis'] as const;
type TourSource = 'automatic' | 'title' | 'empty_state';

export function PortfolioTour({
  ready,
  onRegister,
  onStepChange,
  startRequest = 0,
}: {
  ready: boolean;
  startRequest?: number;
  onRegister?: () => void;
  onStepChange?: (step: number | null) => void;
}) {
  const [step, setStep] = useState<number | null>(() => {
    try {
      return localStorage.getItem(STORAGE_KEY) === 'done' ? null : 0;
    } catch {
      return 0;
    }
  });
  useLayoutEffect(() => {
    if (startRequest > 0) start('empty_state');
  }, [startRequest]);
  const [position, setPosition] = useState({
    left: 0,
    top: 0,
    width: 0,
    height: 0,
    panelLeft: 12,
    panelTop: 12,
  });
  const layerRef = useRef<HTMLDivElement>(null);
  const [host, setHost] = useState<HTMLElement>(document.body);
  const changeRef = useRef(onStepChange);
  changeRef.current = onStepChange;
  const navigationRef = useRef<HTMLElement>(null);
  const panelRef = useRef<HTMLElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const nextRef = useRef<HTMLButtonElement>(null);
  const open = ready && step !== null;
  const sourceRef = useRef<TourSource>('automatic');
  const trackedStepRef = useRef<number | null>(null);
  function properties(index: number) {
    return {
      tour_version: 'v1',
      tour_source: sourceRef.current,
      step_number: index + 1,
      step_id: steps[index].id,
      step_group: groupIds[steps[index].group],
      total_steps: steps.length,
    };
  }
  function start(source: TourSource) {
    if (trackedStepRef.current !== null) return;
    sourceRef.current = source;
    setStep(0);
  }
  useLayoutEffect(() => {
    if (!open || step === null || trackedStepRef.current === step) return;
    if (trackedStepRef.current === null)
      trackPortfolioEvent('portfolio_tour_started', properties(step));
    trackedStepRef.current = step;
    trackPortfolioEvent('portfolio_tour_step_viewed', properties(step));
  }, [open, step]);
  function finish(reason: 'button' | 'escape' | 'complete' = 'button') {
    const index = trackedStepRef.current;
    if (index === null) return;
    if (reason === 'complete') {
      trackPortfolioEvent('portfolio_tour_completed', properties(index));
      if (onRegister) trackPortfolioEvent('portfolio_tour_register_clicked', properties(index));
    } else {
      trackPortfolioEvent('portfolio_tour_exited', { ...properties(index), exit_reason: reason });
    }
    trackedStepRef.current = null;
    try {
      localStorage.setItem(STORAGE_KEY, 'done');
    } catch {
      /* Storage may be disabled. */
    }
    changeRef.current?.(null);
    setStep(null);
  }
  const finishRef = useRef(finish);
  finishRef.current = finish;
  function move(direction: 'previous' | 'next') {
    if (step === null) return;
    const destination = step + (direction === 'next' ? 1 : -1);
    if (destination < 0 || destination >= steps.length) return;
    trackPortfolioEvent('portfolio_tour_step_changed', {
      ...properties(step),
      direction,
      to_step: destination + 1,
    });
    setStep(destination);
  }
  useLayoutEffect(() => {
    changeRef.current?.(open && step !== null ? steps[step].demoStep : null);
  }, [open, step]);
  useLayoutEffect(() => () => changeRef.current?.(null), []);
  useLayoutEffect(() => {
    if (!open) return;
    const initialScroll = window.scrollY;
    document.documentElement.classList.add('portfolio-tour-active');
    const trigger = triggerRef.current;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        event.stopPropagation();
        finishRef.current('escape');
      }
      if (event.key === 'Tab' && navigationRef.current) {
        const controls = Array.from(
          navigationRef.current.querySelectorAll<HTMLButtonElement>('button:not(:disabled)'),
        );
        const index = controls.indexOf(document.activeElement as HTMLButtonElement);
        event.preventDefault();
        controls[(index + (event.shiftKey ? -1 : 1) + controls.length) % controls.length]?.focus();
      }
    };
    document.addEventListener('keydown', onKey, true);
    return () => {
      document.removeEventListener('keydown', onKey, true);
      document.documentElement.classList.remove('portfolio-tour-active');
      window.scrollTo({ top: initialScroll, behavior: 'instant' });
      trigger?.focus({ preventScroll: true });
    };
  }, [open]);
  useLayoutEffect(() => {
    if (!open) return;
    const layer = layerRef.current;
    if (layer?.showPopover) layer.showPopover();
    else layer?.removeAttribute('popover');
    nextRef.current?.focus({ preventScroll: true });
    return () => {
      if (layer?.hidePopover && layer.matches(':popover-open')) layer.hidePopover();
    };
  }, [open, host]);
  useLayoutEffect(() => {
    if (!open || step === null) return;
    let frame = 0;
    let scrolled: Element | null = null;
    function update() {
      if (step === null) return;
      setHost(
        document.querySelector<HTMLElement>('dialog.portfolio-transaction-sheet[open]') ??
          document.body,
      );
      const element = document.querySelector<HTMLElement>(steps[step].selector);
      if (element && panelRef.current) {
        if (scrolled !== element) {
          element.scrollIntoView?.({ block: 'center', behavior: 'instant' });
          scrolled = element;
          nextRef.current?.focus({ preventScroll: true });
        }
        const rect = element.getBoundingClientRect();
        const panel = panelRef.current.getBoundingClientRect();
        const width = window.innerWidth;
        const height = window.visualViewport?.height ?? window.innerHeight;
        const offset = window.visualViewport?.offsetTop ?? 0;
        const rightFits = rect.right + panel.width + 20 < width;
        const leftFits = rect.left > panel.width + 20;
        const side = rightFits || leftFits;
        const panelLeft = side
          ? rightFits
            ? rect.right + 16
            : rect.left - panel.width - 16
          : Math.max(12, Math.min(rect.left, width - panel.width - 12));
        const availableBottom = Math.min(
          offset + height,
          navigationRef.current?.getBoundingClientRect().top ?? offset + height,
        );
        const bottom = Math.max(offset + 12, availableBottom - panel.height - 16);
        const panelTop = side
          ? Math.max(offset + 12, Math.min(rect.top, bottom))
          : rect.bottom + 16 < bottom
            ? rect.bottom + 16
            : rect.top - panel.height - 16 >= offset + 12
              ? rect.top - panel.height - 16
              : bottom;
        const value = {
          left: rect.left - 4,
          top: rect.top - 4,
          width: rect.width + 8,
          height: rect.height + 8,
          panelLeft,
          panelTop,
        };
        setPosition((old) =>
          Object.keys(value).every(
            (key) => old[key as keyof typeof old] === value[key as keyof typeof value],
          )
            ? old
            : value,
        );
      }
      frame = requestAnimationFrame(update);
    }
    frame = requestAnimationFrame(update);
    nextRef.current?.focus({ preventScroll: true });
    return () => cancelAnimationFrame(frame);
  }, [open, step, host]);
  const current = step === null ? null : steps[step];
  return (
    <>
      <button
        data-tour-trigger
        ref={triggerRef}
        type="button"
        className="portfolio-guide-trigger"
        disabled={!ready}
        onClick={() => start('title')}
      >
        <span aria-hidden="true">ⓘ</span> 사용 가이드
      </button>
      {open &&
        current &&
        createPortal(
          <div ref={layerRef} popover="manual" className="portfolio-tour">
            <div
              className="portfolio-tour-highlight"
              aria-hidden="true"
              style={{
                left: position.left,
                top: position.top,
                width: position.width,
                height: position.height,
              }}
            />
            <section
              ref={panelRef}
              className="portfolio-tour-panel"
              aria-label="포트폴리오 사용 안내"
              style={{ left: position.panelLeft, top: position.panelTop }}
            >
              <span className="portfolio-tour-demo-label">체험용 예시 · 저장되지 않아요</span>
              <div className="portfolio-tour-copy" aria-live="polite" aria-atomic="true">
                <h2>{current.title}</h2>
                <p>{current.description}</p>
              </div>
            </section>
            <nav
              ref={navigationRef}
              className="portfolio-tour-bottom-bar"
              aria-label="가이드 단계 이동"
            >
              <div className="portfolio-tour-bottom-inner">
                <div className="portfolio-tour-journey">
                  <ol className="portfolio-tour-flow" aria-label="서비스 이용 순서">
                    {groups.map((label, index) => (
                      <li key={label} aria-current={index === current.group ? 'step' : undefined}>
                        {index + 1} {label}
                      </li>
                    ))}
                  </ol>
                </div>
                <div className="portfolio-tour-navigation">
                  <span className="portfolio-tour-progress">
                    {step! + 1} / {steps.length}
                  </span>
                  <button type="button" className="portfolio-tour-skip" onClick={() => finish()}>
                    종료
                  </button>
                  <button
                    type="button"
                    className="portfolio-secondary-button"
                    disabled={step === 0}
                    onClick={() => move('previous')}
                  >
                    이전
                  </button>
                  <button
                    ref={nextRef}
                    type="button"
                    className="portfolio-primary-button"
                    onClick={() => {
                      if (step === steps.length - 1) {
                        finish('complete');
                        onRegister?.();
                      } else move('next');
                    }}
                  >
                    {step === steps.length - 1
                      ? onRegister
                        ? '내 자산 등록하기'
                        : '완료'
                      : '다음'}
                  </button>
                </div>
              </div>
            </nav>
          </div>,
          host,
        )}
    </>
  );
}
