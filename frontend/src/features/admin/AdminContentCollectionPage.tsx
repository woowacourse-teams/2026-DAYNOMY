import { useEffect, useState, type FormEvent } from 'react';
import { ApiError } from '../../api/client';
import { searchStocks } from '../stocks/api';
import type { StockSearchItem } from '../stocks/types';
import {
  approveAdminContentCollectionReview,
  approveAdminContentCollectionReviews,
  collectAdminContentCollectionSource,
  collectAllAdminContentCollectionSources,
  createAdminContentCollectionSource,
  getAdminContentCollectionReviews,
  getAdminContentCollectionSources,
  rematchAdminContentCollectionReviews,
  rejectAdminContentCollectionReview,
  rejectAdminContentCollectionReviews,
  registerAdminContentCollectionContent,
  updateAdminContentCollectionSource,
} from './api';
import type {
  AdminContentCollectionBatchResult,
  AdminContentCollectionReview,
  AdminContentCollectionSource,
  ContentCollectionSourceType,
} from './types';
import './admin.css';

const SOURCE_LABELS: Record<ContentCollectionSourceType, string> = {
  YOUTUBE: 'YouTube',
  THREADS: 'Threads',
  X: 'X',
};

const SOURCE_PLACEHOLDERS: Record<ContentCollectionSourceType, string> = {
  YOUTUBE: 'https://www.youtube.com/@channel',
  THREADS: 'https://www.threads.net/@account',
  X: 'https://x.com/account',
};

type SelectedAsset = Pick<StockSearchItem, 'assetId' | 'name'>;

function getErrorMessage(error: unknown, fallback: string) {
  return error instanceof ApiError || error instanceof Error ? error.message : fallback;
}

function formatCollectedAt(value: string | null) {
  if (!value) return '수집 전';
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) return value;
  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date);
}

function isValidSourceUrl(sourceType: ContentCollectionSourceType, value: string) {
  try {
    const url = new URL(value);
    const host = url.hostname.toLowerCase().replace(/^www\./, '');
    if (!['http:', 'https:'].includes(url.protocol)) return false;
    if (sourceType === 'YOUTUBE') {
      return (
        host === 'youtube.com' && /^\/(?:@[^/]+|channel\/[^/]+|user\/[^/]+)\/?$/.test(url.pathname)
      );
    }
    if (sourceType === 'THREADS') {
      return ['threads.net', 'threads.com'].includes(host) && /^\/@[^/]+\/?$/.test(url.pathname);
    }
    return ['x.com', 'twitter.com'].includes(host) && /^\/[^/]+\/?$/.test(url.pathname);
  } catch {
    return false;
  }
}

type AdminContentCollectionView = 'sources' | 'reviews';

