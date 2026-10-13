import { trackEvent } from '../../analytics';
import { ApiError } from '../../api/client';
import type { AssetCategory, PortfolioTrendPeriod, StockMarket } from './types';

const EVENTS = [
  'portfolio_dashboard_viewed',
  'portfolio_dashboard_loaded',
  'portfolio_dashboard_load_failed',
  'portfolio_retry_clicked',
  'portfolio_amount_visibility_changed',
  'portfolio_asset_section_toggled',
  'portfolio_period_changed',
  'portfolio_chart_expanded',
  'portfolio_filter_changed',
  'portfolio_sort_changed',
  'portfolio_holding_detail_opened',
  'portfolio_tab_changed',
  'portfolio_editor_opened',
  'portfolio_stock_selected',
  'portfolio_save_submitted',
  'portfolio_save_succeeded',
  'portfolio_save_failed',
  'portfolio_delete_succeeded',
  'portfolio_delete_failed',
  'portfolio_history_filter_changed',
  'portfolio_history_range_changed',
] as const;

type Operation = 'holding_create' | 'holding_update' | 'transaction_create' | 'transaction_update';
type ErrorType = 'unauthorized' | 'client_error' | 'server_error' | 'network_or_unknown';
type PortfolioEventParams = {
  is_logged_in?: boolean;
  has_holdings?: boolean;
  pricing_status?: 'COMPLETE' | 'PARTIAL' | 'MIXED_DATES';
  stage?: 'holdings' | 'calculation' | 'chart' | 'history';
  error_type?: ErrorType;
  hidden?: boolean;
  expanded?: boolean;
  period?: PortfolioTrendPeriod;
  view?: 'inline' | 'dialog';
  category?: 'ALL' | AssetCategory;
  market?: StockMarket;
  tab?: 'holdings' | 'history';
  mode?: Operation;
  entry_point?: 'header' | 'empty_state' | 'history' | 'holding_detail' | 'editor_switch';
  operation?: Operation;
  storage?: 'server' | 'temporary';
  transaction_type?: 'BUY' | 'SELL';
  target?: 'holding' | 'transaction' | 'dashboard' | 'chart' | 'history';
  sort?: 'DEFAULT' | 'EVALUATION' | 'PROFIT' | 'RETURN' | 'WEIGHT';
  filter?: 'ALL' | 'BUY' | 'SELL' | 'BALANCE';
  range_days?: number;
};

const PARAM_KEYS = new Set([
  'is_logged_in',
  'has_holdings',
  'pricing_status',
  'stage',
  'error_type',
  'hidden',
  'expanded',
  'period',
  'view',
  'category',
  'market',
  'tab',
  'mode',
  'entry_point',
  'operation',
  'storage',
  'transaction_type',
  'target',
  'sort',
  'filter',
  'range_days',
]);
export function portfolioErrorType(error: unknown): ErrorType {
  if (error instanceof ApiError) {
    if (error.status === 401 || error.status === 403) return 'unauthorized';
    if (error.status >= 500) return 'server_error';
    if (error.status >= 400) return 'client_error';
  }
  return 'network_or_unknown';
}

export function trackPortfolioEvent(
  name: (typeof EVENTS)[number],
  params: PortfolioEventParams = {},
) {
  if (typeof window === 'undefined' || window.location.pathname !== '/') return;
  const properties = Object.fromEntries(
    Object.entries(params).filter(([key, value]) => PARAM_KEYS.has(key) && value !== undefined),
  );
  // 공통 전송 함수가 GA4와 PostHog를 처리하며, 수집 실패는 사용자 작업과 격리한다.
  try {
    trackEvent(name, properties);
  } catch {
    // 수집 실패는 사용자 작업에 영향을 주지 않는다.
  }
}
