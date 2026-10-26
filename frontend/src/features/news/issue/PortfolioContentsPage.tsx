import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useAuth } from '../../../hooks/useLoginStatus';
import { trackEvent } from '../../../analytics';
import { usePortfolioHoldings } from '../../portfolio/hooks/usePortfolioHoldings';
import { getStockRelatedContents } from '../../stock-content/api';
import {
  STOCK_CONTENT_SOURCE_LABELS,
  type StockRelatedContent,
  type StockRelatedContentsResponse,
} from '../../stock-content/types';
import { SocialPostEmbed } from '../../people/SocialPostEmbed';
import { IssuePagination } from './IssuePagination';
import { trackLoadResult, useContentView } from './analytics';
import { feedDate, youtubeId, youtubeThumbnail } from './feed';
import {
  getContentPopularity,
  recordContentInteraction,
  sortByPopularity,
  stockContentReference,
  type PopularityScores,
} from './popularity';
import xMark from '../../../assets/platform-x.svg';
import threadsMark from '../../../assets/platform-threads.svg';
import './issue.css';
import './portfolioContentsNext.css';
import { PortfolioReadingNext } from './PortfolioReadingNext';
import { examplePortfolio, exampleResponses } from './portfolioContentsExample';

const sources = [
  ['ALL', '전체'],
  ['INTERNAL_NEWS', '데이노미'],
  ['X', 'X'],
  ['THREADS', 'Threads'],
  ['YOUTUBE', 'YouTube'],
  ['OTHER', '기타'],
] as const;

export function PortfolioContentsPage() {
  const { isLoggedIn, loading } = useAuth();
  const [params] = useSearchParams();
  const example = import.meta.env.DEV && params.get('data') === 'example';
  if (example) return <PortfolioContentsView portfolio={examplePortfolio} example />;
  if (loading)
    return (
      <main className="issue-page">
        <p role="status">보유 종목을 불러오는 중입니다.</p>
      </main>
    );
  return <PortfolioContents key={String(isLoggedIn)} isLoggedIn={isLoggedIn} />;
}

function PortfolioContents({ isLoggedIn }: { isLoggedIn: boolean }) {
  const portfolio = usePortfolioHoldings(isLoggedIn);
  return <PortfolioContentsView portfolio={portfolio} />;
}

