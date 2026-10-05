import type {
  InvestmentCalendar,
  InvestmentCalendarEvent,
  InvestmentEventDirection,
  PortfolioReactionStatistics,
} from './types';

function reaction(
  direction: Exclude<InvestmentEventDirection, 'UNAVAILABLE'>,
  sampleCount: number,
  medianReturnRate: number,
  lowerReturnRate: number,
  upperReturnRate: number,
  lowerEstimatedAmount: number,
  upperEstimatedAmount: number,
): PortfolioReactionStatistics {
  return {
    direction,
    sampleCount,
    medianReturnRate,
    lowerReturnRate,
    upperReturnRate,
    lowerEstimatedAmount,
    upperEstimatedAmount,
  };
}

const events: InvestmentCalendarEvent[] = [
  {
    id: 1,
    type: 'US_CPI',
    title: '미국 소비자물가지수 발표',
    announcedAt: '2026-10-14T12:30:00Z',
    daysUntil: 8,
    upcoming: true,
    value: { previousValue: 3.2, actualValue: null, unit: '%', direction: 'UNAVAILABLE' },
    source: { name: '미국 노동통계국', url: 'https://www.bls.gov/cpi/' },
    portfolioAnalysis: {
      status: 'READY',
      impactLevel: 'HIGH',
      relatedAssetCount: 4,
      totalEvaluationAmount: 31_775_950,
      priceBaseDate: '2026-10-05',
      historicalReactions: [
        reaction('DECREASED', 6, 0.8, 0.57, 0.98, 180_000, 310_000),
        reaction('UNCHANGED', 5, 0.1, -0.13, 0.22, -40_000, 70_000),
        reaction('INCREASED', 7, -0.7, -0.91, -0.47, -290_000, -150_000),
      ],
    },
    demoCopy: {
      shortTitle: '미국 CPI',
      relatedLabel: '관련 4종목',
      takeaway: '내 자산은 물가가 오른 뒤 약세였던 경우가 많았습니다.',
      watchMessage: '이번 발표에서 물가상승률이 직전 3.2%보다 높아졌는지 확인하세요.',
      previousLabel: '직전',
      sourceLabel: 'BLS · 2026.09',
      latestResult: '물가상승률이 2.9%에서 3.2%로 올랐어요.',
      latestAssetReaction: '다음 거래일에 0.5% 내렸어요.',
    },
  },
  {
    id: 2,
    type: 'CORPORATE_EARNINGS',
    title: '두산 3분기 실적 발표',
    announcedAt: '2026-10-21T05:00:00Z',
    daysUntil: 15,
    upcoming: true,
    value: { previousValue: 1.2, actualValue: null, unit: '조원', direction: 'UNAVAILABLE' },
    source: { name: '전자공시시스템', url: 'https://dart.fss.or.kr/' },
    portfolioAnalysis: {
      status: 'READY',
      impactLevel: 'HIGH',
      relatedAssetCount: 1,
      totalEvaluationAmount: 31_775_950,
      priceBaseDate: '2026-10-05',
      historicalReactions: [
        reaction('DECREASED', 4, -1.4, -1.92, -0.91, -610_000, -290_000),
        reaction('UNCHANGED', 3, -0.1, -0.25, 0.09, -80_000, 30_000),
        reaction('INCREASED', 5, 1.2, 0.79, 1.7, 250_000, 540_000),
      ],
    },
    demoCopy: {
      shortTitle: '두산 3분기 실적',
      relatedLabel: '직접 관련',
      takeaway: '두산은 영업이익이 늘어난 뒤 강세였던 경우가 많았습니다.',
      watchMessage: '이번 발표에서 영업이익이 지난해 같은 분기 1.2조보다 늘었는지 확인하세요.',
      previousLabel: '작년 3분기',
      sourceLabel: 'OpenDART · 2025.3Q',
      latestResult: '영업이익이 0.9조에서 1.2조로 늘었어요.',
      latestAssetReaction: '다음 거래일에 0.8% 올랐어요.',
    },
  },
  {
    id: 3,
    type: 'KOREA_BASE_RATE',
    title: '한국 기준금리 결정',
    announcedAt: '2026-10-22T01:00:00Z',
    daysUntil: 16,
    upcoming: true,
    value: { previousValue: 2.5, actualValue: null, unit: '%', direction: 'UNAVAILABLE' },
    source: { name: '한국은행', url: 'https://www.bok.or.kr/' },
    portfolioAnalysis: {
      status: 'READY',
      impactLevel: 'MEDIUM',
      relatedAssetCount: 7,
      totalEvaluationAmount: 31_775_950,
      priceBaseDate: '2026-10-05',
      historicalReactions: [
        reaction('DECREASED', 4, 0.4, 0.19, 0.66, 60_000, 210_000),
        reaction('UNCHANGED', 6, 0.1, -0.16, 0.28, -50_000, 90_000),
        reaction('INCREASED', 4, -0.6, -0.82, -0.28, -260_000, -90_000),
      ],
    },
    demoCopy: {
      shortTitle: '한국 기준금리',
      relatedLabel: '전체 7종목',
      takeaway: '내 자산은 기준금리가 오른 뒤 약세였던 경우가 많았습니다.',
      watchMessage: '이번 결정에서 기준금리가 현재 2.50%에서 인상·동결·인하됐는지 확인하세요.',
      previousLabel: '현재',
      sourceLabel: '한국은행 ECOS',
      latestResult: '기준금리가 3.00%에서 2.50%로 내렸어요.',
      latestAssetReaction: '다음 거래일에 0.3% 올랐어요.',
    },
  },
];

export function getMockInvestmentCalendar(year: number, month: number): InvestmentCalendar {
  return {
    year,
    month,
    events: year === 2026 && month === 10 ? events : [],
  };
}
