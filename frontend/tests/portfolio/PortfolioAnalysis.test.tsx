/** @vitest-environment jsdom */

import { cleanup, fireEvent, render } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
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

    expect(await view.findByText('이 뉴스와 직접 관련된 보유 자산이 없어요.')).toBeTruthy();
  });

  it('분석 중이면 로딩 상태를 표시한다', () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => new Promise<Response>(() => undefined)),
    );

    const view = render(
      <PortfolioAnalysis newsId="loading" assets={[{ assetName: '삼성전자', weight: 100 }]} />,
    );

    expect(view.getByRole('status').textContent).toContain(
      '내 포트폴리오에 미치는 영향을 분석하고 있어요.',
    );
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

    expect((await view.findByRole('alert')).textContent).toContain(
      '포트폴리오 분석을 완료하지 못했어요.',
    );

    fireEvent.click(view.getByRole('button', { name: '다시 시도' }));

    expect(await view.findByText(/재시도 후 분석을 완료했습니다/)).toBeTruthy();
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
});
