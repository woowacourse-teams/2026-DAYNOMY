/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { getPortfolioAnalysis } from '../../src/features/portfolio/api';
import { PortfolioAnalysis } from '../../src/features/portfolio/components/PortfolioAnalysis';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('포트폴리오 분석 화면', () => {
  it('API에서 포트폴리오 영향 결과를 표시한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        jsonResponse({
          totalAssetCount: 3,
          analyzedAssetCount: 2,
          impacts: [
            {
              assetName: '삼성전자',
              weight: 50,
              direction: 'POSITIVE',
              impactLevel: 'HIGH',
              summary: '주가가 상승할 수 있습니다.',
              reason: '반도체 수요 증가가 실적 개선으로 이어질 수 있습니다.',
              evidenceSentence: '반도체 수요가 증가했습니다.',
              rank: 1,
            },
            {
              assetName: 'SK하이닉스',
              weight: 30,
              direction: 'NEGATIVE',
              impactLevel: 'MEDIUM',
              summary: '비용 부담이 커질 수 있습니다.',
              reason: '원재료 비용이 증가했습니다.',
              evidenceSentence: '원재료 비용이 증가했습니다.',
              rank: 2,
            },
          ],
        }),
      ),
    );

    const assets = [
      { assetName: '삼성전자', weight: 50 },
      { assetName: 'SK하이닉스', weight: 30 },
      { assetName: '현대차', weight: 20 },
    ];
    const view = render(<PortfolioAnalysis newsId="success" assets={assets} />);

    expect(fetch).not.toHaveBeenCalled();
    fireEvent.click(view.getByRole('button', { name: '포트폴리오 분석하기' }));

    expect(await view.findByRole('heading', { name: '삼성전자' })).toBeTruthy();
    expect(view.getByText(/주가가 상승할 수 있습니다/)).toBeTruthy();
    expect(view.getAllByText('긍정 영향')).toHaveLength(2);
    expect(view.getByText('영향 수준 높음')).toBeTruthy();
    expect(view.getByText('상세 분석 제외')).toBeTruthy();

    const evidenceSummary = view.getByText('판단에 사용한 뉴스 문장');
    const evidenceDetails = evidenceSummary.closest('details') as HTMLDetailsElement;

    expect(evidenceDetails.open).toBe(false);
    fireEvent.click(evidenceSummary);
    expect(evidenceDetails.open).toBe(true);
    expect(view.getByText('반도체 수요가 증가했습니다.')).toBeTruthy();

    fireEvent.click(view.getByText('SK하이닉스').closest('button') as HTMLButtonElement);

    expect(view.getByRole('heading', { name: 'SK하이닉스' })).toBeTruthy();
    expect(view.getByText(/비용 부담이 커질 수 있습니다/)).toBeTruthy();
    expect(view.getAllByText('부정 영향')).toHaveLength(2);
    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining('/api/news/success/portfolio-analysis'),
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ assets }),
      },
    );
  });

  it('영향 분석이 비어 있으면 빈 상태를 표시한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse({ totalAssetCount: 1, analyzedAssetCount: 0, impacts: [] })),
    );

    const view = render(
      <PortfolioAnalysis newsId="retry" assets={[{ assetName: '삼성전자', weight: 100 }]} />,
    );

    fireEvent.click(view.getByRole('button', { name: '포트폴리오 분석하기' }));

    expect(await view.findByText('이 뉴스와 직접 관련된 보유 자산이 없어요.')).toBeTruthy();
  });

  it('분석 버튼을 누르면 로딩 상태를 표시하고 중복 요청을 막는다', () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => new Promise<Response>(() => undefined)),
    );

    const view = render(
      <PortfolioAnalysis newsId="loading" assets={[{ assetName: '삼성전자', weight: 100 }]} />,
    );

    expect(view.queryByRole('status')).toBeNull();

    const analyzeButton = view.getByRole('button', { name: '포트폴리오 분석하기' });
    fireEvent.click(analyzeButton);

    expect(view.getByRole('status').textContent).toContain(
      '내 포트폴리오에 미치는 영향을 분석하고 있어요.',
    );
    expect((view.getByRole('button', { name: '분석 중' }) as HTMLButtonElement).disabled).toBe(
      true,
    );
    expect(fetch).toHaveBeenCalledTimes(1);
  });

  it('분석 API가 실패하면 다시 시도할 수 있다', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({}, 500))
      .mockResolvedValueOnce(
        jsonResponse({
          totalAssetCount: 1,
          analyzedAssetCount: 1,
          impacts: [
            {
              assetName: '삼성전자',
              weight: 100,
              direction: 'POSITIVE',
              impactLevel: 'HIGH',
              summary: '재시도 후 분석을 완료했습니다.',
              reason: '반도체 수요가 증가했습니다.',
              evidenceSentence: '반도체 수요가 증가했습니다.',
              rank: 1,
            },
          ],
        }),
      );
    vi.stubGlobal('fetch', fetchMock);

    const view = render(
      <PortfolioAnalysis newsId="failure" assets={[{ assetName: '삼성전자', weight: 100 }]} />,
    );

    fireEvent.click(view.getByRole('button', { name: '포트폴리오 분석하기' }));

    expect((await view.findByRole('alert')).textContent).toContain(
      '포트폴리오 분석을 완료하지 못했어요.',
    );

    fireEvent.click(view.getByRole('button', { name: '다시 시도' }));

    expect(await view.findByText(/재시도 후 분석을 완료했습니다/)).toBeTruthy();
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('포트폴리오가 비어 있으면 분석 버튼을 비활성화한다', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const view = render(<PortfolioAnalysis newsId="empty" assets={[]} />);

    expect(
      (view.getByRole('button', { name: '포트폴리오 분석하기' }) as HTMLButtonElement).disabled,
    ).toBe(true);
    await waitFor(() => expect(fetchMock).not.toHaveBeenCalled());
  });

  it('포트폴리오 정보를 불러오는 상태와 실패 상태를 구분해 표시한다', () => {
    const view = render(
      <PortfolioAnalysis newsId="portfolio-state" assets={[]} portfolioStatus="loading" />,
    );

    expect(view.getByRole('status').textContent).toContain('포트폴리오 정보를 불러오고 있어요.');
    expect(view.queryByText(/포트폴리오에 자산을 등록하면/)).toBeNull();

    view.rerender(
      <PortfolioAnalysis newsId="portfolio-state" assets={[]} portfolioStatus="error" />,
    );

    expect(view.getByRole('alert').textContent).toContain('포트폴리오 정보를 불러오지 못했어요.');
    expect(
      (view.getByRole('button', { name: '포트폴리오 분석하기' }) as HTMLButtonElement).disabled,
    ).toBe(true);
  });

  it('분석 후 포트폴리오가 변경되어도 분석 당시 자산을 표시한다', async () => {
    const fetchMock = vi.fn(async () =>
      jsonResponse({
        totalAssetCount: 1,
        analyzedAssetCount: 1,
        impacts: [
          {
            assetName: '삼성전자',
            weight: 100,
            direction: 'POSITIVE',
            impactLevel: 'HIGH',
            summary: '반도체 수요가 증가했습니다.',
            reason: '실적 개선이 기대됩니다.',
            evidenceSentence: '반도체 수요가 증가했습니다.',
            rank: 1,
          },
        ],
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    const view = render(
      <PortfolioAnalysis newsId="snapshot" assets={[{ assetName: '삼성전자', weight: 100 }]} />,
    );

    fireEvent.click(view.getByRole('button', { name: '포트폴리오 분석하기' }));
    expect(await view.findByRole('heading', { name: '삼성전자' })).toBeTruthy();

    expect(
      (view.getByRole('button', { name: '다시 분석하기' }) as HTMLButtonElement).disabled,
    ).toBe(true);

    view.rerender(
      <PortfolioAnalysis newsId="snapshot" assets={[{ assetName: 'SK하이닉스', weight: 100 }]} />,
    );

    expect(view.getAllByText('삼성전자').length).toBeGreaterThan(0);
    expect(view.queryByText('SK하이닉스')).toBeNull();
    expect(
      (view.getByRole('button', { name: '다시 분석하기' }) as HTMLButtonElement).disabled,
    ).toBe(false);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('자산 순서만 변경되면 다시 분석 버튼을 활성화하지 않는다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        jsonResponse({
          totalAssetCount: 2,
          analyzedAssetCount: 1,
          impacts: [
            {
              assetName: '삼성전자',
              weight: 60,
              direction: 'POSITIVE',
              impactLevel: 'HIGH',
              summary: '반도체 수요가 증가했습니다.',
              reason: '실적 개선이 기대됩니다.',
              evidenceSentence: '반도체 수요가 증가했습니다.',
              rank: 1,
            },
          ],
        }),
      ),
    );

    const view = render(
      <PortfolioAnalysis
        newsId="reordered"
        assets={[
          { assetName: '삼성전자', weight: 60 },
          { assetName: 'SK하이닉스', weight: 40 },
        ]}
      />,
    );

    fireEvent.click(view.getByRole('button', { name: '포트폴리오 분석하기' }));
    expect(await view.findByRole('heading', { name: '삼성전자' })).toBeTruthy();

    view.rerender(
      <PortfolioAnalysis
        newsId="reordered"
        assets={[
          { assetName: 'SK하이닉스', weight: 40 },
          { assetName: ' 삼성전자 ', weight: 60 },
        ]}
      />,
    );

    expect(
      (view.getByRole('button', { name: '다시 분석하기' }) as HTMLButtonElement).disabled,
    ).toBe(true);

    view.rerender(
      <PortfolioAnalysis
        newsId="reordered"
        assets={[
          { assetName: 'SK하이닉스', weight: 41 },
          { assetName: '삼성전자', weight: 59 },
        ]}
      />,
    );

    expect(
      (view.getByRole('button', { name: '다시 분석하기' }) as HTMLButtonElement).disabled,
    ).toBe(false);
  });

  it('다시 분석하면 최신 포트폴리오로 스냅샷을 교체하고 캐시 없이 요청한다', async () => {
    const createAnalysisResponse = (assetName: string) =>
      jsonResponse({
        totalAssetCount: 1,
        analyzedAssetCount: 1,
        impacts: [
          {
            assetName,
            weight: 100,
            direction: 'POSITIVE',
            impactLevel: 'HIGH',
            summary: `${assetName} 분석 결과입니다.`,
            reason: `${assetName} 관련 뉴스입니다.`,
            evidenceSentence: `${assetName} 관련 내용이 확인됐습니다.`,
            rank: 1,
          },
        ],
      });
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(createAnalysisResponse('삼성전자'))
      .mockResolvedValueOnce(createAnalysisResponse('SK하이닉스'))
      .mockResolvedValueOnce(createAnalysisResponse('삼성전자'));
    vi.stubGlobal('fetch', fetchMock);

    const samsungAssets = [{ assetName: '삼성전자', weight: 100 }];
    const hynixAssets = [{ assetName: 'SK하이닉스', weight: 100 }];
    const view = render(<PortfolioAnalysis newsId="refresh" assets={samsungAssets} />);

    fireEvent.click(view.getByRole('button', { name: '포트폴리오 분석하기' }));
    expect(await view.findByRole('heading', { name: '삼성전자' })).toBeTruthy();

    view.rerender(<PortfolioAnalysis newsId="refresh" assets={hynixAssets} />);
    fireEvent.click(view.getByRole('button', { name: '다시 분석하기' }));
    expect(await view.findByRole('heading', { name: 'SK하이닉스' })).toBeTruthy();
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      expect.stringContaining('/api/news/refresh/portfolio-analysis'),
      expect.objectContaining({ body: JSON.stringify({ assets: hynixAssets }) }),
    );

    view.rerender(<PortfolioAnalysis newsId="refresh" assets={samsungAssets} />);
    fireEvent.click(view.getByRole('button', { name: '다시 분석하기' }));
    expect(await view.findByRole('heading', { name: '삼성전자' })).toBeTruthy();
    expect(fetchMock).toHaveBeenNthCalledWith(
      3,
      expect.stringContaining('/api/news/refresh/portfolio-analysis'),
      expect.objectContaining({ body: JSON.stringify({ assets: samsungAssets }) }),
    );
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it('뉴스가 변경되면 이전 스냅샷과 분석 결과를 초기화한다', async () => {
    const fetchMock = vi.fn(async () =>
      jsonResponse({
        totalAssetCount: 1,
        analyzedAssetCount: 1,
        impacts: [
          {
            assetName: '삼성전자',
            weight: 100,
            direction: 'POSITIVE',
            impactLevel: 'HIGH',
            summary: '기존 뉴스의 분석 결과입니다.',
            reason: '반도체 수요가 증가했습니다.',
            evidenceSentence: '반도체 수요가 증가했습니다.',
            rank: 1,
          },
        ],
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    const assets = [{ assetName: '삼성전자', weight: 100 }];
    const view = render(<PortfolioAnalysis newsId="before" assets={assets} />);

    fireEvent.click(view.getByRole('button', { name: '포트폴리오 분석하기' }));
    expect(await view.findByText(/기존 뉴스의 분석 결과입니다/)).toBeTruthy();

    view.rerender(<PortfolioAnalysis newsId="after" assets={assets} />);

    await waitFor(() => {
      expect(view.queryByText(/기존 뉴스의 분석 결과입니다/)).toBeNull();
    });
    expect(
      (view.getByRole('button', { name: '포트폴리오 분석하기' }) as HTMLButtonElement).disabled,
    ).toBe(false);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('완료된 분석 캐시는 TTL이 지나면 자동으로 제거한다', async () => {
    vi.useFakeTimers();
    const fetchMock = vi.fn(async () =>
      jsonResponse({ totalAssetCount: 1, analyzedAssetCount: 0, impacts: [] }),
    );
    vi.stubGlobal('fetch', fetchMock);
    const assets = [{ assetName: 'TTL 검증 자산', weight: 100 }];

    try {
      await getPortfolioAnalysis('cache-ttl', assets);
      await getPortfolioAnalysis('cache-ttl', assets);
      expect(fetchMock).toHaveBeenCalledTimes(1);

      await vi.advanceTimersByTimeAsync(5 * 60 * 1000);
      await getPortfolioAnalysis('cache-ttl', assets);
      expect(fetchMock).toHaveBeenCalledTimes(2);
      await vi.advanceTimersByTimeAsync(5 * 60 * 1000);
    } finally {
      vi.clearAllTimers();
      vi.useRealTimers();
    }
  });
});
