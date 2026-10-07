import { useContext, useEffect, useState } from 'react';
import { getInvestmentPlan, saveInvestmentPlan } from './api';
import type { InvestmentPlan, InvestmentPlanRequest, InvestmentProduct } from './types';
import { AuthContext } from '../../auth/AuthContext';
import './investment-capacity.css';

type FormState = Omit<
  InvestmentPlanRequest,
  'selectedScenario' | 'selectedProductId' | 'irregularExpenseReserve' | 'emergencyFundContribution'
>;

const INITIAL_FORM: FormState = {
  birthDate: '',
  monthlyIncome: 0,
  monthlyFixedExpense: 0,
  monthlyVariableExpense: 0,
  monthlyDebtRepayment: 0,
  currentCash: 0,
  existingDepositSavings: 0,
  investmentAssets: 0,
  otherAssets: 0,
  goalAmount: 0,
  goalMonths: 12,
};

const numberFormatter = new Intl.NumberFormat('ko-KR');

function formatManwon(value: number) {
  const manwon = Math.round(value / 10_000);
  const eok = Math.floor(manwon / 10_000);
  const remainder = manwon % 10_000;

  if (eok > 0 && remainder > 0) {
    return `${numberFormatter.format(eok)}억 ${numberFormatter.format(remainder)}만원`;
  }
  if (eok > 0) return `${numberFormatter.format(eok)}억원`;
  return `${numberFormatter.format(manwon)}만원`;
}

function formatDate(value: string) {
  return value.replace(/-/g, '.');
}

function roundToManwon(value: number) {
  return Math.round(value / 10_000) * 10_000;
}

function calculateExpectedMaturity(
  initialAmount: number,
  monthlyDeposit: number,
  months: number,
  annualRate: number,
  savings: boolean,
) {
  const monthlyRate = annualRate / 1200;
  let balance = savings ? initialAmount : monthlyDeposit;
  for (let month = 0; month < months; month += 1) {
    balance = balance * (1 + monthlyRate) + (savings ? monthlyDeposit : 0);
  }
  return Math.round(balance);
}

function amountConditionStatus(
  condition: InvestmentProduct['amountConditions'][number],
  amount: number,
) {
  if (condition.thresholdAmount === null) return condition.status;
  return amount >= condition.thresholdAmount ? '금액 충족' : '금액 부족';
}

function ProductAmountField({
  productId,
  label,
  value,
  maxLimit,
  onChange,
}: {
  productId: string;
  label: string;
  value: number;
  maxLimit: number;
  onChange: (value: number) => void;
}) {
  const [draft, setDraft] = useState(value === 0 ? '' : String(Math.round(value / 10_000)));
  const [focused, setFocused] = useState(false);

  useEffect(() => {
    if (!focused) setDraft(value === 0 ? '' : String(Math.round(value / 10_000)));
  }, [focused, value]);

  return (
    <label className="lab-product-amount" htmlFor={`product-amount-${productId}`}>
      <span>{label}</span>
      <span className="lab-input-wrap">
        <input
          id={`product-amount-${productId}`}
          type="number"
          min="0"
          max={maxLimit > 0 ? Math.floor(maxLimit / 10_000) : undefined}
          step="1"
          inputMode="numeric"
          value={draft}
          onFocus={() => setFocused(true)}
          onChange={(event) => {
            const nextValue = event.target.value;
            setDraft(nextValue);
            onChange(Math.max(0, Math.round((Number(nextValue) || 0) * 10_000)));
          }}
          onBlur={() => setFocused(false)}
        />
        <small>만원</small>
      </span>
      {maxLimit > 0 ? <small>공시 최대 {formatManwon(maxLimit)}</small> : null}
    </label>
  );
}

function toForm(plan: InvestmentPlan): FormState {
  return {
    birthDate: plan.birthDate,
    monthlyIncome: plan.monthlyIncome,
    monthlyFixedExpense: plan.monthlyFixedExpense,
    monthlyVariableExpense: plan.monthlyVariableExpense,
    monthlyDebtRepayment: plan.monthlyDebtRepayment,
    currentCash: plan.currentCash,
    existingDepositSavings: plan.existingDepositSavings,
    investmentAssets: plan.investmentAssets,
    otherAssets: plan.otherAssets,
    goalAmount: plan.goalAmount,
    goalMonths: plan.goalMonths,
  };
}

function statusLabel(status: InvestmentPlan['status']) {
  if (status === 'ACHIEVABLE') return '달성 가능';
  if (status === 'ADJUSTABLE') return '조정하면 달성 가능';
  if (status === 'DIFFICULT') return '현재 조건에서는 어려움';
  return '가용 금액 기준';
}

function allocationLabel(type: 'saving' | 'investing' | 'emergency' | 'remaining') {
  if (type === 'saving') return '목표 자금 저축';
  if (type === 'investing') return '장기 여유자금';
  if (type === 'emergency') return '비상금 보완';
  return '남겨둘 월 여유액';
}

