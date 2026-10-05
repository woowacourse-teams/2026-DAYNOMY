export type InvestmentEventType = 'US_CPI' | 'KOREA_BASE_RATE' | 'CORPORATE_EARNINGS';
export type InvestmentEventDirection = 'DECREASED' | 'UNCHANGED' | 'INCREASED' | 'UNAVAILABLE';
export type PortfolioEventAnalysisStatus = 'READY' | 'NO_PORTFOLIO' | 'INSUFFICIENT_DATA';
export type PortfolioEventImpactLevel = 'LOW' | 'MEDIUM' | 'HIGH';

export type InvestmentEventValue = {
  previousValue: number | null;
  actualValue: number | null;
  unit: string;
  direction: InvestmentEventDirection;
};

export type InvestmentEventSource = {
  name: string;
  url: string;
};

export type PortfolioReactionStatistics = {
  direction: Exclude<InvestmentEventDirection, 'UNAVAILABLE'>;
  sampleCount: number;
  medianReturnRate: number | null;
  lowerReturnRate: number | null;
  upperReturnRate: number | null;
  lowerEstimatedAmount: number | null;
  upperEstimatedAmount: number | null;
};

export type PortfolioEventAnalysis = {
  status: PortfolioEventAnalysisStatus;
  impactLevel: PortfolioEventImpactLevel;
  relatedAssetCount: number;
  totalEvaluationAmount: number;
  priceBaseDate: string | null;
  historicalReactions: PortfolioReactionStatistics[];
};

export type InvestmentCalendarDemoCopy = {
  shortTitle: string;
  relatedLabel: string;
  takeaway: string;
  watchMessage: string;
  previousLabel: string;
  sourceLabel: string;
  latestResult: string;
  latestAssetReaction: string;
};

export type InvestmentCalendarEvent = {
  id: number;
  type: InvestmentEventType;
  title: string;
  announcedAt: string;
  daysUntil: number;
  upcoming: boolean;
  value: InvestmentEventValue;
  source: InvestmentEventSource;
  portfolioAnalysis: PortfolioEventAnalysis;
  demoCopy?: InvestmentCalendarDemoCopy;
};

export type InvestmentCalendar = {
  year: number;
  month: number;
  events: InvestmentCalendarEvent[];
};

export type InvestmentCalendarLoadResult = {
  calendar: InvestmentCalendar;
  isDemo: boolean;
};
