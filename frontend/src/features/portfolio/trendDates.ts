import type { PortfolioPerformancePoint, PortfolioTrendPeriod } from './types';

export function getTrendPeriodStart(baseDate: string, period: PortfolioTrendPeriod) {
  const date = new Date(`${baseDate}T00:00:00Z`);
  if (period === 'YTD') {
    date.setUTCMonth(0, 1);
  } else {
    const months = period === '1M' ? 1 : period === '6M' ? 6 : period === '1Y' ? 12 : 60;
    const day = date.getUTCDate();
    date.setUTCDate(1);
    date.setUTCMonth(date.getUTCMonth() - months);
    const lastDay = new Date(
      Date.UTC(date.getUTCFullYear(), date.getUTCMonth() + 1, 0),
    ).getUTCDate();
    date.setUTCDate(Math.min(day, lastDay));
  }
  return date.toISOString().slice(0, 10);
}

/** Display-only values: missing snapshots are not new valuations or daily returns. */
export function fillTrendDates(points: PortfolioPerformancePoint[]) {
  const daily: (PortfolioPerformancePoint & { carriedFrom?: string })[] = [];
  for (const point of points) {
    const previous = daily.at(-1);
    if (previous) {
      const date = new Date(`${previous.baseDate}T00:00:00Z`);
      date.setUTCDate(date.getUTCDate() + 1);
      while (date.toISOString().slice(0, 10) < point.baseDate) {
        daily.push({
          ...previous,
          baseDate: date.toISOString().slice(0, 10),
          carriedFrom: previous.carriedFrom ?? previous.baseDate,
          dailyProfitLoss: null,
          dailyReturnRate: null,
        });
        date.setUTCDate(date.getUTCDate() + 1);
      }
    }
    daily.push(point);
  }
  return daily;
}
