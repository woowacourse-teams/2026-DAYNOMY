import { isStaffHostname } from './serviceDomain';

export type AnalyticsParams = Record<string, string | number | boolean | undefined>;

const analyticsPropertyKeys = new Set([
  'action',
  'aggregation_reason',
  'basis_relation',
  'baseline_only',
  'closed_day_count',
  'collection_status',
  'confirmed',
  'delayed',
  'has_expected_at',
  'has_first_return_expected_at',
  'has_rank',
  'has_result',
  'journey_source',
  'market_scope',
  'overwrite_existing',
  'page',
  'participating',
  'pending_day_count',
  'ready_to_join',
  'source_managed_count',
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

const posthogToken = import.meta.env?.VITE_POSTHOG_PROJECT_TOKEN;
const posthogHost = import.meta.env?.VITE_POSTHOG_HOST;
let stopped = false;
let posthogClient: Promise<(typeof import('posthog-js'))['default'] | null> | undefined;
let activePosthog: (typeof import('posthog-js'))['default'] | undefined;

const measurementId = import.meta.env?.GA_MEASUREMENT_ID;
let initialized = false;
let lastPageLocation = '';
let currentPageReferrer = '';
let lastLeagueVisitAt: number | undefined;

// SPA 메모리에서만 30분간 리그 이후 행동을 구분한다. 계정·URL·투자 내역은 저장하지 않는다.
function leagueJourney() {
  const path = window.location.pathname;
  if (/^\/league(?:\/|$)/.test(path) || path === '/portfolio/publication')
    lastLeagueVisitAt = Date.now();
  return lastLeagueVisitAt !== undefined && Date.now() - lastLeagueVisitAt < 30 * 60_000
    ? { journey_source: 'league' }
    : {};
}

function isPublicPage(pathname: string) {
  const section = pathname.split('/')[1]?.toLowerCase();
  return section !== 'login' && section !== 'admin';
}

function cleanUrl(value: string) {
  if (!value) return '';
  const url = new URL(value, window.location.origin);
  const path = url.pathname.replace(
    /^\/league\/(?!(?:following|portfolio|profile|reviews)\/*$)[^/]+\/*$/i,
    '/league/investor',
  );
  return `${url.origin}${path}`;
}

function canTrack() {
  if (typeof window === 'undefined') return false;
  const disableKey = `ga-disable-${measurementId}`;
  if (isStaffHostname(window.location.hostname) || !isPublicPage(window.location.pathname)) {
    const wasStopped = stopped;
    stopped = true;
    if (!wasStopped) {
      try {
        activePosthog?.stopSessionRecording();
      } catch {
        // before_send still rejects every event if stopping the recorder fails.
      }
    }
    if (measurementId) Reflect.set(window, disableKey, true);
    return false;
  }
  return (
    !stopped &&
    (Boolean(measurementId) || Boolean(posthogToken && posthogHost)) &&
    Reflect.get(window, disableKey) !== true
  );
}

// SDK session-entry properties survive internal navigation, reloads and session rotation.
// Only campaign labels are accepted; search terms and arbitrary query strings stay private.
const campaignKeys = ['utm_source', 'utm_medium', 'utm_campaign'] as const;
function attributionProperties(properties: Record<string, unknown>) {
  const result: AnalyticsParams = {};
  const entryUrl = properties.$session_entry_url;
  if (typeof entryUrl === 'string') {
    try {
      const url = new URL(entryUrl);
      if (url.protocol !== 'https:' && url.protocol !== 'http:') return result;
      result.$session_entry_url = cleanUrl(entryUrl);
      result.$session_entry_pathname = new URL(cleanUrl(entryUrl)).pathname;
      for (const key of campaignKeys) {
        const value = url.searchParams.get(key);
        if (value && /^[a-zA-Z0-9_-]{1,100}$/.test(value)) {
          result[key] = value;
          result[`$session_entry_${key}`] = value;
        }
      }
    } catch {
      // Malformed SDK source URLs must not interrupt analytics or user actions.
    }
  }
  const referrer = properties.$session_entry_referrer;
  let domain = '$direct';
  let source = '';
  if (typeof referrer === 'string' && referrer) {
    try {
      const url = new URL(referrer);
      if (
        (url.protocol === 'https:' || url.protocol === 'http:') &&
        url.origin !== window.location.origin
      ) {
        domain = url.hostname;
        source = `${url.origin}/`;
      }
    } catch {
      // No usable referrer is treated as direct/unknown traffic.
    }
  }
  result.$referring_domain = domain;
  result.$session_entry_referring_domain = domain;
  result.$session_entry_referrer = source;
  return result;
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
    ...leagueJourney(),
    page_path: new URL(pageLocation).pathname,
    page_location: pageLocation,
    page_referrer: pageReferrer,
  };
  window.gtag?.('event', 'page_view', properties);
  capturePostHog('$pageview', properties);
}

export function trackEvent(name: string, params: AnalyticsParams = {}) {
  if (!canTrack()) return;
  initAnalytics();
  const properties = {
    ...leagueJourney(),
    ...Object.fromEntries(
      Object.entries(params).filter(
        ([key, value]) =>
          analyticsPropertyKeys.has(key) && key !== 'journey_source' && value !== undefined,
      ),
    ),
    page_path: new URL(cleanUrl(window.location.href)).pathname,
    page_location: cleanUrl(window.location.href),
    page_referrer: currentPageReferrer || cleanUrl(document.referrer),
  };
  window.gtag?.('event', name, properties);
  capturePostHog(name, properties);
}

// Explicit events only: SDK-generated properties are filtered before delivery as well.
const posthogProperties = new Set([
  ...analyticsPropertyKeys,
  // PostHog SDK가 추가한 수집용 프로젝트 토큰은 전송에 필수다.
  'token',
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
  '$recording_status',
  '$current_url',
  '$pathname',
  '$referrer',
]);
const sentEvents = new Set<string>();
const replayProperties = new Set([
  'token',
  'distinct_id',
  '$device_id',
  '$session_id',
  '$window_id',
  '$lib',
  '$lib_version',
  '$snapshot_bytes',
  '$snapshot_data',
  '$snapshot_host',
]);
function capturePostHog(name: string, properties: AnalyticsParams) {
  if (!posthogToken || !posthogHost || !canTrack()) return;
  sentEvents.add(name);
  posthogClient ??= import('posthog-js')
    .then(({ default: client }) => {
      if (!canTrack()) return null;
      client.init(posthogToken, {
        api_host: posthogHost,
        autocapture: false,
        capture_pageview: false,
        capture_pageleave: false,
        capture_performance: false,
        capture_dead_clicks: false,
        capture_exceptions: false,
        disable_session_recording: false,
        disable_surveys: true,
        disable_conversations: true,
        disable_product_tours: true,
        disable_external_dependency_loading: false,
        advanced_disable_flags: false,
        advanced_disable_feature_flags: true,
        enable_recording_console_log: false,
        disable_capture_url_hashes: true,
        session_recording: {
          maskAllInputs: true,
          maskTextSelector: '*',
          maskAllElementAttributes: false,
          blockSelector:
            'img, svg, canvas, iframe, object, embed, input[type="hidden"], input[type="file"]',
          // Keep CSS classes/stylesheets so pointer positions remain understandable.
          // Text, identifying attributes, links and inline styles are masked.
          maskAttributeFn: (name, value, element) => {
            if (name === 'class') return value;
            if (name === 'rel' && element?.tagName === 'LINK') return value;
            if (name === 'href' && element?.getAttribute('rel') === 'stylesheet') {
              return cleanUrl(value);
            }
            return '';
          },
          recordHeaders: false,
          recordBody: false,
          captureCanvas: { recordCanvas: false },
          // Also sanitizes replay navigation URLs before rrweb snapshots are encoded.
          maskCapturedNetworkRequestFn: (request) => {
            if (!canTrack() || request.method) return null;
            try {
              return { ...request, name: cleanUrl(request.name) };
            } catch {
              return null;
            }
          },
        },
        person_profiles: 'never',
        persistence: 'localStorage',
        ip: false,
        save_campaign_params: false,
        save_referrer: false,
        before_send: (event) => {
          if (!event || !canTrack()) return null;
          delete event.$set;
          delete event.$set_once;
          if (event.event === '$snapshot') {
            // Replay payloads contain masked rrweb arrays, not scalar analytics properties.
            event.properties = Object.fromEntries(
              Object.entries(event.properties).filter(([key]) => replayProperties.has(key)),
            );
            return event;
          }
          if (!sentEvents.has(event.event)) return null;
          const attribution = attributionProperties(event.properties);
          event.properties = Object.fromEntries(
            Object.entries(event.properties).filter(
              ([key, value]) =>
                posthogProperties.has(key) &&
                ['string', 'number', 'boolean'].includes(typeof value),
            ),
          );
          Object.assign(event.properties, attribution);
          event.properties.$current_url = event.properties.page_location;
          event.properties.$pathname = event.properties.page_path;
          event.properties.$referrer = event.properties.page_referrer;
          return event;
        },
      });
      activePosthog = client;
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
