/** @vitest-environment jsdom */
import { act, cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { BrowserRouter, MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SharedPortfolioPage } from '../../src/features/league/SharedPortfolioPage';
import * as api from '../../src/features/league/sharedPortfolioApi';
import { AuthContext } from '../../src/auth/AuthContext';
import * as leagueApi from '../../src/features/league/api';
import * as portfolioApi from '../../src/features/portfolio/api';
import { trackEvent } from '../../src/analytics';

vi.mock('../../src/analytics', () => ({ trackEvent: vi.fn() }));

vi.mock('../../src/features/league/sharedPortfolioApi', async (importOriginal) => ({
  ...(await importOriginal<typeof api>()),
  getSharedPortfolio: vi.fn(),
  importSharedHoldings: vi.fn(),
  changeSharedHoldingVisibility: vi.fn(),
  removeSharedHolding: vi.fn(),
}));
vi.mock('../../src/features/league/api', async (importOriginal) => ({
  ...(await importOriginal<typeof leagueApi>()),
  getInvestmentDecisions: vi.fn(),
  getMyInvestorProfile: vi.fn(),
  recordDecision: vi.fn(),
}));
vi.mock('../../src/features/portfolio/api', () => ({ getSavedPortfolio: vi.fn() }));
const stock = {
  assetId: 1,
  assetCode: '005930',
  assetName: '삼성전자',
  category: 'STOCK' as const,
  market: 'KOSPI' as const,
};
const holding = {
  ...stock,
  quantity: 2,
  averagePurchasePrice: 70000,
  hidden: false,
  pendingReason: false,
  reason: '기존 판단',
  baseDate: '2026-10-02',
  closePrice: 77000,
  evaluationAmount: 154000,
  returnRate: 10,
};
const empty = {
  holdings: [],
  totalPurchaseAmount: 0,
  totalEvaluationAmount: 0,
  totalReturnRate: null,
  pricesComplete: true,
};
const portfolio = {
  ...empty,
  holdings: [holding],
  totalPurchaseAmount: 140000,
  totalEvaluationAmount: 154000,
  totalReturnRate: 10,
};
const storageKey = 'daynomy:portfolio-holdings:v1';
beforeEach(() => {
  vi.mocked(leagueApi.getInvestmentDecisions).mockResolvedValue([]);
  vi.mocked(leagueApi.getMyInvestorProfile).mockResolvedValue(null);
  vi.mocked(api.getSharedPortfolio).mockResolvedValue(empty);
  vi.mocked(portfolioApi.getSavedPortfolio).mockResolvedValue({
    holdings: [{ ...stock, name: stock.assetName, quantity: 2, averagePurchasePrice: 70000 }],
    totalRealizedProfitLoss: 0,
  });
  localStorage.setItem(
    storageKey,
    JSON.stringify([{ ...stock, name: stock.assetName, quantity: 2, averagePurchasePrice: 70000 }]),
  );
});
afterEach(() => {
  cleanup();
  vi.resetAllMocks();
  localStorage.clear();
  window.history.replaceState(null, '', '/');
});
function renderPage() {
  return render(
    <MemoryRouter>
      <AuthContext.Provider
        value={{ isLoggedIn: true, loading: false, role: 'USER', nickname: '초보' }}
      >
        <SharedPortfolioPage />
      </AuthContext.Provider>
    </MemoryRouter>,
  );
}

async function importButton(view: ReturnType<typeof render>) {
  const button = await view.findByRole('button', { name: '종목 추가' });
  await waitFor(() => expect(button).toHaveProperty('disabled', false));
  return button;
}

describe('원본 가져오기와 숨김만 허용하는 공유 포트폴리오', () => {
  it('자동 동기화 중 화면으로 돌아오면 서버의 최신 종목을 다시 읽는다', async () => {
    vi.mocked(api.getSharedPortfolio).mockResolvedValueOnce(empty).mockResolvedValueOnce(portfolio);
    const view = renderPage();
    await view.findByRole('heading', { name: '등록한 종목 0개' });
    fireEvent.focus(window);
    expect(await view.findByRole('heading', { name: '등록한 종목 1개' })).toBeTruthy();
  });
  it('원본 변경은 항상 동기화하며 비공개 종목도 통합 수익률에 포함함을 안내한다', async () => {
    const view = renderPage();
    expect(
      await view.findByText('포트폴리오 자동 동기화 · 비공개 종목도 수익률에 포함'),
    ).toBeTruthy();
    expect(view.getByText(/비공개 종목도/)).toBeTruthy();
    expect(view.queryByRole('checkbox', { name: /자동 반영/ })).toBeNull();
  });
  it('분석 전송이 실패해도 원본 자산을 가져오고 원본 저장소는 변경하지 않는다', async () => {
    vi.mocked(trackEvent).mockImplementation(() => {
      throw new Error('SDK failed');
    });
    vi.mocked(api.importSharedHoldings).mockResolvedValue(portfolio);
    const original = localStorage.getItem(storageKey);
    const view = renderPage();
    fireEvent.click(await importButton(view));
    expect(await view.findByText('2주 · 평균 70,000원')).toBeTruthy();
    expect(view.getByRole('button', { name: '1개 종목 추가' })).toHaveProperty('disabled', true);
    fireEvent.change(view.getByLabelText('삼성전자 보유 이유'), {
      target: { value: '장기 성장 기대' },
    });
    fireEvent.click(view.getByRole('button', { name: '1개 종목 추가' }));
    await waitFor(() =>
      expect(api.importSharedHoldings).toHaveBeenCalledWith(
        [expect.objectContaining({ assetId: 1, quantity: 2 })],
        false,
        {
          1: {
            reason: '장기 성장 기대',
            expectedHoldingPeriod: null,
            expectedChange: '',
            invalidationCondition: '',
            maximumAcceptableLossRate: null,
          },
        },
      ),
    );
    expect(await view.findByRole('heading', { name: '등록한 종목 1개' })).toBeTruthy();
    expect(trackEvent).toHaveBeenCalledWith(
      'league_action',
      expect.objectContaining({ action: 'import_holdings', status: 'success', result_count: 1 }),
    );
    expect(JSON.stringify(vi.mocked(trackEvent).mock.calls)).not.toMatch(
      /삼성전자|장기 성장 기대|70000/,
    );
    expect(localStorage.getItem(storageKey)).toBe(original);
    expect(view.getByRole('link', { name: '공개 설정' }).getAttribute('href')).toBe(
      '/portfolio/publication',
    );
    expect(view.queryByRole('region', { name: '공유 방법과 리그 참여' })).toBeNull();
    expect(view.queryByRole('link', { name: '공개 설정 확인 →' })).toBeNull();
  });

  it('서버 원본 조회 실패를 빈 자산으로 숨기지 않고 다시 가져올 수 있다', async () => {
    vi.mocked(portfolioApi.getSavedPortfolio)
      .mockRejectedValueOnce(new Error('서버 원본 연결 실패'))
      .mockResolvedValueOnce({
        holdings: [{ ...stock, name: stock.assetName, quantity: 2, averagePurchasePrice: 70000 }],
        totalRealizedProfitLoss: 0,
      });
    const view = renderPage();
    fireEvent.click(await importButton(view));
    expect(await view.findByRole('alert')).toHaveProperty('textContent', '서버 원본 연결 실패');
    expect(view.queryByText('원본에 등록된 자산이 없어요.')).toBeNull();
    expect(api.importSharedHoldings).not.toHaveBeenCalled();
    fireEvent.click(await importButton(view));
    expect(await view.findByText('2주 · 평균 70,000원')).toBeTruthy();
  });

  it('숨김 저장 실패 시 기존 표시 상태를 유지하고 재시도한다', async () => {
    vi.mocked(api.getSharedPortfolio).mockResolvedValue(portfolio);
    vi.mocked(api.changeSharedHoldingVisibility)
      .mockRejectedValueOnce(new Error('저장 실패'))
      .mockResolvedValueOnce({ ...portfolio, holdings: [{ ...holding, hidden: true }] });
    const view = renderPage();
    const choice = await view.findByRole('button', { name: '삼성전자 숨기기' });
    fireEvent.click(choice);
    expect(await view.findByRole('alert')).toHaveProperty('textContent', '저장 실패');
    expect(choice).toHaveProperty('textContent', '숨기기');
    expect(view.getByText('2주 · 평균 매수가 70,000원')).toBeTruthy();
    fireEvent.click(choice);
    await waitFor(() => expect(choice).toHaveProperty('textContent', '공개하기'));
    expect(api.changeSharedHoldingVisibility).toHaveBeenLastCalledWith(1, true);
    expect(api.changeSharedHoldingVisibility).toHaveBeenCalledTimes(2);
  });

  it('직접 추가·편집·삭제 입력을 제공하지 않고 원본이 없으면 원본 입력을 안내한다', async () => {
    vi.mocked(portfolioApi.getSavedPortfolio).mockResolvedValue({
      holdings: [],
      totalRealizedProfitLoss: 0,
    });
    const view = renderPage();
    fireEvent.click(await importButton(view));
    expect(await view.findByRole('link', { name: '포트폴리오 입력하기' })).toBeTruthy();
    expect(view.queryByRole('button', { name: '종목 직접 추가' })).toBeNull();
    expect(view.queryByLabelText('종목 검색')).toBeNull();
    expect(view.queryByLabelText('수량 (주)')).toBeNull();
    expect(view.queryByRole('button', { name: '공유용에서 삭제' })).toBeNull();
    expect(api.importSharedHoldings).not.toHaveBeenCalled();
  });

  it('기존 종목 재가져오기는 확인을 요구하고 원본 값을 사용한다', async () => {
    vi.mocked(api.getSharedPortfolio).mockResolvedValue({
      ...portfolio,
      holdings: [{ ...holding, quantity: 3 }],
    });
    vi.mocked(api.importSharedHoldings).mockResolvedValue(portfolio);
    const view = renderPage();
    fireEvent.click(await importButton(view));
    await view.findByRole('heading', { name: '종목 추가' });
    fireEvent.click(view.getByRole('checkbox', { name: /삼성전자/ }));
    expect(view.getByText('리그: 3주 · 평균 70,000원')).toBeTruthy();
    expect(view.getByRole('button', { name: '1개 종목 추가' })).toHaveProperty('disabled', true);
    fireEvent.click(view.getByRole('checkbox', { name: /등록된 1개 종목/ }));
    fireEvent.click(view.getByRole('button', { name: '1개 종목 추가' }));
    await waitFor(() =>
      expect(api.importSharedHoldings).toHaveBeenCalledWith(
        [expect.objectContaining({ quantity: 2, averagePurchasePrice: 70000 })],
        true,
        {},
      ),
    );
  });

  it('숨김과 해제는 자산·성과·원본을 보존하고 수정·삭제 버튼은 없다', async () => {
    vi.mocked(api.getSharedPortfolio).mockResolvedValue(portfolio);
    vi.mocked(api.changeSharedHoldingVisibility)
      .mockResolvedValueOnce({ ...portfolio, holdings: [{ ...holding, hidden: true }] })
      .mockResolvedValueOnce(portfolio);
    const original = localStorage.getItem(storageKey);
    const view = renderPage();
    const choice = await view.findByRole('button', { name: '삼성전자 숨기기' });
    fireEvent.click(choice);
    await waitFor(() => expect(choice).toHaveProperty('textContent', '공개하기'));
    fireEvent.click(choice);
    await waitFor(() => expect(choice).toHaveProperty('textContent', '숨기기'));
    expect(api.changeSharedHoldingVisibility).toHaveBeenLastCalledWith(1, false);
    expect(view.getByText('2주 · 평균 매수가 70,000원')).toBeTruthy();
    expect(view.container.querySelector('.shared-reason-preview')).toHaveProperty(
      'textContent',
      '기존 판단',
    );
    expect(view.queryByRole('button', { name: '수정' })).toBeNull();
    expect(view.queryByRole('button', { name: '공유용에서 삭제' })).toBeNull();
    expect(view.getByRole('heading', { name: '등록한 종목 1개' })).toBeTruthy();
    expect(localStorage.getItem(storageKey)).toBe(original);
  });

  it('조회 실패를 빈 자산으로 숨기지 않고 재시도를 제공한다', async () => {
    vi.mocked(api.getSharedPortfolio)
      .mockRejectedValueOnce(new Error('연결 실패'))
      .mockResolvedValueOnce(empty);
    const view = renderPage();
    expect(await view.findByRole('alert')).toHaveProperty('textContent', '연결 실패');
    expect(view.queryByRole('button', { name: '종목 추가' })).toBeNull();
    fireEvent.click(view.getByRole('button', { name: '다시 불러오기' }));
    expect(await importButton(view)).toBeTruthy();
  });

  it('가져오기 처리 중 중복 저장과 숨김 변경을 막는다', async () => {
    let resolveImport!: (value: typeof portfolio) => void;
    vi.mocked(api.getSharedPortfolio).mockResolvedValue(portfolio);
    vi.mocked(api.importSharedHoldings).mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveImport = resolve;
        }),
    );
    const view = renderPage();
    fireEvent.click(await importButton(view));
    await view.findByRole('heading', { name: '종목 추가' });
    fireEvent.click(view.getByRole('checkbox', { name: /삼성전자/ }));
    fireEvent.click(view.getByRole('checkbox', { name: /등록된 1개 종목/ }));
    fireEvent.click(view.getByRole('button', { name: '1개 종목 추가' }));
    expect(view.getByRole('button', { name: '추가 중…' })).toHaveProperty('disabled', true);
    expect(view.queryByRole('button', { name: '투자 기록' })).toBeNull();
    expect(view.getByRole('checkbox', { name: /삼성전자/ }).closest('fieldset')).toHaveProperty(
      'disabled',
      true,
    );
    expect(api.importSharedHoldings).toHaveBeenCalledTimes(1);
    await act(async () => resolveImport(portfolio));
    expect(view.getByRole('button', { name: '삼성전자 숨기기' })).toHaveProperty('disabled', false);
  });

  it('종목을 선택한 상태와 실제 공개 범위를 구분한다', async () => {
    vi.mocked(api.getSharedPortfolio).mockResolvedValue(portfolio);
    vi.mocked(leagueApi.getMyInvestorProfile).mockResolvedValue({
      publicId: 'my-profile',
      displayName: '초보',
      bio: '',
      experienceLevel: 'BEGINNER',
      riskProfile: 'BALANCED',
      profilePublic: true,
      leagueEnabled: true,
      detailPublic: false,
      allocationPublic: false,
      leagueEnabledAt: null,
    });
    const view = renderPage();
    await view.findByText('주식 · 005930 · 비공개');
    expect(view.getByRole('button', { name: '삼성전자 숨기기' })).toHaveProperty(
      'textContent',
      '숨기기',
    );
    expect(view.getByText('주식 · 005930 · 비공개')).toBeTruthy();
    expect(view.getByRole('link', { name: '공개 설정' }).getAttribute('href')).toBe(
      '/portfolio/publication',
    );
    fireEvent.click(await importButton(view));
    await view.findByRole('heading', { name: '종목 추가' });
    expect(view.queryByRole('heading', { name: '등록한 종목 1개' })).toBeNull();
    fireEvent.click(view.getByRole('button', { name: '취소' }));
    expect(view.getByRole('button', { name: '삼성전자 숨기기' })).toHaveProperty(
      'textContent',
      '숨기기',
    );
  });

  it('공개 상태 조회 실패를 비공개로 표시하지 않고 종목 관리와 설정 확인을 제공한다', async () => {
    vi.mocked(api.getSharedPortfolio).mockResolvedValue(portfolio);
    vi.mocked(leagueApi.getMyInvestorProfile).mockRejectedValue(new Error('연결 실패'));
    const view = renderPage();
    expect(await view.findByRole('button', { name: '다시 확인' })).toBeTruthy();
    expect(view.queryByText('현재 비공개')).toBeNull();
    expect(view.getByText('주식 · 005930 · 공개 확인 필요')).toBeTruthy();
    expect(view.queryByText('주식 · 005930 · 비공개')).toBeNull();
    expect(await importButton(view)).toHaveProperty('disabled', false);
    expect(view.queryByRole('radio', { name: /나만 보기/ })).toBeNull();
    expect(view.queryByRole('link', { name: '다른 사람에게 보이는 내 화면 →' })).toBeNull();
  });

  it.each([
    { profilePublic: false, detailPublic: true, status: '현재 비공개', holdingStatus: '비공개' },
    {
      profilePublic: true,
      detailPublic: true,
      status: '프로필·성과와 선택한 1개 종목 공개 중',
      holdingStatus: '공개 중',
    },
  ])(
    '프로필 공개 $profilePublic, 종목 공개 $detailPublic의 실제 상태를 표시한다',
    async ({ profilePublic, detailPublic, holdingStatus }) => {
      vi.mocked(api.getSharedPortfolio).mockResolvedValue(portfolio);
      vi.mocked(leagueApi.getMyInvestorProfile).mockResolvedValue({
        publicId: 'my-profile',
        displayName: '초보',
        bio: '',
        experienceLevel: 'BEGINNER',
        riskProfile: 'BALANCED',
        profilePublic,
        leagueEnabled: true,
        detailPublic,
        allocationPublic: false,
        leagueEnabledAt: null,
      });
      const view = renderPage();
      expect(await view.findByText(`주식 · 005930 · ${holdingStatus}`)).toBeTruthy();
      expect(view.getByRole('link', { name: '공개 설정' })).toBeTruthy();
      fireEvent.click(await importButton(view));
      await view.findByRole('heading', { name: '종목 추가' });
      expect(Boolean(view.queryByText('추가한 종목과 보유 이유가 공개됩니다.'))).toBe(
        profilePublic && detailPublic,
      );
    },
  );

  it('가격이 누락된 전체 평가금액을 0원이나 부분 합계로 보여주지 않는다', async () => {
    vi.mocked(api.getSharedPortfolio).mockResolvedValue({
      ...portfolio,
      pricesComplete: false,
      totalEvaluationAmount: 0,
      totalReturnRate: null,
      holdings: [{ ...holding, evaluationAmount: null, returnRate: null, baseDate: null }],
    });
    const view = renderPage();
    await view.findByRole('heading', { name: '등록한 종목 1개' });
    expect(view.queryByText('평가금액')).toBeNull();
    expect(view.queryByText('0원')).toBeNull();
    expect(view.queryByText('집계 전')).toBeNull();
  });
  it('비공개 종목은 보유 이유 없이 참여할 수 있고 이유 저장 상태도 갱신한다', async () => {
    vi.mocked(api.getSharedPortfolio).mockResolvedValue({
      ...portfolio,
      holdings: [{ ...holding, hidden: true, reason: '' }],
    });
    vi.mocked(leagueApi.recordDecision).mockResolvedValue({
      id: 7,
      assetId: 1,
      assetCode: '005930',
      assetName: '삼성전자',
      category: 'STOCK',
      transactionType: 'HOLD',
      quantity: 2,
      unitPrice: 70000,
      fee: 0,
      tradedOn: '2026-10-09',
      reason: '장기 성장',
      expectedHoldingPeriod: null,
      expectedChange: '',
      invalidationCondition: '',
      maximumAcceptableLossRate: null,
      writtenAfterTrade: true,
      reviews: [],
    });
    const view = renderPage();
    await view.findByRole('button', { name: '삼성전자 보유 이유 작성' });
    expect(view.queryByText('보유 이유 미작성')).toBeNull();
    expect(await view.findByRole('link', { name: '리그 참여하기' })).toBeTruthy();
    const writeReason = await view.findByRole('button', { name: '삼성전자 보유 이유 작성' });
    await waitFor(() => expect(writeReason).toHaveProperty('disabled', false));
    fireEvent.click(writeReason);
    fireEvent.change(view.getByRole('textbox', { name: '보유 이유' }), {
      target: { value: '장기 성장' },
    });
    fireEvent.click(view.getByRole('button', { name: '보유 이유 저장' }));
    await view.findByText('장기 성장');
    await waitFor(() => expect(view.queryByText('보유 이유 미작성')).toBeNull());
    expect(await view.findByRole('link', { name: '리그 참여하기' })).toBeTruthy();
    expect(leagueApi.recordDecision).toHaveBeenCalledWith(
      expect.objectContaining({
        assetId: 1,
        decision: expect.objectContaining({
          reason: '장기 성장',
          expectedHoldingPeriod: null,
          maximumAcceptableLossRate: null,
        }),
      }),
    );
  });
});

