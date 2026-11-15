/** @vitest-environment jsdom */
import { act, cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { PortfolioReviewSimulation } from '../../src/features/portfolio-review/PortfolioReviewSimulation';
import {
  amountInCents,
  weightInBasisPoints,
  simulationWeights,
  simulationAllocation,
  type SimulationAsset,
} from '../../src/features/portfolio-review/simulationAllocation';
import {
  getReviewSimulationAssets,
  analyzeReviewSimulation,
} from '../../src/features/portfolio-review/api';

const comment = {
  id: 9,
  authorNickname: '리뷰어',
  content: '삼성전자 비중을 줄여보세요.',
  createdAt: '2026-11-14T00:00:00Z',
  updatedAt: '2026-11-14T00:00:00Z',
  replies: [],
};
const assets: SimulationAsset[] = [
  { assetId: 1, assetName: '삼성전자', category: 'STOCK', amount: 600000 },
  { assetId: 2, assetName: 'SK하이닉스', category: 'STOCK', amount: 400000 },
];
const analysis = {
  totalAssetCount: 2,
  analyzedAssetCount: 0,
  overallDirection: null,
  overallScore: null,
  positiveImpactScore: 0,
  negativeImpactScore: 0,
  analyzedAt: null,
  overallImpact: '임시 구성 분석 완료',
  impacts: [],
  sources: [],
};
const path = '/api/users/me/portfolio/analysis/shares/3/comments/9/simulation';
function response(value: unknown, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('댓글 제안 임시 포트폴리오', () => {
  it('수정한 구성을 분석한 뒤에도 접히지 않고 명시적으로 원래 구성으로 되돌릴 수 있다', async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      if (String(input).endsWith('/api/auth/csrf'))
        return Promise.resolve(response({ headerName: 'X-XSRF-TOKEN', token: 'csrf' }));
      if (String(input) === path)
        return Promise.resolve(response(init?.method === 'POST' ? analysis : assets));
      throw new Error('보유 내역 저장 API를 호출하면 안 됩니다.');
    });
    vi.stubGlobal('fetch', fetchMock);
    const view = render(<PortfolioReviewSimulation shareId={3} comment={comment} />);
    fireEvent.click(view.getByRole('button', { name: '제안 반영해 보기' }));
    const first = await view.findByRole('spinbutton', { name: '삼성전자 비중' });
    expect(view.getAllByRole('option').map((option) => option.textContent)).toEqual([
      '전체',
      '주식',
      'ETF',
    ]);
    fireEvent.change(first, { target: { value: '' } });
    const submit = view.getByRole('button', { name: '이 구성으로 포트폴리오 분석' });
    expect(submit.hasAttribute('disabled')).toBe(true);
    fireEvent.change(first, { target: { value: '50' } });
    expect(submit.hasAttribute('disabled')).toBe(true);
    fireEvent.change(view.getByLabelText('SK하이닉스 비중'), { target: { value: '50' } });
    fireEvent.click(submit);
    expect(await view.findByText('임시 구성 분석 완료')).toBeTruthy();
    expect(fetchMock).toHaveBeenCalledWith(
      path,
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({
          assets: [
            { assetName: '삼성전자', weight: 50 },
            { assetName: 'SK하이닉스', weight: 50 },
          ],
        }),
      }),
    );
    expect(assets[0].amount).toBe(600000);
    expect(view.queryByText(/600,000원|1,000,000원|검토 금액|현재 → 검토 후/)).toBeNull();
    expect(view.queryByRole('button', { name: '닫기' })).toBeNull();
    fireEvent.click(view.getByRole('heading', { name: '제안 반영하기' }));
    expect(view.getByRole('region', { name: '제안 반영하기' })).toBeTruthy();
    expect(view.getByText('임시 구성 분석 완료')).toBeTruthy();
    expect((first as HTMLInputElement).value).toBe('50');
    fireEvent.change(first, { target: { value: '40' } });
    expect(view.queryByText('임시 구성 분석 완료')).toBeNull();
    expect(submit.hasAttribute('disabled')).toBe(true);
    fireEvent.click(view.getByRole('button', { name: '원래 구성으로 되돌리기' }));
    expect(
      ((await view.findByRole('spinbutton', { name: '삼성전자 비중' })) as HTMLInputElement).value,
    ).toBe('60.00');
    expect(view.queryByText('임시 구성 분석 완료')).toBeNull();
  });

  it('분석 실패 시 수정한 구성을 유지하고 재시도할 수 있다', async () => {
    let attempts = 0;
    vi.stubGlobal(
      'fetch',
      vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
        if (String(input).endsWith('/api/auth/csrf'))
          return Promise.resolve(response({ headerName: 'X-XSRF-TOKEN', token: 'csrf' }));
        if (init?.method !== 'POST') return Promise.resolve(response(assets));
        return Promise.resolve(
          ++attempts === 1
            ? response({ message: '잠시 후 다시 시도해 주세요.' }, 503)
            : response(analysis),
        );
      }),
    );
    const view = render(<PortfolioReviewSimulation shareId={3} comment={comment} />);
    fireEvent.click(view.getByRole('button', { name: '제안 반영해 보기' }));
    await view.findByRole('spinbutton', { name: '삼성전자 비중' });
    fireEvent.click(view.getByRole('button', { name: '이 구성으로 포트폴리오 분석' }));
    expect(await view.findByRole('alert')).toBeTruthy();
    await waitFor(() =>
      expect(
        view.getByRole('button', { name: '이 구성으로 포트폴리오 분석' }).hasAttribute('disabled'),
      ).toBe(false),
    );
    fireEvent.click(view.getByRole('button', { name: '이 구성으로 포트폴리오 분석' }));
    expect(await view.findByText('임시 구성 분석 완료')).toBeTruthy();
  });

  it.each([
    null,
    {},
    [],
    [{ assetName: '삼성전자' }],
    [{ assetName: '삼성전자', weight: null }],
    [{ assetName: '삼성전자', weight: '100' }],
    [{ assetName: '삼성전자', weight: -1 }],
    [{ ...assets[0], category: 'UNKNOWN' }],
    [{ ...assets[0], category: 'DEPOSIT' }],
    [{ ...assets[0], category: 'SAVINGS' }],
    [{ ...assets[0], category: 'REAL_ESTATE' }],
    [{ ...assets[0], category: null }],
    [{ ...assets[0], assetId: undefined }],
    [{ ...assets[0], assetId: null }],
    [{ ...assets[0], assetId: '1' }],
    [{ ...assets[0], assetId: 0 }],
    [{ ...assets[0], amount: null }],
    [{ ...assets[0], amount: '1000' }],
    [{ ...assets[0], amount: 0 }],
    [{ ...assets[0], amount: -1 }],
    [{ ...assets[0], amount: 1_000_000_000_001 }],
  ])('잘못된 임시 구성 응답을 거부한다: %j', async (value) => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(response(value))),
    );
    await expect(getReviewSimulationAssets(3, 9)).rejects.toThrow('임시 포트폴리오 응답 형식');
  });

  it('알 수 없는 분석 방향 응답을 거부한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((input: RequestInfo | URL) =>
        Promise.resolve(
          response(
            String(input).endsWith('/api/auth/csrf')
              ? { headerName: 'X-XSRF-TOKEN', token: 'csrf' }
              : { ...analysis, overallDirection: 'UNKNOWN' },
          ),
        ),
      ),
    );
    await expect(
      analyzeReviewSimulation(3, 9, [{ assetName: '삼성전자', weight: 100 }]),
    ).rejects.toThrow('임시 포트폴리오 분석 응답 형식');
  });
});

