import { useEffect, useRef, useState } from 'react';
import { ApiError } from '../../api/client';
import { searchStocks } from '../portfolio/api';
import type { StockSearchItem } from '../portfolio/types';
import {
  createAdminStockRelatedContent,
  deleteAdminStockRelatedContent,
  getAdminStockRelatedContents,
  searchAdminYouTubeVideos,
} from '../stock-content/api';
import {
  STOCK_CONTENT_SOURCE_LABELS,
  type StockRelatedContentSource,
  type StockRelatedContentsResponse,
  type YouTubeSearchItem,
} from '../stock-content/types';

const SOURCE_TYPES: StockRelatedContentSource[] = ['YOUTUBE', 'THREADS', 'OTHER'];
type ContentFilter = 'ALL' | StockRelatedContentSource;

const CONTENT_FILTERS: { value: ContentFilter; label: string }[] = [
  { value: 'ALL', label: '전체' },
  { value: 'YOUTUBE', label: 'YouTube' },
  { value: 'THREADS', label: 'Threads' },
  { value: 'INTERNAL_NEWS', label: 'DAYNOMY 이슈' },
  { value: 'OTHER', label: '기타' },
];

function getErrorMessage(error: unknown, fallback: string) {
  return error instanceof ApiError || error instanceof Error ? error.message : fallback;
}

