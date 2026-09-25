type AnalyticsParams = Record<string, string | number | boolean | undefined>;

declare global {
  interface Window {
    dataLayer: unknown[];
    gtag?: (...args: unknown[]) => void;
  }
}

const measurementId = import.meta.env.GA_MEASUREMENT_ID;
let initialized = false;
let lastPageLocation = '';
let currentPageReferrer = '';

function isPublicPage(pathname: string) {
  const section = pathname.split('/')[1];
  return section !== 'login' && section !== 'admin';
}

function cleanUrl(value: string) {
  if (!value) return '';
  const url = new URL(value, window.location.origin);
  return `${url.origin}${url.pathname}`;
}

function canTrack() {
  if (!measurementId) return false;
  const disableKey = `ga-disable-${measurementId}`;
  if (!isPublicPage(window.location.pathname)) {
    Reflect.set(window, disableKey, true);
    return false;
  }
  return Reflect.get(window, disableKey) !== true;
}

export function initAnalytics() {
  if (initialized || typeof document === 'undefined' || !canTrack()) return;
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
  const pathname = new URL(path, window.location.origin).pathname;
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
  window.gtag?.('event', 'page_view', {
    page_path: pathname,
    page_location: pageLocation,
    page_referrer: pageReferrer,
  });
}

export function trackEvent(name: string, params: AnalyticsParams = {}) {
  if (!canTrack()) return;
  initAnalytics();
  window.gtag?.('event', name, {
    ...params,
    page_path: window.location.pathname,
    page_location: cleanUrl(window.location.href),
    page_referrer: currentPageReferrer || cleanUrl(document.referrer),
  });
}
