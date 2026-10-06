import { useContext, useEffect, useRef, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { AuthContext } from '../../auth/AuthContext';
import { saveFinancialPlan } from './financialPlanApi';
import {
  calculateDepositComparison,
  calculateInvestmentProjection,
  calculateYouthSavings,
  createSalaryBudget,
  type Consultation,
  type FinancialPlanInput,
  type SalaryBudgetInput,
} from './financialPlanning';
import './financialPlanning.css';

const won = new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 0 });

type PlannerInput = SalaryBudgetInput & {
  age: number;
  eligible: 'unknown' | 'yes' | 'no';
  matchRate: number;
  depositRate: number;
  savingsRate: number;
  youthRate: number;
  investmentRate: number;
};

function money(value: number) {
  return `${won.format(Math.round(value))}원`;
}

function calculatePlannerResult(input: PlannerInput) {
  const allocation = createSalaryBudget(input);
  const months = input.goalMonths;
  const assetContribution = allocation.savings + allocation.investment;
  const policyDeposit = Math.min(500000, allocation.savings);
  // 목표 자금만 예금 비교에 사용하고, 별도로 입력한 비상금은 묶지 않는다.
  const depositableLump = input.goalSaved;
  const deposit = calculateDepositComparison({
    principal: depositableLump,
    monthlyDeposit: 0,
    months: 12,
    depositRate: input.depositRate,
    savingsRate: 0,
  });
  const regularSavings = calculateDepositComparison({
    principal: 0,
    monthlyDeposit: policyDeposit,
    months: 36,
    depositRate: 0,
    savingsRate: input.savingsRate,
  });
  const youthSavings = calculateYouthSavings({
    monthlyDeposit: policyDeposit,
    annualRate: input.youthRate,
    matchRate: input.matchRate,
  });
  const investment = calculateInvestmentProjection(
    allocation.investment,
    input.goalMonths / 12,
    input.investmentRate,
  );
  const safePlan = calculateDepositComparison({
    principal: 0,
    monthlyDeposit: allocation.savings,
    months,
    depositRate: 0,
    savingsRate: input.savingsRate,
  });
  const saveOnly = calculateDepositComparison({
    principal: 0,
    monthlyDeposit: assetContribution,
    months,
    depositRate: 0,
    savingsRate: input.savingsRate,
  });

  return {
    input: { ...input },
    allocation,
    policyDeposit,
    depositableLump,
    deposit,
    regularSavings,
    youthSavings,
    investment,
    saveOnly: saveOnly.savingsMaturity,
    mixedPlan: safePlan.savingsMaturity + investment.estimatedValue,
  };
}

function makeConsultation(
  title: string,
  summary: string,
  details: string[],
  actions: NonNullable<Consultation['actions']>,
): Consultation {
  return {
    id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
    topic: '저축·투자 계획',
    title,
    summary,
    details,
    actions,
    createdAt: new Date().toISOString(),
  };
}

