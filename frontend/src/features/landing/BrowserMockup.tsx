import { useEffect, useRef } from 'react';
import { LandingIcon } from './LandingIcon';
import overview from '../../assets/landing/overview.png';
import news from '../../assets/landing/news.png';
import sources from '../../assets/landing/sources.png';
import league from '../../assets/landing/league.png';

const screens = {
  overview: {
    image: overview,
    title: '내 포트폴리오',
    description: '자산 평가금액과 손익, AI 분석, 보유 종목 표',
  },
  news: { image: news, title: '보유 자산 소식', description: '보유 종목과 연결된 뉴스와 영상' },
  analysis: {
    image: sources,
    title: 'AI 분석 · 근거',
    description: '종목별 영향 차트, 전체 포트폴리오 분석과 참고한 출처 자료',
  },
  league: {
    image: league,
    title: '주간 투자 리그',
    description: '참여자 순위와 수익률, 투자 기록 바로가기',
  },
};

export function BrowserMockup({
  screen,
  animate = false,
}: {
  screen: keyof typeof screens;
  animate?: boolean;
}) {
  const viewportRef = useRef<HTMLDivElement>(null);
  const imageRef = useRef<HTMLImageElement>(null);
  useEffect(() => {
    const viewport = viewportRef.current;
    const image = imageRef.current;
    if (!viewport || !image || !animate) return;
    let animation: Animation | undefined;
    const start = () => {
      animation?.cancel();
      const style = getComputedStyle(viewport);
      const padding = (parseFloat(style.paddingTop) || 0) + (parseFloat(style.paddingBottom) || 0);
      const overflow = Math.max(0, image.clientHeight - viewport.clientHeight + padding);
      if (!overflow || !image.complete) return;
      animation = image.animate(
        [
          { transform: 'translateY(0)', offset: 0 },
          { transform: `translateY(-${overflow}px)`, offset: 1 },
        ],
        { duration: 12000, easing: 'linear', fill: 'forwards' },
      );
    };
    const observer = new ResizeObserver(start);
    observer.observe(viewport);
    image.addEventListener('load', start);
    start();
    return () => {
      animation?.cancel();
      observer.disconnect();
      image.removeEventListener('load', start);
    };
  }, [screen, animate]);
  const current = screens[screen];
  return (
    <figure className="film-browser" aria-label={`${current.title} 웹 소개용 목업`}>
      <div className="film-browser-chrome" aria-hidden="true">
        <span>● ● ●</span>
        <span>daynomy.com</span>
        <LandingIcon name="ArrowUpRight" size={12} />
      </div>
      <div className={`film-browser-screen film-capture-${screen}`} key={screen} ref={viewportRef}>
        <img
          ref={imageRef}
          src={current.image}
          alt={`${current.title} 실제 서비스 화면 · ${current.description}. 예시 데이터.`}
        />
      </div>
    </figure>
  );
}
