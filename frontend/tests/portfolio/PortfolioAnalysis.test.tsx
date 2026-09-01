/** @vitest-environment jsdom */

import { cleanup, render } from '@testing-library/react';
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
          impacts: [
            {
              bookmarkId: 1,
              assetId: 2,
              name: '삼성전자',
              category: 'STOCK',
              assetCode: '005930',
              direction: 'POSITIVE',
              impactLevel: 'HIGH',
              expectedReaction: '주가가 상승할 수 있습니다.',
              reason: '반도체 수요 증가가 실적 개선으로 이어질 수 있습니다.',
              sortOrder: 1,
            },
          ],
        }),
      ),
    );

    const view = render(<PortfolioAnalysis newsId="success" />);

    expect(await view.findByRole('heading', { name: '삼성전자' })).toBeTruthy();
    expect(view.getByText('주가가 상승할 수 있습니다.')).toBeTruthy();
    const highImpactBadge = view.getByLabelText('영향 분석 요약');
    expect(highImpactBadge.textContent).toContain('긍정 · 영향 높음');
  });

  it('영향 분석이 비어 있으면 빈 상태를 표시한다', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse({ impacts: [] })));

    const view = render(<PortfolioAnalysis newsId="retry" />);

    expect(await view.findByText('분석할 자산 영향이 없습니다')).toBeTruthy();
  });

  it('분석 API가 실패하면 오류 상태를 표시한다', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse({}, 500)));

    const view = render(<PortfolioAnalysis newsId="failure" />);

    expect(await view.findByRole('alert')).toBeTruthy();
    expect(view.getByText('포트폴리오 분석을 불러오지 못했습니다.')).toBeTruthy();
  });
});
