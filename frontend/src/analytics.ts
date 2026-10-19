export type AnalyticsParams = Record<string, string | number | boolean | undefined>;

const analyticsPropertyKeys = new Set([
  'action',
  'analyzed_asset_count',
  'area',
  'asset_count',
  'attempt',
  'category',
  'content_id',
  'content_type',
  'destination',
  'direction',
  'duration_ms',
  'entry_point',
  'error_type',
  'exit_reason',
  'expanded',
  'filter',
  'has_holdings',
  'hidden',
  'is_logged_in',
  'keyword_category',
  'last_check_outcome',
  'league_type',
  'market',
  'mode',
  'news_id',
  'operation',
  'origin',
  'page_number',
  'person_id',
  'period',
  'position',
  'previous_page',
  'previous_source',
  'pricing_status',
  'range_days',
  'rank_bucket',
  'reason',
  'record_type',
  'relative_week',
  'result_count',
  'scope',
  'search_length',
  'selection_valid',
  'sort',
  'source',
  'source_count',
  'source_type',
  'stage',
  'status',
  'status_code',
  'step_group',
  'step_id',
  'step_number',
  'storage',
  'surface',
  'tab',
  'target',
  'to_step',
  'total_steps',
  'tour_source',
  'tour_version',
  'transaction_type',
  'trigger',
  'user_type',
  'view',
  'week_state',
]);

declare global {
  interface Window {
    dataLayer: unknown[];
    gtag?: (...args: unknown[]) => void;
  }
}

const posthogKey = import.meta.env?.VITE_POSTHOG_KEY;
const posthogHost = import.meta.env?.VITE_POSTHOG_HOST;
let stopped = false;
let posthogClient: Promise<(typeof import('posthog-js'))['default'] | null> | undefined;

const measurementId = import.meta.env?.GA_MEASUREMENT_ID;
let initialized = false;
let lastPageLocation = '';
let currentPageReferrer = '';

function isPublicPage(pathname: string) {
  const section = pathname.split('/')[1]?.toLowerCase();
  return section !== 'login' && section !== 'admin';
}

function cleanUrl(value: string) {
  if (!value) return '';
  const url = new URL(value, window.location.origin);
  const path = url.pathname.replace(
    /^\/league\/(?!(?:following|portfolio)\/*$)[^/]+\/*$/i,
    '/league/investor',
  );
  return `${url.origin}${path}`;
}

function canTrack() {
  if (typeof window === 'undefined') return false;
  const disableKey = `ga-disable-${measurementId}`;
  if (!isPublicPage(window.location.pathname)) {
    stopped = true;
    if (measurementId) Reflect.set(window, disableKey, true);
    return false;
  }
  return (
    !stopped &&
    (Boolean(measurementId) || Boolean(posthogKey && posthogHost)) &&
    Reflect.get(window, disableKey) !== true
  );
}

export function initAnalytics() {
  if (initialized || !measurementId || typeof document === 'undefined' || !canTrack()) return;
  initialized = true;
  window.dataLayer = window.dataLayer || [];
  window.gtag = function gtag() {
    window.dataLayer.push(arguments);
  };
  window.gtag('js', new Date());
  window.gtag('config', measurementId, {
    send_page_view: false,
    page_location: cleanUrl(window.location.href),
    page_referrer: cleanUrl(document.referrer),
  });
  const script = document.createElement('script');
  script.async = true;
  script.src = `https://www.googletagmanager.com/gtag/js?id=${measurementId}`;
  document.head.appendChild(script);
}

export function trackPageView(path: string) {
  if (!canTrack()) {
    lastPageLocation = '';
    return;
  }
  initAnalytics();
  const pageLocation = cleanUrl(path);
  if (pageLocation === lastPageLocation) return;
  const pageReferrer = lastPageLocation || cleanUrl(document.referrer);
  lastPageLocation = pageLocation;
  currentPageReferrer = pageReferrer;
  window.gtag?.('config', measurementId, {
    update: true,
    send_page_view: false,
    page_location: pageLocation,
    page_referrer: pageReferrer,
  });
  const properties = {
    page_path: new URL(pageLocation).pathname,
    page_location: pageLocation,
    page_referrer: pageReferrer,
  };
  window.gtag?.('event', 'page_view', properties);
  capturePostHog('$pageview', properties);
}

export function trackEvent(name: string, params: AnalyticsParams = {}) {
  if (!canTrack()) return;
  const properties = {
    ...Object.fromEntries(
      Object.entries(params).filter(
        ([key, value]) => analyticsPropertyKeys.has(key) && value !== undefined,
      ),
    ),
    page_path: new URL(cleanUrl(window.location.href)).pathname,
    page_location: cleanUrl(window.location.href),
    page_referrer: currentPageReferrer || cleanUrl(document.referrer),
  };
  try {
    initAnalytics();
    window.gtag?.('event', name, properties);
  } catch {
    // GA 전송 실패가 분석·저장 처리와 PostHog 전송을 중단하지 않게 한다.
  }
  capturePostHog(name, properties);
}

// Explicit events only: SDK-generated properties are filtered before delivery as well.
const posthogProperties = new Set([
  ...analyticsPropertyKeys,
  'page_path',
  'page_location',
  'page_referrer',
  'distinct_id',
  '$device_id',
  '$session_id',
  '$window_id',
  '$lib',
  '$lib_version',
  '$browser',
  '$browser_version',
  '$os',
  '$os_version',
  '$device_type',
  '$screen_height',
  '$screen_width',
  '$viewport_height',
  '$viewport_width',
  '$process_person_profile',
  '$current_url',
  '$pathname',
  '$referrer',
]);
const sentEvents = new Set<string>();
function capturePostHog(name: string, properties: AnalyticsParams) {
  if (!posthogKey || !posthogHost || !canTrack()) return;
  sentEvents.add(name);
  posthogClient ??= import('posthog-js')
    .then(({ default: client }) => {
      if (!canTrack()) return null;
      client.init(posthogKey, {
        api_host: posthogHost,
        autocapture: false,
        capture_pageview: false,
        capture_pageleave: false,
        capture_performance: false,
        capture_dead_clicks: false,
        capture_exceptions: false,
        disable_session_recording: true,
        disable_surveys: true,
        disable_external_dependency_loading: true,
        advanced_disable_flags: true,
        person_profiles: 'never',
        persistence: 'localStorage',
        ip: false,
        save_campaign_params: false,
        save_referrer: false,
        before_send: (event) => {
          if (!event || !canTrack() || !sentEvents.has(event.event)) return null;
          event.properties = Object.fromEntries(
            Object.entries(event.properties).filter(
              ([key, value]) =>
                posthogProperties.has(key) &&
                ['string', 'number', 'boolean'].includes(typeof value),
            ),
          );
          event.properties.$current_url = event.properties.page_location;
          event.properties.$pathname = event.properties.page_path;
          event.properties.$referrer = event.properties.page_referrer;
          return event;
        },
      });
      return client;
    })
    .catch(() => null);
  void posthogClient
    .then((client) => {
      if (!client || !canTrack()) return;
      client.capture(name, {
        ...properties,
        $current_url: properties.page_location,
        $pathname: properties.page_path,
        $referrer: properties.page_referrer,
      });
    })
    .catch(() => {
      /* Analytics must not interrupt user actions. */
    });
}