export function AdminStockContentsPanel() {
  const [keyword, setKeyword] = useState('');
  const [stocks, setStocks] = useState<StockSearchItem[]>([]);
  const [selectedStock, setSelectedStock] = useState<StockSearchItem | null>(null);
  const [contents, setContents] = useState<StockRelatedContentsResponse | null>(null);
  const [contentFilter, setContentFilter] = useState<ContentFilter>('ALL');
  const [youtubeResults, setYoutubeResults] = useState<YouTubeSearchItem[]>([]);
  const [sourceType, setSourceType] = useState<StockRelatedContentSource>('YOUTUBE');
  const [title, setTitle] = useState('');
  const [url, setUrl] = useState('');
  const [loading, setLoading] = useState(false);
  const [youtubeLoading, setYoutubeLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [youtubeError, setYoutubeError] = useState('');
  const selectedStockRef = useRef<StockSearchItem | null>(null);
  const contentsRequestIdRef = useRef(0);
  const contentsControllerRef = useRef<AbortController | null>(null);
  const youtubeRequestIdRef = useRef(0);
  const youtubeControllerRef = useRef<AbortController | null>(null);

  useEffect(() => {
    return () => {
      contentsControllerRef.current?.abort();
      youtubeControllerRef.current?.abort();
    };
  }, []);

  async function search() {
    const trimmedKeyword = keyword.trim();
    if (!trimmedKeyword) {
      setError('종목명 또는 종목코드를 입력해주세요.');
      setStocks([]);
      return;
    }

    setLoading(true);
    setError('');
    try {
      setStocks(await searchStocks(trimmedKeyword));
    } catch (caughtError) {
      setStocks([]);
      setError(getErrorMessage(caughtError, '종목을 검색하지 못했습니다.'));
    } finally {
      setLoading(false);
    }
  }

  async function selectStock(stock: StockSearchItem) {
    const requestId = contentsRequestIdRef.current + 1;
    contentsRequestIdRef.current = requestId;
    selectedStockRef.current = stock;
    youtubeRequestIdRef.current += 1;
    youtubeControllerRef.current?.abort();
    youtubeControllerRef.current = null;
    setSelectedStock(stock);
    setContents(null);
    setContentFilter('ALL');
    setYoutubeResults([]);
    setYoutubeLoading(false);
    setYoutubeError('');
    setLoading(true);
    setError('');
    contentsControllerRef.current?.abort();
    const controller = new AbortController();
    contentsControllerRef.current = controller;
    try {
      const nextContents = await getAdminStockRelatedContents(stock.assetId, controller.signal);
      if (
        requestId !== contentsRequestIdRef.current ||
        selectedStockRef.current?.assetId !== stock.assetId
      ) {
        return;
      }
      setContents(nextContents);
    } catch (caughtError) {
      if (
        controller.signal.aborted ||
        requestId !== contentsRequestIdRef.current ||
        selectedStockRef.current?.assetId !== stock.assetId
      ) {
        return;
      }
      setError(getErrorMessage(caughtError, '종목 관련 자료를 불러오지 못했습니다.'));
    } finally {
      if (requestId === contentsRequestIdRef.current) {
        setLoading(false);
        contentsControllerRef.current = null;
      }
    }
  }

  async function reloadContents(assetId: number) {
    if (selectedStockRef.current?.assetId !== assetId) return;

    const requestId = contentsRequestIdRef.current + 1;
    contentsRequestIdRef.current = requestId;
    contentsControllerRef.current?.abort();
    const controller = new AbortController();
    contentsControllerRef.current = controller;
    try {
      const nextContents = await getAdminStockRelatedContents(assetId, controller.signal);
      if (
        requestId !== contentsRequestIdRef.current ||
        selectedStockRef.current?.assetId !== assetId
      ) {
        return;
      }
      setContents(nextContents);
    } catch (caughtError) {
      if (
        controller.signal.aborted ||
        requestId !== contentsRequestIdRef.current ||
        selectedStockRef.current?.assetId !== assetId
      ) {
        return;
      }
      setError(getErrorMessage(caughtError, '종목 관련 자료를 불러오지 못했습니다.'));
    } finally {
      if (requestId === contentsRequestIdRef.current) {
        contentsControllerRef.current = null;
      }
    }
  }

  async function searchYouTube() {
    const stock = selectedStockRef.current;
    if (!stock || youtubeLoading) return;

    const requestId = youtubeRequestIdRef.current + 1;
    youtubeRequestIdRef.current = requestId;
    const assetId = stock.assetId;
    setYoutubeLoading(true);
    setYoutubeError('');
    youtubeControllerRef.current?.abort();
    const controller = new AbortController();
    youtubeControllerRef.current = controller;
    try {
      const keyword = `${stock.name} ${stock.assetCode}`;
      const results = await searchAdminYouTubeVideos(assetId, keyword, controller.signal);
      if (
        requestId !== youtubeRequestIdRef.current ||
        selectedStockRef.current?.assetId !== assetId
      ) {
        return;
      }
      setYoutubeResults(results);
    } catch (caughtError) {
      if (
        controller.signal.aborted ||
        requestId !== youtubeRequestIdRef.current ||
        selectedStockRef.current?.assetId !== assetId
      ) {
        return;
      }
      setYoutubeResults([]);
      setYoutubeError(getErrorMessage(caughtError, 'YouTube 영상을 검색하지 못했습니다.'));
    } finally {
      if (requestId === youtubeRequestIdRef.current) {
        setYoutubeLoading(false);
        youtubeControllerRef.current = null;
      }
    }
  }

  async function saveYouTube(video: YouTubeSearchItem) {
    const stock = selectedStockRef.current;
    if (!stock || saving) return;

    const assetId = stock.assetId;
    setSaving(true);
    setError('');
    try {
      if (selectedStockRef.current?.assetId !== assetId) return;
      await createAdminStockRelatedContent(assetId, {
        sourceType: 'YOUTUBE',
        title: video.title,
        url: video.url,
        imageUrl: video.thumbnailUrl,
      });
      if (selectedStockRef.current?.assetId !== assetId) return;
      setYoutubeResults((current) => current.filter((item) => item.url !== video.url));
      await reloadContents(assetId);
    } catch (caughtError) {
      setError(getErrorMessage(caughtError, 'YouTube 영상을 등록하지 못했습니다.'));
    } finally {
      setSaving(false);
    }
  }

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const stock = selectedStockRef.current;
    if (!stock || !title.trim() || !url.trim() || saving) return;

    const assetId = stock.assetId;
    setSaving(true);
    setError('');
    try {
      if (selectedStockRef.current?.assetId !== assetId) return;
      await createAdminStockRelatedContent(assetId, {
        sourceType,
        title: title.trim(),
        url: url.trim(),
      });
      if (selectedStockRef.current?.assetId !== assetId) return;
      setTitle('');
      setUrl('');
      await reloadContents(assetId);
    } catch (caughtError) {
      setError(getErrorMessage(caughtError, '관련 자료를 등록하지 못했습니다.'));
    } finally {
      setSaving(false);
    }
  }

  async function removeContent(contentId: number) {
    const stock = selectedStockRef.current;
    if (!stock || !window.confirm('이 링크를 삭제할까요?')) return;

    const assetId = stock.assetId;
    setSaving(true);
    setError('');
    try {
      await deleteAdminStockRelatedContent(assetId, contentId);
      if (selectedStockRef.current?.assetId !== assetId) return;
      await reloadContents(assetId);
    } catch (caughtError) {
      setError(getErrorMessage(caughtError, '관련 자료를 삭제하지 못했습니다.'));
    } finally {
      setSaving(false);
    }
  }

  const allContents = contents?.contents ?? [];
  const visibleContents =
    contentFilter === 'ALL'
      ? allContents
      : allContents.filter((content) => content.sourceType === contentFilter);

  function getContentCount(filter: ContentFilter) {
    return filter === 'ALL'
      ? allContents.length
      : allContents.filter((content) => content.sourceType === filter).length;
  }

  return (
    <section className="admin-related-content-panel" aria-labelledby="related-content-title">
      <div className="admin-page-heading">
        <div>
          <p className="admin-kicker">종목 정보 허브</p>
          <h2 id="related-content-title">종목 관련 링크</h2>
          <p>종목별 YouTube, Threads와 DAYNOMY 뉴스의 제목·URL을 관리합니다.</p>
        </div>
      </div>

      <form
        className="admin-related-search"
        onSubmit={(event) => {
          event.preventDefault();
          void search();
        }}
      >
        <label className="admin-field">
          <span>종목 검색</span>
          <input
            type="search"
            value={keyword}
            placeholder="삼성전자 또는 005930"
            onChange={(event) => setKeyword(event.target.value)}
          />
        </label>
        <button type="submit" className="admin-primary-button" disabled={loading}>
          {loading ? '검색 중…' : '검색'}
        </button>
      </form>

      {stocks.length > 0 ? (
        <ul className="admin-stock-search-results">
          {stocks.map((stock) => (
            <li key={stock.assetId}>
              <button type="button" onClick={() => void selectStock(stock)}>
                <strong>{stock.name}</strong>
                <span>
                  {stock.assetCode} · {stock.market}
                </span>
              </button>
            </li>
          ))}
        </ul>
      ) : null}

      {selectedStock ? (
        <div className="admin-related-content-workspace">
          <div className="admin-selected-stock">
            <strong>{selectedStock.name}</strong>
            <span>
              {selectedStock.assetCode} · {selectedStock.market}
            </span>
          </div>
          <section className="admin-youtube-search" aria-labelledby="youtube-search-title">
            <div className="admin-youtube-heading">
              <div>
                <strong id="youtube-search-title">YouTube 영상 검색</strong>
                <p>종목명과 종목코드로 검색한 결과를 바로 관련 콘텐츠로 등록할 수 있습니다.</p>
              </div>
              <button
                type="button"
                className="admin-secondary-button"
                onClick={() => void searchYouTube()}
                disabled={youtubeLoading}
              >
                {youtubeLoading ? '검색 중…' : 'YouTube 검색'}
              </button>
            </div>
            {youtubeError ? <small className="admin-field-error">{youtubeError}</small> : null}
            {youtubeResults.length > 0 ? (
              <ul className="admin-youtube-results">
                {youtubeResults.map((video) => (
                  <li key={video.url}>
                    {video.thumbnailUrl ? (
                      <img src={video.thumbnailUrl} alt="" loading="lazy" />
                    ) : null}
                    <div>
                      <strong>{video.title}</strong>
                      <span>{video.channelTitle}</span>
                    </div>
                    <button
                      type="button"
                      className="admin-secondary-button"
                      onClick={() => void saveYouTube(video)}
                      disabled={saving}
                    >
                      등록
                    </button>
                  </li>
                ))}
              </ul>
            ) : null}
          </section>
          <form className="admin-related-form" onSubmit={submit}>
            <label className="admin-field">
              <span>출처</span>
              <select
                value={sourceType}
                onChange={(event) => setSourceType(event.target.value as StockRelatedContentSource)}
              >
                {SOURCE_TYPES.map((type) => (
                  <option value={type} key={type}>
                    {STOCK_CONTENT_SOURCE_LABELS[type]}
                  </option>
                ))}
              </select>
            </label>
            <label className="admin-field">
              <span>제목</span>
              <input
                value={title}
                maxLength={255}
                onChange={(event) => setTitle(event.target.value)}
                placeholder="삼성전자 관련 자료 제목"
              />
            </label>
            <label className="admin-field admin-related-url-field">
              <span>URL</span>
              <input
                type="url"
                value={url}
                onChange={(event) => setUrl(event.target.value)}
                placeholder="https://..."
              />
            </label>
            <button
              type="submit"
              className="admin-primary-button"
              disabled={saving || !title.trim() || !url.trim()}
            >
              {saving ? '저장 중…' : '링크 저장'}
            </button>
          </form>

          {contents ? (
            <div className="admin-related-content-toolbar">
              <div className="admin-related-content-summary">
                <strong>연결된 자료</strong>
                <span>{allContents.length}건</span>
              </div>
              <div
                className="admin-related-content-filters"
                role="group"
                aria-label="자료 카테고리"
              >
                {CONTENT_FILTERS.map((filter) => (
                  <button
                    type="button"
                    className="admin-related-content-filter"
                    aria-pressed={contentFilter === filter.value}
                    key={filter.value}
                    onClick={() => setContentFilter(filter.value)}
                  >
                    <span>{filter.label}</span>
                    <small>{getContentCount(filter.value)}</small>
                  </button>
                ))}
              </div>
            </div>
          ) : null}

          <ul className="admin-related-content-list">
            {visibleContents.map((content) => (
              <li key={content.id}>
                {content.imageUrl ? <img src={content.imageUrl} alt="" loading="lazy" /> : null}
                <div>
                  <span>{STOCK_CONTENT_SOURCE_LABELS[content.sourceType]}</span>
                  <strong>{content.title}</strong>
                  <a href={content.url} target="_blank" rel="noreferrer">
                    {content.url}
                  </a>
                </div>
                <button
                  type="button"
                  disabled={saving}
                  onClick={() => void removeContent(content.id)}
                >
                  삭제
                </button>
              </li>
            ))}
            {contents && visibleContents.length === 0 ? (
              <li className="admin-related-empty">
                {contentFilter === 'ALL'
                  ? '등록된 링크가 없습니다.'
                  : '선택한 카테고리에 등록된 링크가 없습니다.'}
              </li>
            ) : null}
          </ul>
        </div>
      ) : null}

      {error ? (
        <div className="admin-alert" role="alert">
          <strong>{error}</strong>
        </div>
      ) : null}
    </section>
  );
}
