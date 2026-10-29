import { youtubeThumbnail } from '../news/issue/feed';
import { useAuth } from '../../hooks/useLoginStatus';
import { trackEvent, type AnalyticsParams } from '../../analytics';
import { trackLoadResult, useContentView } from '../news/issue/analytics';
import {
  getContentPopularity,
  recordContentInteraction,
  sortByPopularity,
  stockContentReference,
  type PopularityScores,
} from '../news/issue/popularity';
import { memo, useEffect, useMemo, useRef, useState, type RefObject } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import defaultNewsImage from '../../assets/default-news-real-estate.webp';
import { usePortfolioHoldings } from '../portfolio/hooks/usePortfolioHoldings';
import { getStockRelatedContents } from './api';
import {
  STOCK_CONTENT_SOURCE_LABELS,
  type StockRelatedContent,
  type StockRelatedContentSource,
} from './types';
import './stock-content.css';

type ContentFilter = 'ALL' | StockRelatedContentSource;

const SOURCE_FILTERS: { value: ContentFilter; label: string }[] = [
  { value: 'ALL', label: '전체' },
  { value: 'INTERNAL_NEWS', label: 'DAYNOMY' },
  { value: 'YOUTUBE', label: 'YouTube' },
  { value: 'THREADS', label: 'Threads' },
  { value: 'X', label: 'X' },
];

const X_EMBED_SCRIPT_ID = 'daynomy-x-embed-script';
const THREADS_EMBED_SCRIPT_ID = 'daynomy-threads-embed-script';

function ArrowLeftIcon() {
  return (
    <svg viewBox="0 0 16 16" aria-hidden="true">
      <path d="M10.75 3.25 6 8l4.75 4.75M6.5 8h7" />
    </svg>
  );
}

function ArrowUpRightIcon() {
  return (
    <svg viewBox="0 0 16 16" aria-hidden="true">
      <path d="M4.5 11.5 11.75 4.25M6.25 4.25h5.5v5.5" />
    </svg>
  );
}

function PlayIcon() {
  return (
    <svg viewBox="0 0 16 16" aria-hidden="true">
      <path d="m6.25 4.75 5 3.25-5 3.25z" fill="currentColor" stroke="none" />
    </svg>
  );
}

function ContentThumbnail({ imageUrl }: { imageUrl: string | null }) {
  const [source, setSource] = useState(imageUrl ?? defaultNewsImage);

  return (
    <img
      className="stock-content-news-thumbnail"
      src={source}
      alt=""
      loading="lazy"
      decoding="async"
      onError={() => {
        if (source !== defaultNewsImage) setSource(defaultNewsImage);
      }}
    />
  );
}

function getYouTubeVideoId(rawUrl: string) {
  let parsedUrl: URL;
  try {
    parsedUrl = new URL(rawUrl);
  } catch {
    return null;
  }

  const hostname = parsedUrl.hostname.toLowerCase();
  const pathSegments = parsedUrl.pathname.split('/').filter(Boolean);
  let videoId = '';

  if (hostname === 'youtu.be') {
    videoId = pathSegments[0] ?? '';
  } else if (hostname === 'youtube.com' || hostname === 'www.youtube.com') {
    if (parsedUrl.pathname === '/watch') {
      videoId = parsedUrl.searchParams.get('v') ?? '';
    } else if (pathSegments[0] === 'embed') {
      videoId = pathSegments[1] ?? '';
    }
  }

  if (!videoId || !/^[A-Za-z0-9_-]+$/.test(videoId)) {
    return null;
  }

  return videoId;
}

function YouTubeThumbnail({ content }: { content: StockRelatedContent }) {
  const source = youtubeThumbnail(content.url);
  const [failedSource, setFailedSource] = useState<string | null>(null);

  if (!source || source === failedSource) {
    return <span className="stock-content-video-thumbnail-fallback">YouTube</span>;
  }

  return (
    <img
      className="stock-content-video-thumbnail"
      src={source}
      alt=""
      loading="lazy"
      decoding="async"
      onError={() => setFailedSource(source)}
    />
  );
}

