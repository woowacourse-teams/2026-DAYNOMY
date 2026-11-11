import { trackEvent } from '../../analytics';
import { ApiError } from '../../api/client';
import type { PortfolioTrendPeriod, SavedPortfolioAnalysisResponse } from './types';
import type { AssetCategory, StockMarket } from '../stocks/types';

const EVENTS = [
  'portfolio_tour_started',
  'portfolio_tour_step_viewed',
  'portfolio_tour_step_changed',
  'portfolio_tour_exited',
  'portfolio_tour_completed',
  'portfolio_tour_register_clicked',
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
  'portfolio_bulk_opened',
  'portfolio_bulk_save_submitted',
  'portfolio_bulk_save_result',
  'portfolio_bulk_closed',
  'portfolio_csv_read_result',
  'portfolio_csv_mapping_changed',
  'portfolio_csv_preview_ready',
  'portfolio_csv_preview_failed',
  'portfolio_search_result',
  'portfolio_validation_failed',
  'portfolio_editor_closed',
  'portfolio_analysis_cta_viewed',
  'portfolio_analysis_completed',
] as const;

type Operation = 'holding_update' | 'transaction_create' | 'transaction_update';
type ErrorType = 'unauthorized' | 'client_error' | 'server_error' | 'network_or_unknown';
type PortfolioEventParams = {
  tour_version?: string;
  tour_source?: 'automatic' | 'title';
  step_number?: number;
  step_id?: string;
  step_group?: 'registration' | 'news' | 'analysis';
  total_steps?: number;
  to_step?: number;
  direction?: 'previous' | 'next';
  exit_reason?: 'button' | 'escape';
  is_logged_in?: boolean;
  has_holdings?: boolean;
  market_scope?: ReturnType<typeof portfolioMarketScope>;
  pricing_status?: 'COMPLETE' | 'PARTIAL' | 'MIXED_DATES';
  stage?:
    | 'holdings'
    | 'calculation'
    | 'chart'
    | 'history'
    | 'file'
    | 'parse'
    | 'matching'
    | 'search'
    | 'input'
    | 'validation_error'
    | 'save_error'
    | 'mapping'
    | 'preview';
  error_type?: ErrorType;
  hidden?: boolean;
  expanded?: boolean;
  period?: PortfolioTrendPeriod;
  view?: 'inline' | 'dialog';
  category?: 'ALL' | AssetCategory;
  market?: StockMarket;
  tab?: 'holdings' | 'history';
  mode?: Operation | 'holdings' | 'add' | 'csv';
  entry_point?:
    'intro' | 'header' | 'empty_state' | 'ledger' | 'history' | 'holding_detail' | 'editor_switch';
  operation?: Operation;
  storage?: 'server' | 'temporary';
  transaction_type?: 'BUY' | 'SELL';
  target?: 'holding' | 'transaction' | 'dashboard' | 'chart' | 'history';
  sort?:
    'DEFAULT' | 'QUANTITY' | 'PURCHASE' | 'CLOSE' | 'EVALUATION' | 'PROFIT' | 'RETURN' | 'WEIGHT';
  sort_direction?: 'ascending' | 'descending';
  filter?: 'ALL' | 'BUY' | 'SELL';
  range_days?: number;
  duration_ms?: number;
  row_count?: number;
  matched_count?: number;
  unmatched_count?: number;
  skipped_count?: number;
  imported_count?: number;
  csv_kind?: 'balances' | 'transactions';
  mapping_mode?: 'automatic' | 'manual';
  status?:
    | 'success'
    | 'duplicate'
    | 'error'
    | 'READY'
    | 'FAILED'
    | 'ERROR'
    | 'CONFLICT'
    | 'TRIAL_EXHAUSTED'
    | 'REFRESH_LIMIT_EXCEEDED'
    | 'EMPTY'
    | 'PORTFOLIO_CHANGED'
    | 'RECHECK_REQUIRED';
  reason?:
    | 'none'
    | 'loading'
    | 'restoring'
    | 'processing'
    | 'trial_exhausted'
    | 'refresh_limit_exceeded'
    | 'summary_pending'
    | 'invalid_file'
    | 'invalid_csv'
    | 'stock_not_found'
    | 'request_failed'
    | 'invalid_value'
    | 'retry';
  field?: string;
  trigger?: 'blur' | 'submit';
  has_changes?: boolean;
  search_length?: number;
  result_count?: number;
  kr_result_count?: number;
  us_result_count?: number;
  surface?: 'transaction_editor' | 'bulk_editor';
  holding_count_bucket?: '0' | '1' | '2_5' | '6_20' | '21_plus';
  enabled?: boolean;
  user_type?: 'member' | 'guest';
  action?: 'analyze' | 'refresh' | 'check_status' | 'login' | 'retry';
  asset_count?: number;
  summary_status?: string;
  last_check_outcome?: Exclude<SavedPortfolioAnalysisResponse['lastCheckOutcome'], null>;
};

const PARAM_KEYS = new Set([
  'tour_version',
  'tour_source',
  'step_number',
  'step_id',
  'step_group',
  'total_steps',
  'to_step',
  'direction',
  'exit_reason',
  'is_logged_in',
  'has_holdings',
  'market_scope',
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
  'sort_direction',
  'filter',
  'range_days',
  'duration_ms',
  'row_count',
  'matched_count',
  'unmatched_count',
  'skipped_count',
  'imported_count',
  'csv_kind',
  'mapping_mode',
  'status',
  'reason',
  'field',
  'trigger',
  'has_changes',
  'search_length',
  'result_count',
  'kr_result_count',
  'us_result_count',
  'surface',
  'holding_count_bucket',
  'enabled',
  'user_type',
  'action',
  'asset_count',
  'summary_status',
  'last_check_outcome',
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

export function portfolioMarketScope(
  holdings: readonly { currency?: string; market: string }[],
): 'none' | 'domestic' | 'us' | 'mixed' {
  const domestic = holdings.some(
    (holding) => holding.currency === 'KRW' || ['KOSPI', 'KOSDAQ'].includes(holding.market),
  );
  const us = holdings.some(
    (holding) =>
      holding.currency === 'USD' ||
      ['NASDAQ', 'NYSE', 'NYSE_ARCA', 'NYSE_AMERICAN', 'BATS'].includes(holding.market),
  );
  return domestic && us ? 'mixed' : domestic ? 'domestic' : us ? 'us' : 'none';
}
