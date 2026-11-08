import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { trackEvent } from '../../../analytics';
import { useAuth } from '../../../hooks/useLoginStatus';
import { InvestmentCalendarPage } from '../../investment-calendar/InvestmentCalendarPage';
import { useFeaturedPeople } from '../../people/useFeaturedPeople';
import { usePortfolioHoldings } from '../../portfolio/hooks/usePortfolioHoldings';
import { feedDate, type FeedItem } from './feed';
import { getNewsFeed } from './feedApi';
import { IssueFeed } from './IssueFeed';
import { feedProperties, trackLoadResult, useContentImpression, useContentView } from './analytics';
import './newsHomePreview.css';

const latestSources = [
  ['ALL', '전체'],
  ['DAYNOMY', '데이노미'],
  ['X', 'X'],
  ['THREADS', 'Threads'],
  ['YOUTUBE', 'YouTube'],
] as const;

function TrendingItem({
  item,
  position,
  className,
}: {
  item: FeedItem;
  position: number;
  className: string;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const analytics = feedProperties(item, 'news_home_trending', position);
  useContentImpression(ref, item.key, 'view_content', analytics);
  return (
    <div ref={ref} className={`${className} briefing-live-story`}>
      {position > 1 ? <span className="briefing-story-kicker">트렌딩 0{position}</span> : null}
      <IssueFeed items={[item]} surface="news_home_trending" offset={position - 1} />
    </div>
  );
}

function LiveTrending({ items }: { items: FeedItem[] }) {
  if (!items.length) return null;
  return (
    <div className={`briefing-trending-grid briefing-trending-count-${items.length}`}>
      <TrendingItem className="briefing-feature-story" item={items[0]} position={1} />
      {items.length > 1 ? (
        <div className="briefing-secondary-stories">
          {items.slice(1).map((item, index) => (
            <TrendingItem
              className="briefing-secondary-story"
              item={item}
              key={item.key}
              position={index + 2}
            />
          ))}
        </div>
      ) : null}
    </div>
  );
}

export default function NewsHomePreview({ preview = false }: { preview?: boolean }) {
  const isPreview = import.meta.env.DEV && preview;
  const headingRef = useRef<HTMLHeadingElement>(null);
  const { isLoggedIn } = useAuth();
  const portfolio = usePortfolioHoldings(isLoggedIn);
  const [trendingItems, setTrendingItems] = useState<FeedItem[]>([]);
  const [latest, setLatest] = useState<FeedItem[]>([]);
  const [latestTotalElements, setLatestTotalElements] = useState(0);
  const [trendingLoading, setTrendingLoading] = useState(true);
  const [latestLoading, setLatestLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [rankingFailed, setRankingFailed] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [latestSource, setLatestSource] = useState<'ALL' | FeedItem['source']>('ALL');
  const people = useFeaturedPeople(isPreview ? 'news_home_preview' : 'news_home', 1, 100);
  const trackNewsHomeEvent = (name: string, params: Record<string, string | number | boolean>) => {
    if (!isPreview) trackEvent(name, params);
  };

  useEffect(() => {
    const previousTitle = document.title;
    document.title = '소식 | DAYNOMY';
    headingRef.current?.focus({ preventScroll: true });
    return () => {
      document.title = previousTitle;
    };
  }, []);

  useEffect(() => {
    const startedAt = performance.now();
    let ignore = false;
    const controller = new AbortController();
    setTrendingLoading(true);
    setRankingFailed(false);
    getNewsFeed('DAYNOMY', 1, 4, 'POPULAR', controller.signal)
      .then((result) => {
        if (ignore) return;
        setTrendingItems(result.items);
        if (!isPreview)
          trackLoadResult(
            'news_home',
            'popularity',
            startedAt,
            'success',
            result.items.length,
            attempt,
          );
      })
      .catch(() => {
        if (ignore || controller.signal.aborted) return;
        setTrendingItems([]);
        setRankingFailed(true);
        if (!isPreview) trackLoadResult('news_home', 'popularity', startedAt, 'error', 0, attempt);
      })
      .finally(() => {
        if (!ignore) setTrendingLoading(false);
      });
    return () => {
      ignore = true;
      controller.abort();
    };
  }, [attempt, isPreview]);

  useEffect(() => {
    const startedAt = performance.now();
    let ignore = false;
    const controller = new AbortController();
    setLatestLoading(true);
    setFailed(false);
    getNewsFeed(latestSource, 1, 8, 'LATEST', controller.signal)
      .then((result) => {
        if (ignore) return;
        setLatest(result.items);
        setLatestTotalElements(result.totalElements);
        if (!isPreview)
          trackLoadResult('news_home', 'feed', startedAt, 'success', result.items.length, attempt);
      })
      .catch(() => {
        if (ignore || controller.signal.aborted) return;
        setLatest([]);
        setLatestTotalElements(0);
        setFailed(true);
        if (!isPreview) trackLoadResult('news_home', 'feed', startedAt, 'error', 0, attempt);
      })
      .finally(() => {
        if (!ignore) setLatestLoading(false);
      });
    return () => {
      ignore = true;
      controller.abort();
    };
  }, [attempt, isPreview, latestSource]);

  const listStatus = failed ? 'partial_error' : 'ready';
  useContentView(!isPreview && !latestLoading, 'view_content_list', {
    surface: 'news_home',
    source_type: latestSource,
    result_count: latestTotalElements,
    status: listStatus,
  });
  useContentView(!isPreview && !latestLoading && !failed && !latest.length, 'view_content_empty', {
    surface: 'news_home',
    source_type: latestSource,
    reason: latestSource === 'ALL' ? 'no_content' : 'filter_empty',
  });

  function retry() {
    trackNewsHomeEvent('retry_content_load', {
      surface: 'news_home',
      area: 'all',
      attempt: attempt + 1,
    });
    setAttempt((value) => value + 1);
    people.retry();
  }

  function retryPeople() {
    trackNewsHomeEvent('retry_content_load', { surface: 'news_home', area: 'people' });
    people.retry();
  }

  return (
    <main className="issue-page briefing-preview">
      <h1 className="briefing-page-title" ref={headingRef} tabIndex={-1}>
        소식
      </h1>

      {isPreview ? (
        <div className="briefing-preview-notice">
          <span>디자인 미리보기 · 기존 서비스 데이터</span>
        </div>
      ) : null}

      <section className="briefing-trending" aria-labelledby="briefing-trending-title">
        <div className="briefing-section-heading">
          <div>
            <h2 id="briefing-trending-title">트렌딩</h2>
            <p>최근 7일간 반응 기준</p>
          </div>
        </div>
        {trendingLoading ? (
          <p className="issue-state" role="status">
            소식을 불러오고 있습니다.
          </p>
        ) : (
          <>
            {failed || rankingFailed ? (
              <div className="briefing-data-warning" role="alert">
                <span>
                  {rankingFailed
                    ? '인기 순위를 불러오지 못했습니다.'
                    : '일부 소식을 불러오지 못했습니다.'}
                </span>
                <button type="button" onClick={retry}>
                  다시 시도
                </button>
              </div>
            ) : null}
            {trendingItems.length ? (
              <LiveTrending items={trendingItems} />
            ) : (
              <div className="briefing-empty">
                <h3>
                  {rankingFailed
                    ? '인기 소식을 잠시 불러올 수 없어요'
                    : '아직 집계된 인기 소식이 없어요'}
                </h3>
                <a href="#briefing-latest">최신 소식 보기</a>
              </div>
            )}
          </>
        )}
      </section>

      <section className="briefing-portfolio-entry" aria-labelledby="briefing-portfolio-title">
        <div className="briefing-section-heading">
          <h2 id="briefing-portfolio-title">내 종목 소식</h2>
          <Link
            aria-label="내 종목 소식 전체 보기"
            onClick={() =>
              trackNewsHomeEvent('click_content_more', {
                surface: 'news_home_portfolio',
                destination: 'portfolio_news',
              })
            }
            to="/news/contents?scope=portfolio"
          >
            모두 보기
          </Link>
        </div>
        {portfolio.loading ? (
          <p className="briefing-holdings-state">보유 종목을 불러오고 있습니다.</p>
        ) : portfolio.error ? (
          <p className="briefing-holdings-state">보유 종목을 불러오지 못했습니다.</p>
        ) : portfolio.holdings.length ? (
          <ul className="briefing-holdings">
            {portfolio.holdings.slice(0, 4).map((holding) => (
              <li key={holding.assetId}>
                <Link
                  onClick={() =>
                    trackNewsHomeEvent('click_content_more', {
                      surface: 'news_home_portfolio',
                      destination: 'portfolio_asset_news',
                    })
                  }
                  to={`/news/contents?scope=portfolio&asset=${holding.assetId}`}
                >
                  <span>{holding.name.slice(0, 1)}</span>
                  <strong>{holding.name}</strong>
                  <small>{holding.market}</small>
                </Link>
              </li>
            ))}
          </ul>
        ) : (
          <p className="briefing-holdings-state">
            자산을 등록하면 관련 소식을 이곳에서 빠르게 확인할 수 있습니다
          </p>
        )}
      </section>

      <div className="briefing-layout">
        <section
          className="briefing-latest"
          id="briefing-latest"
          aria-labelledby="briefing-latest-title"
        >
          <div className="briefing-section-heading">
            <h2 id="briefing-latest-title">전체 소식</h2>
            <Link
              aria-label="전체 소식 전체 보기"
              onClick={() =>
                trackNewsHomeEvent('click_content_more', {
                  surface: 'news_home',
                  destination: 'news_contents',
                })
              }
              to="/news/contents"
            >
              모두 보기
            </Link>
          </div>
          <div
            className="issue-source-filters briefing-source-filters"
            role="group"
            aria-label="전체 소식 카테고리"
          >
            {latestSources.map(([value, label]) => (
              <button
                key={value}
                type="button"
                aria-pressed={latestSource === value}
                onClick={() => {
                  if (latestSource === value) return;
                  trackNewsHomeEvent('filter_content_source', {
                    surface: 'news_home',
                    previous_source: latestSource,
                    source_type: value,
                  });
                  setLatestSource(value);
                }}
              >
                {label}
              </button>
            ))}
          </div>
          {latestLoading ? (
            <p className="issue-state" role="status">
              최신 소식을 불러오고 있습니다.
            </p>
          ) : latest.length ? (
            <IssueFeed items={latest} surface="news_home" />
          ) : (
            <p className="issue-state">
              {failed
                ? '소식을 불러오지 못했어요. 상단에서 다시 시도해 주세요.'
                : latestSource === 'ALL'
                  ? '아직 등록된 소식이 없어요'
                  : '선택한 카테고리의 소식이 없어요'}
            </p>
          )}
        </section>

        <aside className="briefing-sidebar" aria-label="소식 부가 정보">
          <section className="briefing-people" aria-labelledby="briefing-people-title">
            <div className="briefing-section-heading">
              <h2 id="briefing-people-title">인물의 글</h2>
              <Link
                aria-label="인물의 글 전체 보기"
                onClick={() =>
                  trackNewsHomeEvent('click_content_more', {
                    surface: 'news_home_people',
                    destination: 'people',
                  })
                }
                to="/people"
              >
                모두 보기
              </Link>
            </div>
            {people.loading ? (
              <p role="status">인물의 글을 불러오고 있습니다.</p>
            ) : people.error ? (
              <p role="alert">
                {people.error}{' '}
                <button type="button" onClick={retryPeople}>
                  다시 시도
                </button>
              </p>
            ) : people.people.some((person) => person.posts.length) ? (
              <div className="briefing-people-list">
                {people.people
                  .filter((person) => person.posts.length)
                  .slice(0, 3)
                  .map((person, index) => {
                    const post = [...person.posts].sort(
                      (a, b) =>
                        (Date.parse(b.publishedAt ?? b.createdAt ?? '') || 0) -
                        (Date.parse(a.publishedAt ?? a.createdAt ?? '') || 0),
                    )[0];
                    return (
                      <Link
                        className="briefing-person-preview"
                        key={person.id}
                        onClick={() =>
                          trackNewsHomeEvent('click_person_preview', {
                            surface: 'news_home_people',
                            person_id: person.id,
                            position: index + 1,
                          })
                        }
                        to={`/people?person=${person.id}&view=all`}
                      >
                        <span className="issue-person-avatar" aria-hidden="true">
                          {person.name[0]}
                          {person.profileImageUrl ? (
                            <img
                              src={person.profileImageUrl}
                              alt=""
                              onError={(event) => {
                                event.currentTarget.hidden = true;
                              }}
                            />
                          ) : null}
                        </span>
                        <div>
                          <p>
                            <strong>{person.name}</strong> <span>{post.sourceType}</span>
                          </p>
                          <h3>{post.title}</h3>
                          <small>{feedDate(post.createdAt, '등록')}</small>
                        </div>
                      </Link>
                    );
                  })}
              </div>
            ) : (
              <p className="issue-state">아직 등록된 인물의 글이 없어요.</p>
            )}
          </section>
          <div id="briefing-calendar" className="briefing-calendar-content">
            <InvestmentCalendarPage embedded compact upcomingOnly />
          </div>
        </aside>
      </div>
    </main>
  );
}
