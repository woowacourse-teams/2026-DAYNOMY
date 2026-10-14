import { getAdminNewsDetail, updateAdminNews } from './api';
import { AdminDaynomyNewsSearch } from './components/AdminDaynomyNewsSearch';
import { getPublishedDate } from '../stock-content/publishedDate';
import { useEffect, useRef, useState } from 'react';
import { ApiError } from '../../api/client';
import { searchStocks } from '../portfolio/api';
import type { StockSearchItem } from '../portfolio/types';
import {
  createAdminStockRelatedContent,
  deleteAdminStockRelatedContent,
  getAdminStockRelatedContents,
  searchAdminSocialPosts,
  searchAdminYouTubeVideos,
} from '../stock-content/api';
import {
  STOCK_CONTENT_SOURCE_LABELS,
  type StockRelatedContentSource,
  type StockRelatedContentsResponse,
  type SocialPostSearchItem,
  type YouTubeSearchItem,
} from '../stock-content/types';

const SOURCE_TYPES: StockRelatedContentSource[] = ['YOUTUBE', 'THREADS', 'X', 'OTHER'];
type ContentFilter = 'ALL' | StockRelatedContentSource;

const CONTENT_FILTERS: { value: ContentFilter; label: string }[] = [
  { value: 'ALL', label: '전체' },
  { value: 'YOUTUBE', label: 'YouTube' },
  { value: 'THREADS', label: 'Threads' },
  { value: 'X', label: 'X' },
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
  const [socialResults, setSocialResults] = useState<SocialPostSearchItem[]>([]);
  const [socialSearched, setSocialSearched] = useState(false);
  const [sourceType, setSourceType] = useState<StockRelatedContentSource>('YOUTUBE');
  const [title, setTitle] = useState('');
  const [url, setUrl] = useState('');
  const [publishedDate, setPublishedDate] = useState('');
  const [loading, setLoading] = useState(false);
  const [youtubeLoading, setYoutubeLoading] = useState(false);
  const [socialLoading, setSocialLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [youtubeError, setYoutubeError] = useState('');
  const [socialError, setSocialError] = useState('');
  const [stockSearched, setStockSearched] = useState(false);
  const [youtubeSearched, setYoutubeSearched] = useState(false);
  const [notice, setNotice] = useState('');
  const [workspace, setWorkspace] = useState<'list' | 'add'>('list');
  const [addMethod, setAddMethod] = useState<'url' | 'youtube' | 'social' | 'daynomy'>('url');
  const [savedUrls, setSavedUrls] = useState<string[]>([]);
  const selectedStockRef = useRef<StockSearchItem | null>(null);
  const contentsRequestIdRef = useRef(0);
  const contentsControllerRef = useRef<AbortController | null>(null);
  const youtubeRequestIdRef = useRef(0);
  const youtubeControllerRef = useRef<AbortController | null>(null);
  const socialRequestIdRef = useRef(0);
  const socialControllerRef = useRef<AbortController | null>(null);

  useEffect(() => {
    return () => {
      contentsControllerRef.current?.abort();
      youtubeControllerRef.current?.abort();
      socialControllerRef.current?.abort();
    };
  }, []);

  async function search() {
    if (loading) return;
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
      setStockSearched(true);
    } catch (caughtError) {
      setStocks([]);
      setError(getErrorMessage(caughtError, '종목을 검색하지 못했습니다.'));
    } finally {
      setLoading(false);
    }
  }

  async function selectStock(stock: StockSearchItem) {
    if (saving || loading || selectedStockRef.current?.assetId === stock.assetId) return;
    if (
      (title.trim() || url.trim() || publishedDate) &&
      !window.confirm('작성 중인 링크가 있습니다. 내용을 버리고 다른 종목으로 이동할까요?')
    )
      return;
    setTitle('');
    setUrl('');
    setPublishedDate('');
    setSourceType('YOUTUBE');
    setWorkspace('list');
    setAddMethod('url');
    setSavedUrls([]);
    setNotice('');
    setYoutubeSearched(false);
    const requestId = contentsRequestIdRef.current + 1;
    contentsRequestIdRef.current = requestId;
    selectedStockRef.current = stock;
    youtubeRequestIdRef.current += 1;
    youtubeControllerRef.current?.abort();
    youtubeControllerRef.current = null;
    socialRequestIdRef.current += 1;
    socialControllerRef.current?.abort();
    socialControllerRef.current = null;
    setSelectedStock(stock);
    setContents(null);
    setContentFilter('ALL');
    setYoutubeResults([]);
    setYoutubeLoading(false);
    setYoutubeError('');
    setSocialResults([]);
    setSocialSearched(false);
    setSocialLoading(false);
    setSocialError('');
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

    setLoading(true);
    setError('');
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
        setLoading(false);
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
    setYoutubeSearched(false);
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
      setYoutubeSearched(true);
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

    if (isRegistered(video.url)) return;
    const assetId = stock.assetId;
    setSaving(true);
    setError('');
    setNotice('');
    try {
      if (selectedStockRef.current?.assetId !== assetId) return;
      await createAdminStockRelatedContent(assetId, {
        sourceType: 'YOUTUBE',
        title: video.title,
        url: video.url,
        imageUrl: video.thumbnailUrl,
        publishedDate: getPublishedDate(video.publishedAt),
      });
      if (selectedStockRef.current?.assetId !== assetId) return;
      setSavedUrls((current) => [...current, video.url]);
      setNotice(`${stock.name}에 영상을 연결했습니다.`);
      await reloadContents(assetId);
    } catch (caughtError) {
      setError(getErrorMessage(caughtError, 'YouTube 영상을 등록하지 못했습니다.'));
    } finally {
      setSaving(false);
    }
  }

  async function searchSocialPosts() {
    const stock = selectedStockRef.current;
    if (!stock || socialLoading) return;

    const requestId = socialRequestIdRef.current + 1;
    socialRequestIdRef.current = requestId;
    const assetId = stock.assetId;
    setSocialLoading(true);
    setSocialSearched(false);
    setSocialError('');
    socialControllerRef.current?.abort();
    const controller = new AbortController();
    socialControllerRef.current = controller;
    try {
      const results = await searchAdminSocialPosts(assetId, stock.name, controller.signal);
      if (
        requestId !== socialRequestIdRef.current ||
        selectedStockRef.current?.assetId !== assetId
      ) {
        return;
      }
      setSocialResults(results);
      setSocialSearched(true);
    } catch (caughtError) {
      if (
        controller.signal.aborted ||
        requestId !== socialRequestIdRef.current ||
        selectedStockRef.current?.assetId !== assetId
      ) {
        return;
      }
      setSocialResults([]);
      setSocialSearched(true);
      setSocialError(getErrorMessage(caughtError, 'Threads·X 게시물을 검색하지 못했습니다.'));
    } finally {
      if (requestId === socialRequestIdRef.current) {
        setSocialLoading(false);
        socialControllerRef.current = null;
      }
    }
  }

  async function saveSocialPost(post: SocialPostSearchItem) {
    const stock = selectedStockRef.current;
    if (!stock || saving) return;

    if (isRegistered(post.url)) return;
    const assetId = stock.assetId;
    const contentTitle =
      post.title.trim() || `${STOCK_CONTENT_SOURCE_LABELS[post.sourceType]} 게시물`;
    setSaving(true);
    setError('');
    setNotice('');
    try {
      if (selectedStockRef.current?.assetId !== assetId) return;
      await createAdminStockRelatedContent(assetId, {
        sourceType: post.sourceType,
        title: contentTitle,
        url: post.url,
        publishedDate: getPublishedDate(post.publishedDate),
      });
      if (selectedStockRef.current?.assetId !== assetId) return;
      setSavedUrls((current) => [...current, post.url]);
      setNotice(`${stock.name}에 게시물을 연결했습니다.`);
      await reloadContents(assetId);
    } catch (caughtError) {
      setError(getErrorMessage(caughtError, 'Threads·X 게시물을 등록하지 못했습니다.'));
    } finally {
      setSaving(false);
    }
  }

  async function linkNews(newsId: number) {
    const stock = selectedStockRef.current;
    const url = `/news/${newsId}`;
    if (!stock || saving || isRegistered(url)) return;
    setSaving(true);
    setError('');
    setNotice('');
    try {
      const news = await getAdminNewsDetail(newsId);
      if (news.status !== 'PUBLISHED') {
        throw new Error('현재 발행된 뉴스만 연결할 수 있습니다. 다시 검색해 주세요.');
      }
      if (!news.relatedAssets) {
        throw new Error('기존 연결 종목을 확인하지 못했습니다. 다시 시도해 주세요.');
      }
      const assetIds = news.relatedAssets.map((asset) => asset.assetId);
      if (!assetIds.includes(stock.assetId)) {
        // shortcut: 기존 전체 수정 API는 동시 편집 충돌을 감지하지 못한다. 동시 편집이 필요하면 버전 검증을 추가한다.
        await updateAdminNews(
          newsId,
          {
            title: news.title,
            content: news.content,
            sources: news.sources,
            category: news.category,
            assetIds: [...assetIds, stock.assetId],
          },
          null,
        );
      }
      if (selectedStockRef.current?.assetId !== stock.assetId) return;
      setSavedUrls((current) => [...current, url]);
      setNotice(`${stock.name}에 DAYNOMY 뉴스를 연결했습니다.`);
      await reloadContents(stock.assetId);
    } catch (caughtError) {
      setError(getErrorMessage(caughtError, 'DAYNOMY 뉴스를 연결하지 못했습니다.'));
    } finally {
      setSaving(false);
    }
  }

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const stock = selectedStockRef.current;
    if (!stock || !title.trim() || !url.trim() || saving) return;
    if (isRegistered(url.trim())) {
      setError('이미 연결된 URL입니다. 등록된 자료에서 확인해 주세요.');
      return;
    }

    const assetId = stock.assetId;
    setSaving(true);
    setError('');
    setNotice('');
    try {
      if (selectedStockRef.current?.assetId !== assetId) return;
      await createAdminStockRelatedContent(assetId, {
        sourceType,
        title: title.trim(),
        url: url.trim(),
        publishedDate: getPublishedDate(publishedDate),
      });
      if (selectedStockRef.current?.assetId !== assetId) return;
      setSavedUrls((current) => [...current, url.trim()]);
      setNotice(`${stock.name}에 링크를 연결했습니다.`);
      setTitle('');
      setUrl('');
      setPublishedDate('');
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
    setNotice('');
    try {
      const removedUrl = contents?.contents.find((content) => content.id === contentId)?.url;
      await deleteAdminStockRelatedContent(assetId, contentId);
      if (selectedStockRef.current?.assetId !== assetId) return;
      setSavedUrls((current) => current.filter((value) => value !== removedUrl));
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

  function isRegistered(value: string) {
    return savedUrls.includes(value) || allContents.some((content) => content.url === value);
  }

  function updateUrl(value: string) {
    setUrl(value);
    try {
      const host = new URL(value.trim()).hostname.replace(/^www\./, '').replace(/^m\./, '');
      setSourceType(
        host === 'youtube.com' || host === 'youtu.be'
          ? 'YOUTUBE'
          : host === 'x.com' || host === 'twitter.com'
            ? 'X'
            : host === 'threads.net' || host === 'threads.com'
              ? 'THREADS'
              : 'OTHER',
      );
    } catch {
      /* Keep the selected source while the address is incomplete. */
    }
  }

  function getContentCount(filter: ContentFilter) {
    return filter === 'ALL'
      ? allContents.length
      : allContents.filter((content) => content.sourceType === filter).length;
  }

  return (
    <section className="admin-related-content-panel" aria-labelledby="related-content-title">
      <div className="admin-page-heading">
        <div>
          <h2 id="related-content-title">종목 관련 링크</h2>
          <p>종목별 YouTube, Threads, X와 DAYNOMY 뉴스의 제목·URL을 관리합니다.</p>
        </div>
      </div>

      <div className="admin-link-layout">
        <aside className="admin-target-picker" aria-label="종목 선택">
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
                disabled={saving}
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
                  <button
                    type="button"
                    disabled={saving || loading}
                    aria-pressed={selectedStock?.assetId === stock.assetId}
                    onClick={() => void selectStock(stock)}
                  >
                    <strong>{stock.name}</strong>
                    <span>
                      {stock.assetCode} · {stock.market}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          ) : null}

          {stockSearched && !loading && stocks.length === 0 ? (
            <p className="admin-picker-empty">
              검색 결과가 없습니다. 종목명이나 코드를 바꿔 검색해 주세요.
            </p>
          ) : null}
          {!stockSearched ? (
            <p className="admin-field-hint">관리할 종목을 먼저 검색해 주세요.</p>
          ) : null}
        </aside>
        <div className="admin-link-workspace">
          {selectedStock ? (
            <div className="admin-related-content-workspace">
              <div className="admin-selected-stock">
                <strong>{selectedStock.name}</strong>
                <span>
                  {selectedStock.assetCode} · {selectedStock.market}
                </span>
              </div>
              <div className="admin-workspace-switch" role="group" aria-label="종목 작업 선택">
                <button
                  type="button"
                  aria-pressed={workspace === 'list'}
                  onClick={() => setWorkspace('list')}
                >
                  연결된 자료 <span>{allContents.length}</span>
                </button>
                <button
                  type="button"
                  disabled={!contents || loading}
                  aria-pressed={workspace === 'add'}
                  onClick={() => setWorkspace('add')}
                >
                  자료 추가
                </button>
              </div>
              {loading ? (
                <p className="admin-picker-empty" role="status">
                  연결된 자료를 불러오는 중입니다.
                </p>
              ) : null}
              {!loading && !contents ? (
                <button
                  className="admin-secondary-button"
                  type="button"
                  onClick={() => void reloadContents(selectedStock.assetId)}
                >
                  자료 다시 불러오기
                </button>
              ) : null}
              {workspace === 'add' ? (
                <div className="admin-add-workspace">
                  <div className="admin-method-switch" role="group" aria-label="자료 추가 방법">
                    <button
                      type="button"
                      aria-pressed={addMethod === 'url'}
                      onClick={() => setAddMethod('url')}
                    >
                      URL 직접 등록
                    </button>
                    <button
                      type="button"
                      aria-pressed={addMethod === 'youtube'}
                      onClick={() => setAddMethod('youtube')}
                    >
                      YouTube에서 찾기
                    </button>
                    <button
                      type="button"
                      aria-pressed={addMethod === 'social'}
                      onClick={() => setAddMethod('social')}
                    >
                      Threads·X에서 찾기
                    </button>
                    <button
                      type="button"
                      aria-pressed={addMethod === 'daynomy'}
                      onClick={() => setAddMethod('daynomy')}
                    >
                      DAYNOMY에서 찾기
                    </button>
                  </div>
                  {addMethod === 'youtube' ? (
                    <section
                      className="admin-youtube-search"
                      aria-labelledby="youtube-search-title"
                    >
                      <div className="admin-youtube-heading">
                        <div>
                          <strong id="youtube-search-title">YouTube 영상 검색</strong>
                          <p>
                            종목명과 종목코드로 검색한 결과를 바로 관련 콘텐츠로 등록할 수 있습니다.
                          </p>
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
                      {youtubeError ? (
                        <small className="admin-field-error">{youtubeError}</small>
                      ) : null}
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
                                disabled={saving || isRegistered(video.url)}
                              >
                                {isRegistered(video.url) ? '등록됨' : '등록'}
                              </button>
                            </li>
                          ))}
                        </ul>
                      ) : null}
                      {youtubeSearched && !youtubeError && youtubeResults.length === 0 ? (
                        <p className="admin-field-hint">
                          검색 결과가 없습니다. URL 직접 등록을 이용해 주세요.
                        </p>
                      ) : null}
                    </section>
                  ) : null}
                  {addMethod === 'social' ? (
                    <section className="admin-youtube-search" aria-labelledby="social-search-title">
                      <div className="admin-youtube-heading">
                        <div>
                          <strong id="social-search-title">Threads·X 게시물 검색</strong>
                          <p>AI 웹 검색으로 찾은 공개 게시물 URL을 확인한 뒤 등록할 수 있습니다.</p>
                        </div>
                        <button
                          type="button"
                          className="admin-secondary-button"
                          onClick={() => void searchSocialPosts()}
                          disabled={socialLoading}
                        >
                          {socialLoading ? '검색 중…' : 'Threads·X 검색'}
                        </button>
                      </div>
                      {socialError ? (
                        <small className="admin-field-error">{socialError}</small>
                      ) : null}
                      {socialResults.length > 0 ? (
                        <ul className="admin-social-results">
                          {socialResults.map((post) => (
                            <li key={post.url}>
                              <div>
                                <span>
                                  {STOCK_CONTENT_SOURCE_LABELS[post.sourceType]}
                                  {post.publishedDate ? ` · ${post.publishedDate}` : ''}
                                </span>
                                <strong>{post.title}</strong>
                                <a href={post.url} target="_blank" rel="noreferrer">
                                  원문 보기
                                </a>
                              </div>
                              <button
                                type="button"
                                className="admin-secondary-button"
                                onClick={() => void saveSocialPost(post)}
                                disabled={saving || isRegistered(post.url)}
                              >
                                {isRegistered(post.url) ? '등록됨' : '등록'}
                              </button>
                            </li>
                          ))}
                        </ul>
                      ) : null}
                      {socialSearched && !socialError && socialResults.length === 0 ? (
                        <small className="admin-field-hint">검색 결과가 없습니다.</small>
                      ) : null}
                    </section>
                  ) : null}
                  {addMethod === 'url' ? (
                    <section className="admin-manual-content">
                      <h3>자료 URL 붙여넣기</h3>
                      <p className="admin-field-hint">
                        {selectedStock.name}에 연결할 링크와 제목을 입력하세요. 출처는 주소에서 자동
                        선택됩니다.
                      </p>
                      <form className="admin-related-form" onSubmit={submit}>
                        <label className="admin-field admin-related-url-field">
                          <span>URL</span>
                          <input
                            disabled={saving}
                            type="url"
                            value={url}
                            onChange={(event) => updateUrl(event.target.value)}
                            placeholder="https://..."
                          />
                        </label>
                        <label className="admin-field">
                          <span>출처</span>
                          <select
                            disabled={saving}
                            value={sourceType}
                            onChange={(event) =>
                              setSourceType(event.target.value as StockRelatedContentSource)
                            }
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
                            disabled={saving}
                            value={title}
                            maxLength={255}
                            onChange={(event) => setTitle(event.target.value)}
                            placeholder={`${selectedStock.name} 관련 자료 제목`}
                          />
                        </label>
                        <label className="admin-field">
                          <span>원문 게시일 (선택)</span>
                          <input
                            disabled={saving}
                            type="date"
                            value={publishedDate}
                            onChange={(event) => setPublishedDate(event.target.value)}
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
                    </section>
                  ) : null}
                </div>
              ) : null}

              <AdminDaynomyNewsSearch
                key={selectedStock.assetId}
                stockName={selectedStock.name}
                hidden={workspace !== 'add' || addMethod !== 'daynomy'}
                saving={saving}
                isRegistered={isRegistered}
                onLink={linkNews}
              />

              {workspace === 'list' ? (
                <>
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
                        {content.imageUrl ? (
                          <img src={content.imageUrl} alt="" loading="lazy" />
                        ) : null}
                        <div>
                          <span>{STOCK_CONTENT_SOURCE_LABELS[content.sourceType]}</span>
                          <strong>{content.title}</strong>
                          {content.publishedDate ? (
                            <time dateTime={content.publishedDate}>
                              게시일 {content.publishedDate.replaceAll('-', '.')}
                            </time>
                          ) : null}
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
                  {contents && allContents.length === 0 ? (
                    <button
                      type="button"
                      className="admin-secondary-button"
                      onClick={() => setWorkspace('add')}
                    >
                      첫 자료 연결하기
                    </button>
                  ) : null}
                </>
              ) : null}
            </div>
          ) : (
            <div className="admin-target-empty">
              <h3>어떤 종목의 자료를 관리할까요?</h3>
              <p>
                왼쪽에서 종목을 검색하고 선택하면 연결된 자료를 확인하고 새 자료를 추가할 수
                있습니다.
              </p>
            </div>
          )}
        </div>
      </div>

      {notice && !error ? (
        <div className="admin-operation-notice admin-operation-success" role="status">
          <span>{notice}</span>
          <button type="button" onClick={() => setWorkspace('list')}>
            연결된 자료 보기
          </button>
          <button type="button" onClick={() => setNotice('')} aria-label="완료 안내 닫기">
            닫기
          </button>
        </div>
      ) : null}
      {error ? (
        <div className="admin-alert admin-operation-notice" role="alert">
          <strong>{error}</strong>
          <button type="button" onClick={() => setError('')} aria-label="오류 안내 닫기">
            닫기
          </button>
        </div>
      ) : null}
    </section>
  );
}