export function FinancialPlanPage() {
  const auth = useContext(AuthContext);
  const isLoggedIn = auth?.isLoggedIn ?? false;
  const [saving, setSaving] = useState(false);
  const [input, setInput] = useState<PlannerInput>({
    monthlyIncome: 3000000,
    rent: 600000,
    livingExpenses: 900000,
    fixedExpenses: 300000,
    emergencySavings: 3000000,
    goalAmount: 10000000,
    goalSaved: 1000000,
    goalMonths: 24,
    risk: 'MEDIUM',
    hasHighInterestDebt: false,
    age: 25,
    eligible: 'unknown',
    matchRate: 0.06,
    depositRate: 3,
    savingsRate: 4,
    youthRate: 7,
    investmentRate: 6,
  });
  const [result, setResult] = useState<ReturnType<typeof calculatePlannerResult> | null>(null);
  const [savedMessage, setSavedMessage] = useState('');
  const [error, setError] = useState('');
  const resultHeading = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    if (!result) return;
    resultHeading.current?.focus({ preventScroll: true });
    resultHeading.current?.scrollIntoView?.({ block: 'start' });
  }, [result]);

  function simulate(event: FormEvent) {
    event.preventDefault();
    setSavedMessage('');
    setError('');
    try {
      setResult(calculatePlannerResult(input));
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : '입력값을 확인해 주세요.');
    }
  }

  async function savePlan() {
    if (!result || saving || auth?.loading) return;
    const actions = [
      result.allocation.monthlyAvailable === 0
        ? { id: 'budget', label: '지출을 조정한 뒤 월급 계획 다시 계산하기', completed: false }
        : null,
      result.allocation.debt > 0
        ? { id: 'debt', label: '고금리 부채 상환 계획 세우기', completed: false }
        : null,
      result.allocation.emergency > 0
        ? {
            id: 'emergency',
            label: `비상금 ${money(result.allocation.emergencyTarget)} 만들기`,
            completed: false,
          }
        : null,
      result.allocation.savings > 0
        ? { id: 'saving', label: '적금 조건과 중도해지 조건 확인하기', completed: false }
        : null,
      result.allocation.investment > 0
        ? { id: 'invest', label: '소액으로 분산투자 시작하기', completed: false }
        : null,
      { id: 'portfolio', label: 'DAYNOMY 포트폴리오에 자산 기록하기', completed: false },
    ].filter((action): action is NonNullable<typeof action> => action !== null);
    const consultation = makeConsultation(
      `${result.input.goalMonths}개월 저축·투자 계획`,
      `월 ${money(result.allocation.monthlyAvailable)} 배분`,
      [
        `예·적금 ${money(result.allocation.savings)} · 투자 ${money(result.allocation.investment)}`,
        `비상금 월 ${money(result.allocation.emergency)} · 부채 상환 월 ${money(result.allocation.debt)}`,
        `목표 ${money(result.input.goalAmount)} · 목표 저축 월 ${money(result.allocation.requiredSavings)} 필요`,
      ],
      actions,
    );
    try {
      setSaving(true);
      await saveFinancialPlan(isLoggedIn, {
        ...consultation,
        monthlyPlan: result.allocation.monthlyPlan,
      });
      setSavedMessage(
        `저축·투자 계획을 ${isLoggedIn ? '계정과' : '이 브라우저의'} 주간 기록에 저장했어요.`,
      );
      setError('');
    } catch {
      setError('계획을 저장하지 못했어요. 연결 상태와 저장 공간을 확인한 뒤 다시 시도해 주세요.');
    } finally {
      setSaving(false);
    }
  }

  const allocations = result
    ? [
        { key: 'debt', label: '고금리 부채 상환', amount: result.allocation.debt },
        { key: 'emergency', label: '비상금', amount: result.allocation.emergency },
        { key: 'savings', label: '예·적금', amount: result.allocation.savings },
        { key: 'investment', label: '장기 투자', amount: result.allocation.investment },
      ].filter((item) => item.amount > 0)
    : [];

  return (
    <main className="finance-page">
      <section className="finance-hero">
        <h1>내 저축·투자 계획 만들기</h1>
        <p>월급과 지출을 입력하면, 목표까지 매달 얼마를 모을지 알려드려요.</p>
        <a className="finance-start-link" href="#finance-plan-input">
          내 상황 입력하기 ↓
        </a>
      </section>

      <section className="finance-section" id="finance-plan-input">
        <div className="finance-section-heading">
          <h2>월급에서 얼마나 남나요?</h2>
          <p>모든 금액은 원 단위예요. 예시 값을 내 상황에 맞게 바꿔 주세요.</p>
        </div>
        <form className="finance-form finance-planner-form" onSubmit={simulate}>
          <fieldset>
            <legend>월급과 지출</legend>
            <label>
              세후 월급
              <input
                aria-label="세후 월급"
                type="number"
                min="0"
                max="1000000000000"
                step="1"
                required
                value={input.monthlyIncome}
                onChange={(event) =>
                  setInput({ ...input, monthlyIncome: Number(event.target.value) })
                }
              />
              <small>{money(input.monthlyIncome)} · 통장에 실제로 들어오는 월급</small>
            </label>
            <label>
              월세·주거비
              <input
                aria-label="월세·주거비"
                type="number"
                min="0"
                max="1000000000000"
                step="1"
                required
                value={input.rent}
                onChange={(event) => setInput({ ...input, rent: Number(event.target.value) })}
              />
              <small>{money(input.rent)} · 월세와 관리비 등</small>
            </label>
            <label>
              한 달 생활비
              <input
                aria-label="한 달 생활비"
                type="number"
                min="0"
                max="1000000000000"
                step="1"
                required
                value={input.livingExpenses}
                onChange={(event) =>
                  setInput({ ...input, livingExpenses: Number(event.target.value) })
                }
              />
              <small>{money(input.livingExpenses)} · 식비·교통·쇼핑 등. 주거비는 제외</small>
            </label>
            <label>
              기타 고정 지출
              <input
                aria-label="기타 고정 지출"
                type="number"
                min="0"
                max="1000000000000"
                step="1"
                required
                value={input.fixedExpenses}
                onChange={(event) =>
                  setInput({ ...input, fixedExpenses: Number(event.target.value) })
                }
              />
              <small>
                {money(input.fixedExpenses)} · 보험·통신·대출 최소 상환액 등. 위 항목과 중복 없이
                입력
              </small>
            </label>
          </fieldset>
          <fieldset>
            <legend>모으고 싶은 돈</legend>
            <label>
              목표 금액
              <input
                aria-label="목표 금액"
                type="number"
                min="0"
                max="1000000000000"
                step="1"
                required
                value={input.goalAmount}
                onChange={(event) => setInput({ ...input, goalAmount: Number(event.target.value) })}
              />
              <small>{money(input.goalAmount)}</small>
            </label>
            <label>
              목표를 위해 이미 모은 돈
              <input
                aria-label="목표를 위해 이미 모은 돈"
                type="number"
                min="0"
                max="1000000000000"
                step="1"
                required
                value={input.goalSaved}
                onChange={(event) => setInput({ ...input, goalSaved: Number(event.target.value) })}
              />
              <small>{money(input.goalSaved)} · 비상금·투자자산은 빼고 입력</small>
            </label>
            <label>
              목표 기간 (개월)
              <input
                aria-label="목표 기간 (개월)"
                type="number"
                min="1"
                max="120"
                step="1"
                required
                value={input.goalMonths}
                onChange={(event) => setInput({ ...input, goalMonths: Number(event.target.value) })}
              />
            </label>
          </fieldset>
          <fieldset>
            <legend>비상금과 투자 성향</legend>
            <label>
              현재 마련한 비상금
              <input
                aria-label="현재 마련한 비상금"
                type="number"
                min="0"
                max="1000000000000"
                step="1"
                required
                value={input.emergencySavings}
                onChange={(event) =>
                  setInput({ ...input, emergencySavings: Number(event.target.value) })
                }
              />
              <small>{money(input.emergencySavings)}</small>
            </label>
            <label>
              투자금이 내려갔을 때
              <select
                aria-label="투자금이 내려갔을 때"
                value={input.risk}
                onChange={(event) =>
                  setInput({ ...input, risk: event.target.value as FinancialPlanInput['risk'] })
                }
              >
                <option value="LOW">조금만 내려가도 불안해요</option>
                <option value="MEDIUM">장기 목표라면 기다릴 수 있어요</option>
                <option value="HIGH">큰 변동도 감수할 수 있어요</option>
              </select>
            </label>
            <label>
              고금리 부채
              <select
                aria-label="고금리 부채"
                value={input.hasHighInterestDebt ? 'yes' : 'no'}
                onChange={(event) =>
                  setInput({ ...input, hasHighInterestDebt: event.target.value === 'yes' })
                }
              >
                <option value="no">없어요</option>
                <option value="yes">카드론·리볼빙 등이 있어요</option>
              </select>
            </label>
          </fieldset>
          <details className="finance-rate-settings">
            <summary>상품 조건과 예상 수익률 설정</summary>
            <div>
              <label>
                정기예금 연이율
                <input
                  aria-label="정기예금 연이율"
                  type="number"
                  min="0"
                  max="30"
                  step="0.1"
                  value={input.depositRate}
                  onChange={(event) =>
                    setInput({ ...input, depositRate: Number(event.target.value) })
                  }
                />
              </label>
              <label>
                일반 적금 연이율
                <input
                  aria-label="일반 적금 연이율"
                  type="number"
                  min="0"
                  max="30"
                  step="0.1"
                  value={input.savingsRate}
                  onChange={(event) =>
                    setInput({ ...input, savingsRate: Number(event.target.value) })
                  }
                />
              </label>
              <label>
                청년미래적금 연이율
                <input
                  aria-label="청년미래적금 연이율"
                  type="number"
                  min="0"
                  max="30"
                  step="0.1"
                  value={input.youthRate}
                  onChange={(event) =>
                    setInput({ ...input, youthRate: Number(event.target.value) })
                  }
                />
              </label>
              <label>
                장기투자 예상 연수익률
                <input
                  aria-label="장기투자 예상 연수익률"
                  type="number"
                  min="0"
                  max="15"
                  step="0.5"
                  value={input.investmentRate}
                  onChange={(event) =>
                    setInput({ ...input, investmentRate: Number(event.target.value) })
                  }
                />
              </label>
              <label>
                나이
                <input
                  aria-label="나이"
                  type="number"
                  min="1"
                  max="100"
                  value={input.age}
                  onChange={(event) => setInput({ ...input, age: Number(event.target.value) })}
                />
              </label>
              <label>
                청년미래적금 유형
                <select
                  aria-label="청년미래적금 유형"
                  value={input.matchRate}
                  onChange={(event) =>
                    setInput({ ...input, matchRate: Number(event.target.value) })
                  }
                >
                  <option value={0.06}>일반형 6%</option>
                  <option value={0.12}>우대형 12%</option>
                </select>
              </label>
              <label>
                소득 요건 확인
                <select
                  aria-label="소득 요건 확인"
                  value={input.eligible}
                  onChange={(event) =>
                    setInput({ ...input, eligible: event.target.value as PlannerInput['eligible'] })
                  }
                >
                  <option value="unknown">아직 모르겠어요</option>
                  <option value="yes">충족하는 것으로 확인했어요</option>
                  <option value="no">충족하지 않아요</option>
                </select>
              </label>
            </div>
          </details>
          <button type="submit" disabled={saving}>
            내 저축·투자 계획 만들기
          </button>
          <p className="finance-source-note">
            계산은 브라우저에서 합니다. 저장을 누르면{' '}
            {isLoggedIn
              ? '월 배분 계획과 할 일을 내 계정에 저장합니다. 원본 포트폴리오와는 별개예요.'
              : '현재 브라우저에 보관됩니다. 로그인 후 저장한 계획은 계정에 보관됩니다.'}
          </p>
        </form>
        {error && (
          <p className="finance-budget-warning" role="alert">
            {error}
          </p>
        )}
      </section>

      {result && (
        <>
          <section className="finance-section">
            <div className="finance-section-heading">
              <h2 ref={resultHeading} tabIndex={-1}>
                매달 {money(result.allocation.monthlyAvailable)}을 나눌 수 있어요
              </h2>
              <p>
                세후 월급 {money(result.input.monthlyIncome)} − 지출{' '}
                {money(result.allocation.monthlyExpenses)}
              </p>
            </div>
            <dl className="finance-budget-summary">
              <div>
                <dt>모으고 싶은 금액</dt>
                <dd>{money(result.input.goalAmount)}</dd>
              </div>
              <div>
                <dt>이미 모은 목표 자금</dt>
                <dd>{money(result.input.goalSaved)}</dd>
              </div>
              <div>
                <dt>{result.input.goalMonths}개월 동안 매달 필요한 저축</dt>
                <dd>{money(result.allocation.requiredSavings)}</dd>
              </div>
              <div>
                <dt>이 계획의 월 목표 저축</dt>
                <dd>{money(result.allocation.savings)}</dd>
              </div>
            </dl>
            {result.allocation.expenseDeficit > 0 ? (
              <p className="finance-budget-warning" role="alert">
                지출이 월급보다 {money(result.allocation.expenseDeficit)} 많아요. 저축·투자보다 지출
                조정이 먼저예요.
              </p>
            ) : result.allocation.monthlyAvailable === 0 ? (
              <p className="finance-budget-warning" role="alert">
                현재 남는 돈이 없어요. 지출이나 목표 기간을 조정한 뒤 다시 계산해 주세요.
              </p>
            ) : null}
            {result.allocation.goalShortfall > 0 ? (
              <p className="finance-budget-warning" role="alert">
                목표 저축이 월 {money(result.allocation.goalShortfall)} 부족해요. 비상금·부채 상환
                배분을 유지하면{' '}
                {result.allocation.monthsToGoal === null
                  ? '현재 저축액으로는 목표에 도달할 수 없어요.'
                  : `약 ${result.allocation.monthsToGoal}개월이 필요해요.`}{' '}
                지출을 줄이거나 목표 금액·기간을 바꿔 보세요.
              </p>
            ) : (
              <p className="finance-budget-explanation">
                {result.input.goalMonths}개월 동안 이 계획을 유지하면 목표 자금은{' '}
                {money(result.allocation.goalBalance)}이에요. 이자·투자 수익은 포함하지 않았어요.
              </p>
            )}
            <p className="finance-budget-explanation">
              {result.input.hasHighInterestDebt
                ? '고금리 부채가 있어 투자 대신 추가 상환에 배분했어요.'
                : '목표 저축은 투자와 따로 모으고, 남은 돈만 투자에 배분했어요.'}{' '}
              비상금은 월 지출 3개월분인 {money(result.allocation.emergencyTarget)}을 기준으로
              계산했어요. 비상금 목표를 채우거나 지출이 바뀌면 다시 계산해 주세요.
            </p>
            <div className="finance-allocation" aria-label="월 자금 배분 결과">
              {allocations.map((item) => (
                <article key={item.key} className={`finance-allocation-card ${item.key}`}>
                  <span>{item.label}</span>
                  <strong>{money(item.amount)}</strong>
                  <div aria-hidden="true">
                    <i
                      style={{
                        width: `${Math.round((item.amount / result.allocation.monthlyAvailable) * 100)}%`,
                      }}
                    />
                  </div>
                  <small>
                    {Math.round((item.amount / result.allocation.monthlyAvailable) * 100)}%
                  </small>
                </article>
              ))}
            </div>
            <div className="finance-result-action">
              <div>
                <strong>다음 할 일: 계획 저장하기</strong>
                <p>월 계획을 주간 목표로 가져와 실제 저축·투자 금액을 비교하세요.</p>
              </div>
              <button
                type="button"
                onClick={() => void savePlan()}
                disabled={saving || Boolean(savedMessage) || Boolean(auth?.loading)}
              >
                {saving ? '저장 중…' : savedMessage ? '저장 완료' : '이 계획 저장하기'}
              </button>
            </div>
            {savedMessage ? (
              <p className="finance-save-feedback" role="status">
                {savedMessage} <Link to="/finance/weekly-records">주간 기록으로 →</Link>
              </p>
            ) : null}
            <p className="finance-source-note">
              교육용 배분 예시이며 개인별 투자자문이 아닙니다. 입력값을 바꿨다면 다시 계산해 주세요.{' '}
              <a
                href="https://www.finra.org/investors/insights/tips-new-investors"
                target="_blank"
                rel="noreferrer"
              >
                비상금·부채 관리 원칙
              </a>
            </p>
            <details className="finance-result-details">
              <summary>
                같은 월급의 다른 배분 예시<span>실제 사용자 평균이 아닌 가상의 예시</span>
              </summary>
              <p className="finance-budget-explanation">
                세후 월급 {money(result.input.monthlyIncome)}, 지출{' '}
                {money(result.allocation.monthlyExpenses)}, 비상금 마련 완료·고금리 부채 없음을
                가정합니다. 현재 목표의 달성 여부와는 별개예요.
              </p>
              <div className="finance-budget-table-wrap">
                <table className="finance-budget-table">
                  <caption>남는 돈 {money(result.allocation.monthlyAvailable)}의 배분 예시</caption>
                  <thead>
                    <tr>
                      <th scope="col">상황</th>
                      <th scope="col">저축</th>
                      <th scope="col">투자</th>
                    </tr>
                  </thead>
                  <tbody>
                    {[
                      { label: '가까운 목표 우선', rate: 0.8 },
                      { label: '장기 목표·변동 감수', rate: 0.5 },
                    ].map((example) => {
                      const saving = Math.round(result.allocation.monthlyAvailable * example.rate);
                      return (
                        <tr key={example.label}>
                          <th scope="row">{example.label}</th>
                          <td>{money(saving)}</td>
                          <td>{money(result.allocation.monthlyAvailable - saving)}</td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </details>
          </section>

          <section className="finance-section">
            <details className="finance-result-details">
              <summary>
                저축 상품 비교 펼쳐보기<span>예금·일반 적금·청년미래적금</span>
              </summary>
              <div className="finance-section-heading">
                <span>03 · 저축 상품 비교</span>
                <h2>안전하게 모을 돈은 이렇게 나눠볼 수 있어요</h2>
                <p>
                  예금은 이미 모은 목표 자금, 적금은 월 저축 배분액 중 최대 50만 원으로 비교해요.
                  비상금은 제외했어요. 예금 만기가 목표 기간보다 길면 사용 시점도 확인해 주세요.
                </p>
              </div>
              <div className="finance-product-grid">
                <article className="finance-product-card">
                  <span>목돈 · 정기예금</span>
                  <h3>{money(result.deposit.depositMaturity)}</h3>
                  <p>1년 뒤 세후 예상액</p>
                  <dl>
                    <div>
                      <dt>예치 가능한 목돈</dt>
                      <dd>{money(result.depositableLump)}</dd>
                    </div>
                    <div>
                      <dt>세후 이자</dt>
                      <dd>{money(result.deposit.depositInterestAfterTax)}</dd>
                    </div>
                  </dl>
                  {result.depositableLump === 0 && (
                    <small>아직 모은 목표 자금이 없어 예금 비교 금액이 0원이에요.</small>
                  )}
                </article>
                <article className="finance-product-card">
                  <span>일반 적금</span>
                  <h3>{money(result.regularSavings.savingsMaturity)}</h3>
                  <p>월 {money(result.policyDeposit)} · 3년 예상액</p>
                  <dl>
                    <div>
                      <dt>내가 낸 돈</dt>
                      <dd>{money(result.regularSavings.savingsPrincipal)}</dd>
                    </div>
                    <div>
                      <dt>세후 이자</dt>
                      <dd>{money(result.regularSavings.savingsInterestAfterTax)}</dd>
                    </div>
                  </dl>
                </article>
                <article className="finance-product-card recommended">
                  <span>정책 적금 · 청년미래적금</span>
                  <h3>{money(result.youthSavings.maturity)}</h3>
                  <p>월 {money(result.policyDeposit)} · 3년 예상액</p>
                  <dl>
                    <div>
                      <dt>내가 낸 돈</dt>
                      <dd>{money(result.youthSavings.principal)}</dd>
                    </div>
                    <div>
                      <dt>예상 은행 이자</dt>
                      <dd>{money(result.youthSavings.bankInterest)}</dd>
                    </div>
                    <div>
                      <dt>정부기여금</dt>
                      <dd>{money(result.youthSavings.governmentContribution)}</dd>
                    </div>
                  </dl>
                  <small>
                    {result.input.age < 19 || result.input.age > 34
                      ? '기본 연령은 만 19~34세예요. 병역 예외는 공식 심사가 필요해요.'
                      : result.input.eligible === 'no'
                        ? '입력한 내용상 소득 요건 확인이 필요해요.'
                        : '연령 범위에 해당해요. 실제 가입 여부는 공식 심사가 필요해요.'}
                  </small>
                </article>
              </div>
              <p className="finance-source-note">
                기준일 2026년 10월 3일 ·{' '}
                <a href="https://www.fsc.go.kr/po010101/87726" target="_blank" rel="noreferrer">
                  금융위원회 청년미래적금 기준
                </a>
              </p>
            </details>
          </section>

          <section className="finance-section">
            <details className="finance-result-details">
              <summary>
                투자 시뮬레이션 펼쳐보기<span>예상 수익과 하락 상황 함께 보기</span>
              </summary>
              <div className="finance-section-heading">
                <span>04 · 투자 시뮬레이션</span>
                <h2>수익뿐 아니라 하락 가능성도 같이 확인해요</h2>
                <p>개별 종목 추천이 아닌 분산된 장기투자를 가정한 교육용 계산이에요.</p>
              </div>
              <div className="finance-investment-layout">
                <article className="finance-investment-card">
                  <span>매달 투자할 금액</span>
                  <strong>{money(result.allocation.investment)}</strong>
                  <dl>
                    <div>
                      <dt>{result.input.goalMonths}개월간 투자 원금</dt>
                      <dd>{money(result.investment.principal)}</dd>
                    </div>
                    <div>
                      <dt>연 {result.input.investmentRate}% 가정 예상액</dt>
                      <dd>{money(result.investment.estimatedValue)}</dd>
                    </div>
                    <div>
                      <dt>예상 수익</dt>
                      <dd>{money(result.investment.estimatedReturn)}</dd>
                    </div>
                  </dl>
                </article>
                <article className="finance-risk-card">
                  <span>하락 상황도 견딜 수 있나요?</span>
                  <strong>{money(result.investment.afterTwentyPercentDrop)}</strong>
                  <p>예상 자산이 형성된 뒤 시장이 20% 하락했다고 가정한 금액이에요.</p>
                  <small>투자 수익률은 보장되지 않으며 원금 손실이 발생할 수 있어요.</small>
                </article>
              </div>
              <div className="finance-future-comparison">
                <div>
                  <span>같은 돈을 모두 적금에 넣는 경우</span>
                  <strong>{money(result.saveOnly)}</strong>
                </div>
                <div>
                  <span>현재 저축·투자 계획을 병행하는 경우</span>
                  <strong>{money(result.mixedPlan)}</strong>
                </div>
                <p>
                  두 금액 모두 입력한 금리와 수익률이 유지된다는 가정이며, 이미 모은 돈·비상금·부채
                  상환액은 비교에서 제외했어요.{' '}
                  <a
                    href="https://www.finra.org/investors/investing/investing-basics/asset-allocation-diversification"
                    target="_blank"
                    rel="noreferrer"
                  >
                    분산투자 원칙 보기
                  </a>
                </p>
              </div>
            </details>
          </section>
        </>
      )}

      <section className="finance-section finance-guide-section">
        <h2>계좌 개설이나 금융 용어가 낯선가요?</h2>
        <p>방법과 조건은 초보 가이드에서 알아보고, 여기서는 나에게 맞는 금액을 정하세요.</p>
        <Link to="/finance/guides">초보 가이드에서 알아보기 →</Link>
      </section>

      <section className="finance-next">
        <h2>계획을 실제 자산과 연결해 볼까요?</h2>
        <p>금융 이슈를 확인하고 투자한 자산은 포트폴리오에 기록해 보세요.</p>
        <div>
          <Link to="/news">오늘의 이슈</Link>
          <Link to="/">내 포트폴리오</Link>
        </div>
      </section>
    </main>
  );
}

export { WeeklyPracticePage } from './WeeklyPracticePage';
