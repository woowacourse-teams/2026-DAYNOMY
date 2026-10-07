/** @vitest-environment jsdom */
import { act, cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { InvestmentRecordsPage } from '../../src/features/league/InvestmentRecordsPage';
import * as api from '../../src/features/league/sharedPortfolioApi';
import * as leagueApi from '../../src/features/league/api';
import * as portfolioApi from '../../src/features/portfolio/api';

vi.mock('../../src/features/league/sharedPortfolioApi', async (importOriginal) => ({
  ...(await importOriginal<typeof api>()),
  getSharedPortfolio: vi.fn(),
  importSharedHoldings: vi.fn(),
  changeSharedHoldingVisibility: vi.fn(),
}));
vi.mock('../../src/features/league/api', async (importOriginal) => ({
  ...(await importOriginal<typeof leagueApi>()),
  getTransactions: vi.fn(),
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
  vi.mocked(leagueApi.getTransactions).mockResolvedValue([]);
  vi.mocked(api.getSharedPortfolio).mockResolvedValue(empty);
  vi.mocked(portfolioApi.getSavedPortfolio).mockResolvedValue({
    holdings: [{ ...stock, name: stock.assetName, quantity: 2, averagePurchasePrice: 70000 }],
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
});
function renderPage() {
  return render(
    <MemoryRouter>
      <InvestmentRecordsPage />
    </MemoryRouter>,
  );
}

describe('원본 가져오기와 숨김만 허용하는 공유 포트폴리오', () => {
  it('원본 자산을 선택해 가져오고 원본 저장소는 변경하지 않는다', async () => {
    vi.mocked(api.importSharedHoldings).mockResolvedValue(portfolio);
    const original = localStorage.getItem(storageKey);
    const view = renderPage();
    fireEvent.click(await view.findByRole('button', { name: '내 포트폴리오에서 가져오기' }));
    expect(await view.findByText('원본: 2주 · 평균 70,000원')).toBeTruthy();
    fireEvent.click(view.getByRole('button', { name: '1종목 가져오기' }));
    await waitFor(() =>
      expect(api.importSharedHoldings).toHaveBeenCalledWith(
        [expect.objectContaining({ assetId: 1, quantity: 2 })],
        false,
      ),
    );
    expect(await view.findByRole('heading', { name: '공유용 자산 1개' })).toBeTruthy();
    expect(localStorage.getItem(storageKey)).toBe(original);
  });

  it('서버 원본 조회 실패를 빈 자산으로 숨기지 않고 다시 가져올 수 있다', async () => {
    vi.mocked(portfolioApi.getSavedPortfolio)
      .mockRejectedValueOnce(new Error('서버 원본 연결 실패'))
      .mockResolvedValueOnce({
        holdings: [{ ...stock, name: stock.assetName, quantity: 2, averagePurchasePrice: 70000 }],
      });
    const view = renderPage();
    fireEvent.click(await view.findByRole('button', { name: '내 포트폴리오에서 가져오기' }));
    expect(await view.findByRole('alert')).toHaveProperty('textContent', '서버 원본 연결 실패');
    expect(view.queryByText('원본에 등록된 자산이 없어요.')).toBeNull();
    expect(api.importSharedHoldings).not.toHaveBeenCalled();
    fireEvent.click(view.getByRole('button', { name: '내 포트폴리오에서 가져오기' }));
    expect(await view.findByText('원본: 2주 · 평균 70,000원')).toBeTruthy();
  });

  it('숨김 저장 실패 시 기존 표시 상태를 유지하고 재시도한다', async () => {
    vi.mocked(api.getSharedPortfolio).mockResolvedValue(portfolio);
    vi.mocked(api.changeSharedHoldingVisibility)
      .mockRejectedValueOnce(new Error('저장 실패'))
      .mockResolvedValueOnce({ ...portfolio, holdings: [{ ...holding, hidden: true }] });
    const view = renderPage();
    fireEvent.click(await view.findByRole('button', { name: '숨기기' }));
    expect(await view.findByRole('alert')).toHaveProperty('textContent', '저장 실패');
    expect(view.getByRole('button', { name: '숨기기' })).toBeTruthy();
    expect(view.getByText('2주')).toBeTruthy();
    fireEvent.click(view.getByRole('button', { name: '숨기기' }));
    await view.findByRole('button', { name: '숨김 해제' });
    expect(api.changeSharedHoldingVisibility).toHaveBeenLastCalledWith(1, true);
    expect(api.changeSharedHoldingVisibility).toHaveBeenCalledTimes(2);
  });

  it('직접 추가·편집·삭제 입력을 제공하지 않고 원본이 없으면 원본 입력을 안내한다', async () => {
    vi.mocked(portfolioApi.getSavedPortfolio).mockResolvedValue({ holdings: [] });
    const view = renderPage();
    fireEvent.click(await view.findByRole('button', { name: '내 포트폴리오에서 가져오기' }));
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
    fireEvent.click(await view.findByRole('button', { name: '내 포트폴리오에서 가져오기' }));
    fireEvent.click(await view.findByRole('checkbox', { name: /삼성전자/ }));
    expect(view.getByText('공유용: 3주 · 평균 70,000원')).toBeTruthy();
    expect(view.getByRole('button', { name: '1종목 가져오기' })).toHaveProperty('disabled', true);
    fireEvent.click(view.getByRole('checkbox', { name: /기존 1종목/ }));
    fireEvent.click(view.getByRole('button', { name: '1종목 가져오기' }));
    await waitFor(() =>
      expect(api.importSharedHoldings).toHaveBeenCalledWith(
        [expect.objectContaining({ quantity: 2, averagePurchasePrice: 70000 })],
        true,
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
    fireEvent.click(await view.findByRole('button', { name: '숨기기' }));
    fireEvent.click(await view.findByRole('button', { name: '숨김 해제' }));
    await view.findByRole('button', { name: '숨기기' });
    expect(api.changeSharedHoldingVisibility).toHaveBeenLastCalledWith(1, false);
    expect(view.getAllByText('+10.00%')).toHaveLength(2);
    expect(view.getByText('기존 판단')).toBeTruthy();
    expect(view.queryByRole('button', { name: '수정' })).toBeNull();
    expect(view.queryByRole('button', { name: '공유용에서 삭제' })).toBeNull();
    expect(view.getByRole('heading', { name: '공유용 자산 1개' })).toBeTruthy();
    expect(localStorage.getItem(storageKey)).toBe(original);
  });

  it('조회 실패를 빈 자산으로 숨기지 않고 재시도를 제공한다', async () => {
    vi.mocked(api.getSharedPortfolio)
      .mockRejectedValueOnce(new Error('연결 실패'))
      .mockResolvedValueOnce(empty);
    const view = renderPage();
    expect(await view.findByRole('alert')).toHaveProperty('textContent', '연결 실패');
    expect(view.queryByRole('button', { name: '내 포트폴리오에서 가져오기' })).toBeNull();
    fireEvent.click(view.getByRole('button', { name: '다시 불러오기' }));
    expect(await view.findByRole('button', { name: '내 포트폴리오에서 가져오기' })).toBeTruthy();
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
    fireEvent.click(await view.findByRole('button', { name: '내 포트폴리오에서 가져오기' }));
    fireEvent.click(await view.findByRole('checkbox', { name: /삼성전자/ }));
    fireEvent.click(view.getByRole('checkbox', { name: /기존 1종목/ }));
    fireEvent.click(view.getByRole('button', { name: '1종목 가져오기' }));
    expect(view.getByRole('button', { name: '가져오는 중…' })).toHaveProperty('disabled', true);
    expect(view.getByRole('button', { name: '숨기기' })).toHaveProperty('disabled', true);
    expect(api.importSharedHoldings).toHaveBeenCalledTimes(1);
    await act(async () => resolveImport(portfolio));
    expect(view.getByRole('button', { name: '숨기기' })).toHaveProperty('disabled', false);
  });
});