// Keep the example independent of saved holdings and their API requests.
function PortfolioContentsView({
  portfolio,
  example = false,
}: {
  portfolio: {
    holdings: { assetId: number; name: string }[];
    loading: boolean;
    error: string;
    retry: () => void;
  };
  example?: boolean;
}) {
  const [params, setParams] = useSearchParams();
  const selectedAsset = params.get('asset') ?? 'ALL';
  const requestedContent = params.get('open');
  const revealedContent = useRef<string | null>(null);
  const requestedSource = params.get('source') ?? 'ALL';
  const source = sources.some(([value]) => value === requestedSource) ? requestedSource : 'ALL';
  const page = Math.max(1, Number.parseInt(params.get('page') ?? '1', 10) || 1);
  const sort = params.get('sort') === 'popular' ? 'popular' : 'latest';
  const [responses, setResponses] = useState<StockRelatedContentsResponse[]>([]);
  const [loadedKey, setLoadedKey] = useState<string | null>(null);
  const [failedIds, setFailedIds] = useState<number[]>([]);
  const [attempt, setAttempt] = useState(0);
  const [expanded, setExpanded] = useState<string | null>(null);
  const [companySearch, setCompanySearch] = useState('');
  const [popularity, setPopularity] = useState<PopularityScores>(new Map());
  const [popularityLoading, setPopularityLoading] = useState(false);
  const [popularityError, setPopularityError] = useState('');
  const assetKey = JSON.stringify(
    [
      ...new Set(
        portfolio.holdings
          .filter((item) => selectedAsset === 'ALL' || String(item.assetId) === selectedAsset)
          .map((item) => item.assetId),
      ),
    ].sort((a, b) => a - b),
  );

  useEffect(() => {
    const previous = document.title;
    document.title = '보유 종목 소식 | DAYNOMY';
    return () => {
      document.title = previous;
    };
  }, []);

  useEffect(() => {
    if (portfolio.loading || portfolio.error) return;
    if (example) {
      setResponses(exampleResponses);
      setFailedIds([]);
      setLoadedKey(assetKey);
      return;
    }
    const startedAt = performance.now();
    const controller = new AbortController();
    const ids: number[] = JSON.parse(assetKey);
    setLoadedKey(null);
    setResponses([]);
    setFailedIds([]);
    if (!ids.length) {
      setLoadedKey(assetKey);
      return () => controller.abort();
    }
    Promise.allSettled(ids.map((id) => getStockRelatedContents(id, controller.signal))).then(
      (results) => {
        if (controller.signal.aborted) return;
        const nextResponses = results.flatMap((result) =>
          result.status === 'fulfilled' ? [result.value] : [],
        );
        const nextFailedIds = ids.filter((_, index) => results[index].status === 'rejected');
        trackLoadResult(
          'portfolio_contents',
          selectedAsset === 'ALL' ? 'all' : 'selected',
          startedAt,
          nextFailedIds.length === ids.length
            ? 'error'
            : nextFailedIds.length
              ? 'partial_error'
              : 'success',
          new Set(nextResponses.flatMap((response) => response.contents.map((item) => item.url)))
            .size,
          attempt,
        );
        setResponses(nextResponses);
        setFailedIds(nextFailedIds);
        setLoadedKey(assetKey);
      },
    );
    return () => controller.abort();
  }, [assetKey, portfolio.loading, portfolio.error, attempt, selectedAsset, example]);

  useEffect(() => {
    if (example || sort !== 'popular') {
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
  }, [attempt, sort, example]);

  function filter(key: 'asset' | 'source' | 'page' | 'sort', value: string) {
    const next = new URLSearchParams(params);
    next.delete('open');
    if (key === 'sort' && value === 'latest') next.delete('sort');
    else next.set(key, value);
    if (key !== 'page') next.delete('page');
    setExpanded(null);
    setParams(next);
  }

  const selectedHolding = portfolio.holdings.find(
    (holding) => String(holding.assetId) === selectedAsset,
  );
  const validSelection = selectedAsset === 'ALL' || !!selectedHolding;
  const loading = portfolio.loading || (!portfolio.error && loadedKey !== assetKey);
  const unique = new Map<string, StockRelatedContent & { companies: string[] }>();
  if (!loading && !portfolio.error) {
    for (const response of responses) {
      if (selectedAsset !== 'ALL' && String(response.assetId) !== selectedAsset) continue;
      for (const item of response.contents) {
        if (source !== 'ALL' && item.sourceType !== source) continue;
        const existing = unique.get(item.url);
        if (existing) {
          if (
            (Date.parse(item.publishedDate ?? '') || 0) >
            (Date.parse(existing.publishedDate ?? '') || 0)
          )
            existing.publishedDate = item.publishedDate;
          if ((Date.parse(item.createdAt ?? '') || 0) > (Date.parse(existing.createdAt ?? '') || 0))
            existing.createdAt = item.createdAt;
          if (!existing.companies.includes(response.assetName))
            existing.companies.push(response.assetName);
        } else unique.set(item.url, { ...item, companies: [response.assetName] });
      }
    }
  }
  const latestItems = [...unique.values()].sort(
    (a, b) =>
      (Date.parse(b.publishedDate ?? b.createdAt ?? '') || 0) -
        (Date.parse(a.publishedDate ?? a.createdAt ?? '') || 0) || b.id - a.id,
  );
  const items =
    sort === 'popular'
      ? sortByPopularity(latestItems, stockContentReference, popularity)
      : latestItems;
  const totalPages = Math.max(1, Math.ceil(items.length / 12));
  const requestedIndex = items.findIndex((item) => String(item.id) === requestedContent);
  const requestedItem = requestedIndex >= 0 ? items[requestedIndex] : undefined;
  const requestedKey = requestedItem ? `${requestedItem.assetId}-${requestedItem.id}` : null;
  const currentPage =
    requestedIndex >= 0 ? Math.floor(requestedIndex / 12) + 1 : Math.min(page, totalPages);
  useEffect(() => {
    if (!requestedContent) revealedContent.current = null;
    if (!requestedKey || loading || popularityLoading || revealedContent.current === requestedKey)
      return;
    revealedContent.current = requestedKey;
    setExpanded(requestedKey);
    const frame = requestAnimationFrame(() => {
      const trigger = document.getElementById(`portfolio-trigger-${requestedKey}`);
      trigger?.scrollIntoView?.({ block: 'start' });
      trigger?.focus({ preventScroll: true });
    });
    return () => cancelAnimationFrame(frame);
  }, [requestedKey, requestedContent, loading, popularityLoading]);
  const selectionFailed =
    selectedAsset === 'ALL'
      ? failedIds.length > 0
      : failedIds.some((id) => String(id) === selectedAsset);
  const requestedAssetCount = (JSON.parse(assetKey) as number[]).length;
  const settled = !loading && !popularityLoading && !portfolio.error && validSelection;
  const listStatus = failedIds.length
    ? failedIds.length === requestedAssetCount
      ? 'error'
      : 'partial_error'
    : popularityError
      ? 'partial_error'
      : 'ready';
  const sourceType = source === 'INTERNAL_NEWS' ? 'DAYNOMY' : source;
  useContentView(!example && settled, 'view_content_list', {
    surface: 'portfolio_contents',
    source_type: sourceType,
    result_count: items.length,
    page_number: currentPage,
    status: listStatus,
  });
  useContentView(
    !example && settled && listStatus === 'ready' && !items.length,
    'view_content_empty',
    {
      surface: 'portfolio_contents',
      source_type: sourceType,
      reason: !portfolio.holdings.length
        ? 'no_holdings'
        : source !== 'ALL'
          ? 'filter_empty'
          : 'no_linked_content',
    },
  );

  const matchingHoldings = portfolio.holdings.filter((holding) =>
    holding.name.toLocaleLowerCase().includes(companySearch.trim().toLocaleLowerCase()),
  );

  return (
    <main className="issue-page portfolio-contents-page">
      {example && (
        <p className="portfolio-example-notice">
          가상 시나리오 · 아래 기업 소식은 실제 사실이 아니며, 실제 보유 자산에 저장되지 않습니다.
        </p>
      )}
      <div className="portfolio-contents-layout">
        <aside className="portfolio-company-sidebar" aria-label="보유 회사 탐색">
          <Link className="issue-back" to="/">
            ← 포트폴리오
          </Link>
          <label className="sr-only" htmlFor="portfolio-company-search">
            보유 종목 검색
          </label>
          <div className="portfolio-company-search">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <circle cx="10.5" cy="10.5" r="6.5" />
              <path d="m16 16 5 5" />
            </svg>
            <input
              id="portfolio-company-search"
              type="search"
              placeholder="종목 검색"
              value={companySearch}
              onChange={(event) => setCompanySearch(event.target.value)}
              disabled={portfolio.loading || !!portfolio.error}
            />
          </div>
          <nav className="portfolio-content-companies" aria-label="보유 회사 선택">
            <button aria-pressed={selectedAsset === 'ALL'} onClick={() => filter('asset', 'ALL')}>
              전체 보유 종목
            </button>
            {matchingHoldings.map((holding) => (
              <button
                key={holding.assetId}
                aria-pressed={selectedAsset === String(holding.assetId)}
                onClick={() => filter('asset', String(holding.assetId))}
              >
                <span className="portfolio-company-avatar" aria-hidden="true">
                  {holding.name.slice(0, 1)}
                </span>
                <span>{holding.name}</span>
              </button>
            ))}
            {!portfolio.loading &&
              !portfolio.error &&
              companySearch.trim() &&
              !matchingHoldings.length && (
                <p className="portfolio-company-empty" role="status">
                  검색한 보유 종목이 없습니다.
                </p>
              )}
          </nav>
        </aside>
        <section className="portfolio-company-content" aria-labelledby="portfolio-content-title">
          <header className="portfolio-content-heading">
            <div>
              <p className="portfolio-content-eyebrow">내 종목 소식</p>
              <h1 id="portfolio-content-title">{selectedHolding?.name ?? '전체 보유 종목'}</h1>
            </div>
            <Link to="/news">
              <span>모든 소식 보기</span>
              <svg
                viewBox="0 0 24 24"
                aria-hidden="true"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.7"
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                <path d="M4 12h16m-6-6 6 6-6 6" />
              </svg>
            </Link>
          </header>
          {!portfolio.loading &&
            !portfolio.error &&
            portfolio.holdings.length > 0 &&
            validSelection && (
              <div className="portfolio-content-toolbar">
                <div className="issue-source-filters" role="group" aria-label="콘텐츠 출처">
                  {sources.map(([value, label]) => (
                    <button
                      key={value}
                      type="button"
                      aria-pressed={source === value}
                      onClick={() => {
                        if (value === source) return;
                        if (!example)
                          trackEvent('filter_content_source', {
                            surface: 'portfolio_contents',
                            previous_source: sourceType,
                            source_type: value === 'INTERNAL_NEWS' ? 'DAYNOMY' : value,
                            result_count: new Set(
                              responses.flatMap((response) =>
                                response.contents
                                  .filter((item) => value === 'ALL' || item.sourceType === value)
                                  .map((item) => item.url),
                              ),
                            ).size,
                          });
                        filter('source', value);
                      }}
                    >
                      {label}
                    </button>
                  ))}
                </div>
                <div className="issue-sort-options" role="group" aria-label="콘텐츠 정렬">
                  <button
                    type="button"
                    aria-pressed={sort === 'latest'}
                    onClick={() => filter('sort', 'latest')}
                  >
                    최신순
                  </button>
                  <button
                    type="button"
                    aria-pressed={sort === 'popular'}
                    onClick={() => filter('sort', 'popular')}
                  >
                    인기순
                  </button>
                </div>
              </div>
            )}
          {loading || popularityLoading ? (
            <p className="issue-state" role="status">
              보유 종목 소식을 불러오는 중입니다.
            </p>
          ) : portfolio.error ? (
            <p className="issue-state" role="alert">
              {portfolio.error}{' '}
              <button
                onClick={() => {
                  trackEvent('retry_content_load', {
                    surface: 'portfolio_contents',
                    area: 'holdings',
                  });
                  portfolio.retry();
                }}
              >
                다시 시도
              </button>
            </p>
          ) : !validSelection ? (
            <p className="issue-state">
              현재 보유하지 않은 종목입니다.{' '}
              <button onClick={() => filter('asset', 'ALL')}>전체 보유 종목 보기</button>
            </p>
          ) : (
            <>
              {popularityError && (
                <p className="issue-state" role="alert">
                  {popularityError}{' '}
                  <button onClick={() => setAttempt((value) => value + 1)}>다시 시도</button>
                </p>
              )}
              {selectionFailed && (
                <p className="issue-state" role="alert">
                  일부 종목의 소식을 불러오지 못했습니다.{' '}
                  <button
                    onClick={() => {
                      trackEvent('retry_content_load', {
                        surface: 'portfolio_contents',
                        area: 'all',
                        attempt: attempt + 1,
                      });
                      setAttempt((value) => value + 1);
                    }}
                  >
                    다시 시도
                  </button>
                </p>
              )}
              {!portfolio.holdings.length ? (
                <p className="issue-state">보유 종목을 등록하면 관련 소식을 볼 수 있습니다. </p>
              ) : !items.length && !selectionFailed ? (
                <p className="issue-state">선택한 조건에 연결된 소식이 없습니다.</p>
              ) : null}
              <ul className="issue-feed issue-feed--visual">
                {items.slice((currentPage - 1) * 12, currentPage * 12).map((item, index) => {
                  const social =
                    item.sourceType === 'X' ||
                    item.sourceType === 'THREADS' ||
                    item.sourceType === 'YOUTUBE';
                  const key = `${item.assetId}-${item.id}`;
                  const videoId = item.sourceType === 'YOUTUBE' ? youtubeId(item.url) : null;
                  const image =
                    item.sourceType === 'YOUTUBE' ? youtubeThumbnail(item.url) : item.imageUrl;
                  const label =
                    item.sourceType === 'INTERNAL_NEWS'
                      ? '데이노미'
                      : STOCK_CONTENT_SOURCE_LABELS[item.sourceType];
                  const analytics = {
                    surface: 'portfolio_contents',
                    content_id: `content-${item.id}`,
                    content_type: item.sourceType === 'INTERNAL_NEWS' ? 'news' : 'external',
                    source_type: item.sourceType === 'INTERNAL_NEWS' ? 'DAYNOMY' : item.sourceType,
                    position: (currentPage - 1) * 12 + index + 1,
                  };
                  return (
                    <li key={key}>
                      <article className="issue-feed-row issue-feed-row--visual issue-feed-row--news">
                        <span className="issue-feed-thumbnail" aria-hidden="true">
                          <span className="issue-feed-thumbnail-fallback">
                            {item.sourceType === 'X' || item.sourceType === 'THREADS' ? (
                              <img src={item.sourceType === 'X' ? xMark : threadsMark} alt="" />
                            ) : (
                              label
                            )}
                          </span>
                          {image && (
                            <img
                              src={image}
                              alt=""
                              onError={(event) => {
                                event.currentTarget.hidden = true;
                              }}
                            />
                          )}
                        </span>
                        <div className="issue-meta">
                          <span className="issue-source">{label}</span>
                          <span>{item.companies.join(' · ')}</span>
                          <time dateTime={item.publishedDate ?? item.createdAt ?? undefined}>
                            {feedDate(item.publishedDate ?? item.createdAt)}
                          </time>
                        </div>
                        {social ? (
                          <button
                            id={`portfolio-trigger-${key}`}
                            className="issue-row-title"
                            aria-expanded={expanded === key}
                            aria-controls={`portfolio-post-${key}`}
                            onClick={() => {
                              if (!example && expanded !== key) {
                                trackEvent('expand_content', analytics);
                                void recordContentInteraction(stockContentReference(item), 'OPEN');
                              }
                              setExpanded(expanded === key ? null : key);
                            }}
                          >
                            <span className="issue-post-title">{item.title}</span>
                            <span className="issue-expand-label">
                              {expanded === key
                                ? '접기'
                                : example
                                  ? '글 읽기'
                                  : item.sourceType === 'YOUTUBE'
                                    ? '영상 보기'
                                    : '글 펼치기'}
                              <svg
                                className="issue-expand-chevron"
                                viewBox="0 0 16 16"
                                aria-hidden="true"
                              >
                                <path
                                  d="m4 6 4 4 4-4"
                                  fill="none"
                                  stroke="currentColor"
                                  strokeWidth="1.5"
                                />
                              </svg>
                            </span>
                          </button>
                        ) : (
                          <a
                            onClick={() => {
                              if (example) return;
                              trackEvent(
                                item.sourceType === 'INTERNAL_NEWS'
                                  ? 'click_content'
                                  : 'click_content_original',
                                analytics,
                              );
                              void recordContentInteraction(
                                stockContentReference(item),
                                item.sourceType === 'INTERNAL_NEWS' ? 'OPEN' : 'OUTBOUND_CLICK',
                              );
                            }}
                            className="issue-row-title"
                            href={
                              item.sourceType === 'INTERNAL_NEWS'
                                ? portfolioNewsUrl(item.url)
                                : item.url
                            }
                            target={item.sourceType === 'INTERNAL_NEWS' ? undefined : '_blank'}
                            rel="noreferrer"
                          >
                            <h3>{item.title}</h3>
                            <span className="issue-expand-label">
                              {item.sourceType === 'INTERNAL_NEWS' ? '기사 읽기' : '원문 보기'} ↗
                            </span>
                          </a>
                        )}
                        {social && (
                          <div
                            id={`portfolio-post-${key}`}
                            className="issue-inline-post"
                            hidden={expanded !== key}
                          >
                            {expanded === key && (
                              <>
                                <div className="issue-reading-embed">
                                  {item.sourceType === 'YOUTUBE' ? (
                                    <>
                                      {videoId ? (
                                        <iframe
                                          className="issue-video"
                                          title={item.title}
                                          src={`https://www.youtube-nocookie.com/embed/${videoId}`}
                                          allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                                          allowFullScreen
                                        />
                                      ) : (
                                        <p>이 영상은 YouTube에서 확인해 주세요.</p>
                                      )}
                                      <a
                                        onClick={() => {
                                          trackEvent('click_content_original', analytics);
                                          void recordContentInteraction(
                                            stockContentReference(item),
                                            'OUTBOUND_CLICK',
                                          );
                                        }}
                                        className="issue-video-link"
                                        href={item.url}
                                        target="_blank"
                                        rel="noreferrer"
                                      >
                                        YouTube에서 원문 보기 ↗
                                      </a>
                                    </>
                                  ) : (
                                    (item.sourceType === 'X' || item.sourceType === 'THREADS') && (
                                      <SocialPostEmbed
                                        analytics={analytics}
                                        onOriginalClick={() =>
                                          void recordContentInteraction(
                                            stockContentReference(item),
                                            'OUTBOUND_CLICK',
                                          )
                                        }
                                        post={{
                                          sourceType: item.sourceType,
                                          title: item.title,
                                          url: item.url,
                                        }}
                                      />
                                    )
                                  )}
                                </div>
                                <PortfolioReadingNext />
                                <button
                                  className="issue-inline-close"
                                  onClick={() => {
                                    setExpanded(null);
                                    document.getElementById(`portfolio-trigger-${key}`)?.focus();
                                  }}
                                >
                                  글 접기 ↑
                                </button>
                              </>
                            )}
                          </div>
                        )}
                      </article>
                    </li>
                  );
                })}
              </ul>
              {totalPages > 1 && (
                <IssuePagination
                  page={currentPage}
                  totalPages={totalPages}
                  onChange={(next) => filter('page', String(next))}
                  analytics={
                    example
                      ? undefined
                      : {
                          surface: 'portfolio_contents',
                          source_type: sourceType,
                        }
                  }
                />
              )}
              {!portfolio.holdings.length && (
                <section className="portfolio-contents-next" aria-labelledby="portfolio-next-title">
                  <div>
                    <h2 id="portfolio-next-title">내 포트폴리오부터 시작해 보세요</h2>
                    <p>보유 종목을 등록하면 관련 소식과 포트폴리오 분석을 이용할 수 있어요.</p>
                  </div>
                  <Link to="/">포트폴리오로 이동</Link>
                </section>
              )}
            </>
          )}
        </section>
      </div>
    </main>
  );
}

function portfolioNewsUrl(url: string) {
  const target = new URL(url, window.location.origin);
  target.searchParams.set('from', 'portfolio');
  return target.href;
}
