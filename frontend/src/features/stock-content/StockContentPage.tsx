import { useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getStockRelatedContents } from './api';
import {
  STOCK_CONTENT_SOURCE_LABELS,
  type StockRelatedContent,
  type StockRelatedContentSource,
} from './types';
import './stock-content.css';

const SOURCE_ORDER: StockRelatedContentSource[] = ['INTERNAL_NEWS', 'YOUTUBE', 'THREADS', 'OTHER'];

function groupContents(contents: StockRelatedContent[]) {
  return SOURCE_ORDER.map((sourceType) => ({
    sourceType,
    contents: contents.filter((content) => content.sourceType === sourceType),
  })).filter((group) => group.contents.length > 0);
}

function getYouTubeEmbedUrl(rawUrl: string) {
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

  return `https://www.youtube.com/embed/${videoId}`;
}

export function StockContentPage() {
  const { assetId: assetIdParam } = useParams();
  const assetId = Number(assetIdParam);
  const [response, setResponse] = useState<Awaited<
    ReturnType<typeof getStockRelatedContents>
  > | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!Number.isSafeInteger(assetId) || assetId <= 0) {
      setLoading(false);
      setError('올바른 종목을 찾을 수 없습니다.');
      return;
    }

    const controller = new AbortController();
    setLoading(true);
    setError('');
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

  const groups = useMemo(() => groupContents(response?.contents ?? []), [response]);

  return (
    <main className="stock-content-page">
      <Link className="stock-content-back" to="/">
        ← 포트폴리오로 돌아가기
      </Link>

      {loading ? <p className="stock-content-state">종목 관련 자료를 불러오고 있습니다.</p> : null}
      {!loading && error ? (
        <section className="stock-content-state" role="alert">
          <strong>관련 자료를 불러오지 못했어요.</strong>
          <p>{error}</p>
        </section>
      ) : null}
      {!loading && !error && response ? (
        <>
          <header className="stock-content-heading">
            <p>종목 정보 허브</p>
            <h1>{response.assetName}</h1>
            <span>{response.assetCode} · 관련 이슈와 자료</span>
          </header>

          {groups.length === 0 ? (
            <section className="stock-content-empty">
              <strong>아직 등록된 관련 자료가 없습니다.</strong>
              <p>관리자 화면에서 YouTube, Threads와 뉴스 링크를 등록할 수 있어요.</p>
            </section>
          ) : (
            <div className="stock-content-groups">
              {groups.map((group) => (
                <section key={group.sourceType} aria-labelledby={`source-${group.sourceType}`}>
                  <div className="stock-content-section-heading">
                    <h2 id={`source-${group.sourceType}`}>
                      {STOCK_CONTENT_SOURCE_LABELS[group.sourceType]}
                    </h2>
                    <span>{group.contents.length}</span>
                  </div>
                  <ul className="stock-content-list">
                    {group.contents.map((content) => (
                      <li key={content.id}>
                        {content.sourceType === 'YOUTUBE' && getYouTubeEmbedUrl(content.url) ? (
                          <div className="stock-content-video">
                            <iframe
                              title={content.title}
                              src={getYouTubeEmbedUrl(content.url) ?? undefined}
                              loading="lazy"
                              referrerPolicy="strict-origin-when-cross-origin"
                              allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                              allowFullScreen
                            />
                            <div className="stock-content-video-caption">
                              <strong>{content.title}</strong>
                              <a href={content.url} target="_blank" rel="noreferrer">
                                YouTube에서 보기 ↗
                              </a>
                            </div>
                          </div>
                        ) : (
                          <a href={content.url} target="_blank" rel="noreferrer">
                            <strong>{content.title}</strong>
                            <span>{content.url}</span>
                            <span aria-hidden="true">↗</span>
                          </a>
                        )}
                      </li>
                    ))}
                  </ul>
                </section>
              ))}
            </div>
          )}
        </>
      ) : null}
    </main>
  );
}
