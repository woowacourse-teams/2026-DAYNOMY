import { useMemo, useState } from 'react';
import { Link, Navigate, useParams } from 'react-router-dom';
import type { FinancialGuide } from './guideData';
import { findFinancialGuide } from './guideData';
import { completeGuide, loadGuideProgress, saveGuideSteps } from './guideProgress';
import { IndexChoiceGuide } from './IndexChoiceGuide';
import './guides.css';

function getStepId(index: number) {
  return `step-${index + 1}`;
}

const IRREGULAR_EXPENSE_CATEGORIES = ['여행', '병원', '경조사', '세금', '보험', '기타'] as const;

function IrregularExpenseCalculator() {
  const [amounts, setAmounts] = useState<Record<string, number>>({});
  const annualTotal = IRREGULAR_EXPENSE_CATEGORIES.reduce(
    (total, category) => total + (amounts[category] ?? 0),
    0,
  );
  const monthlyReserve = Math.ceil(annualTotal / 12);

  return (
    <section className="irregular-expense-tool" aria-labelledby="irregular-expense-title">
      <div className="irregular-expense-intro">
        <span>직접 해보기</span>
        <h3 id="irregular-expense-title">지난 1년 내역, 이렇게 찾고 계산해요</h3>
        <ol>
          <li>
            <b>01</b>
            <p>
              카드·은행 앱에서 조회 기간을 <strong>오늘 기준 지난 1년</strong>으로 바꿔요.
            </p>
          </li>
          <li>
            <b>02</b>
            <p>
              검색창에 <strong>항공·숙박, 병원·약국, 보험</strong>을 차례로 검색해 합계를 적어요.
            </p>
          </li>
          <li>
            <b>03</b>
            <p>현금으로 낸 경조사비와 자동차세처럼 검색되지 않는 돈은 기억나는 만큼 따로 더해요.</p>
          </li>
        </ol>
      </div>

      <div className="irregular-expense-calculator">
        <div className="irregular-expense-fields">
          {IRREGULAR_EXPENSE_CATEGORIES.map((category) => (
            <label key={category}>
              <span>{category}</span>
              <span>
                <input
                  type="number"
                  min="0"
                  step="1"
                  inputMode="numeric"
                  aria-label={`${category} 연간 지출`}
                  value={amounts[category] || ''}
                  placeholder="0"
                  onChange={(event) =>
                    setAmounts((current) => ({
                      ...current,
                      [category]: Math.max(0, Number(event.target.value) || 0),
                    }))
                  }
                />
                만원
              </span>
            </label>
          ))}
        </div>

        <div className="irregular-expense-result" aria-live="polite">
          <p>
            연간 합계 <strong>{annualTotal.toLocaleString()}만원</strong> ÷ 12개월
          </p>
          <div>
            <span>매달 미리 빼둘 돈</span>
            <strong>{monthlyReserve.toLocaleString()}만원</strong>
          </div>
          <small>
            계산한 금액은 생활비 계좌가 아닌 별도 통장으로 월급날 자동이체해 두면 편해요.
          </small>
        </div>
      </div>
    </section>
  );
}

