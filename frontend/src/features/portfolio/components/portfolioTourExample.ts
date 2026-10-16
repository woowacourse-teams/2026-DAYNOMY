import type { PortfolioCalculation, PortfolioAnalysisResponse, StockSearchItem } from '../types';
import type { StockRelatedContent } from '../../stock-content/types';

// Display-only examples. Never pass these values to persistence or analysis APIs.
export const tourStock: StockSearchItem = {
  assetId: 1,
  assetCode: '005930',
  name: '삼성전자',
  category: 'STOCK',
  market: 'KOSPI',
};
export const tourCalculation: PortfolioCalculation = {
  baseDate: null,
  pricingStatus: 'COMPLETE',
  pricedHoldingCount: 1,
  unpricedHoldingCount: 0,
  totalPurchaseAmount: 700000,
  totalEvaluationAmount: 720000,
  dailyProfitLoss: 0,
  dailyReturnRate: 0,
  totalProfitLoss: 20000,
  totalReturnRate: (20000 / 700000) * 100,
  holdings: [
    {
      ...tourStock,
      quantity: 10,
      averagePurchasePrice: 70000,
      priceAvailable: true,
      baseDate: null,
      closePrice: 72000,
      purchaseAmount: 700000,
      evaluationAmount: 720000,
      profitLoss: 20000,
      returnRate: (20000 / 700000) * 100,
      weight: 100,
    },
  ],
  marketAllocations: [{ market: 'KOSPI', evaluationAmount: 720000, weight: 100 }],
};
export const tourNews: (StockRelatedContent & { assets: string[] })[] = [
  {
    id: -1,
    assetId: 1,
    sourceType: 'OTHER',
    title: '[체험용 가상 소식] 예시 기업, 신제품 수요 증가 발표',
    url: '#tour-example',
    imageUrl: null,
    createdAt: null,
    assets: ['삼성전자 · 사용법 설명용 예시'],
  },
];
const sources = [
  {
    title: '체험용 가상 소식 · 실제 뉴스가 아닙니다',
    url: '#tour-example',
    excerpt: '신제품의 수요가 늘었다고 가정한 예시예요. 실제 분석에서는 원문과 날짜를 확인하세요.',
  },
];
export const tourAnalysis: PortfolioAnalysisResponse = {
  totalAssetCount: 1,
  analyzedAssetCount: 1,
  overallDirection: 'POSITIVE',
  overallScore: 25,
  positiveImpactScore: 25,
  negativeImpactScore: 0,
  analyzedAt: null,
  overallImpact:
    '체험용 예시입니다. 삼성전자 한 종목으로 구성되어 해당 종목의 가격 변화가 전체 자산에 영향을 줘요.\n\n가상의 신제품 수요 증가 소식은 매출에 긍정적으로 작용할 수 있어요. 실제 뉴스나 투자 전망이 아닙니다.',
  impacts: [
    {
      assetName: '삼성전자',
      weight: 100,
      direction: 'POSITIVE',
      impactLevel: 'MEDIUM',
      rank: 1,
      issueSummary: '체험용 가상 소식에서 신제품 수요가 늘었다고 가정했어요.',
      expectedReaction: '수요가 매출 증가로 이어지면 긍정적으로 작용할 수 있어요.',
      outlook: '판매량과 생산 비용을 함께 확인해야 해요. 아래에서 분석의 근거를 살펴보세요.',
      reason: '',
      evidenceSentence: '',
      sources,
    },
  ],
  sources,
};