function defaultProductAmount(
  plan: InvestmentPlan,
  scenario: InvestmentPlan['scenarios'][number],
  product: InvestmentProduct,
) {
  const productsOfType = plan.products.filter((item) => item.type === product.type);
  const productIndex = productsOfType.findIndex((item) => item.id === product.id);

  if (product.type === '적금') {
    const firstAmount = roundToManwon(scenario.monthlySaving * 0.6);
    if (productIndex === 0) return firstAmount;
    if (productIndex === 1) return Math.max(0, scenario.monthlySaving - firstAmount);
    return 0;
  }

  return productIndex === 0 ? roundToManwon(plan.availableCurrentCash * 0.5) : 0;
}

function hasUnmetAmountCondition(product: InvestmentProduct, amount: number) {
  return product.amountConditions.some(
    (condition) => condition.thresholdAmount !== null && amount < condition.thresholdAmount,
  );
}

function amountRateAssessment(product: InvestmentProduct, amount: number) {
  if (hasUnmetAmountCondition(product, amount)) {
    return {
      status: '금액 조건 미충족',
      detail: '현재 배분액으로는 일부 금액 조건을 충족하지 못해 기본금리 기준으로 비교해요.',
    };
  }

  if (product.amountConditions.length > 0 || product.benefitConditions.length > 0) {
    return {
      status: '금융기관 확인 필요',
      detail: '금액별·우대조건별 실제 적용 금리표가 API에 없어 금융기관에서 확인해야 해요.',
    };
  }

  return {
    status: '기본금리 기준 비교 가능',
    detail: `금액 조건이 없어 공시 기본금리 ${product.baseRate.toFixed(2)}%를 비교 기준으로 사용해요.`,
  };
}

function rankProductsByAmount(
  products: InvestmentProduct[],
  amount: number,
  excludedProductId?: string,
) {
  return products
    .filter((product) => product.id !== excludedProductId)
    .toSorted((left, right) => {
      const leftUnmet = hasUnmetAmountCondition(left, amount);
      const rightUnmet = hasUnmetAmountCondition(right, amount);
      if (leftUnmet !== rightUnmet) return leftUnmet ? 1 : -1;
      return right.baseRate - left.baseRate || right.maxRate - left.maxRate;
    });
}

function MoneyField({
  id,
  label,
  value,
  onChange,
}: {
  id: keyof FormState;
  label: string;
  value: number;
  onChange: (value: number) => void;
}) {
  const [draft, setDraft] = useState(value === 0 ? '' : String(Math.round(value / 10_000)));
  const [focused, setFocused] = useState(false);

  useEffect(() => {
    if (!focused) setDraft(value === 0 ? '' : String(Math.round(value / 10_000)));
  }, [focused, value]);

  return (
    <label className="lab-field" htmlFor={id}>
      <span>{label}</span>
      <span className="lab-input-wrap">
        <input
          id={id}
          type="number"
          min="0"
          step="1"
          inputMode="numeric"
          aria-label={label}
          value={draft}
          onFocus={(event) => {
            setFocused(true);
            if (draft === '0') event.currentTarget.select();
          }}
          onChange={(event) => {
            const nextValue = event.target.value;
            setDraft(nextValue);
            onChange(Math.max(0, Math.round((Number(nextValue) || 0) * 10_000)));
          }}
          onBlur={() => setFocused(false)}
        />
        <small>만원</small>
      </span>
    </label>
  );
}