function SocialPostEmbed({
  content,
  analytics,
  onOriginalClick,
}: {
  content: StockRelatedContent;
  analytics: AnalyticsParams;
  onOriginalClick: () => void;
}) {
  const isThreads = content.sourceType === 'THREADS';

  return (
    <article className="stock-content-social-embed">
      {isThreads ? (
        <blockquote
          className="text-post-media"
          data-text-post-permalink={content.url}
          data-text-post-version="0"
        >
          <a
            onClick={() => {
              trackEvent('click_content_original', analytics);
              onOriginalClick();
            }}
            href={content.url}
            target="_blank"
            rel="noreferrer"
          >
            {content.title}
          </a>
        </blockquote>
      ) : (
        <blockquote className="twitter-tweet" data-dnt="true">
          <a
            onClick={() => {
              trackEvent('click_content_original', analytics);
              onOriginalClick();
            }}
            href={content.url}
            target="_blank"
            rel="noreferrer"
          >
            {content.title}
          </a>
        </blockquote>
      )}
      <a
        onClick={() => {
          trackEvent('click_content_original', analytics);
          onOriginalClick();
        }}
        className="stock-content-social-fallback"
        href={content.url}
        target="_blank"
        rel="noreferrer"
      >
        <span>{STOCK_CONTENT_SOURCE_LABELS[content.sourceType]}에서 원문 보기</span>
        <ArrowUpRightIcon />
      </a>
    </article>
  );
}

function getYouTubeEmbedUrl(rawUrl: string) {
  const videoId = getYouTubeVideoId(rawUrl);
  return videoId ? `https://www.youtube.com/embed/${videoId}` : null;
}

const StockContentFeed = memo(function StockContentFeed({
  contents,
  selectedSourceLabel,
  triggerRef,
  onPlay,
}: {
  contents: StockRelatedContent[];
  selectedSourceLabel: string;
  triggerRef: RefObject<HTMLButtonElement | null>;
  onPlay: (id: number) => void;
}) {
  return (
    <ul className="stock-content-feed" aria-label={`${selectedSourceLabel} 소식 목록`}>
      {contents.map((content, index) => {
        const analytics = {
          surface: 'stock_contents',
          content_id: `content-${content.id}`,
          content_type: content.sourceType === 'INTERNAL_NEWS' ? 'news' : 'external',
          source_type: content.sourceType === 'INTERNAL_NEWS' ? 'DAYNOMY' : content.sourceType,
          position: index + 1,
        };
        const isVideo = content.sourceType === 'YOUTUBE';
        const isNews = content.sourceType === 'INTERNAL_NEWS';
        const isSocial = content.sourceType === 'THREADS' || content.sourceType === 'X';

        return (
          <li key={content.id}>
            {isVideo && getYouTubeEmbedUrl(content.url) ? (
              <article className="stock-content-video">
                <div className="stock-content-video-frame">
                  <button
                    className="stock-content-video-preview"
                    type="button"
                    aria-label={`${content.title} 크게 재생`}
                    onClick={(event) => {
                      trackEvent('open_content_video', analytics);
                      void recordContentInteraction(stockContentReference(content), 'OPEN');
                      triggerRef.current = event.currentTarget;
                      onPlay(content.id);
                    }}
                  >
                    <YouTubeThumbnail content={content} />
                    <span className="stock-content-video-play" aria-hidden="true">
                      <PlayIcon />
                    </span>
                  </button>
                </div>
                <div className="stock-content-video-caption">
                  <strong>{content.title}</strong>
                  <a
                    onClick={() => {
                      trackEvent('click_content_original', analytics);
                      void recordContentInteraction(
                        stockContentReference(content),
                        'OUTBOUND_CLICK',
                      );
                    }}
                    href={content.url}
                    target="_blank"
                    rel="noreferrer"
                  >
                    <span>YouTube에서 보기</span>
                    <ArrowUpRightIcon />
                  </a>
                </div>
              </article>
            ) : isNews ? (
              <a
                onClick={() => {
                  trackEvent('click_content', analytics);
                  void recordContentInteraction(stockContentReference(content), 'OPEN');
                }}
                className="stock-content-news-card"
                href={content.url}
                target="_blank"
                rel="noreferrer"
              >
                <ContentThumbnail imageUrl={content.imageUrl} />
                <span className="stock-content-news-card-copy">
                  <span>DAYNOMY 이슈</span>
                  <strong>{content.title}</strong>
                </span>
                <ArrowUpRightIcon />
              </a>
            ) : isSocial ? (
              <SocialPostEmbed
                content={content}
                analytics={analytics}
                onOriginalClick={() =>
                  void recordContentInteraction(stockContentReference(content), 'OUTBOUND_CLICK')
                }
              />
            ) : (
              <a
                onClick={() => {
                  trackEvent('click_content_original', analytics);
                  void recordContentInteraction(stockContentReference(content), 'OUTBOUND_CLICK');
                }}
                className="stock-content-link-card"
                href={content.url}
                target="_blank"
                rel="noreferrer"
              >
                <span className="stock-content-link-card-icon" aria-hidden="true">
                  <ArrowUpRightIcon />
                </span>
                <span className="stock-content-link-card-copy">
                  <strong>{content.title}</strong>
                  <span>{content.url}</span>
                </span>
                <ArrowUpRightIcon />
              </a>
            )}
            {content.publishedDate ? (
              <time className="stock-content-published-date" dateTime={content.publishedDate}>
                게시일 {content.publishedDate.replaceAll('-', '.')}
              </time>
            ) : null}
          </li>
        );
      })}
    </ul>
  );
});

