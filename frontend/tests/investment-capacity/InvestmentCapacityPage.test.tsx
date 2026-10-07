/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthContext } from '../../src/auth/AuthContext';
import { InvestmentCapacityPage } from '../../src/features/investment-capacity/InvestmentCapacityPage';

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

const plan = {
  birthDate: '2000-01-01',
  monthlyIncome: 3000000,
  monthlyFixedExpense: 1000000,
  monthlyVariableExpense: 300000,
  irregularExpenseReserve: 0,
  emergencyFundContribution: 0,
  monthlyDebtRepayment: 0,
  currentCash: 3000000,
  existingDepositSavings: 0,
  investmentAssets: 0,
  otherAssets: 0,
  totalAssets: 3000000,
  availableCurrentCash: 0,
  goalAmount: 10000000,
  goalMonths: 24,
  emergencyFundTarget: 7800000,
  emergencyFundGap: 4800000,
  safeMonthlyCapacity: 1700000,
  requiredMonthlySaving: 416667,
  status: 'ADJUSTABLE',
  interpretation: '비상금을 함께 준비해요.',
  selectedScenario: 'STABLE',
  selectedProductId: 'regular-savings',
  scenarios: [
    {
      type: 'STABLE',
      label: '안정형',
      description: '목표 자금은 안전하게 모아요.',
      monthlySaving: 416667,
      monthlyInvesting: 0,
      monthlyEmergencyFund: 340000,
      expectedAmount: 10000000,
      status: 'DIFFICULT',
      caution: '중도 해지에 주의하세요.',
    },
    {
      type: 'BALANCED',
      label: '균형형',
      description: '여유자금만 투자해요.',
      monthlySaving: 416667,
      monthlyInvesting: 0,
      monthlyEmergencyFund: 340000,
      expectedAmount: 10000000,
      status: 'DIFFICULT',
      caution: '투자금은 손실될 수 있어요.',
    },
  ],
  products: [
    {
      id: 'regular-savings',
      name: '테스트 적금',
      type: '적금',
      baseRate: 3,
      maxRate: 4.5,
      maxLimit: 10000000,
      termMonths: 12,
      monthlyDeposit: 416667,
      expectedMaturityAmount: 5100000,
      expectedInterest: 100000,
      liquidity: '낮음',
      earlyWithdrawalNote: '중도 해지 시 금리가 낮아질 수 있어요.',
      depositProtection: true,
      eligibility: '일반 가입 가능',
      recommendationReason: '목표 기간과 월 납입액에 맞아요.',
      dataNote: '금융감독원 금융상품 한눈에 API 공시 기준입니다.',
      companyName: '테스트은행',
      joinWay: '인터넷뱅킹',
      benefitConditions: [{ description: '급여이체', status: '확인 필요' }],
      amountConditions: [
        {
          description: '5천만원 이상 가입 시 우대',
          thresholdAmount: 50000000,
          status: '확인 필요',
        },
      ],
      disclosureMonth: '202610',
      sourceUrl: 'https://finlife.fss.or.kr/finlife/main/main.do?menuNo=700000',
      termOptions: [
        {
          id: 'regular-savings-12',
          termMonths: 12,
          baseRate: 3,
          maxRate: 4.5,
          monthlyDeposit: 416667,
          expectedMaturityAmount: 5100000,
          expectedInterest: 100000,
        },
        {
          id: 'regular-savings-24',
          termMonths: 24,
          baseRate: 3.2,
          maxRate: 4.8,
          monthlyDeposit: 220000,
          expectedMaturityAmount: 5400000,
          expectedInterest: 120000,
        },
      ],
    },
  ],
};

function authenticatedView() {
  return render(
    <AuthContext.Provider value={{ isLoggedIn: true, loading: false, role: 'USER' }}>
      <InvestmentCapacityPage />
    </AuthContext.Provider>,
  );
}

