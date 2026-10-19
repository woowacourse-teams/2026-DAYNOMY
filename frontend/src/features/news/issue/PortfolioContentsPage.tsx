import { useEffect, useState } from 'react';
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
import { feedDate, youtubeId } from './feed';
import xMark from '../../../assets/platform-x.svg';
import threadsMark from '../../../assets/platform-threads.svg';
import './issue.css';

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
  const [params, setParams] = useSearchParams();
  const selectedAsset = params.get('asset') ?? 'ALL';
  const requestedSource = params.get('source') ?? 'ALL';
  const source = sources.some(([value]) => value === requestedSource) ? requestedSource : 'ALL';
  const page = Math.max(1, Number.parseInt(params.get('page') ?? '1', 10) || 1);
  const [responses, setResponses] = useState<StockRelatedContentsResponse[]>([]);
  const [loadedKey, setLoadedKey] = useState<string | null>(null);
  const [failedIds, setFailedIds] = useState<number[]>([]);
  const [attempt, setAttempt] = useState(0);
  const [expanded, setExpanded] = useState<string | null>(null);
  const [companySearch, setCompanySearch] = useState('');
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
    const controller = new AbortController();
    const ids: number[] = JSON.parse(assetKey);
    setLoadedKey(null);
    setResponses([]);
    setFailedIds([]);
    Promise.allSettled(ids.map((id) => getStockRelatedContents(id, controller.signal))).then(
      (results) => {
        if (controller.signal.aborted) return;
        setResponses(
          results.flatMap((result) => (result.status === 'fulfilled' ? [result.value] : [])),
        );
        setFailedIds(ids.filter((_, index) => results[index].status === 'rejected'));
        setLoadedKey(assetKey);
      },
    );
    return () => controller.abort();
  }, [assetKey, portfolio.loading, portfolio.error, attempt]);

  function filter(key: 'asset' | 'source' | 'page', value: string) {
    const next = new URLSearchParams(params);
    next.set(key, value);
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
  const items = [...unique.values()].sort(
    (a, b) =>
      (Date.parse(b.publishedDate ?? b.createdAt ?? '') || 0) -
        (Date.parse(a.publishedDate ?? a.createdAt ?? '') || 0) || b.id - a.id,
  );
  const totalPages = Math.max(1, Math.ceil(items.length / 12));
  const currentPage = Math.min(page, totalPages);
  const selectionFailed =
    selectedAsset === 'ALL'
      ? failedIds.length > 0
      : failedIds.some((id) => String(id) === selectedAsset);

  const matchingHoldings = portfolio.holdings.filter((holding) =>
    holding.name.toLocaleLowerCase().includes(companySearch.trim().toLocaleLowerCase()),
  );

  return (
    <main className="issue-page portfolio-contents-page">
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
                      onClick={() => filter('source', value)}
                    >
                      {label}
                    </button>
                  ))}
                </div>
                <span>최근순</span>
              </div>
            )}
          {loading ? (
            <p className="issue-state" role="status">
              보유 종목 소식을 불러오는 중입니다.
            </p>
          ) : portfolio.error ? (
            <p className="issue-state" role="alert">
              {portfolio.error} <button onClick={portfolio.retry}>다시 시도</button>
            </p>
          ) : !validSelection ? (
            <p className="issue-state">
              현재 보유하지 않은 종목입니다.{' '}
              <button onClick={() => filter('asset', 'ALL')}>전체 보유 종목 보기</button>
            </p>
          ) : (
            <>
              {selectionFailed && (
                <p className="issue-state" role="alert">
                  일부 종목의 소식을 불러오지 못했습니다.{' '}
                  <button onClick={() => setAttempt((value) => value + 1)}>다시 시도</button>
                </p>
              )}
              {!portfolio.holdings.length ? (
                <p className="issue-state">
                  보유 종목을 등록하면 관련 소식을 볼 수 있습니다.{' '}
                  <Link to="/">포트폴리오로 이동</Link>
                </p>
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
                    item.imageUrl ??
                    (videoId ? `https://i.ytimg.com/vi/${videoId}/hqdefault.jpg` : null);
                  const label =
                    item.sourceType === 'INTERNAL_NEWS'
                      ? '데이노미'
                      : STOCK_CONTENT_SOURCE_LABELS[item.sourceType];
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
                              if (expanded !== key)
                                trackEvent('expand_content', {
                                  surface: 'portfolio_contents',
                                  content_id: item.id,
                                  source_type: item.sourceType,
                                  position: (currentPage - 1) * 12 + index + 1,
                                });
                              setExpanded(expanded === key ? null : key);
                            }}
                          >
                            <span className="issue-post-title">{item.title}</span>
                            <span className="issue-expand-label">
                              {expanded === key
                                ? '접기'
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
                            className="issue-row-title"
                            href={item.url}
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
                                        post={{
                                          sourceType: item.sourceType,
                                          title: item.title,
                                          url: item.url,
                                        }}
                                      />
                                    )
                                  )}
                                </div>
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
              {items.length > 0 && (
                <IssuePagination
                  page={currentPage}
                  totalPages={totalPages}
                  onChange={(next) => filter('page', String(next))}
                  analytics={{
                    surface: 'portfolio_contents',
                    asset_id: selectedAsset,
                    source_type: source,
                  }}
                />
              )}
            </>
          )}
        </section>
      </div>
    </main>
  );
}
