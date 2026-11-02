import { trackEvent } from '../../analytics';
import { ApiError } from '../../api/client';
import type { CollectionStatus, TransactionType, Performance } from './types';

type Surface =
  | 'service_header'
  | 'ranking'
  | 'investor'
  | 'investor_details'
  | 'ranking_weeks'
  | 'own_profile'
  | 'following'
  | 'holdings'
  | 'source_holdings'
  | 'decisions'
  | 'reviews'
  | 'publication'
  | 'daily_history';
type Action =
  | 'participate'
  | 'manage_holdings'
  | 'open_profile'
  | 'open_following'
  | 'open_metrics'
  | 'change_page'
  | 'retry'
  | 'follow'
  | 'unfollow'
  | 'open_import'
  | 'import_holdings'
  | 'change_visibility'
  | 'remove_holding'
  | 'open_decision'
  | 'save_decision'
  | 'open_reason'
  | 'edit_reason'
  | 'open_review'
  | 'save_review'
  | 'edit_review'
  | 'save_publication'
  | 'join'
  | 'update'
  | 'leave'
  | 'open_dashboard'
  | 'change_daily_week'
  | 'toggle_daily_history';
type ErrorType = 'unauthorized' | 'client_error' | 'server_error' | 'network_or_unknown';
type Event =
  | 'view_league'
  | 'change_league_week'
  | 'view_investor_profile'
  | 'publish_portfolio'
  | 'save_investment_decision'
  | 'save_investment_review'
  | 'edit_investment_review'
  | 'league_action'
  | 'league_load_result'
  | 'league_enrollment_viewed'
  | 'league_enrollment'
  | 'league_aggregation_viewed'
  | 'league_aggregation_refresh'
  | 'league_ranking_viewed'
  | 'league_performance_viewed';
type Params = {
  surface?: Surface;
  action?: Action;
  status?: 'clicked' | 'submitted' | 'success' | 'error';
  error_type?: ErrorType;
  source?: 'ranking' | 'following' | 'direct';
  rank_bucket?: 'top_3' | 'top_10' | 'other' | 'unknown';
  league_type?: 'WEEKLY_RETURN';
  week_state?: 'current' | 'confirmed' | 'in_progress';
  relative_week?: number;
  record_type?: TransactionType;
  origin?: 'shared_portfolio';
  scope?: 'details' | 'private';
  result_count?: number;
  page?: number;
  confirmed?: boolean;
  collection_status?: CollectionStatus;
  hidden?: boolean;
  overwrite_existing?: boolean;
  view?: 'daily' | 'weekly';
  market_scope?: 'none' | 'domestic' | 'us' | 'mixed';
  has_holdings?: boolean;
  participating?: boolean;
  ready_to_join?: boolean;
  has_result?: boolean;
  has_rank?: boolean;
  basis_relation?: ReturnType<typeof leagueBasisRelation>;
  aggregation_reason?: string;
  delayed?: boolean;
  has_expected_at?: boolean;
  has_first_return_expected_at?: boolean;
  trigger?: 'manual' | 'scheduled';
  pending_day_count?: number;
  closed_day_count?: number;
  baseline_only?: boolean;
  source_managed_count?: number;
};
const keys = new Set([
  'surface',
  'action',
  'status',
  'error_type',
  'source',
  'rank_bucket',
  'league_type',
  'week_state',
  'relative_week',
  'record_type',
  'origin',
  'scope',
  'result_count',
  'page',
  'confirmed',
  'collection_status',
  'hidden',
  'overwrite_existing',
  'view',
  'market_scope',
  'has_holdings',
  'participating',
  'ready_to_join',
  'has_result',
  'has_rank',
  'basis_relation',
  'aggregation_reason',
  'delayed',
  'has_expected_at',
  'has_first_return_expected_at',
  'trigger',
  'pending_day_count',
  'closed_day_count',
  'baseline_only',
  'source_managed_count',
]);

export function leagueErrorType(error: unknown): ErrorType {
  if (error instanceof ApiError) {
    if (error.status === 401 || error.status === 403) return 'unauthorized';
    if (error.status >= 500) return 'server_error';
    if (error.status >= 400) return 'client_error';
  }
  return 'network_or_unknown';
}

export function trackLeagueEvent(name: Event, params: Params = {}) {
  const properties = Object.fromEntries(
    Object.entries(params).filter(([key, value]) => keys.has(key) && value !== undefined),
  );
  try {
    trackEvent(name, properties);
  } catch {
    // 분석 전송 실패가 저장 결과나 사용자 동작을 바꾸지 않도록 한다.
  }
}

export function leagueBasisRelation(
  performance: Pick<Performance, 'asOfDate' | 'rankingAsOfDate'>,
) {
  const { asOfDate, rankingAsOfDate } = performance;
  if (!asOfDate || !rankingAsOfDate) return 'unknown';
  return asOfDate > rankingAsOfDate
    ? 'personal_ahead'
    : asOfDate < rankingAsOfDate
      ? 'personal_behind'
      : 'same';
}
