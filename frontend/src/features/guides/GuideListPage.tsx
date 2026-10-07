import { Link } from 'react-router-dom';
import { FINANCIAL_GUIDES } from './guideData';
import { loadGuideProgress } from './guideProgress';
import { GuideHeroCarousel } from './GuideHeroCarousel';
import './guides.css';

const ACCOUNT_GUIDE_STEP_COUNT = 8;

export function GuideListPage() {
  const progress = loadGuideProgress();
  const isComplete = (guideId: string) => {
    const completedSteps = progress[guideId] ?? [];
    return guideId === 'account-opening'
      ? completedSteps.length >= ACCOUNT_GUIDE_STEP_COUNT
      : completedSteps.includes('completed');
  };
  return (
    <main className="guide-page">
      <GuideHeroCarousel />

      <section className="guide-library" aria-labelledby="guide-library-title">
        <div className="guide-section-heading">
          <h2 id="guide-library-title">전체 가이드</h2>
          <small>{FINANCIAL_GUIDES.length}개의 가이드</small>
        </div>

        <div className="guide-list">
          {FINANCIAL_GUIDES.map((guide, index) => {
            const completed = isComplete(guide.id);
            const completedSteps = progress[guide.id]?.length ?? 0;

            return (
              <Link className="guide-list-item" to={`/guides/${guide.id}`} key={guide.id}>
                <span className="guide-list-index">{String(index + 1).padStart(2, '0')}</span>
                <div className="guide-list-copy">
                  <div className="guide-list-meta">
                    <span>{guide.category}</span>
                    <span>{guide.duration}</span>
                  </div>
                  <h3>{guide.title}</h3>
                  <p>{guide.description}</p>
                </div>
                <footer>
                  <span className={completed ? 'is-complete' : ''}>
                    {completed
                      ? '완료'
                      : completedSteps > 0
                        ? `${completedSteps}단계 진행 중`
                        : '시작 전'}
                  </span>
                  <b aria-hidden="true">↗</b>
                </footer>
              </Link>
            );
          })}
        </div>
      </section>

      <aside className="guide-disclaimer">
        <strong>안내</strong>
        <p>
          이 가이드는 금융상품 가입이나 수익을 보장하지 않습니다. 실제 가입 전에는 금융회사가
          제공하는 최신 약관과 비용, 위험 정보를 확인하세요.
        </p>
      </aside>
    </main>
  );
}