export function StockContentPage() {
  const { assetId: assetIdParam } = useParams();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const assetId = Number(assetIdParam);
  const { isLoggedIn, loading: authLoading } = useAuth();
  const { holdings, loading: holdingsLoading } = usePortfolioHoldings(isLoggedIn);
  const [response, setResponse] = useState<Awaited<
    ReturnType<typeof getStockRelatedContents>
  > | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [selectedSource, setSelectedSource] = useState<ContentFilter>('ALL');
  const [activeVideoId, setActiveVideoId] = useState<number | null>(null);
  const [popularity, setPopularity] = useState<PopularityScores>(new Map());
  const [popularityLoading, setPopularityLoading] = useState(false);
  const [popularityError, setPopularityError] = useState('');
  const sort = params.get('sort') === 'popular' ? 'popular' : 'latest';
  const activeVideoTriggerRef = useRef<HTMLButtonElement | null>(null);
  const modalDialogRef = useRef<HTMLElement | null>(null);
  const modalCloseButtonRef = useRef<HTMLButtonElement | null>(null);

  useEffect(() => {
    if (!Number.isSafeInteger(assetId) || assetId <= 0) {
      setLoading(false);
      setError('올바른 종목을 찾을 수 없습니다.');
      return;
    }

    const startedAt = performance.now();
    const controller = new AbortController();
    setLoading(true);
    setError('');
    setSelectedSource('ALL');
    setActiveVideoId(null);
    getStockRelatedContents(assetId, controller.signal)
      .then((nextResponse) => {
        if (!controller.signal.aborted) {
          setResponse(nextResponse);
          trackLoadResult(
            'stock_contents',
            'all',
            startedAt,
            'success',
            nextResponse.contents.length,
            0,
          );
        }
      })
      .catch((caughtError) => {
        if (!controller.signal.aborted) {
          trackLoadResult('stock_contents', 'all', startedAt, 'error', 0, 0);
          setResponse(null);
          setError(
            caughtError instanceof Error ? caughtError.message : '관련 자료를 불러오지 못했습니다.',
          );
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [assetId]);

  useEffect(() => {
    if (sort !== 'popular') {
      setPopularityLoading(false);
      setPopularityError('');
      return;
    }
    const controller = new AbortController();
    setPopularityLoading(true);
    setPopularityError('');
    setPopularity(new Map());
    getContentPopularity(controller.signal)
      .then((scores) => {
        if (!controller.signal.aborted) setPopularity(scores);
      })
      .catch(() => {
        if (!controller.signal.aborted) setPopularityError('인기순을 불러오지 못했습니다.');
      })
      .finally(() => {
        if (!controller.signal.aborted) setPopularityLoading(false);
      });
    return () => controller.abort();
  }, [sort]);

  useEffect(() => {
    const socialContents = (response?.contents ?? []).filter(
      (content) => selectedSource === 'ALL' || content.sourceType === selectedSource,
    );
    if (socialContents.some((content) => content.sourceType === 'X')) {
      const renderXEmbeds = () => {
        const xWindow = window as typeof window & {
          twttr?: { widgets?: { load: (element?: HTMLElement) => void } };
        };
        xWindow.twttr?.widgets?.load(document.querySelector('.stock-content-page') ?? undefined);
      };
      const existingScript = document.getElementById(X_EMBED_SCRIPT_ID) as HTMLScriptElement | null;
      if (existingScript) {
        renderXEmbeds();
      } else {
        const script = document.createElement('script');
        script.id = X_EMBED_SCRIPT_ID;
        script.src = 'https://platform.x.com/widgets.js';
        script.async = true;
        script.addEventListener('load', renderXEmbeds, { once: true });
        document.body.append(script);
      }
    }

    if (socialContents.some((content) => content.sourceType === 'THREADS')) {
      document.getElementById(THREADS_EMBED_SCRIPT_ID)?.remove();
      const script = document.createElement('script');
      script.id = THREADS_EMBED_SCRIPT_ID;
      script.src = 'https://www.threads.com/embed.js';
      script.async = true;
      document.body.append(script);
    }
  }, [response, selectedSource]);

  useEffect(() => {
    if (activeVideoId === null) return;

    const previousOverflow = document.body.style.overflow;
    const trigger = activeVideoTriggerRef.current;
    const focusableSelector =
      'button:not([disabled]), iframe, a[href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setActiveVideoId(null);
        return;
      }

      if (event.key !== 'Tab') return;

      const dialog = modalDialogRef.current;
      if (!dialog) return;

      const focusableElements = Array.from(dialog.querySelectorAll<HTMLElement>(focusableSelector));
      if (focusableElements.length === 0) {
        event.preventDefault();
        return;
      }

      const firstElement = focusableElements[0];
      const lastElement = focusableElements[focusableElements.length - 1];
      if (!dialog.contains(document.activeElement)) {
        event.preventDefault();
        (event.shiftKey ? lastElement : firstElement).focus();
      } else if (event.shiftKey && document.activeElement === firstElement) {
        event.preventDefault();
        lastElement.focus();
      } else if (!event.shiftKey && document.activeElement === lastElement) {
        event.preventDefault();
        firstElement.focus();
      }
    };

    document.body.style.overflow = 'hidden';
    modalCloseButtonRef.current?.focus();
    window.addEventListener('keydown', handleKeyDown);

    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener('keydown', handleKeyDown);
      if (trigger && document.contains(trigger)) trigger.focus();
    };
  }, [activeVideoId]);

  const sourceFilters = useMemo(() => {
    const contents = response?.contents ?? [];
    const filters = SOURCE_FILTERS.map((filter) => ({
      ...filter,
      count:
        filter.value === 'ALL'
          ? contents.length
          : contents.filter((content) => content.sourceType === filter.value).length,
    }));
    const otherCount = contents.filter((content) => content.sourceType === 'OTHER').length;
    return otherCount > 0
      ? [...filters, { value: 'OTHER' as const, label: '기타', count: otherCount }]
      : filters;
  }, [response]);
  const visibleContents = useMemo(() => {
    const latestContents = (response?.contents ?? []).filter(
      (content) => selectedSource === 'ALL' || content.sourceType === selectedSource,
    );
    return sort === 'popular'
      ? sortByPopularity(latestContents, stockContentReference, popularity)
      : latestContents;
  }, [popularity, response, selectedSource, sort]);
  const contentCount = response?.contents.length ?? 0;
  const selectedSourceLabel =
    sourceFilters.find((filter) => filter.value === selectedSource)?.label ?? '전체';
  const activeVideo = useMemo(
    () =>
      response?.contents.find(
        (content) =>
          content.id === activeVideoId &&
          content.sourceType === 'YOUTUBE' &&
          getYouTubeEmbedUrl(content.url),
      ) ?? null,
    [activeVideoId, response],
  );

  const settled = !loading && !popularityLoading && !error && response?.assetId === assetId;
  useContentView(settled, 'view_content_list', {
    surface: 'stock_contents',
    source_type: selectedSource === 'INTERNAL_NEWS' ? 'DAYNOMY' : selectedSource,
    result_count: visibleContents.length,
  });
  useContentView(settled && !visibleContents.length, 'view_content_empty', {
    surface: 'stock_contents',
    reason: selectedSource === 'ALL' ? 'no_linked_content' : 'filter_empty',
    source_type: selectedSource === 'INTERNAL_NEWS' ? 'DAYNOMY' : selectedSource,
  });

  return (
    <main className="stock-content-page">
      <div className="stock-content-container">
        <nav className="stock-content-topbar" aria-label="현재 위치">
          <Link className="stock-content-back" to="/">
            <ArrowLeftIcon />
            <span>포트폴리오</span>
          </Link>
          <span className="stock-content-topbar-label">종목 자료</span>
        </nav>

        {loading ? (
          <div className="stock-content-loading" aria-busy="true" aria-label="자료 불러오는 중">
            <div className="stock-content-skeleton stock-content-skeleton--small" />
            <div className="stock-content-skeleton stock-content-skeleton--title" />
            <div className="stock-content-skeleton stock-content-skeleton--meta" />
            <div className="stock-content-loading-section">
              <div className="stock-content-skeleton stock-content-skeleton--section" />
              <div className="stock-content-skeleton stock-content-skeleton--row" />
              <div className="stock-content-skeleton stock-content-skeleton--row" />
            </div>
          </div>
        ) : null}

        {!loading && error ? (
          <section className="stock-content-state" role="alert">
            <span className="stock-content-state-mark">!</span>
            <strong>관련 자료를 불러오지 못했어요.</strong>
            <p>{error}</p>
            <Link className="stock-content-state-link" to="/">
              포트폴리오로 돌아가기
              <ArrowUpRightIcon />
            </Link>
          </section>
        ) : null}

        {!loading && !error && response ? (
          <div
            className={
              authLoading || holdingsLoading || holdings.length > 0
                ? 'stock-content-layout'
                : 'stock-content-layout stock-content-layout--without-sidebar'
            }
          >
            {authLoading || holdingsLoading || holdings.length > 0 ? (
              <aside className="stock-content-assets" aria-label="내 포트폴리오 종목">
                <div className="stock-content-assets-heading">
                  <strong>내 포트폴리오</strong>
                  {!holdingsLoading ? <span>{holdings.length}</span> : null}
                </div>
                {holdingsLoading ? (
                  <div className="stock-content-assets-loading" aria-label="보유 종목 불러오는 중">
                    <span />
                    <span />
                    <span />
                  </div>
                ) : (
                  <nav className="stock-content-asset-list" aria-label="종목 선택">
                    {holdings.map((holding) => (
                      <Link
                        className={
                          holding.assetId === assetId
                            ? 'stock-content-asset is-active'
                            : 'stock-content-asset'
                        }
                        to={`/stocks/${holding.assetId}${sort === 'popular' ? '?sort=popular' : ''}`}
                        aria-current={holding.assetId === assetId ? 'page' : undefined}
                        key={holding.assetId}
                      >
                        <span className="stock-content-asset-mark" aria-hidden="true">
                          {holding.name.slice(0, 1)}
                        </span>
                        <span className="stock-content-asset-copy">
                          <strong>{holding.name}</strong>
                          <small>
                            {holding.assetCode} · {holding.market}
                          </small>
                        </span>
                      </Link>
                    ))}
                  </nav>
                )}
              </aside>
            ) : null}

            <section className="stock-content-main" aria-labelledby="stock-content-title">
              {holdings.length > 0 ? (
                <label className="stock-content-mobile-select">
                  <span>종목 선택</span>
                  <select
                    value={assetId}
                    onChange={(event) =>
                      navigate(
                        `/stocks/${event.currentTarget.value}${sort === 'popular' ? '?sort=popular' : ''}`,
                      )
                    }
                  >
                    {holdings.map((holding) => (
                      <option value={holding.assetId} key={holding.assetId}>
                        {holding.name} · {holding.assetCode}
                      </option>
                    ))}
                  </select>
                </label>
              ) : null}

              <header className="stock-content-heading">
                <div className="stock-content-title-row">
                  <h1 id="stock-content-title">{response.assetName} 소식</h1>
                  <span className="stock-content-code">{response.assetCode}</span>
                </div>
                <p>이슈부터 영상과 SNS 반응까지 한 곳에서 확인하세요.</p>
                <div className="stock-content-summary" aria-label="자료 요약">
                  <strong>{contentCount}</strong>
                  <span>개의 관련 자료</span>
                </div>
              </header>

              <div className="stock-content-controls">
                <div className="stock-content-source-tabs" aria-label="소식 출처">
                  {sourceFilters.map((filter) => (
                    <button
                      className={
                        selectedSource === filter.value
                          ? 'stock-content-source-tab is-active'
                          : 'stock-content-source-tab'
                      }
                      type="button"
                      aria-label={`${filter.label} ${filter.count}개`}
                      aria-pressed={selectedSource === filter.value}
                      onClick={() => {
                        if (filter.value === selectedSource) return;
                        trackEvent('filter_content_source', {
                          surface: 'stock_contents',
                          previous_source:
                            selectedSource === 'INTERNAL_NEWS' ? 'DAYNOMY' : selectedSource,
                          source_type: filter.value === 'INTERNAL_NEWS' ? 'DAYNOMY' : filter.value,
                          result_count: filter.count,
                        });
                        setSelectedSource(filter.value);
                      }}
                      key={filter.value}
                    >
                      <span>{filter.label}</span>
                      <small>{filter.count}</small>
                    </button>
                  ))}
                </div>
                <div className="stock-content-sort" role="group" aria-label="콘텐츠 정렬">
                  <button
                    type="button"
                    aria-pressed={sort === 'latest'}
                    onClick={() => {
                      const next = new URLSearchParams(params);
                      next.delete('sort');
                      setParams(next);
                    }}
                  >
                    최신순
                  </button>
                  <button
                    type="button"
                    aria-pressed={sort === 'popular'}
                    onClick={() => {
                      const next = new URLSearchParams(params);
                      next.set('sort', 'popular');
                      setParams(next);
                    }}
                  >
                    인기순
                  </button>
                </div>
              </div>

              {popularityLoading ? (
                <p className="stock-content-sort-state" role="status">
                  인기순을 불러오는 중입니다.
                </p>
              ) : null}
              {popularityError ? (
                <p className="stock-content-sort-state" role="alert">
                  {popularityError} 최신순으로 표시합니다.
                </p>
              ) : null}

              {visibleContents.length === 0 ? (
                <section className="stock-content-empty">
                  <span className="stock-content-empty-mark">—</span>
                  <strong>
                    {selectedSource === 'ALL'
                      ? '아직 등록된 관련 자료가 없습니다.'
                      : `아직 등록된 ${selectedSourceLabel} 소식이 없습니다.`}
                  </strong>
                  <p>새로운 소식이 등록되면 이곳에서 확인할 수 있어요.</p>
                </section>
              ) : (
                <StockContentFeed
                  contents={visibleContents}
                  selectedSourceLabel={selectedSourceLabel}
                  triggerRef={activeVideoTriggerRef}
                  onPlay={setActiveVideoId}
                />
              )}
            </section>
          </div>
        ) : null}
      </div>

      {activeVideo ? (
        <div
          className="stock-content-video-modal"
          role="presentation"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) setActiveVideoId(null);
          }}
        >
          <section
            className="stock-content-video-modal-dialog"
            ref={modalDialogRef}
            role="dialog"
            aria-modal="true"
            aria-labelledby="stock-content-video-modal-title"
            onMouseDown={(event) => event.stopPropagation()}
          >
            <div className="stock-content-video-modal-header">
              <strong id="stock-content-video-modal-title">{activeVideo.title}</strong>
              <button
                className="stock-content-video-modal-close"
                ref={modalCloseButtonRef}
                type="button"
                aria-label="영상 닫기"
                onClick={() => setActiveVideoId(null)}
              >
                닫기
              </button>
            </div>
            <div className="stock-content-video-modal-frame">
              <iframe
                title={activeVideo.title}
                src={
                  getYouTubeEmbedUrl(activeVideo.url)
                    ? `${getYouTubeEmbedUrl(activeVideo.url)}?autoplay=1&playsinline=1&rel=0`
                    : undefined
                }
                loading="eager"
                referrerPolicy="strict-origin-when-cross-origin"
                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                allowFullScreen
              />
            </div>
          </section>
        </div>
      ) : null}
    </main>
  );
}
