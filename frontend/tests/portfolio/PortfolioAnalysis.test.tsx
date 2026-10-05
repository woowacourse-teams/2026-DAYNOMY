/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { PortfolioAnalysis } from '../../src/features/portfolio/components/PortfolioAnalysis';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

const assets = [
  { assetName: '삼성전자', weight: 60 },
  { assetName: 'SK하이닉스', weight: 40 },
];

const analysisResponse = {
  totalAssetCount: 2,
  analyzedAssetCount: 2,
  overallDirection: 'POSITIVE',
  overallScore: 51,
  positiveImpactScore: 60,
  negativeImpactScore: 9,
  analyzedAt: '2026-10-05T08:00:00Z',
  overallImpact: '반도체 업황 개선 기대가 포트폴리오에 긍정적으로 작용할 수 있어요.',
  impacts: [
    {
      assetName: '삼성전자',
      weight: 60,
      direction: 'POSITIVE',
      impactLevel: 'HIGH',
      issueSummary: '반도체 수요가 증가했어요.',
      expectedReaction: '실적 개선 기대가 주가에 긍정적으로 반영될 수 있어요.',
      outlook: '수요 회복 흐름을 확인해야 해요.',
      reason: '보유 비중이 높아 포트폴리오에 미치는 영향도 커요.',
      evidenceSentence: '반도체 수요가 전년보다 증가했어요.',
      sources: [{ title: '반도체 산업 동향', url: 'https://example.com/semiconductor' }],
      rank: 1,
    },
    {
      assetName: 'SK하이닉스',
      weight: 40,
      direction: 'NEGATIVE',
      impactLevel: 'LOW',
      issueSummary: '메모리 가격 변동성이 커졌어요.',
      expectedReaction: '단기 주가 변동성이 커질 수 있어요.',
      outlook: '가격 안정 여부를 지켜봐야 해요.',
      reason: '부정 영향은 예상되지만 영향 수준은 낮아요.',
      evidenceSentence: '메모리 현물 가격 변동성이 확대됐어요.',
      sources: [{ title: '메모리 시장 동향', url: 'https://example.com/memory' }],
      rank: 2,
    },
  ],
  sources: [
    { title: '반도체 산업 동향', url: 'https://example.com/semiconductor' },
    { title: '메모리 시장 동향', url: 'https://example.com/memory' },
  ],
};

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe('포트폴리오 분석 화면', () => {
  it('전체 보유 자산으로 분석하고 자산별 결과와 출처를 선택해 표시한다', async () => {
    const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) =>
      jsonResponse(analysisResponse),
    );
    vi.stubGlobal('fetch', fetchMock);
    const view = render(<PortfolioAnalysis assets={assets} />);

    fireEvent.click(view.getByRole('button', { name: '분석하기' }));

    expect(await view.findByText('긍정 · +51.00점')).toBeTruthy();
    expect(view.getByText(analysisResponse.overallImpact)).toBeTruthy();
    expect(view.getByRole('heading', { name: '삼성전자' })).toBeTruthy();
    expect(view.getByText('실적 개선 기대가 주가에 긍정적으로 반영될 수 있어요.')).toBeTruthy();

    const request = fetchMock.mock.calls[0];
    expect(String(request[0])).toContain('/api/portfolio/analysis');
    expect(request[1]).toEqual(
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ assets }),
      }),
    );

    fireEvent.click(view.getByText('SK하이닉스').closest('button') as HTMLButtonElement);
    expect(view.getByRole('heading', { name: 'SK하이닉스' })).toBeTruthy();
    expect(view.getByText('단기 주가 변동성이 커질 수 있어요.')).toBeTruthy();

    const evidenceSummary = view.getByText('판단에 사용한 출처');
    fireEvent.click(evidenceSummary);
    const sourceLink = view.getByRole('link', { name: '메모리 시장 동향' });
    expect(sourceLink.getAttribute('href')).toBe('https://example.com/memory');
    expect(sourceLink.getAttribute('target')).toBe('_blank');
  });

  it('분석 중에는 로딩 상태를 표시하고 중복 요청을 막는다', async () => {
    let resolveResponse: ((response: Response) => void) | undefined;
    const fetchMock = vi.fn(
      () =>
        new Promise<Response>((resolve) => {
          resolveResponse = resolve;
        }),
    );
    vi.stubGlobal('fetch', fetchMock);
    const view = render(<PortfolioAnalysis assets={assets} />);

    fireEvent.click(view.getByRole('button', { name: '분석하기' }));

    expect(view.getByRole('status').textContent).toContain(
      '내 포트폴리오에 미치는 영향을 분석하고 있어요.',
    );
    const loadingButton = view.getByRole('button', { name: '분석 중' }) as HTMLButtonElement;
    expect(loadingButton.disabled).toBe(true);
    fireEvent.click(loadingButton);
    expect(fetchMock).toHaveBeenCalledTimes(1);

    resolveResponse?.(jsonResponse(analysisResponse));
    expect(await view.findByRole('button', { name: '다시 분석하기' })).toBeTruthy();
  });

  it('분석 요청이 실패하면 오류를 표시하고 다시 요청할 수 있다', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({}, 500))
      .mockResolvedValueOnce(jsonResponse(analysisResponse));
    vi.stubGlobal('fetch', fetchMock);
    const view = render(<PortfolioAnalysis assets={assets} />);

    fireEvent.click(view.getByRole('button', { name: '분석하기' }));
    expect((await view.findByRole('alert')).textContent).toContain(
      '포트폴리오 분석을 완료하지 못했어요.',
    );

    fireEvent.click(view.getByRole('button', { name: '다시 시도' }));
    expect(await view.findByRole('heading', { name: '삼성전자' })).toBeTruthy();
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('보유 자산이 없으면 안내하고 분석 요청을 막는다', () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const view = render(<PortfolioAnalysis assets={[]} />);

    expect(view.getByText(/포트폴리오에 자산을 등록하면/)).toBeTruthy();
    expect((view.getByRole('button', { name: '분석하기' }) as HTMLButtonElement).disabled).toBe(
      true,
    );
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('분석 후 보유 자산 구성이 변경되면 이전 결과를 초기화한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse(analysisResponse)),
    );
    const view = render(<PortfolioAnalysis assets={assets} />);

    fireEvent.click(view.getByRole('button', { name: '분석하기' }));
    expect(await view.findByRole('heading', { name: '삼성전자' })).toBeTruthy();

    view.rerender(<PortfolioAnalysis assets={[{ assetName: '현대차', weight: 100 }]} />);

    await waitFor(() => expect(view.queryByRole('heading', { name: '삼성전자' })).toBeNull());
    expect(view.getByRole('button', { name: '분석하기' })).toBeTruthy();
  });
});
