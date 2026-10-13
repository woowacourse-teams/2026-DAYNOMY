import { useEffect, useRef, type RefObject } from 'react';
import { trackEvent, type AnalyticsParams } from '../../../analytics';
import type { FeedItem } from './feed';

export function feedProperties(item: FeedItem, surface: string, position: number): AnalyticsParams {
  return {
    surface,
    content_id: item.key,
    content_type: item.source === 'DAYNOMY' ? 'news' : 'post' in item ? 'person_post' : 'external',
    source_type: item.source,
    position,
  };
}

export function trackLoadResult(
  surface: string,
  area: string,
  startedAt: number,
  status: string,
  resultCount: number,
  attempt: number,
) {
  trackEvent('content_load_result', {
    surface,
    area,
    status,
    result_count: resultCount,
    attempt,
    duration_ms: Math.max(0, Math.round(performance.now() - startedAt)),
  });
}

// A view is emitted once per consecutive settled state, including direct URL selections.
export function useContentView(enabled: boolean, name: string, params: AnalyticsParams) {
  const previous = useRef('');
  const serialized = JSON.stringify(params);
  useEffect(() => {
    if (!enabled) {
      previous.current = '';
      return;
    }
    const key = name + serialized;
    if (previous.current === key) return;
    previous.current = key;
    trackEvent(name, JSON.parse(serialized));
  }, [enabled, name, serialized]);
}

// Count actual visibility once per key during this mount, not each carousel rotation/render.
export function useContentImpression<T extends HTMLElement>(
  ref: RefObject<T | null>,
  key: string,
  name: string,
  params: AnalyticsParams,
  enabled = true,
) {
  const seen = useRef(new Set<string>());
  const serialized = JSON.stringify(params);
  useEffect(() => {
    const element = ref.current;
    if (
      !enabled ||
      !element ||
      seen.current.has(key) ||
      typeof IntersectionObserver === 'undefined'
    )
      return;
    let visible = false;
    const record = () => {
      if (!visible || document.hidden || seen.current.has(key)) return;
      seen.current.add(key);
      trackEvent(name, JSON.parse(serialized));
      observer.disconnect();
    };
    const observer = new IntersectionObserver(
      (entries) => {
        visible = entries.some((entry) => entry.isIntersecting && entry.intersectionRatio >= 0.5);
        record();
      },
      { threshold: 0.5 },
    );
    observer.observe(element);
    document.addEventListener('visibilitychange', record);
    return () => {
      observer.disconnect();
      document.removeEventListener('visibilitychange', record);
    };
  }, [ref, key, name, serialized, enabled]);
}
