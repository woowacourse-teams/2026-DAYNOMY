/** @vitest-environment jsdom */

import { cleanup, render } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { PortfolioAnalysis } from '../../src/features/portfolio/components/PortfolioAnalysis';

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('포트폴리오 분석 화면', () => {
  it('목업 포트폴리오 영향 결과를 표시한다', () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const view = render(<PortfolioAnalysis newsId="success" />);

    expect(view.getByRole('heading', { name: 'KODEX 200' })).toBeTruthy();
    expect(view.getByRole('heading', { name: '삼성전자' })).toBeTruthy();
    expect(view.getByText('분산 투자 수요가 늘면 ETF로 자금이 유입될 수 있습니다.')).toBeTruthy();
    expect(view.getByText('영향 높음')).toBeTruthy();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('뉴스 ID와 관계없이 목업 분석을 표시한다', () => {
    const view = render(<PortfolioAnalysis newsId="retry" />);

    expect(view.getByRole('heading', { name: 'KODEX 200' })).toBeTruthy();
  });
});