describe('금융 실험실 페이지', () => {
  it('로그인하지 않으면 계획 저장 안내를 보여준다', () => {
    const view = render(<InvestmentCapacityPage />);

    expect(view.getByRole('link', { name: '로그인하고 저장' })).toBeTruthy();
    expect(view.getByRole('heading', { name: '현재 재무상태 진단' })).toBeTruthy();
  });

  it('저장된 계획을 불러와 시나리오와 상품을 보여준다', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(plan), {
        status: 200,
        headers: { 'content-type': 'application/json' },
      }),
    );

    const view = authenticatedView();

    await waitFor(() => expect(view.getByRole('button', { name: '수정' })).toBeTruthy());
    expect(view.getByText('현재 재무상태')).toBeTruthy();
    expect(view.getByText('월 실수령액')).toBeTruthy();
    expect(view.getByText('기존 예금·적금')).toBeTruthy();
    expect(view.getByText('총 보유자산')).toBeTruthy();
    expect(view.container.querySelector('.lab-saved-summary')?.textContent).toContain('300만원');
    expect(view.getByRole('button', { name: '수정' })).toBeTruthy();
    expect(view.getByText('안전한 월 운용 가능액')).toBeTruthy();
    expect(view.getAllByText('테스트 적금').length).toBeGreaterThan(0);
    expect(view.getByText('한 곳에 몰지 않는 추천 배분')).toBeTruthy();
    expect(view.getByText('목표 자금 저축')).toBeTruthy();
    expect(view.getByText('장기 여유자금')).toBeTruthy();
    expect(view.queryByText(/금액은 만원 단위로 입력하고/)).toBeNull();

    fireEvent.click(view.getByRole('button', { name: /월 저축 60%/ }));
    expect(view.getByText('금융회사')).toBeTruthy();
    expect(view.getByText('기본 3.00% · 최고 4.50%')).toBeTruthy();
    expect(view.getAllByText('금액 조건 미충족').length).toBeGreaterThan(0);
  });

  it('수정 버튼을 누르면 저장된 재무상태 입력창을 다시 보여준다', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(plan), {
        status: 200,
        headers: { 'content-type': 'application/json' },
      }),
    );

    const view = authenticatedView();

    await waitFor(() => expect(view.getByRole('button', { name: '수정' })).toBeTruthy());
    fireEvent.click(view.getByRole('button', { name: '수정' }));

    expect(view.getByLabelText('월 실수령액')).toBeTruthy();
    expect(view.getByLabelText('바로 사용할 수 있는 현금')).toBeTruthy();
    expect(view.getByLabelText('기존 예금·적금')).toBeTruthy();
  });

  it('목표 금액이 없으면 기간 동안 운용할 월 금액을 보여준다', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          ...plan,
          goalAmount: 0,
          requiredMonthlySaving: 0,
          status: 'GOAL_NOT_SET',
          interpretation: '현재 안전한 월 운용 가능액을 기준으로 비교해보세요.',
        }),
        {
          status: 200,
          headers: { 'content-type': 'application/json' },
        },
      ),
    );

    const view = authenticatedView();

    await waitFor(() => expect(view.getByText('기간 동안 운용할 월 금액')).toBeTruthy());
    expect(view.getByText('가용 금액 기준')).toBeTruthy();
  });

  it('상품 금액을 바꾸면 예상 결과와 남는 운용 가능액을 다시 계산한다', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(plan), {
        status: 200,
        headers: { 'content-type': 'application/json' },
      }),
    );

    const view = authenticatedView();

    await waitFor(() =>
      expect(view.container.querySelector('#product-amount-regular-savings-12')).toBeTruthy(),
    );
    fireEvent.change(view.container.querySelector('#product-amount-regular-savings-12')!, {
      target: { value: '100' },
    });

    expect(view.getByText('남는 월 운용 가능액')).toBeTruthy();
    expect(view.getByText('70만원')).toBeTruthy();
  });

  it('입력한 계획을 CSRF 토큰과 함께 저장한다', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch');
    fetchMock
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ token: 'csrf', headerName: 'X-CSRF-TOKEN' }), {
          status: 200,
          headers: { 'content-type': 'application/json' },
        }),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify(plan), {
          status: 200,
          headers: { 'content-type': 'application/json' },
        }),
      );

    const view = authenticatedView();
    await waitFor(() => expect(view.getByLabelText('생년월일')).toBeTruthy());
    fireEvent.change(view.getByLabelText('생년월일'), { target: { value: '2000-01-01' } });
    fireEvent.change(view.getByLabelText('월 실수령액'), { target: { value: '3000000' } });
    fireEvent.change(view.getByLabelText('목표 금액'), { target: { value: '10000000' } });
    fireEvent.click(view.getByRole('button', { name: '플랜 계산하고 저장' }));

    await waitFor(() => expect(view.getAllByText('테스트 적금').length).toBeGreaterThan(0));
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });
});
