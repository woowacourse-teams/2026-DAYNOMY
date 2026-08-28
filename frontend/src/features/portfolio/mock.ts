import type { PortfolioAnalysisResponse } from './types';

export function getMockPortfolioAnalysis(_newsId: string): PortfolioAnalysisResponse {
  return {
    impacts: [
      {
        bookmarkId: 1,
        assetId: 1,
        name: 'KODEX 200',
        category: 'ETF',
        assetCode: '069500',
        direction: 'POSITIVE',
        impactLevel: 'HIGH',
        expectedReaction: '분산 투자 수요가 늘면 ETF로 자금이 유입될 수 있습니다.',
        reason:
          '시장 변동성이 커질수록 개별 종목보다 대표 지수를 추종하는 ETF에 관심이 모일 수 있습니다.',
        sortOrder: 1,
      },
      {
        bookmarkId: 2,
        assetId: 2,
        name: '삼성전자',
        category: 'STOCK',
        assetCode: '005930',
        direction: 'POSITIVE',
        impactLevel: 'MEDIUM',
        expectedReaction:
          'ETF 자금 유입이 이어지면 편입 비중이 높은 종목도 수급의 영향을 받을 수 있습니다.',
        reason:
          '대형주 중심 ETF의 자금 흐름은 편입 비중이 높은 종목의 수급과 주가 흐름에 연결될 수 있습니다.',
        sortOrder: 2,
      },
      {
        bookmarkId: 3,
        assetId: 3,
        name: 'TIGER 미국S&P500',
        category: 'ETF',
        assetCode: '360750',
        direction: 'POSITIVE',
        impactLevel: 'LOW',
        expectedReaction:
          '장기 분산 투자 수요가 이어지면 해외 지수형 ETF도 관심을 받을 수 있습니다.',
        reason:
          '시장 방향을 한쪽으로 예측하기 어려울수록 지역과 자산을 나누려는 투자 수요가 커질 수 있습니다.',
        sortOrder: 3,
      },
    ],
  };
}