function AdminContentCollectionPage({ view }: { view: AdminContentCollectionView }) {
  const [sources, setSources] = useState<AdminContentCollectionSource[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [editingId, setEditingId] = useState<number | null>(null);
  const [name, setName] = useState('');
  const [sourceType, setSourceType] = useState<ContentCollectionSourceType>('YOUTUBE');
  const [sourceUrl, setSourceUrl] = useState('');
  const [enabled, setEnabled] = useState(true);
  const [selectedAsset, setSelectedAsset] = useState<SelectedAsset | null>(null);
  const [stockKeyword, setStockKeyword] = useState('');
  const [stockCountry, setStockCountry] = useState<'KR' | 'US'>('KR');
  const [stockResults, setStockResults] = useState<StockSearchItem[]>([]);
  const [stockLoading, setStockLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [collectingId, setCollectingId] = useState<number | null>(null);
  const [collectingAll, setCollectingAll] = useState(false);
  const [batchResult, setBatchResult] = useState<AdminContentCollectionBatchResult | null>(null);
  const [reviews, setReviews] = useState<AdminContentCollectionReview[]>([]);
  const [reviewTotal, setReviewTotal] = useState(0);
  const [reviewLoading, setReviewLoading] = useState(true);
  const [reviewingId, setReviewingId] = useState<number | null>(null);
  const [reviewAssetIds, setReviewAssetIds] = useState<Record<number, number[]>>({});
  const [reviewPublishedDates, setReviewPublishedDates] = useState<Record<number, string>>({});
  const [selectedReviewIds, setSelectedReviewIds] = useState<number[]>([]);
  const [reviewBulkAction, setReviewBulkAction] = useState<'approve' | 'reject' | 'rematch' | null>(
    null,
  );
  const [reviewKeywords, setReviewKeywords] = useState<Record<number, string>>({});
  const [reviewSearchResults, setReviewSearchResults] = useState<Record<number, StockSearchItem[]>>(
    {},
  );
  const [reviewSearchingId, setReviewSearchingId] = useState<number | null>(null);
  const [manualSourceId, setManualSourceId] = useState('');
  const [manualUrl, setManualUrl] = useState('');
  const [manualTitle, setManualTitle] = useState('');
  const [manualDescription, setManualDescription] = useState('');
  const [manualPublishedDate, setManualPublishedDate] = useState('');
  const [manualSaving, setManualSaving] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    if (view === 'sources') {
      void loadSources(controller.signal);
    } else {
      void loadReviews(controller.signal);
      void loadManualSources(controller.signal);
    }
    return () => controller.abort();
  }, [view]);

  async function loadSources(signal?: AbortSignal) {
    setLoading(true);
    try {
      setSources(await getAdminContentCollectionSources(signal));
      setError('');
    } catch (caughtError) {
      if (!signal?.aborted) {
        setError(getErrorMessage(caughtError, '수집 출처를 불러오지 못했습니다.'));
      }
    } finally {
      if (!signal?.aborted) setLoading(false);
    }
  }

  async function loadManualSources(signal?: AbortSignal) {
    try {
      const response = await getAdminContentCollectionSources(signal);
      setSources(response.filter((source) => source.sourceType !== 'YOUTUBE'));
    } catch (caughtError) {
      if (!signal?.aborted) {
        setError(getErrorMessage(caughtError, '수동 등록할 출처를 불러오지 못했습니다.'));
      }
    }
  }

  async function loadReviews(signal?: AbortSignal) {
    setReviewLoading(true);
    try {
      const response = await getAdminContentCollectionReviews(signal);
      setReviews(response.items);
      setReviewTotal(response.totalElements);
      setReviewAssetIds(
        Object.fromEntries(
          response.items.map((review) => [
            review.id,
            review.suggestedAssets.map((asset) => asset.id),
          ]),
        ),
      );
      setReviewPublishedDates(
        Object.fromEntries(response.items.map((review) => [review.id, review.publishedDate ?? ''])),
      );
      setSelectedReviewIds((current) =>
        current.filter((id) => response.items.some((review) => review.id === id)),
      );
    } catch (caughtError) {
      if (!signal?.aborted) {
        setError(getErrorMessage(caughtError, '검수 대기 콘텐츠를 불러오지 못했습니다.'));
      }
    } finally {
      if (!signal?.aborted) setReviewLoading(false);
    }
  }

  function resetForm() {
    setEditingId(null);
    setName('');
    setSourceType('YOUTUBE');
    setSourceUrl('');
    setEnabled(true);
    setSelectedAsset(null);
    setStockKeyword('');
    setStockResults([]);
  }

  function editSource(source: AdminContentCollectionSource) {
    setEditingId(source.id);
    setName(source.name);
    setSourceType(source.sourceType);
    setSourceUrl(source.sourceUrl);
    setEnabled(source.enabled);
    setSelectedAsset(
      source.assetId && source.assetName
        ? { assetId: source.assetId, name: source.assetName }
        : null,
    );
    setStockKeyword('');
    setStockResults([]);
    setError('');
    setNotice('');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  async function submitSource(event: FormEvent) {
    event.preventDefault();
    if (saving) return;
    if (!name.trim()) {
      setError('출처 이름을 입력해 주세요.');
      return;
    }
    if (!isValidSourceUrl(sourceType, sourceUrl.trim())) {
      setError(`${SOURCE_LABELS[sourceType]} 채널 또는 프로필 URL을 확인해 주세요.`);
      return;
    }
    setSaving(true);
    setError('');
    setNotice('');
    try {
      const request = {
        name: name.trim(),
        sourceType,
        sourceUrl: sourceUrl.trim(),
        assetId: selectedAsset?.assetId ?? null,
        enabled,
      };
      const saved = editingId
        ? await updateAdminContentCollectionSource(editingId, request)
        : await createAdminContentCollectionSource(request);
      setSources((current) =>
        editingId
          ? current.map((source) => (source.id === saved.id ? saved : source))
          : [...current, saved].sort((left, right) => left.id - right.id),
      );
      setNotice(editingId ? '수집 출처를 수정했습니다.' : '수집 출처를 등록했습니다.');
      resetForm();
    } catch (caughtError) {
      setError(getErrorMessage(caughtError, '수집 출처를 저장하지 못했습니다.'));
    } finally {
      setSaving(false);
    }
  }

  async function findStocks() {
    const keyword = stockKeyword.trim();
    if (!keyword || stockLoading) return;
    setStockLoading(true);
    setError('');
    try {
      setStockResults(await searchStocks(keyword, undefined, stockCountry));
    } catch (caughtError) {
      setStockResults([]);
      setError(getErrorMessage(caughtError, '종목을 검색하지 못했습니다.'));
    } finally {
      setStockLoading(false);
    }
  }

  async function toggleSource(source: AdminContentCollectionSource) {
    if (saving || collectingAll || collectingId !== null) return;
    setSaving(true);
    setError('');
    setNotice('');
    try {
      const updated = await updateAdminContentCollectionSource(source.id, {
        name: source.name,
        sourceType: source.sourceType,
        sourceUrl: source.sourceUrl,
        assetId: source.assetId,
        enabled: !source.enabled,
      });
      setSources((current) => current.map((item) => (item.id === updated.id ? updated : item)));
      setNotice(updated.enabled ? '예약 수집을 활성화했습니다.' : '예약 수집을 비활성화했습니다.');
    } catch (caughtError) {
      setError(getErrorMessage(caughtError, '수집 상태를 변경하지 못했습니다.'));
    } finally {
      setSaving(false);
    }
  }

  async function collectSource(source: AdminContentCollectionSource) {
    if (collectingId !== null || collectingAll || saving) return;
    setCollectingId(source.id);
    setError('');
    setNotice('');
    try {
      const result = await collectAdminContentCollectionSource(source.id);
      setNotice(
        `${source.name}: ${result.fetchedCount}건 수집, ${result.savedCount}건 저장, ${result.linkedCount}건 종목 후보`,
      );
      await loadSources();
    } catch (caughtError) {
      setError(getErrorMessage(caughtError, '출처를 수집하지 못했습니다.'));
    } finally {
      setCollectingId(null);
    }
  }

  async function collectAll() {
    if (collectingAll || collectingId !== null || saving) return;
    setCollectingAll(true);
    setBatchResult(null);
    setError('');
    setNotice('');
    try {
      const result = await collectAllAdminContentCollectionSources();
      setBatchResult(result);
      setNotice('활성 출처 전체 수집을 완료했습니다.');
      await loadSources();
    } catch (caughtError) {
      setError(getErrorMessage(caughtError, '활성 출처 전체를 수집하지 못했습니다.'));
    } finally {
      setCollectingAll(false);
    }
  }

  async function searchReviewAssets(reviewId: number) {
    const keyword = reviewKeywords[reviewId]?.trim();
    if (!keyword || reviewSearchingId !== null) return;
    setReviewSearchingId(reviewId);
    setError('');
    try {
      const [krStocks, usStocks] = await Promise.all([
        searchStocks(keyword),
        searchStocks(keyword, undefined, 'US'),
      ]);
      const unique = new Map(
        [...krStocks, ...usStocks].map((stock) => [stock.assetId, stock] as const),
      );
      setReviewSearchResults((current) => {
        const accumulated = new Map(
          [...(current[reviewId] ?? []), ...unique.values()].map(
            (stock) => [stock.assetId, stock] as const,
          ),
        );
        return { ...current, [reviewId]: [...accumulated.values()] };
      });
    } catch (caughtError) {
      setError(getErrorMessage(caughtError, '검수할 종목을 검색하지 못했습니다.'));
    } finally {
      setReviewSearchingId(null);
    }
  }

  async function approveReview(review: AdminContentCollectionReview) {
    const assetIds = reviewAssetIds[review.id] ?? [];
    if (assetIds.length === 0 || reviewingId !== null || reviewBulkAction !== null) {
      setError('승인할 종목을 먼저 선택해 주세요.');
      return;
    }
    setReviewingId(review.id);
    setError('');
    setNotice('');
    try {
      await approveAdminContentCollectionReview(
        review.id,
        assetIds,
        review.publishedDate === null ? reviewPublishedDates[review.id] || null : null,
      );
      setNotice('수집 콘텐츠를 승인해 종목 소식에 공개했습니다.');
      await loadReviews();
    } catch (caughtError) {
      setError(getErrorMessage(caughtError, '수집 콘텐츠를 승인하지 못했습니다.'));
    } finally {
      setReviewingId(null);
    }
  }

  async function rejectReview(review: AdminContentCollectionReview) {
    if (
      reviewingId !== null ||
      reviewBulkAction !== null ||
      !window.confirm('이 수집 콘텐츠를 거절할까요?')
    )
      return;
    setReviewingId(review.id);
    setError('');
    setNotice('');
    try {
      await rejectAdminContentCollectionReview(review.id);
      setNotice('수집 콘텐츠를 거절했습니다.');
      await loadReviews();
    } catch (caughtError) {
      setError(getErrorMessage(caughtError, '수집 콘텐츠를 거절하지 못했습니다.'));
    } finally {
      setReviewingId(null);
    }
  }

  function toggleReviewAsset(reviewId: number, assetId: number) {
    setReviewAssetIds((current) => {
      const selected = current[reviewId] ?? [];
      return {
        ...current,
        [reviewId]: selected.includes(assetId)
          ? selected.filter((id) => id !== assetId)
          : [...selected, assetId],
      };
    });
  }

  function toggleReviewSelection(reviewId: number) {
    setSelectedReviewIds((current) =>
      current.includes(reviewId) ? current.filter((id) => id !== reviewId) : [...current, reviewId],
    );
  }

  function toggleAllReviewSelections() {
    const ids = reviews.map((review) => review.id);
    setSelectedReviewIds(
      ids.length > 0 && ids.every((id) => selectedReviewIds.includes(id)) ? [] : ids,
    );
  }

  async function approveSelectedReviews() {
    if (selectedReviewIds.length === 0 || reviewBulkAction !== null || reviewingId !== null) return;
    const missing = selectedReviewIds.filter((id) => (reviewAssetIds[id] ?? []).length === 0);
    if (missing.length > 0) {
      setError(`선택한 콘텐츠 중 ${missing.length}건은 연결 종목이 없습니다.`);
      return;
    }
    if (!window.confirm(`선택한 ${selectedReviewIds.length}건을 승인하고 공개할까요?`)) return;
    setReviewBulkAction('approve');
    setError('');
    setNotice('');
    try {
      await approveAdminContentCollectionReviews(
        selectedReviewIds.map((contentId) => ({
          contentId,
          assetIds: reviewAssetIds[contentId],
          publishedDate:
            reviews.find((review) => review.id === contentId)?.publishedDate === null
              ? reviewPublishedDates[contentId] || null
              : null,
        })),
      );
      setNotice(`수집 콘텐츠 ${selectedReviewIds.length}건을 승인해 공개했습니다.`);
      setSelectedReviewIds([]);
      await loadReviews();
    } catch (caughtError) {
      setError(getErrorMessage(caughtError, '선택한 수집 콘텐츠를 승인하지 못했습니다.'));
    } finally {
      setReviewBulkAction(null);
    }
  }

  async function rejectSelectedReviews() {
    if (selectedReviewIds.length === 0 || reviewBulkAction !== null || reviewingId !== null) return;
    if (!window.confirm(`선택한 ${selectedReviewIds.length}건을 거절할까요?`)) return;
    setReviewBulkAction('reject');
    setError('');
    setNotice('');
    try {
      await rejectAdminContentCollectionReviews(selectedReviewIds);
      setNotice(`수집 콘텐츠 ${selectedReviewIds.length}건을 거절했습니다.`);
      setSelectedReviewIds([]);
      await loadReviews();
    } catch (caughtError) {
      setError(getErrorMessage(caughtError, '선택한 수집 콘텐츠를 거절하지 못했습니다.'));
    } finally {
      setReviewBulkAction(null);
    }
  }

  async function rematchReviews() {
    if (reviewBulkAction !== null || reviewingId !== null) return;
    setReviewBulkAction('rematch');
    setError('');
    setNotice('');
    try {
      const result = await rematchAdminContentCollectionReviews();
      setNotice(
        `미매칭 ${result.processedCount}건 중 ${result.matchedCount}건에서 종목 후보 ${result.suggestionCount}개를 찾았습니다.`,
      );
      await loadReviews();
    } catch (caughtError) {
      setError(getErrorMessage(caughtError, '미매칭 콘텐츠를 다시 매칭하지 못했습니다.'));
    } finally {
      setReviewBulkAction(null);
    }
  }

  async function registerManualContent(event: FormEvent) {
    event.preventDefault();
    if (manualSaving) return;
    const sourceId = Number(manualSourceId);
    if (!Number.isSafeInteger(sourceId) || sourceId <= 0) {
      setError('게시물을 등록할 X 또는 Threads 출처를 선택해 주세요.');
      return;
    }
    if (!manualUrl.trim()) {
      setError('게시물 URL을 입력해 주세요.');
      return;
    }
    setManualSaving(true);
    setError('');
    setNotice('');
    try {
      const result = await registerAdminContentCollectionContent(sourceId, {
        url: manualUrl.trim(),
        title: manualTitle.trim() || null,
        description: manualDescription.trim() || null,
        publishedDate: manualPublishedDate || null,
      });
      setManualUrl('');
      setManualTitle('');
      setManualDescription('');
      setManualPublishedDate('');
      setNotice(
        result.linkedCount > 0
          ? `게시물을 검수 대기에 등록하고 종목 후보 ${result.linkedCount}개를 자동 연결했습니다.`
          : '게시물을 검수 대기에 등록했습니다. 자동 연결된 종목이 없어 검수에서 직접 선택해 주세요.',
      );
      await loadReviews();
    } catch (caughtError) {
      setError(getErrorMessage(caughtError, '게시물을 등록하지 못했습니다.'));
    } finally {
      setManualSaving(false);
    }
  }

  const busy = saving || collectingAll || collectingId !== null;

  return (
    <main className="admin-content admin-content-collection-page">
      <div className="admin-page-heading">
        <div>
          <h1>{view === 'sources' ? '소식 출처 관리' : '소식 승인·거절'}</h1>
          <p>
            {view === 'sources'
              ? '경제 YouTube 채널과 기업 공식 Threads·X 계정을 등록하고 소식을 수집합니다.'
              : '수집된 소식의 종목과 게시일을 확인한 뒤 공개 여부를 결정합니다.'}
          </p>
        </div>
        {view === 'sources' ? (
          <button
            type="button"
            className="admin-primary-button"
            disabled={busy || sources.every((source) => !source.enabled)}
            onClick={collectAll}
          >
            {collectingAll ? '전체 수집 중…' : '활성 출처 전체 수집'}
          </button>
        ) : null}
      </div>

      {notice ? (
        <section className="admin-success-panel" role="status" aria-live="polite">
          <strong>{notice}</strong>
        </section>
      ) : null}
      {error ? (
        <section className="admin-alert" role="alert">
          <strong>{error}</strong>
        </section>
      ) : null}
      {view === 'sources' && batchResult ? (
        <section className="admin-collection-summary" aria-label="전체 수집 결과">
          <strong>
            출처 {batchResult.sourceCount}개 중 {batchResult.successCount}개 성공
          </strong>
          <dl>
            <div>
              <dt>실패</dt>
              <dd>{batchResult.failureCount}개</dd>
            </div>
            <div>
              <dt>수집</dt>
              <dd>{batchResult.fetchedCount}건</dd>
            </div>
            <div>
              <dt>신규 저장</dt>
              <dd>{batchResult.savedCount}건</dd>
            </div>
            <div>
              <dt>중복</dt>
              <dd>{batchResult.duplicateCount}건</dd>
            </div>
            <div>
              <dt>종목 후보</dt>
              <dd>{batchResult.linkedCount}건</dd>
            </div>
          </dl>
        </section>
      ) : null}

      {view === 'reviews' ? (
        <>
          <section className="admin-collection-manual" aria-labelledby="collection-manual-title">
            <div className="admin-person-section-heading">
              <div>
                <h2 id="collection-manual-title">수동 게시물 등록</h2>
                <p>
                  등록된 X·Threads 출처의 게시물 URL을 넣으면 종목 후보를 자동으로 찾아 검수 대기에
                  저장합니다.
                </p>
              </div>
            </div>
            <form className="admin-collection-manual-form" onSubmit={registerManualContent}>
              <label className="admin-field">
                <span>출처</span>
                <select
                  aria-label="수동 게시물 출처"
                  value={manualSourceId}
                  onChange={(event) => setManualSourceId(event.target.value)}
                  required
                >
                  <option value="">X·Threads 출처 선택</option>
                  {sources.map((source) => (
                    <option key={source.id} value={source.id}>
                      {SOURCE_LABELS[source.sourceType]} · {source.name}
                    </option>
                  ))}
                </select>
              </label>
              <label className="admin-field admin-collection-manual-url">
                <span>게시물 URL</span>
                <input
                  type="url"
                  value={manualUrl}
                  placeholder="https://x.com/account/status/..."
                  onChange={(event) => setManualUrl(event.target.value)}
                  required
                />
              </label>
              <label className="admin-field">
                <span>게시일 (선택)</span>
                <input
                  type="date"
                  value={manualPublishedDate}
                  onChange={(event) => setManualPublishedDate(event.target.value)}
                />
              </label>
              <label className="admin-field">
                <span>제목 (선택)</span>
                <input
                  value={manualTitle}
                  maxLength={255}
                  placeholder="Threads는 제목을 넣으면 종목 매칭에 활용합니다."
                  onChange={(event) => setManualTitle(event.target.value)}
                />
              </label>
              <label className="admin-field admin-collection-manual-description">
                <span>본문·설명 (선택)</span>
                <textarea
                  value={manualDescription}
                  maxLength={10000}
                  placeholder="게시물 본문을 붙여 넣으면 정확한 종목명으로 자동 연결합니다."
                  onChange={(event) => setManualDescription(event.target.value)}
                />
              </label>
              <p className="admin-field-hint admin-collection-manual-hint">
                X는 공개 메타데이터에서 본문과 게시일을 확인합니다. Threads는 본문을 직접 가져오지
                못할 수 있어 제목이나 설명을 함께 입력하면 자동 연결 정확도가 높아집니다.
              </p>
              <button type="submit" className="admin-primary-button" disabled={manualSaving}>
                {manualSaving ? '등록 중…' : '검수 대기에 등록'}
              </button>
            </form>
          </section>
          <section className="admin-collection-reviews" aria-labelledby="collection-reviews-title">
            <div className="admin-person-section-heading">
              <div>
                <h2 id="collection-reviews-title">수집 콘텐츠 검수</h2>
                <p>
                  자동 매칭된 종목을 확인하거나 변경한 뒤 승인하세요. 승인 전에는 공개되지 않습니다.
                </p>
              </div>
              <div className="admin-collection-review-heading-actions">
                <strong>{reviewTotal}건 대기</strong>
                <button
                  type="button"
                  className="admin-secondary-button"
                  disabled={reviewBulkAction !== null || reviewingId !== null}
                  onClick={() => void rematchReviews()}
                >
                  {reviewBulkAction === 'rematch' ? '재매칭 중…' : '미매칭 자동 재매칭'}
                </button>
              </div>
            </div>
            {reviewLoading ? (
              <p className="admin-table-loading" aria-busy="true">
                검수 대기 콘텐츠를 불러오는 중입니다.
              </p>
            ) : reviews.length === 0 ? (
              <div className="admin-empty-state">
                <strong>검수 대기 중인 콘텐츠가 없습니다.</strong>
              </div>
            ) : (
              <>
                <div className="admin-collection-review-bulk">
                  <label>
                    <input
                      type="checkbox"
                      checked={
                        reviews.length > 0 &&
                        reviews.every((review) => selectedReviewIds.includes(review.id))
                      }
                      onChange={toggleAllReviewSelections}
                    />
                    현재 목록 전체 선택
                  </label>
                  <button
                    type="button"
                    className="admin-primary-button"
                    disabled={selectedReviewIds.length === 0 || reviewBulkAction !== null}
                    onClick={() => void approveSelectedReviews()}
                  >
                    {reviewBulkAction === 'approve'
                      ? '일괄 승인 중…'
                      : `선택 승인 (${selectedReviewIds.length})`}
                  </button>
                  <button
                    type="button"
                    className="admin-secondary-button"
                    disabled={selectedReviewIds.length === 0 || reviewBulkAction !== null}
                    onClick={() => void rejectSelectedReviews()}
                  >
                    {reviewBulkAction === 'reject'
                      ? '일괄 거절 중…'
                      : `선택 거절 (${selectedReviewIds.length})`}
                  </button>
                </div>
                <ul className="admin-collection-review-list">
                  {reviews.map((review) => {
                    const selectedAssetIds = reviewAssetIds[review.id] ?? [];
                    const availableAssets = new Map(
                      [
                        ...review.suggestedAssets,
                        ...(reviewSearchResults[review.id] ?? []).map((stock) => ({
                          id: stock.assetId,
                          name: stock.name,
                          assetCode: stock.assetCode,
                        })),
                      ].map((asset) => [asset.id, asset] as const),
                    );
                    const selectedAssets = selectedAssetIds.flatMap((id) => {
                      const asset = availableAssets.get(id);
                      return asset ? [asset] : [];
                    });
                    return (
                      <li key={review.id}>
                        <div className="admin-collection-review-copy">
                          <label className="admin-collection-review-select">
                            <input
                              type="checkbox"
                              checked={selectedReviewIds.includes(review.id)}
                              onChange={() => toggleReviewSelection(review.id)}
                            />
                            일괄 작업 선택
                          </label>
                          <span>
                            {SOURCE_LABELS[review.sourceType]} · {review.sourceName}
                            {review.publishedDate ? (
                              ` · ${review.publishedDate}`
                            ) : (
                              <span className="admin-collection-review-date-unknown">
                                날짜 미확인
                              </span>
                            )}
                          </span>
                          <strong>{review.title}</strong>
                          {review.description ? <p>{review.description}</p> : null}
                          <a href={review.url} target="_blank" rel="noreferrer">
                            원문 보기 ↗
                          </a>
                        </div>
                        <div className="admin-collection-review-controls">
                          <strong>연결 종목 {selectedAssets.length}개</strong>
                          {review.publishedDate === null ? (
                            <label className="admin-field admin-collection-review-date-field">
                              <span>원문 게시일 (선택)</span>
                              <input
                                aria-label={`${review.title} 게시일`}
                                type="date"
                                value={reviewPublishedDates[review.id] ?? ''}
                                onChange={(event) =>
                                  setReviewPublishedDates((current) => ({
                                    ...current,
                                    [review.id]: event.target.value,
                                  }))
                                }
                              />
                            </label>
                          ) : null}
                          {selectedAssets.length > 0 ? (
                            <div className="admin-collection-review-results">
                              {selectedAssets.map((asset) => (
                                <button
                                  key={asset.id}
                                  type="button"
                                  aria-label={`${asset.name} 연결 해제`}
                                  onClick={() => toggleReviewAsset(review.id, asset.id)}
                                >
                                  {asset.name} · {asset.assetCode} ×
                                </button>
                              ))}
                            </div>
                          ) : (
                            <span className="admin-field-hint">종목 미선택</span>
                          )}
                          <div className="admin-collection-review-search">
                            <input
                              aria-label={`${review.title} 종목 검색`}
                              type="search"
                              value={reviewKeywords[review.id] ?? ''}
                              placeholder="종목명 또는 종목코드"
                              onChange={(event) =>
                                setReviewKeywords((current) => ({
                                  ...current,
                                  [review.id]: event.target.value,
                                }))
                              }
                            />
                            <button
                              type="button"
                              className="admin-secondary-button"
                              disabled={reviewSearchingId !== null || reviewingId !== null}
                              onClick={() => void searchReviewAssets(review.id)}
                            >
                              {reviewSearchingId === review.id ? '검색 중…' : '종목 검색'}
                            </button>
                          </div>
                          {reviewSearchResults[review.id]?.length ? (
                            <div className="admin-collection-review-results">
                              {reviewSearchResults[review.id].map((stock) => (
                                <button
                                  key={stock.assetId}
                                  type="button"
                                  aria-pressed={selectedAssetIds.includes(stock.assetId)}
                                  onClick={() => toggleReviewAsset(review.id, stock.assetId)}
                                >
                                  {stock.name} · {stock.assetCode}
                                </button>
                              ))}
                            </div>
                          ) : null}
                          <div className="admin-row-actions">
                            <button
                              type="button"
                              className="admin-primary-button"
                              disabled={
                                selectedAssetIds.length === 0 ||
                                reviewingId !== null ||
                                reviewBulkAction !== null
                              }
                              onClick={() => void approveReview(review)}
                            >
                              {reviewingId === review.id ? '처리 중…' : '승인 및 공개'}
                            </button>
                            <button
                              type="button"
                              className="admin-secondary-button"
                              disabled={reviewingId !== null || reviewBulkAction !== null}
                              onClick={() => void rejectReview(review)}
                            >
                              거절
                            </button>
                          </div>
                        </div>
                      </li>
                    );
                  })}
                </ul>
              </>
            )}
          </section>
        </>
      ) : null}

      {view === 'sources' ? (
        <>
          <section
            className="admin-collection-editor"
            aria-labelledby="collection-source-form-title"
          >
            <div className="admin-person-section-heading">
              <div>
                <h2 id="collection-source-form-title">
                  {editingId ? '수집 출처 수정' : '수집 출처 등록'}
                </h2>
                <p>경제 채널은 종목을 비워두고, 기업 공식 계정은 해당 종목을 연결하세요.</p>
              </div>
              {editingId ? (
                <button
                  type="button"
                  className="admin-secondary-button"
                  onClick={resetForm}
                  disabled={saving}
                >
                  새 출처 등록
                </button>
              ) : null}
            </div>
            <form className="admin-collection-form" onSubmit={submitSource}>
              <label className="admin-field">
                <span>출처 이름</span>
                <input
                  value={name}
                  maxLength={100}
                  onChange={(event) => setName(event.target.value)}
                />
              </label>
              <label className="admin-field">
                <span>플랫폼</span>
                <select
                  value={sourceType}
                  onChange={(event) => {
                    setSourceType(event.target.value as ContentCollectionSourceType);
                    setSourceUrl('');
                  }}
                >
                  {Object.entries(SOURCE_LABELS).map(([value, label]) => (
                    <option key={value} value={value}>
                      {label}
                    </option>
                  ))}
                </select>
              </label>
              <label className="admin-field admin-collection-url-field">
                <span>채널·프로필 URL</span>
                <input
                  type="url"
                  value={sourceUrl}
                  maxLength={2000}
                  placeholder={SOURCE_PLACEHOLDERS[sourceType]}
                  onChange={(event) => setSourceUrl(event.target.value)}
                />
              </label>
              <label className="admin-collection-enabled">
                <input
                  type="checkbox"
                  checked={enabled}
                  onChange={(event) => setEnabled(event.target.checked)}
                />
                예약 수집 활성화
              </label>

              <fieldset className="admin-collection-asset">
                <legend>연결 종목 (선택)</legend>
                {selectedAsset ? (
                  <div className="admin-collection-selected-asset">
                    <strong>{selectedAsset.name}</strong>
                    <button type="button" onClick={() => setSelectedAsset(null)}>
                      연결 해제
                    </button>
                  </div>
                ) : (
                  <p className="admin-field-hint">
                    종목명이 콘텐츠 제목·설명에 있으면 자동 연결됩니다.
                  </p>
                )}
                <div className="admin-collection-stock-search" aria-label="연결 종목 검색">
                  <label className="admin-field">
                    <span>국가</span>
                    <select
                      value={stockCountry}
                      onChange={(event) => setStockCountry(event.target.value as 'KR' | 'US')}
                    >
                      <option value="KR">한국</option>
                      <option value="US">미국</option>
                    </select>
                  </label>
                  <label className="admin-field">
                    <span>종목명·코드</span>
                    <input
                      value={stockKeyword}
                      onChange={(event) => setStockKeyword(event.target.value)}
                    />
                  </label>
                  <button
                    type="button"
                    className="admin-secondary-button"
                    disabled={stockLoading}
                    onClick={() => void findStocks()}
                  >
                    {stockLoading ? '검색 중…' : '검색'}
                  </button>
                </div>
                {stockResults.length > 0 ? (
                  <div className="admin-collection-stock-results">
                    {stockResults.map((stock) => (
                      <button
                        key={stock.assetId}
                        type="button"
                        onClick={() => {
                          setSelectedAsset(stock);
                          setStockResults([]);
                        }}
                      >
                        <strong>{stock.name}</strong>
                        <span>{stock.assetCode}</span>
                      </button>
                    ))}
                  </div>
                ) : null}
              </fieldset>
              <button type="submit" className="admin-primary-button" disabled={saving}>
                {saving ? '저장 중…' : editingId ? '수정 저장' : '출처 등록'}
              </button>
            </form>
          </section>

          <section className="admin-table-panel" aria-label="등록된 수집 출처">
            {loading ? (
              <p className="admin-table-loading" aria-busy="true">
                수집 출처를 불러오는 중입니다.
              </p>
            ) : sources.length === 0 ? (
              <div className="admin-empty-state">
                <strong>등록된 수집 출처가 없습니다.</strong>
              </div>
            ) : (
              <div className="admin-table-scroll">
                <table className="admin-news-table admin-collection-table">
                  <thead>
                    <tr>
                      <th>출처</th>
                      <th>연결 종목</th>
                      <th>최근 수집</th>
                      <th>상태</th>
                      <th>작업</th>
                    </tr>
                  </thead>
                  <tbody>
                    {sources.map((source) => (
                      <tr key={source.id}>
                        <td>
                          <strong>{source.name}</strong>
                          <a href={source.sourceUrl} target="_blank" rel="noreferrer">
                            {SOURCE_LABELS[source.sourceType]} ↗
                          </a>
                        </td>
                        <td>{source.assetName ?? '자동 매칭'}</td>
                        <td>{formatCollectedAt(source.lastCollectedAt)}</td>
                        <td>
                          <span
                            className={`admin-status ${source.enabled ? 'admin-status-published' : 'admin-status-deleted'}`}
                          >
                            {source.enabled ? '활성' : '중지'}
                          </span>
                        </td>
                        <td>
                          <div className="admin-row-actions">
                            <button
                              type="button"
                              className="admin-secondary-button"
                              onClick={() => editSource(source)}
                              disabled={busy}
                            >
                              수정
                            </button>
                            <button
                              type="button"
                              className="admin-secondary-button"
                              onClick={() => void toggleSource(source)}
                              disabled={busy}
                            >
                              {source.enabled ? '중지' : '활성화'}
                            </button>
                            <button
                              type="button"
                              className="admin-primary-button"
                              onClick={() => void collectSource(source)}
                              disabled={busy}
                            >
                              {collectingId === source.id ? '수집 중…' : '지금 수집'}
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </>
      ) : null}
    </main>
  );
}

export function AdminContentSourcesPage() {
  return <AdminContentCollectionPage view="sources" />;
}

export function AdminContentReviewsPage() {
  return <AdminContentCollectionPage view="reviews" />;
}