function StandardGuideDetail({ guide }: { guide: FinancialGuide }) {
  const stepIds = useMemo(() => guide.steps.map((_, index) => getStepId(index)), [guide.steps]);
  const [completedSteps, setCompletedSteps] = useState(() => {
    const savedSteps = loadGuideProgress()[guide.id] ?? [];
    return savedSteps.filter((stepId) => stepIds.includes(stepId));
  });
  const [activeStepId, setActiveStepId] = useState(
    () => stepIds.find((stepId) => !completedSteps.includes(stepId)) ?? stepIds.at(-1) ?? '',
  );

  const completedCount = completedSteps.length;
  const isFinished = completedCount === guide.steps.length;
  const activeIndex = Math.max(stepIds.indexOf(activeStepId), 0);
  const progressPercent =
    guide.steps.length === 0 ? 0 : (completedCount / guide.steps.length) * 100;

  function completeStep(stepId: string, stepIndex: number) {
    const nextCompleted = [...new Set([...completedSteps, stepId])];
    const hasFinished = nextCompleted.length === guide.steps.length;

    setCompletedSteps(nextCompleted);
    saveGuideSteps(guide.id, hasFinished ? [...nextCompleted, 'completed'] : nextCompleted);

    if (!hasFinished) {
      const nextStepId = stepIds.slice(stepIndex + 1).find((id) => !nextCompleted.includes(id));
      setActiveStepId(nextStepId ?? stepIds.find((id) => !nextCompleted.includes(id)) ?? stepId);
    }
  }

  function resetGuide() {
    setCompletedSteps([]);
    setActiveStepId(stepIds[0] ?? '');
    saveGuideSteps(guide.id, []);
  }

  return (
    <main className="guide-page guide-detail-page">
      <nav className="guide-breadcrumb" aria-label="현재 위치">
        <Link to="/guides">가이드</Link>
        <span aria-hidden="true">/</span>
        <strong>{guide.title}</strong>
      </nav>

      <header className="guide-detail-hero">
        <div>
          <span>
            {guide.category} · {guide.duration}
          </span>
          <h1>{guide.title}</h1>
        </div>
        <p>{guide.summary}</p>
      </header>

      <section className="guide-detail-content" aria-labelledby="guide-steps-title">
        <div className="guide-section-heading guide-step-heading">
          <div>
            <span>지금 볼 내용</span>
            <h2 id="guide-steps-title">{guide.steps[activeIndex]?.title}</h2>
          </div>
          <strong>
            {completedCount} / {guide.steps.length}
          </strong>
        </div>

        <div
          className="guide-step-progress"
          role="progressbar"
          aria-label="가이드 진행률"
          aria-valuemin={0}
          aria-valuemax={guide.steps.length}
          aria-valuenow={completedCount}
        >
          <i style={{ width: `${progressPercent}%` }} />
        </div>

        <ol className="guide-step-accordion">
          {guide.steps.map((step, index) => {
            const stepId = stepIds[index];
            const isActive = activeStepId === stepId;
            const isComplete = completedSteps.includes(stepId);
            const panelId = `${guide.id}-${stepId}-panel`;

            return (
              <li
                key={stepId}
                className={`${isActive ? 'is-active' : ''} ${isComplete ? 'is-complete' : ''}`}
              >
                <button
                  type="button"
                  className="guide-step-trigger"
                  aria-expanded={isActive}
                  aria-controls={panelId}
                  onClick={() => setActiveStepId(stepId)}
                >
                  <span className="guide-step-index" aria-hidden="true">
                    {isComplete ? '✓' : String(index + 1).padStart(2, '0')}
                  </span>
                  <span className="guide-step-trigger-copy">
                    <small>{step.label}</small>
                    <strong>{step.title}</strong>
                  </span>
                  <span className="guide-step-state">
                    {isComplete ? '완료' : isActive ? '진행 중' : '보기'}
                  </span>
                  <i className="guide-step-chevron" aria-hidden="true" />
                </button>

                {isActive ? (
                  <div id={panelId} className="guide-step-panel">
                    <p className="guide-step-description">{step.description}</p>
                    <ul className="guide-step-points">
                      {step.points.map((point) => (
                        <li key={point}>{point}</li>
                      ))}
                    </ul>
                    {guide.id === 'safety-money' && index === 1 ? (
                      <IrregularExpenseCalculator />
                    ) : null}
                    {step.example ? (
                      <aside className="guide-step-example">
                        <strong>예시</strong>
                        <p>{step.example}</p>
                      </aside>
                    ) : null}
                    {step.caution ? (
                      <aside className="guide-step-caution">
                        <strong>주의</strong>
                        <p>{step.caution}</p>
                      </aside>
                    ) : null}
                    <div className="guide-step-action">
                      {isComplete ? (
                        <span>이 단계는 확인했어요.</span>
                      ) : (
                        <button type="button" onClick={() => completeStep(stepId, index)}>
                          이 단계 완료
                        </button>
                      )}
                    </div>
                  </div>
                ) : null}
              </li>
            );
          })}
        </ol>
      </section>

      {isFinished ? (
        <section className="guide-check-panel" aria-labelledby="guide-check-title">
          <div>
            <span>가이드 완료</span>
            <h2 id="guide-check-title">이제 직접 적용해 볼 차례예요.</h2>
          </div>
          <ul>
            {guide.checks.map((check) => (
              <li key={check}>✓ {check}</li>
            ))}
          </ul>
          <button type="button" className="is-complete" onClick={resetGuide}>
            처음부터 다시 보기
          </button>
        </section>
      ) : null}

      <footer className="guide-detail-footer">
        <Link to="/guides">← 다른 가이드 보기</Link>
      </footer>
    </main>
  );
}

export function GuideDetailPage() {
  const { guideId = '' } = useParams();
  const guide = findFinancialGuide(guideId);

  if (!guide || guide.id === 'account-opening') {
    return <Navigate to="/guides" replace />;
  }

  if (guide.id === 'index-choice') {
    return <IndexChoiceGuide onComplete={() => completeGuide(guideId)} />;
  }

  return <StandardGuideDetail key={guide.id} guide={guide} />;
}