it.each([false, true])(
  '가져오기 성공 이벤트는 종목 관리에서 한 번 기록한 뒤 이동한다 (덮어쓰기: %s)',
  async (overwrite) => {
    window.history.replaceState(null, '', '/league/portfolio#select');
    if (overwrite) vi.mocked(api.getSharedPortfolio).mockResolvedValue(portfolio);
    vi.mocked(api.importSharedHoldings)
      .mockRejectedValueOnce(new Error('가져오기 실패'))
      .mockResolvedValueOnce(portfolio);
    const successLocations: string[] = [];
    vi.mocked(trackEvent).mockImplementation((name, params) => {
      if (
        name === 'league_action' &&
        params?.action === 'import_holdings' &&
        params.status === 'success'
      ) {
        successLocations.push(window.location.pathname);
      }
    });
    const view = render(
      <BrowserRouter>
        <Routes>
          <Route path="/league/portfolio" element={<SharedPortfolioPage />} />
          <Route path="/portfolio/publication" element={<h1>참여 설정 화면</h1>} />
        </Routes>
      </BrowserRouter>,
    );
    await view.findByRole('heading', { name: '종목 추가' });
    if (overwrite) {
      fireEvent.click(view.getByRole('checkbox', { name: /삼성전자/ }));
      fireEvent.click(view.getByRole('checkbox', { name: /등록된 1개 종목/ }));
    } else {
      fireEvent.change(view.getByLabelText('삼성전자 보유 이유'), {
        target: { value: '장기 성장 기대' },
      });
    }
    const submit = view.getByRole('button', { name: '1개 종목 추가' });
    await waitFor(() => expect(submit).toHaveProperty('disabled', false));
    fireEvent.click(submit);
    expect(await view.findByRole('alert')).toHaveProperty('textContent', '가져오기 실패');
    expect(window.location.pathname).toBe('/league/portfolio');
    expect(successLocations).toEqual([]);
    await waitFor(() => expect(submit).toHaveProperty('disabled', false));
    fireEvent.click(submit);
    await view.findByRole('heading', { name: '참여 설정 화면' });
    expect(window.location.pathname + window.location.search).toBe(
      '/portfolio/publication?join=true',
    );
    expect(successLocations).toEqual(['/league/portfolio']);
    expect(trackEvent).toHaveBeenCalledWith('league_action', {
      surface: 'holdings',
      action: 'import_holdings',
      status: 'success',
      result_count: 1,
      overwrite_existing: overwrite,
    });
  },
);

