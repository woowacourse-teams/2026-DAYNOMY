/** @vitest-environment jsdom */

import { cleanup, fireEvent, render } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { PortfolioAnalysis } from '../../src/features/portfolio/components/PortfolioAnalysis';

const analysis = {
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
      reason: '반도체 수요가 증가하고 있습니다.',
      sortOrder: 1,
    },
  ],
};

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
  it('북마크 자산의 분석 결과를 표시한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse(analysis)),
    );

    const view = render(<PortfolioAnalysis newsId="success" />);

    expect(await view.findByRole('heading', { name: '삼성전자' })).toBeTruthy();
    expect(view.getByText('주가가 상승할 수 있습니다.')).toBeTruthy();
    expect(view.getByText('영향 높음')).toBeTruthy();
  });

  it('분석 실패를 안내하고 다시 시도한다', async () => {
    let requestCount = 0;
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        requestCount += 1;
        return requestCount === 1
          ? jsonResponse({ code: 'ANALYSIS_FAILED', message: '분석 생성에 실패했습니다.' }, 500)
          : jsonResponse(analysis);
      }),
    );

    const view = render(<PortfolioAnalysis newsId="retry" />);

    expect((await view.findByRole('alert')).textContent).toContain('분석 생성에 실패했습니다.');
    fireEvent.click(view.getByRole('button', { name: '다시 시도' }));
    expect(await view.findByRole('heading', { name: '삼성전자' })).toBeTruthy();
    expect(requestCount).toBe(2);
  });
});