describe('비중으로 구성 검토', () => {
  it('종목 검색 오류를 재시도한다', async () => {
    let attempts = 0;
    vi.stubGlobal(
      'fetch',
      vi.fn((input: RequestInfo | URL) =>
        Promise.resolve(
          String(input) === path
            ? response(assets)
            : ++attempts <= 2
              ? response({}, 503)
              : response({ stocks: [] }),
        ),
      ),
    );
    const view = render(<PortfolioReviewSimulation shareId={3} comment={comment} />);
    fireEvent.click(view.getByRole('button', { name: '제안 반영해 보기' }));
    fireEvent.change(await view.findByLabelText('종목 검색'), { target: { value: '검색' } });
    fireEvent.click(await view.findByRole('button', { name: '검색 재시도' }, { timeout: 3000 }));
    expect(
      await view.findByText('검색 결과가 없어요. 다른 검색어나 종류를 선택해 주세요.'),
    ).toBeTruthy();
  });

  it.each([
    ['STOCK', '엔비디아', '엔비디아'],
    ['ETF', 'KODEX 200', 'KODEX 200'],
  ])(
    '%s 추가·폼 제출로는 분석하지 않고 분석 버튼을 눌러야 요청한다',
    async (category, name, analysisName) => {
      const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
        if (String(input).endsWith('/api/auth/csrf'))
          return Promise.resolve(response({ headerName: 'X-XSRF-TOKEN', token: 'csrf' }));
        if (String(input).startsWith('/api/stocks?'))
          return Promise.resolve(
            response({
              stocks: [{ assetId: 3, assetCode: 'TEST', name, category, market: 'NASDAQ' }],
            }),
          );
        if (String(input) !== path) throw new Error('기존 포트폴리오를 저장하면 안 됩니다.');
        return Promise.resolve(response(init?.method === 'POST' ? analysis : assets));
      });
      vi.stubGlobal('fetch', fetchMock);
      const view = render(<PortfolioReviewSimulation shareId={3} comment={comment} />);
      fireEvent.click(view.getByRole('button', { name: '제안 반영해 보기' }));
      await view.findByRole('spinbutton', { name: '삼성전자 비중' });
      fireEvent.change(view.getByLabelText('종류'), { target: { value: category } });
      fireEvent.change(view.getByLabelText('종목 검색'), { target: { value: name } });
      fireEvent.click(await view.findByRole('button', { name: new RegExp(`${name}.*선택`) }));
      fireEvent.change(view.getByLabelText('추가할 비중 (%)'), { target: { value: '50' } });
      expect(document.activeElement).toBe(view.getByLabelText('추가할 비중 (%)'));
      fireEvent.click(view.getByRole('button', { name: '임시 구성에 추가' }));
      expect(view.getByText(`${name} 추가 완료`)).toBeTruthy();
      expect(document.activeElement).toBe(view.getByLabelText('종목 검색'));
      expect(view.getByText('150.00%')).toBeTruthy();
      expect(
        view.getByRole('button', { name: '이 구성으로 포트폴리오 분석' }).hasAttribute('disabled'),
      ).toBe(true);
      fireEvent.change(view.getByLabelText('삼성전자 비중'), { target: { value: '30' } });
      fireEvent.change(view.getByLabelText('SK하이닉스 비중'), { target: { value: '20' } });
      expect(view.getByText('100.00%')).toBeTruthy();
      expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false);
      await act(async () => {
        fireEvent.submit(view.getByLabelText('삼성전자 비중').closest('form')!);
      });
      expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false);
      expect(view.queryByText('임시 구성 분석 완료')).toBeNull();
      fireEvent.click(view.getByRole('button', { name: '이 구성으로 포트폴리오 분석' }));
      await view.findByText('임시 구성 분석 완료');
      expect(fetchMock.mock.calls.filter(([, init]) => init?.method === 'POST')).toHaveLength(1);
      const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST');
      expect(JSON.parse(post![1]!.body as string)).toEqual({
        assets: [
          { assetName: '삼성전자', weight: 30 },
          { assetName: 'SK하이닉스', weight: 20 },
          { assetName: analysisName, weight: 50 },
        ],
      });
      fireEvent.click(view.getByRole('button', { name: `${name} 삭제` }));
      expect(view.queryByText('임시 구성 분석 완료')).toBeNull();
      expect(
        view.getByRole('button', { name: '이 구성으로 포트폴리오 분석' }).hasAttribute('disabled'),
      ).toBe(true);
      fireEvent.click(view.getByRole('button', { name: '원래 구성으로 되돌리기' }));
      expect(view.queryByRole('spinbutton', { name: `${name} 비중` })).toBeNull();
      expect(view.queryByText('임시 구성 분석 완료')).toBeNull();
    },
  );

  it('같은 자산의 중복 추가와 잘못된 비중을 막는다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((input: RequestInfo | URL) =>
        Promise.resolve(
          response(
            String(input).startsWith('/api/stocks?')
              ? {
                  stocks: [
                    {
                      assetId: 1,
                      assetCode: '005930',
                      name: '삼성전자',
                      category: 'STOCK',
                      market: 'KOSPI',
                    },
                    {
                      assetId: 3,
                      assetCode: 'NEW',
                      name: '새 종목',
                      category: 'STOCK',
                      market: 'KOSPI',
                    },
                  ],
                }
              : assets,
          ),
        ),
      ),
    );
    const view = render(<PortfolioReviewSimulation shareId={3} comment={comment} />);
    fireEvent.click(view.getByRole('button', { name: '제안 반영해 보기' }));
    await view.findByRole('spinbutton', { name: '삼성전자 비중' });
    fireEvent.change(view.getByLabelText('종목 검색'), { target: { value: '삼성전자' } });
    expect(view.queryByLabelText('추가할 비중 (%)')).toBeNull();
    expect(
      (await view.findByRole('button', { name: /삼성전자.*이미 포함됨/ })).hasAttribute('disabled'),
    ).toBe(true);
    fireEvent.click(view.getByRole('button', { name: /새 종목.*선택/ }));
    fireEvent.change(view.getByLabelText('추가할 비중 (%)'), { target: { value: '-1' } });
    expect(view.getByLabelText('추가할 비중 (%)').getAttribute('aria-invalid')).toBe('true');
    expect(view.getByText('0% 초과, 100% 이하로 소수점 두 자리까지 입력해 주세요.')).toBeTruthy();
    expect(view.getByRole('button', { name: '임시 구성에 추가' }).hasAttribute('disabled')).toBe(
      true,
    );
  });

  it('소수 금액과 큰 금액도 합계 100%로 계산한다', () => {
    const draft = ['0.01', '0.01', '0.01'].map((amount, index) => ({
      assetId: index + 1,
      assetName: `자산${index}`,
      category: 'STOCK' as const,
      amount,
    }));
    expect(simulationAllocation(draft)).toEqual({ total: 0.03, weights: [33.34, 33.33, 33.33] });
    expect(
      simulationAllocation(draft.map((asset) => ({ ...asset, amount: '1000000000000' })))?.weights,
    ).toEqual([33.34, 33.33, 33.33]);
    expect(
      simulationAllocation([
        { ...draft[0], amount: '10' },
        { ...draft[1], amount: '0.01' },
      ])?.weights,
    ).toEqual([99.9, 0.1]);
  });

  it.each(['', '0', '-1', 'NaN', '1e6', '0.001', '1000000000001'])(
    '금액 %s는 계산하지 않는다',
    (value) => {
      expect(amountInCents(value)).toBeNull();
    },
  );
});

describe('직접 입력한 비중 검증', () => {
  it.each(['', '0', '-1', 'NaN', '1e2', '0.001', '100.01'])('비중 %s를 거부한다', (value) => {
    expect(weightInBasisPoints(value)).toBeNull();
  });

  it('소수점 비중의 합계를 정확히 계산하고 입력값을 재분배하지 않는다', () => {
    const draft = ['33.34', '33.33', '33.33'].map((weight, index) => ({
      assetId: index + 1,
      assetName: `종목${index}`,
      category: 'STOCK' as const,
      weight,
    }));
    expect(simulationWeights(draft)).toEqual({ total: 100, weights: [33.34, 33.33, 33.33] });
    expect(simulationWeights(draft.slice(0, 2))).toEqual({ total: 66.67, weights: [33.34, 33.33] });
    expect(simulationWeights(draft.map((asset) => ({ ...asset, weight: '50' })))).toEqual({
      total: 150,
      weights: [50, 50, 50],
    });
    expect(simulationWeights([{ ...draft[0], weight: '100' }])).toEqual({
      total: 100,
      weights: [100],
    });
    expect(simulationWeights([{ ...draft[0], weight: '0' }])).toBeNull();
    expect(simulationWeights([])).toBeNull();
  });
});
