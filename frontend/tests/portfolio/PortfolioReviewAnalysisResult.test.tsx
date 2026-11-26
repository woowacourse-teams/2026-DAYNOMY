/** @vitest-environment jsdom */
import { cleanup, fireEvent, render } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { PortfolioReviewAnalysisResult } from '../../src/features/portfolio-review/PortfolioReviewAnalysisResult';
import { tourAnalysis } from '../../src/features/portfolio/components/portfolioTourExample';

afterEach(cleanup);

describe('리뷰 분석 결과 표시', () => {
  it('제안 구성 맥락을 유지하고 출처와 점수 설명을 접어서 표시한다', () => {
    const view = render(
      <PortfolioReviewAnalysisResult
        analysis={tourAnalysis}
        title="제안 구성 AI 분석"
        portfolioLabel="개선안 포트폴리오"
        contextNote="실제 보유에는 반영되지 않은 개선안 분석이에요."
      />,
    );
    expect(view.getByRole('heading', { name: '제안 구성 AI 분석' })).toBeTruthy();
    expect(view.getByText('실제 보유에는 반영되지 않은 개선안 분석이에요.')).toBeTruthy();
    expect(view.getByText(/체험용 예시예요/)).toBeTruthy();
    expect(view.getByText(/예상 수익률이나 상승 확률은 아니에요/)).toBeTruthy();
    const details = view.container.querySelectorAll('details');
    expect(details).toHaveLength(2);
    expect(details[0].hasAttribute('open')).toBe(false);
    expect(details[1].hasAttribute('open')).toBe(false);
    fireEvent.click(details[0].querySelector('summary')!);
    fireEvent.click(details[1].querySelector('summary')!);
    expect(
      view.getByRole('link', { name: '체험용 가상 소식 · 실제 뉴스가 아닙니다' }),
    ).toBeTruthy();
    fireEvent.click(view.getByRole('button', { name: /삼성전자\s*TOP 1/ }));
    expect(view.getByRole('button', { name: '삼성전자 분석 보기' })).toBeTruthy();
    expect(view.getByText(tourAnalysis.impacts[0].expectedReaction)).toBeTruthy();
    expect(view.getByText(tourAnalysis.impacts[0].outlook)).toBeTruthy();
    expect(
      view.getByRole('button', { name: /삼성전자, 보유 비중 100%/ }).getAttribute('aria-pressed'),
    ).toBe('true');
    expect(
      view.getByRole('link', { name: '체험용 가상 소식 · 실제 뉴스가 아닙니다' }),
    ).toBeTruthy();
    fireEvent.click(view.getByRole('button', { name: '삼성전자 분석 보기' }));
    expect(view.getByText(/체험용 예시예요/)).toBeTruthy();
    expect(view.getByRole('button', { name: '개선안 포트폴리오 분석 보기' })).toBeTruthy();
  });

  it('부분 공개에서는 전체 점수·요약·출처·제외 종목을 표시하지 않는다', () => {
    const analysis = {
      ...tourAnalysis,
      overallImpact: '비공개 전체 요약',
      overallScore: 99,
      excludedAssets: [{ assetName: '비공개 종목', weight: 40 }],
      sources: [{ title: '비공개 전체 출처', url: 'https://example.com/private' }],
      impacts: [{ ...tourAnalysis.impacts[0], weight: 60 }],
    };
    const view = render(
      <PortfolioReviewAnalysisResult
        analysis={analysis}
        title="공개 종목 분석"
        showOverall={false}
      />,
    );
    expect(view.queryByText('비공개 전체 요약')).toBeNull();
    expect(view.queryByText('+99.00점')).toBeNull();
    expect(view.queryByText('비공개 종목')).toBeNull();
    expect(view.queryByRole('button', { name: '전체 포트폴리오 분석 보기' })).toBeNull();
    expect(view.queryByText('비공개 전체 출처')).toBeNull();
    expect(view.queryByRole('heading', { name: '영향 점수 산정 기준' })).toBeNull();
    expect(view.getByText('60%')).toBeTruthy();
  });

  it('도넛 키보드 선택과 근거 부족·분석 제외 상태를 구분한다', () => {
    const analysis = {
      ...tourAnalysis,
      excludedAssets: [{ assetName: '제외 종목', weight: 20 }],
      impacts: [
        { ...tourAnalysis.impacts[0], weight: 40 },
        {
          ...tourAnalysis.impacts[0],
          assetName: '근거 부족 종목',
          weight: 40,
          direction: null,
          impactLevel: null,
          rank: 2,
        },
      ],
    };
    const view = render(<PortfolioReviewAnalysisResult analysis={analysis} title="검토 분석" />);
    expect(
      view.getByRole('button', { name: /제외 종목\s*분석 대상 제외/ }).hasAttribute('disabled'),
    ).toBe(true);
    fireEvent.click(view.getByRole('button', { name: /삼성전자\s*TOP 1/ }));
    fireEvent.keyDown(view.getByRole('button', { name: /근거 부족 종목, 보유 비중 40%/ }), {
      key: 'Enter',
    });
    expect(view.getByText('판단할 근거가 충분하지 않아요.')).toBeTruthy();
    expect(view.getByText('영향 수준 판단 보류')).toBeTruthy();
    expect(view.container.querySelector('path[d*="NaN"]')).toBeNull();
  });
});