it('처음 참여하는 사용자는 종목과 보유 이유 준비를 마친 뒤 참여 설정으로 이동한다', async () => {
  vi.mocked(api.getSharedPortfolio).mockResolvedValue(portfolio);
  const view = renderPage();
  expect(await view.findByRole('link', { name: '리그 참여하기' })).toHaveProperty(
    'href',
    expect.stringContaining('/portfolio/publication?join=true'),
  );
  expect(view.queryByRole('link', { name: '공개 프로필 보기' })).toBeNull();
  expect(view.queryByRole('link', { name: '투자 기록·복기' })).toBeNull();
});

it('종목 빼기는 확인 후 실행하고 실패 시 종목과 원본을 보존한 채 재시도한다', async () => {
  vi.mocked(api.getSharedPortfolio).mockResolvedValue(portfolio);
  vi.mocked(api.removeSharedHolding)
    .mockRejectedValueOnce(new Error('삭제 실패'))
    .mockResolvedValueOnce(empty);
  const original = localStorage.getItem(storageKey);
  const view = renderPage();
  fireEvent.click(await view.findByRole('button', { name: '삼성전자 리그에서 삭제' }));
  expect(view.getByText(/마지막 종목을 삭제하면 리그 참여가 종료됩니다/)).toBeTruthy();
  expect(api.removeSharedHolding).not.toHaveBeenCalled();
  fireEvent.click(view.getByRole('button', { name: '취소' }));
  expect(api.removeSharedHolding).not.toHaveBeenCalled();
  fireEvent.click(view.getByRole('button', { name: '삼성전자 리그에서 삭제' }));
  fireEvent.click(view.getByRole('button', { name: '삭제 확인' }));
  await view.findByText('삭제 실패');
  expect(view.getByRole('heading', { name: '삼성전자' })).toBeTruthy();
  fireEvent.click(view.getByRole('button', { name: '삭제 확인' }));
  await view.findByRole('heading', { name: '등록한 종목 0개' });
  expect(api.removeSharedHolding).toHaveBeenCalledWith(1);
  expect(view.queryByRole('heading', { name: '삼성전자' })).toBeNull();
  expect(localStorage.getItem(storageKey)).toBe(original);
});
