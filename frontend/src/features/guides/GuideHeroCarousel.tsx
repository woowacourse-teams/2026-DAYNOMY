import { useEffect, useState, type FocusEvent } from 'react';
import { Link } from 'react-router-dom';
import accountOpeningImage from '../../assets/guides/account-opening.webp';
import etfComparisonImage from '../../assets/guides/etf-comparison.webp';
import firstOrderImage from '../../assets/guides/first-order.webp';

const AUTO_PLAY_INTERVAL = 5000;

const GUIDE_BANNERS = [
  {
    guideId: 'account-opening',
    label: '계좌 개설 · 10분',
    title: '계좌 개설, 어디서부터 눌러야 할까?',
    description: '계좌 확인부터 첫 입금과 자동이체까지 실제 순서대로 따라가세요.',
    image: accountOpeningImage,
    imageAlt: '스마트폰과 계좌 준비 단계를 표현한 금융 일러스트',
    tone: 'lavender',
  },
  {
    guideId: 'index-choice',
    label: '월급 투자 · 5분',
    title: '월 30만 원, 나스닥 vs S&P500 어디에 넣을까?',
    description: '구성과 변동성을 비교하고 매달 나누어 담는 세 가지 예시를 확인하세요.',
    image: etfComparisonImage,
    imageAlt: '서로 다른 자산 바구니를 저울로 비교하는 금융 일러스트',
    tone: 'dark',
  },
  {
    guideId: 'first-order',
    label: '첫 매수 · 5분',
    title: '첫 주문 버튼을 누르기 전에 볼 숫자들',
    description: '상품명, 수량, 가격과 예상 주문금액을 작은 금액으로 먼저 확인하세요.',
    image: firstOrderImage,
    imageAlt: '스마트폰 주문 화면과 확인 표시를 표현한 금융 일러스트',
    tone: 'warm',
  },
] as const;

export function GuideHeroCarousel() {
  const [activeSlide, setActiveSlide] = useState(0);
  const [isPaused, setIsPaused] = useState(false);

  useEffect(() => {
    const prefersReducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    if (isPaused || prefersReducedMotion) return;

    const intervalId = window.setInterval(() => {
      setActiveSlide((current) => (current + 1) % GUIDE_BANNERS.length);
    }, AUTO_PLAY_INTERVAL);

    return () => window.clearInterval(intervalId);
  }, [activeSlide, isPaused]);

  function moveSlide(direction: number) {
    setActiveSlide(
      (current) => (current + direction + GUIDE_BANNERS.length) % GUIDE_BANNERS.length,
    );
  }

  function resumeAfterFocus(event: FocusEvent<HTMLElement>) {
    if (event.relatedTarget instanceof Node && event.currentTarget.contains(event.relatedTarget)) {
      return;
    }
    setIsPaused(false);
  }

  return (
    <header
      className="guide-carousel"
      aria-label="추천 금융 가이드"
      aria-roledescription="carousel"
      onMouseEnter={() => setIsPaused(true)}
      onMouseLeave={() => setIsPaused(false)}
      onFocusCapture={() => setIsPaused(true)}
      onBlurCapture={resumeAfterFocus}
    >
      <div className="guide-carousel-slides" aria-live="polite">
        {GUIDE_BANNERS.map((banner, index) => {
          const isActive = activeSlide === index;

          return (
            <article
              className={`guide-carousel-slide is-${banner.tone}${isActive ? ' is-active' : ''}`}
              aria-hidden={!isActive}
              key={banner.guideId}
            >
              <img
                src={banner.image}
                alt={banner.imageAlt}
                loading={index === 0 ? 'eager' : 'lazy'}
              />
              <Link to={`/guides/${banner.guideId}`} tabIndex={isActive ? 0 : -1}>
                <span>{banner.label}</span>
                <h1>{banner.title}</h1>
                <p>{banner.description}</p>
                <strong>
                  가이드 보기 <i aria-hidden="true">↗</i>
                </strong>
              </Link>
            </article>
          );
        })}
      </div>

      <div className="guide-carousel-controls">
        <div role="group" aria-label="배너 선택">
          {GUIDE_BANNERS.map((banner, index) => (
            <button
              type="button"
              className={activeSlide === index ? 'is-active' : ''}
              aria-label={`${index + 1}번째 배너: ${banner.title}`}
              aria-current={activeSlide === index ? 'true' : undefined}
              onClick={() => setActiveSlide(index)}
              key={banner.guideId}
            />
          ))}
        </div>
        <div role="group" aria-label="배너 이동">
          <button type="button" aria-label="이전 배너" onClick={() => moveSlide(-1)}>
            ←
          </button>
          <span aria-hidden="true">
            {String(activeSlide + 1).padStart(2, '0')} /{' '}
            {String(GUIDE_BANNERS.length).padStart(2, '0')}
          </span>
          <button type="button" aria-label="다음 배너" onClick={() => moveSlide(1)}>
            →
          </button>
        </div>
      </div>
    </header>
  );
}
