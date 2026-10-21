import { request, requestWithCsrf } from '../../../api/client';
import type { StockRelatedContent } from '../../stock-content/types';
import type { FeedItem } from './feed';

export type PopularityContentType = 'NEWS' | 'PERSON_POST' | 'ASSET_CONTENT';
export type ContentInteractionType = 'OPEN' | 'OUTBOUND_CLICK';
export type ContentReference = { contentType: PopularityContentType; contentId: number };
export type PopularityScores = ReadonlyMap<string, number>;

const VISITOR_STORAGE_KEY = 'daynomy-content-visitor-v1';
const INTERACTION_STORAGE_KEY = 'daynomy-content-interactions-v1';
let fallbackVisitorId: string | null = null;
const pendingInteractions = new Set<string>();

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isUuid(value: unknown): value is string {
  return (
    typeof value === 'string' &&
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value)
  );
}

function uuid() {
  return crypto.randomUUID();
}

function storage(): Storage | null {
  try {
    return window.localStorage;
  } catch {
    return null;
  }
}

function visitorId() {
  const localStorage = storage();
  const stored = localStorage?.getItem(VISITOR_STORAGE_KEY);
  if (isUuid(stored)) return stored;
  if (!fallbackVisitorId) fallbackVisitorId = uuid();
  try {
    localStorage?.setItem(VISITOR_STORAGE_KEY, fallbackVisitorId);
  } catch {
    // A session-only identifier still permits best-effort interaction recording.
  }
  return fallbackVisitorId;
}

function seoulDate() {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date());
}

function storedInteractionKeys() {
  try {
    const parsed: unknown = JSON.parse(storage()?.getItem(INTERACTION_STORAGE_KEY) ?? '[]');
    return Array.isArray(parsed) && parsed.every((item) => typeof item === 'string')
      ? new Set(parsed.filter((item) => item.startsWith(seoulDate())))
      : new Set<string>();
  } catch {
    return new Set<string>();
  }
}

function saveInteractionKeys(keys: Set<string>) {
  try {
    storage()?.setItem(INTERACTION_STORAGE_KEY, JSON.stringify([...keys]));
  } catch {
    // Storage failures must not block content navigation.
  }
}

export function popularityKey(reference: ContentReference) {
  return `${reference.contentType}:${reference.contentId}`;
}

export function feedContentReference(item: FeedItem): ContentReference {
  if ('article' in item) return { contentType: 'NEWS', contentId: Number(item.article.id) };
  if ('post' in item) return { contentType: 'PERSON_POST', contentId: item.post.id };
  return { contentType: 'ASSET_CONTENT', contentId: item.content.id };
}

export function stockContentReference(item: StockRelatedContent): ContentReference {
  if (item.sourceType === 'INTERNAL_NEWS') {
    const newsId = item.url.match(/^\/news\/(\d+)\/?$/)?.[1];
    if (newsId) return { contentType: 'NEWS', contentId: Number(newsId) };
  }
  return { contentType: 'ASSET_CONTENT', contentId: item.id };
}

export async function getContentPopularity(signal?: AbortSignal): Promise<PopularityScores> {
  const response = await request<unknown>('/api/content-rankings?periodDays=7', { signal });
  if (!isRecord(response) || response.periodDays !== 7 || !Array.isArray(response.items)) {
    throw new Error('인기순 응답 형식이 올바르지 않습니다.');
  }
  const scores = new Map<string, number>();
  for (const item of response.items) {
    if (
      !isRecord(item) ||
      !['NEWS', 'PERSON_POST', 'ASSET_CONTENT'].includes(String(item.contentType)) ||
      !Number.isSafeInteger(item.contentId) ||
      Number(item.contentId) <= 0 ||
      !Number.isSafeInteger(item.score) ||
      Number(item.score) <= 0 ||
      typeof item.lastInteractedAt !== 'string'
    ) {
      throw new Error('인기순 응답 형식이 올바르지 않습니다.');
    }
    scores.set(`${item.contentType}:${item.contentId}`, Number(item.score));
  }
  return scores;
}

export function sortByPopularity<T>(
  items: T[],
  reference: (item: T) => ContentReference,
  scores: PopularityScores,
) {
  return items
    .map((item, index) => ({ item, index }))
    .sort(
      (left, right) =>
        (scores.get(popularityKey(reference(right.item))) ?? 0) -
          (scores.get(popularityKey(reference(left.item))) ?? 0) || left.index - right.index,
    )
    .map(({ item }) => item);
}

export async function recordContentInteraction(
  reference: ContentReference,
  interactionType: ContentInteractionType,
) {
  const interactionKey = `${seoulDate()}:${popularityKey(reference)}:${interactionType}`;
  const recorded = storedInteractionKeys();
  if (recorded.has(interactionKey) || pendingInteractions.has(interactionKey)) return;
  pendingInteractions.add(interactionKey);
  recorded.add(interactionKey);
  saveInteractionKeys(recorded);
  try {
    await requestWithCsrf<void>('/api/content-interactions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        eventId: uuid(),
        visitorId: visitorId(),
        ...reference,
        interactionType,
      }),
    });
  } catch {
    const retryable = storedInteractionKeys();
    retryable.delete(interactionKey);
    saveInteractionKeys(retryable);
  } finally {
    pendingInteractions.delete(interactionKey);
  }
}
