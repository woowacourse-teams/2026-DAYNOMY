import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import defaultNewsImage from '../../assets/default-news-real-estate.webp';
import { getStockRelatedContents } from './api';
import {
  STOCK_CONTENT_SOURCE_LABELS,
  type StockRelatedContent,
  type StockRelatedContentSource,
} from './types';
import './stock-content.css';

const SOURCE_ORDER: StockRelatedContentSource[] = ['INTERNAL_NEWS', 'YOUTUBE', 'THREADS', 'OTHER'];

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
  const videoId = getYouTubeVideoId(content.url);
  const [source, setSource] = useState(
    content.imageUrl ?? (videoId ? `https://i.ytimg.com/vi/${videoId}/hqdefault.jpg` : null),
  );

  if (!source) {
    return <span className="stock-content-video-thumbnail-fallback">YouTube</span>;
  }

  return (
    <img
      className="stock-content-video-thumbnail"
      src={source}
      alt=""
      loading="lazy"
      decoding="async"
      onError={() => setSource(null)}
    />
  );
}

function groupContents(contents: StockRelatedContent[]) {
  return SOURCE_ORDER.map((sourceType) => ({
    sourceType,
    contents: contents.filter((content) => content.sourceType === sourceType),
  })).filter((group) => group.contents.length > 0);
}

function getYouTubeEmbedUrl(rawUrl: string) {
  const videoId = getYouTubeVideoId(rawUrl);
  return videoId ? `https://www.youtube.com/embed/${videoId}` : null;
}

export function StockContentPage() {
  const { assetId: assetIdParam } = useParams();
  const assetId = Number(assetIdParam);
  const [response, setResponse] = useState<Awaited<
    ReturnType<typeof getStockRelatedContents>
  > | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [activeVideoId, setActiveVideoId] = useState<number | null>(null);
  const activeVideoTriggerRef = useRef<HTMLButtonElement | null>(null);
  const modalDialogRef = useRef<HTMLElement | null>(null);
  const modalCloseButtonRef = useRef<HTMLButtonElement | null>(null);

  useEffect(() => {
    if (!Number.isSafeInteger(assetId) || assetId <= 0) {
      setLoading(false);
      setError('올바른 종목을 찾을 수 없습니다.');
      return;
    }

    const controller = new AbortController();
    setLoading(true);
    setError('');
    setActiveVideoId(null);
    getStockRelatedContents(assetId, controller.signal)
      .then((nextResponse) => {
        if (!controller.signal.aborted) setResponse(nextResponse);
      })
      .catch((caughtError) => {
        if (!controller.signal.aborted) {
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

  const groups = useMemo(() => groupContents(response?.contents ?? []), [response]);
  const contentCount = response?.contents.length ?? 0;
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
          <>
            <header className="stock-content-heading">
              <div className="stock-content-title-row">
                <h1>{response.assetName}</h1>
                <span className="stock-content-code">{response.assetCode}</span>
              </div>
              <p>관련 이슈와 시장 자료를 한 곳에서 확인하세요.</p>
              <div className="stock-content-summary" aria-label="자료 요약">
                <strong>{contentCount}</strong>
                <span>개의 관련 자료</span>
              </div>
            </header>

            {groups.length === 0 ? (
              <section className="stock-content-empty">
                <span className="stock-content-empty-mark">—</span>
                <strong>아직 등록된 관련 자료가 없습니다.</strong>
                <p>새로운 이슈와 영상을 등록하면 이곳에서 확인할 수 있어요.</p>
              </section>
            ) : (
              <div className="stock-content-groups">
                {groups.map((group) => {
                  const isVideoGroup = group.sourceType === 'YOUTUBE';
                  const isNewsGroup = group.sourceType === 'INTERNAL_NEWS';

                  return (
                    <section
                      className={
                        isVideoGroup
                          ? 'stock-content-group stock-content-group--videos'
                          : 'stock-content-group'
                      }
                      key={group.sourceType}
                      aria-labelledby={`source-${group.sourceType}`}
                    >
                      <div className="stock-content-section-heading">
                        <div>
                          <h2 id={`source-${group.sourceType}`}>
                            {STOCK_CONTENT_SOURCE_LABELS[group.sourceType]}
                          </h2>
                          <span>
                            {isVideoGroup ? '관련 영상' : '관련 링크'} · {group.contents.length}개
                          </span>
                        </div>
                        <span
                          className="stock-content-count"
                          aria-label={`${group.contents.length}개`}
                        >
                          {group.contents.length}
                        </span>
                      </div>
                      <ul
                        className={
                          isVideoGroup
                            ? 'stock-content-list stock-content-list--videos'
                            : isNewsGroup
                              ? 'stock-content-list stock-content-list--news'
                              : 'stock-content-list'
                        }
                      >
                        {group.contents.map((content) => (
                          <li key={content.id}>
                            {isVideoGroup && getYouTubeEmbedUrl(content.url) ? (
                              <article className="stock-content-video">
                                <div className="stock-content-video-frame">
                                  <button
                                    className="stock-content-video-preview"
                                    type="button"
                                    aria-label={`${content.title} 크게 재생`}
                                    onClick={(event) => {
                                      activeVideoTriggerRef.current = event.currentTarget;
                                      setActiveVideoId(content.id);
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
                                  <a href={content.url} target="_blank" rel="noreferrer">
                                    <span>YouTube에서 보기</span>
                                    <ArrowUpRightIcon />
                                  </a>
                                </div>
                              </article>
                            ) : isNewsGroup ? (
                              <a
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
                            ) : (
                              <a
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
                          </li>
                        ))}
                      </ul>
                    </section>
                  );
                })}
              </div>
            )}
          </>
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