export function InvestmentCapacityPage() {
  const auth = useContext(AuthContext);
  const isLoggedIn = auth?.isLoggedIn ?? false;
  const authLoading = auth?.loading ?? false;
  const [form, setForm] = useState<FormState>(INITIAL_FORM);
  const [plan, setPlan] = useState<InvestmentPlan | null>(null);
  const [selectedScenario, setSelectedScenario] = useState('STABLE');
  const [selectedProduct, setSelectedProduct] = useState<string | null>(null);
  const [selectedTerms, setSelectedTerms] = useState<Record<string, number>>({});
  const [selectedAmounts, setSelectedAmounts] = useState<Record<string, number>>({});
  const [selectedAllocation, setSelectedAllocation] = useState<string | null>(null);
  const [isFinancialStateEditing, setIsFinancialStateEditing] = useState(true);
  const [isPlanLoading, setIsPlanLoading] = useState(false);
  const [saveState, setSaveState] = useState<'idle' | 'loading' | 'saved' | 'error'>('idle');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    if (authLoading) return;
    if (!isLoggedIn) {
      setIsFinancialStateEditing(true);
      return;
    }

    let cancelled = false;
    setIsPlanLoading(true);
    getInvestmentPlan()
      .then((savedPlan) => {
        if (cancelled || !savedPlan) return;
        setForm(toForm(savedPlan));
        setPlan(savedPlan);
        setIsFinancialStateEditing(false);
        setSelectedScenario(savedPlan.selectedScenario ?? 'STABLE');
        const savedProductId = savedPlan.selectedProductId;
        setSelectedProduct(
          savedProductId && savedPlan.products.some((product) => product.id === savedProductId)
            ? savedProductId
            : (savedPlan.products[0]?.id ?? null),
        );
        setSaveState('saved');
        setErrorMessage(null);
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        setSaveState('error');
        setErrorMessage(error instanceof Error ? error.message : null);
      })
      .finally(() => {
        if (!cancelled) setIsPlanLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [authLoading, isLoggedIn]);

  function updateField<Key extends keyof FormState>(key: Key, value: FormState[Key]) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  function productGroupKey(product: InvestmentPlan['products'][number]) {
    return `${product.companyName}|${product.name}|${product.type}`;
  }

  const productGroups = plan
    ? ['예금', '적금']
        .map((type) => ({
          type,
          products: plan.products.filter((product) => product.type === type),
        }))
        .filter((group) => group.products.length > 0)
    : [];

  async function persistPlan(
    scenario = selectedScenario,
    productId = selectedProduct,
  ): Promise<InvestmentPlan | null> {
    if (!isLoggedIn || !form.birthDate) return null;

    const request: InvestmentPlanRequest = {
      ...form,
      irregularExpenseReserve: Math.round(form.monthlyVariableExpense * 0.1),
      emergencyFundContribution: 0,
      selectedScenario: scenario,
      selectedProductId: productId,
    };
    setSaveState('loading');
    setErrorMessage(null);
    try {
      const savedPlan = await saveInvestmentPlan(request);
      setPlan(savedPlan);
      setIsFinancialStateEditing(false);
      setSelectedScenario(savedPlan.selectedScenario ?? scenario);
      setSelectedProduct(savedPlan.selectedProductId ?? productId);
      setSaveState('saved');
      setErrorMessage(null);
      return savedPlan;
    } catch (error: unknown) {
      setSaveState('error');
      setErrorMessage(error instanceof Error ? error.message : null);
      return null;
    }
  }

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void persistPlan();
  }

  return (
    <main className="lab-page">
      <header className="lab-header">
        <div>
          <h1>내 목표에 맞는 금융 플랜을 만들어보세요</h1>
          <p>현재 재무상태와 목표를 입력하면 감당 가능한 월 납입액과 전략을 계산합니다.</p>
        </div>
        {!isLoggedIn && !authLoading ? (
          <a
            className="lab-login-link"
            href="/login"
            onClick={() =>
              sessionStorage.setItem('daynomy:post-login-path', '/investment-capacity')
            }
          >
            로그인하고 저장
          </a>
        ) : null}
      </header>

      <form className="lab-form" onSubmit={handleSubmit}>
        {isPlanLoading ? (
          <section className="lab-plan-loading" aria-live="polite" role="status">
            <strong>저장된 플랜을 불러오는 중이에요</strong>
            <p>실제 금융상품 정보를 함께 조회하고 있어 잠시 시간이 걸릴 수 있어요.</p>
          </section>
        ) : plan && !isFinancialStateEditing ? (
          <section className="lab-saved-summary" aria-labelledby="financial-state-summary-title">
            <div className="lab-saved-summary-heading">
              <div>
                <h2 id="financial-state-summary-title">현재 재무상태</h2>
                <p>저장된 정보를 확인하고 필요할 때 수정하세요.</p>
              </div>
              <button
                className="lab-summary-edit-button"
                type="button"
                onClick={() => setIsFinancialStateEditing(true)}
              >
                수정
              </button>
            </div>
            <dl className="lab-saved-summary-grid">
              <div>
                <dt>생년월일</dt>
                <dd>{formatDate(plan.birthDate)}</dd>
              </div>
              <div>
                <dt>월 실수령액</dt>
                <dd>{formatManwon(plan.monthlyIncome)}</dd>
              </div>
              <div>
                <dt>월 고정지출</dt>
                <dd>{formatManwon(plan.monthlyFixedExpense)}</dd>
              </div>
              <div>
                <dt>월 변동지출</dt>
                <dd>{formatManwon(plan.monthlyVariableExpense)}</dd>
              </div>
              <div>
                <dt>월 부채 상환액</dt>
                <dd>{formatManwon(plan.monthlyDebtRepayment)}</dd>
              </div>
              <div>
                <dt>바로 사용할 수 있는 현금</dt>
                <dd>{formatManwon(plan.currentCash)}</dd>
              </div>
              <div>
                <dt>기존 예금·적금</dt>
                <dd>{formatManwon(plan.existingDepositSavings)}</dd>
              </div>
              <div>
                <dt>투자자산</dt>
                <dd>{formatManwon(plan.investmentAssets)}</dd>
              </div>
              <div>
                <dt>기타 자산</dt>
                <dd>{formatManwon(plan.otherAssets)}</dd>
              </div>
              <div>
                <dt>총 보유자산</dt>
                <dd>{formatManwon(plan.totalAssets)}</dd>
              </div>
            </dl>
          </section>
        ) : (
          <section className="lab-card" aria-labelledby="financial-state-title">
            <div className="lab-card-heading">
              <span>01</span>
              <div>
                <h2 id="financial-state-title">현재 재무상태 진단</h2>
                <p>안전하게 운용할 수 있는 금액을 계산하는 데 필요한 정보예요.</p>
              </div>
            </div>
            <div className="lab-financial-group">
              <div className="lab-fields">
                <label className="lab-field" htmlFor="birthDate">
                  <span>생년월일</span>
                  <input
                    id="birthDate"
                    type="date"
                    value={form.birthDate}
                    onChange={(event) => updateField('birthDate', event.target.value)}
                    required
                  />
                </label>
                <MoneyField
                  id="monthlyIncome"
                  label="월 실수령액"
                  value={form.monthlyIncome}
                  onChange={(value) => updateField('monthlyIncome', value)}
                />
                <MoneyField
                  id="monthlyFixedExpense"
                  label="월 고정지출"
                  value={form.monthlyFixedExpense}
                  onChange={(value) => updateField('monthlyFixedExpense', value)}
                />
                <MoneyField
                  id="monthlyVariableExpense"
                  label="월 변동지출"
                  value={form.monthlyVariableExpense}
                  onChange={(value) => updateField('monthlyVariableExpense', value)}
                />
                <MoneyField
                  id="monthlyDebtRepayment"
                  label="월 부채 상환액"
                  value={form.monthlyDebtRepayment}
                  onChange={(value) => updateField('monthlyDebtRepayment', value)}
                />
              </div>
            </div>
            <div className="lab-financial-group">
              <div className="lab-financial-group-heading">
                <h3>보유자산</h3>
                <p>
                  월 납입금과 별도로 목돈으로 운용할 자산을 입력해요. 기존 예금·적금은 만기나 해지
                  후 사용할 수 있는 금액만 적어주세요.
                </p>
              </div>
              <div className="lab-fields">
                <MoneyField
                  id="currentCash"
                  label="바로 사용할 수 있는 현금"
                  value={form.currentCash}
                  onChange={(value) => updateField('currentCash', value)}
                />
                <MoneyField
                  id="existingDepositSavings"
                  label="기존 예금·적금"
                  value={form.existingDepositSavings}
                  onChange={(value) => updateField('existingDepositSavings', value)}
                />
                <MoneyField
                  id="investmentAssets"
                  label="투자자산"
                  value={form.investmentAssets}
                  onChange={(value) => updateField('investmentAssets', value)}
                />
                <MoneyField
                  id="otherAssets"
                  label="기타 자산"
                  value={form.otherAssets}
                  onChange={(value) => updateField('otherAssets', value)}
                />
              </div>
            </div>
          </section>
        )}

        <section className="lab-card" aria-labelledby="goal-title">
          <div className="lab-card-heading">
            <span>02</span>
            <div>
              <h2 id="goal-title">목표와 기간 설정</h2>
              <p>목표 금액은 선택사항이에요. 비워두면 기간과 가용 금액 기준으로 추천해요.</p>
            </div>
          </div>
          <div className="lab-goal-fields">
            <MoneyField
              id="goalAmount"
              label="목표 금액"
              value={form.goalAmount}
              onChange={(value) => updateField('goalAmount', value)}
            />
            <label className="lab-field" htmlFor="goalMonths">
              <span>목표까지 남은 기간</span>
              <span className="lab-input-wrap">
                <input
                  id="goalMonths"
                  type="number"
                  min="1"
                  max="600"
                  value={form.goalMonths}
                  onChange={(event) =>
                    updateField(
                      'goalMonths',
                      Math.min(600, Math.max(1, Number(event.target.value) || 1)),
                    )
                  }
                  required
                />
                <small>개월</small>
              </span>
            </label>
          </div>
          <button className="lab-submit-button" type="submit" disabled={saveState === 'loading'}>
            {saveState === 'loading' ? '계산 중…' : '플랜 계산하고 저장'}
          </button>
          {!isLoggedIn && !authLoading ? (
            <p className="lab-form-note">
              금융 계획은 로그인 후 저장하고 다음 방문에도 불러올 수 있어요.
            </p>
          ) : null}
          {saveState === 'error' ? (
            <p className="lab-error" role="alert">
              {errorMessage ?? '금융 계획을 불러오거나 저장하지 못했어요.'} 잠시 후 다시 시도해
              주세요.
            </p>
          ) : null}
        </section>
      </form>

      {plan ? (
        <section className="lab-results" aria-labelledby="lab-results-title">
          <div className="lab-results-hero">
            <div className="lab-results-heading">
              <h2 id="lab-results-title">계산 결과와 추천 전략</h2>
              <strong className={`lab-status ${plan.status.toLowerCase()}`}>
                {statusLabel(plan.status)}
              </strong>
            </div>
            <p className="lab-interpretation">{plan.interpretation}</p>
            <p className="lab-experiment-note">
              {plan.goalAmount === 0
                ? '목표 금액 없이도 기간과 월 운용 가능액을 바꿔보며 상품을 비교할 수 있어요.'
                : '상품별 기간과 금액을 바꿔보면서 예상 이자와 다른 목표에 남길 금액을 비교해보세요.'}
            </p>
          </div>

          <div className="lab-metrics">
            <div>
              <span>안전한 월 운용 가능액</span>
              <strong>{formatManwon(plan.safeMonthlyCapacity)}</strong>
            </div>
            <div>
              <span>
                {plan.goalAmount === 0 ? '기간 동안 운용할 월 금액' : '목표에 필요한 월 납입액'}
              </span>
              <strong>
                {formatManwon(
                  plan.goalAmount === 0 ? plan.safeMonthlyCapacity : plan.requiredMonthlySaving,
                )}
              </strong>
            </div>
            <div>
              <span>비상금 부족액</span>
              <strong>{formatManwon(plan.emergencyFundGap)}</strong>
            </div>
            <div>
              <span>목돈으로 운용 가능한 자산</span>
              <strong>{formatManwon(plan.availableCurrentCash)}</strong>
            </div>
          </div>

          <div className="lab-section-heading">
            <h3>전략 시나리오</h3>
            <span>
              {plan.goalAmount === 0
                ? '목표 없이 기간 동안 운용할 방식을 선택하세요.'
                : '내 상황에 맞는 방식을 선택하세요.'}
            </span>
          </div>
          {(() => {
            const activeScenario =
              plan.scenarios.find((scenario) => scenario.type === selectedScenario) ??
              plan.scenarios[0];
            const remainingAmount = Math.max(
              0,
              plan.safeMonthlyCapacity -
                activeScenario.monthlySaving -
                activeScenario.monthlyInvesting -
                activeScenario.monthlyEmergencyFund,
            );
            const allocations = [
              {
                type: 'saving' as const,
                amount: activeScenario.monthlySaving,
                detail: '예금·적금으로 목표 자금을 먼저 확보해요.',
              },
              {
                type: 'investing' as const,
                amount: activeScenario.monthlyInvesting,
                detail:
                  activeScenario.monthlyInvesting > 0
                    ? '목표 기간 전에 쓰지 않을 장기 여유자금만 투자해요.'
                    : '현재 전략에서는 투자하지 않고 안전하게 모아요.',
              },
              {
                type: 'emergency' as const,
                amount: activeScenario.monthlyEmergencyFund,
                detail: '비상금이 부족하면 목표 자금보다 먼저 보완해요.',
              },
              {
                type: 'remaining' as const,
                amount: remainingAmount,
                detail: '다른 목표, 추가 납입, 예상 밖의 지출에 남겨둘 수 있어요.',
              },
            ];

            return (
              <div className="lab-allocation" aria-label={`${activeScenario.label} 자금 배분`}>
                <div className="lab-allocation-heading">
                  <div>
                    <h4>{activeScenario.label} 자금 배분</h4>
                    <p>
                      월 가용액과 목돈 자산을 분리해 역할별로 나눈 예시예요. 월 납입금은 적금에,
                      목돈은 비상금을 제외한 예금에 배분해요.
                    </p>
                  </div>
                  <strong>{formatManwon(plan.safeMonthlyCapacity)} 안에서 배분</strong>
                </div>
                <div className="lab-allocation-grid">
                  {allocations.map((allocation) => (
                    <div className={`lab-allocation-item ${allocation.type}`} key={allocation.type}>
                      <span>{allocationLabel(allocation.type)}</span>
                      <strong>{formatManwon(allocation.amount)}</strong>
                      <small>{allocation.detail}</small>
                    </div>
                  ))}
                </div>
              </div>
            );
          })()}
          <div className="lab-scenarios" role="group" aria-label="전략 시나리오">
            {plan.scenarios.map((scenario) => (
              <button
                type="button"
                key={scenario.type}
                className={`lab-scenario${selectedScenario === scenario.type ? ' selected' : ''}`}
                aria-pressed={selectedScenario === scenario.type}
                onClick={() => {
                  setSelectedScenario(scenario.type);
                  void persistPlan(scenario.type, selectedProduct);
                }}
              >
                <div className="lab-scenario-topline">
                  <span>{scenario.label}</span>
                  <strong>월 {formatManwon(scenario.monthlySaving)}</strong>
                </div>
                <p>{scenario.description}</p>
                <small>{scenario.caution}</small>
              </button>
            ))}
          </div>

          <div className="lab-section-heading">
            <h3>상품 비교·선택</h3>
            <span>
              {plan.goalAmount === 0
                ? '현재 가용 금액과 목표 기간을 기준으로 고른 실제 공시 상품입니다.'
                : '금융감독원 금융상품 한눈에 API의 실제 공시 상품입니다.'}
            </span>
          </div>
          {(() => {
            const activeScenario =
              plan.scenarios.find((scenario) => scenario.type === selectedScenario) ??
              plan.scenarios[0];
            const firstSavingsAmount = roundToManwon(activeScenario.monthlySaving * 0.6);
            const secondSavingsAmount = Math.max(
              0,
              activeScenario.monthlySaving - firstSavingsAmount,
            );
            const depositAmount = roundToManwon(plan.availableCurrentCash * 0.5);
            const savingsProducts = plan.products.filter((product) => product.type === '적금');
            const depositProducts = plan.products.filter((product) => product.type === '예금');
            const primarySavings = rankProductsByAmount(savingsProducts, firstSavingsAmount)[0];
            const secondarySavings = rankProductsByAmount(
              savingsProducts,
              secondSavingsAmount,
              primarySavings?.id,
            )[0];
            const depositProduct = rankProductsByAmount(depositProducts, depositAmount)[0];
            const allocationOptions = [
              {
                id: 'saving-primary',
                label: '월 저축 60%',
                amount: firstSavingsAmount,
                detail: '월 저축의 기본축으로 사용해요.',
                product: primarySavings,
              },
              {
                id: 'saving-secondary',
                label: '월 저축 40%',
                amount: secondSavingsAmount,
                detail: '다른 상품으로 나눠 한 곳에 몰리지 않게 해요.',
                product: secondarySavings,
              },
              {
                id: 'deposit',
                label: '목돈 자산 중 예금',
                amount: depositAmount,
                detail:
                  plan.availableCurrentCash > 0
                    ? '비상금을 제외한 목돈 자산의 절반만 예금으로 묶어요.'
                    : '비상금 제외 목돈 자산이 없어 예금은 무리해서 추천하지 않아요.',
                product: depositProduct,
              },
            ];
            const selectedAllocationOption = allocationOptions.find(
              (option) => option.id === selectedAllocation,
            );

            return (
              <div className="lab-diversification" aria-label="분산 추천 배분">
                <div className="lab-diversification-heading">
                  <div>
                    <h4>한 곳에 몰지 않는 추천 배분</h4>
                    <p>월 납입금과 목돈을 나눠 실제 상품을 비교해요.</p>
                  </div>
                  <span>금액 조건 확인</span>
                </div>
                <div className="lab-diversification-list">
                  {allocationOptions.map((option) => (
                    <button
                      type="button"
                      className={`lab-diversification-item${selectedAllocation === option.id ? ' selected' : ''}`}
                      aria-pressed={selectedAllocation === option.id}
                      key={option.id}
                      onClick={() => setSelectedAllocation(option.id)}
                    >
                      <span>{option.label}</span>
                      <strong>{formatManwon(option.amount)}</strong>
                      {option.product ? (
                        <small className="lab-diversification-product">
                          <b>{option.product.companyName}</b>
                          <span>{option.product.name}</span>
                        </small>
                      ) : (
                        <small className="lab-diversification-product">
                          {option.id === 'saving-secondary'
                            ? '다른 적금 또는 현금성 자산'
                            : '추천 예금 상품'}
                        </small>
                      )}
                      {option.product ? (
                        <em
                          className={`lab-allocation-status ${
                            amountRateAssessment(option.product, option.amount).status ===
                            '기본금리 기준 비교 가능'
                              ? 'available'
                              : 'confirm'
                          }`}
                        >
                          {amountRateAssessment(option.product, option.amount).status}
                        </em>
                      ) : null}
                    </button>
                  ))}
                </div>
                {selectedAllocationOption ? (
                  <div className="lab-diversification-detail" aria-live="polite">
                    <div className="lab-diversification-detail-heading">
                      <div>
                        <span className="lab-diversification-detail-label">
                          {selectedAllocationOption.label}
                        </span>
                        <h5>
                          {selectedAllocationOption.product
                            ? selectedAllocationOption.product.name
                            : '상품을 특정하지 않았어요'}
                        </h5>
                      </div>
                      <button type="button" onClick={() => setSelectedAllocation(null)}>
                        닫기
                      </button>
                    </div>
                    <p className="lab-diversification-detail-intro">
                      {selectedAllocationOption.detail}
                    </p>
                    {selectedAllocationOption.product ? (
                      <>
                        <div className="lab-rate-assessment">
                          <strong>
                            {
                              amountRateAssessment(
                                selectedAllocationOption.product,
                                selectedAllocationOption.amount,
                              ).status
                            }
                          </strong>
                          <span>
                            {
                              amountRateAssessment(
                                selectedAllocationOption.product,
                                selectedAllocationOption.amount,
                              ).detail
                            }
                          </span>
                        </div>
                        <dl>
                          <div>
                            <dt>금융회사</dt>
                            <dd>{selectedAllocationOption.product.companyName}</dd>
                          </div>
                          <div>
                            <dt>추천 금액</dt>
                            <dd>{formatManwon(selectedAllocationOption.amount)}</dd>
                          </div>
                          <div>
                            <dt>기간</dt>
                            <dd>{selectedAllocationOption.product.termMonths}개월</dd>
                          </div>
                          <div>
                            <dt>금리</dt>
                            <dd>
                              기본 {selectedAllocationOption.product.baseRate.toFixed(2)}% · 최고{' '}
                              {selectedAllocationOption.product.maxRate.toFixed(2)}%
                            </dd>
                          </div>
                          <div>
                            <dt>유동성</dt>
                            <dd>{selectedAllocationOption.product.liquidity}</dd>
                          </div>
                          <div>
                            <dt>가입 방법</dt>
                            <dd>{selectedAllocationOption.product.joinWay}</dd>
                          </div>
                        </dl>
                      </>
                    ) : (
                      <p className="lab-diversification-detail-note">
                        현재 조회된 상품 중 두 번째 적금이 없어, 이 금액은 다른 적금이나 현금성
                        자산으로 남겨두는 예시예요.
                      </p>
                    )}
                    {selectedAllocationOption.product?.benefitConditions.length ? (
                      <div>
                        <strong>우대조건</strong>
                        <ul>
                          {selectedAllocationOption.product.benefitConditions.map((condition) => (
                            <li key={condition.description}>{condition.description}</li>
                          ))}
                        </ul>
                      </div>
                    ) : null}
                    {selectedAllocationOption.product ? (
                      <small>{selectedAllocationOption.product.earlyWithdrawalNote}</small>
                    ) : null}
                  </div>
                ) : null}
              </div>
            );
          })()}
          <div className="lab-product-groups">
            {productGroups.length > 0 ? (
              productGroups.map((group) => (
                <section className="lab-product-group" key={group.type}>
                  <div className="lab-product-group-heading">
                    <h4>{group.type}</h4>
                    <span>{group.products.length}개 상품</span>
                  </div>
                  <div className="lab-products">
                    {group.products.map((product) => {
                      const groupKey = productGroupKey(product);
                      const selectedTerm = selectedTerms[groupKey] ?? product.termMonths;
                      const termOption =
                        product.termOptions.find((option) => option.termMonths === selectedTerm) ??
                        product.termOptions[0];
                      const productId = termOption?.id ?? product.id;
                      const baseRate = termOption?.baseRate ?? product.baseRate;
                      const maxRate = termOption?.maxRate ?? product.maxRate;
                      const monthlyDeposit = termOption?.monthlyDeposit ?? product.monthlyDeposit;
                      const savings = product.type === '적금';
                      const activeScenario =
                        plan.scenarios.find((scenario) => scenario.type === selectedScenario) ??
                        plan.scenarios[0];
                      const amount =
                        selectedAmounts[groupKey] ??
                        (activeScenario
                          ? defaultProductAmount(plan, activeScenario, product)
                          : monthlyDeposit);
                      const calculationMonths = termOption?.termMonths ?? product.termMonths;
                      const expectedMaturityForAmount = calculateExpectedMaturity(
                        0,
                        amount,
                        calculationMonths,
                        baseRate,
                        savings,
                      );
                      const principal = savings ? amount * calculationMonths : amount;
                      const expectedInterestForAmount = Math.max(
                        0,
                        expectedMaturityForAmount - principal,
                      );
                      const remainingAmount = savings
                        ? Math.max(0, plan.safeMonthlyCapacity - amount)
                        : Math.max(0, plan.availableCurrentCash - amount);
                      const requiredAdditionalCash = savings
                        ? 0
                        : Math.max(0, amount - plan.availableCurrentCash);
                      const isSelected = selectedProduct === productId;

                      return (
                        <article
                          className={`lab-product${isSelected ? ' selected' : ''}`}
                          key={groupKey}
                        >
                          <div className="lab-product-topline">
                            <span>{product.type}</span>
                            <div className="lab-product-rates">
                              <span>기본금리 {baseRate.toFixed(2)}%</span>
                              <strong>최고 우대금리 {maxRate.toFixed(2)}%</strong>
                            </div>
                          </div>
                          <p className="lab-product-company">{product.companyName}</p>
                          <h4>{product.name}</h4>
                          <div
                            className="lab-term-options"
                            role="group"
                            aria-label="상품 기간 비교"
                          >
                            <span>기간 비교</span>
                            <div>
                              {product.termOptions.map((option) => (
                                <button
                                  type="button"
                                  key={option.id}
                                  className={option.id === productId ? 'selected' : ''}
                                  aria-pressed={option.id === productId}
                                  onClick={() => {
                                    setSelectedTerms((current) => ({
                                      ...current,
                                      [groupKey]: option.termMonths,
                                    }));
                                    if (
                                      selectedProduct === product.id ||
                                      selectedProduct === productId
                                    ) {
                                      setSelectedProduct(option.id);
                                    }
                                  }}
                                >
                                  {option.termMonths}개월
                                </button>
                              ))}
                            </div>
                          </div>
                          <dl>
                            <div>
                              <dt>{savings ? '월 납입 금액' : '예치 금액'}</dt>
                              <dd>{formatManwon(amount)}</dd>
                            </div>
                            <div>
                              <dt>예상 만기액</dt>
                              <dd>{formatManwon(expectedMaturityForAmount)}</dd>
                            </div>
                            <div>
                              <dt>예상 이자</dt>
                              <dd>{formatManwon(expectedInterestForAmount)}</dd>
                            </div>
                            <div>
                              <dt>기간 / 유동성</dt>
                              <dd>
                                {termOption?.termMonths ?? product.termMonths}개월 /{' '}
                                {product.liquidity}
                              </dd>
                            </div>
                          </dl>
                          <ProductAmountField
                            productId={productId}
                            label={savings ? '이 상품에 매달 넣을 금액' : '이 상품에 넣을 금액'}
                            value={amount}
                            maxLimit={product.maxLimit}
                            onChange={(nextAmount) =>
                              setSelectedAmounts((current) => ({
                                ...current,
                                [groupKey]: nextAmount,
                              }))
                            }
                          />
                          <div className="lab-product-remainder">
                            <span>
                              {savings
                                ? '남는 월 운용 가능액'
                                : requiredAdditionalCash > 0
                                  ? '추가로 필요한 현금'
                                  : '남는 현금'}
                            </span>
                            <strong>
                              {formatManwon(
                                requiredAdditionalCash > 0
                                  ? requiredAdditionalCash
                                  : remainingAmount,
                              )}
                            </strong>
                            <small>
                              {requiredAdditionalCash > 0
                                ? `현재 비상금 제외 가용 현금 ${formatManwon(plan.availableCurrentCash)}보다 큰 금액이에요.`
                                : savings
                                  ? '다른 적금·예금·비상금에 배분할 수 있어요.'
                                  : '다른 목표나 비상금으로 남겨둘 수 있어요.'}
                            </small>
                          </div>
                          <p>{product.recommendationReason}</p>
                          <small>가입 방법: {product.joinWay}</small>
                          {product.benefitConditions.length > 0 ? (
                            <div className="lab-benefit-conditions">
                              <small>우대·특별 조건</small>
                              <ul>
                                {product.benefitConditions.map((condition) => (
                                  <li key={condition.description}>
                                    <span>{condition.status}</span> {condition.description}
                                  </li>
                                ))}
                              </ul>
                              <small>
                                현재 입력 정보만으로는 우대 조건 충족 여부를 확정하지 않았어요.
                              </small>
                            </div>
                          ) : null}
                          {product.amountConditions.length > 0 ? (
                            <div className="lab-amount-conditions">
                              <small>금액에 따라 달라질 수 있는 조건</small>
                              <ul>
                                {product.amountConditions.map((condition) => (
                                  <li key={condition.description}>
                                    <span>{amountConditionStatus(condition, amount)}</span>{' '}
                                    {condition.description}
                                  </li>
                                ))}
                              </ul>
                              <small>
                                금액 조건 충족 여부는 표시하지만, API에 금액별 적용금리표가 없어
                                실제 금리는 금융사에서 확인해야 해요.
                              </small>
                            </div>
                          ) : null}
                          <small>공시월: {product.disclosureMonth}</small>
                          <small>{product.earlyWithdrawalNote}</small>
                          <a
                            className="lab-product-source"
                            href={product.sourceUrl}
                            target="_blank"
                            rel="noreferrer"
                          >
                            금융상품 한눈에에서 확인
                          </a>
                          <button
                            type="button"
                            className="lab-product-button"
                            aria-pressed={isSelected}
                            onClick={() => {
                              setSelectedProduct(productId);
                              void persistPlan(selectedScenario, productId);
                            }}
                          >
                            {isSelected ? '선택한 상품' : '이 상품 선택'}
                          </button>
                        </article>
                      );
                    })}
                  </div>
                </section>
              ))
            ) : (
              <p className="lab-empty-products">
                현재 금융감독원 API에서 조회된 예금·적금 상품이 없습니다. 잠시 후 다시 시도해
                주세요.
              </p>
            )}
          </div>
          <p className="lab-disclaimer">{plan.products[0]?.dataNote}</p>
        </section>
      ) : null}
    </main>
  );
}
