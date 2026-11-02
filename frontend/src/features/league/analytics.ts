import { trackEvent } from '../../analytics';
import { ApiError } from '../../api/client';
import type { CollectionStatus, TransactionType } from './types';

export type ReviewReminderEntryPoint = 'ranking' | 'holdings' | 'source_holdings';

type Surface =
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
  | 'review_coach'
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
  | 'review_guide'
  | 'dismiss_reminder'
  | 'snooze_reminder'
  | 'save_publication'
  | 'change_daily_week'
  | 'toggle_daily_history';
type ErrorType = 'unauthorized' | 'client_error' | 'server_error' | 'network_or_unknown';
type Event =
  | 'view_league_review_reminder'
  | 'league_review_reminder_action'
  | 'league_review_saved'
  | 'league_review_article_click'
  | 'view_league'
  | 'change_league_week'
  | 'view_investor_profile'
  | 'publish_portfolio'
  | 'save_investment_decision'
  | 'save_investment_review'
  | 'edit_investment_review'
  | 'league_action'
  | 'league_load_result';
type Params = {
  surface?: Surface;
  entry_point?: ReviewReminderEntryPoint;
  target?: 'bubble' | 'cat' | 'entry' | 'minimize' | 'hide' | 'later';
  position?: number;
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
  guide_used?: boolean;
  retrieval_status?: 'FOUND' | 'NO_EVIDENCE';
  duration_ms?: number;
};
const keys = new Set([
  'surface',
  'entry_point',
  'target',
  'position',
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
  'guide_used',
  'retrieval_status',
  'duration_ms',
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
