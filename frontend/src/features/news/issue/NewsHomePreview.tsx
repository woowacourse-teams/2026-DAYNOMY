import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { InvestmentCalendarPage } from '../../investment-calendar/InvestmentCalendarPage';
import { useFeaturedPeople } from '../../people/useFeaturedPeople';
import { getAllContents } from './contents';
import { createFeed, feedDate, type ExternalContent } from './feed';
import bankImage from '../../../assets/news-home-card-2.jpg';
import dollarImage from '../../../assets/news-home-card-3.jpg';
import threadsImage from '../../../assets/platform-threads.svg';
import xImage from '../../../assets/platform-x.svg';
import { IssueFeed } from './IssueFeed';
import { getAllNews } from './todayNews';
import type { NewsListItem } from '../newslist/types';
import {
  feedContentReference,
  getContentPopularity,
  popularityKey,
  sortByPopularity,
  type PopularityScores,
} from './popularity';
import './newsHomePreview.css';

const examples = [
  {
    image: bankImage,
    source: 'DAYNOMY Issue · 경제',
    title:
      '금리 인하 기대가 낮아진 가운데 물가와 환율이 동시에 움직일 때, 이번 주 시장에서 함께 확인해야 할 세 가지 변수',
    action: '기사 읽기',
  },
  {
    image: xImage,
    source: '인물의 글 · X',
    title:
      'AI 투자 확대가 반도체와 전력 인프라 기업의 실적으로 이어지기까지, 매출 성장과 비용 부담을 함께 봐야 하는 이유',
    action: '글 펼치기',
  },
  {
    image: dollarImage,
    source: 'YouTube · 환율',
    title:
      '달러 강세가 이어지는 상황에서 수출 기업의 실적과 해외 주식 투자자의 원화 수익률은 어떻게 달라질까',
    action: '영상 보기',
  },
];

function Arrow() {
  return (
    <svg
      className="briefing-arrow"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      aria-hidden="true"
    >
      <path d="M4 12h15m-6-6 6 6-6 6" />
    </svg>
  );
}

export default function NewsHomePreview({ preview = false }: { preview?: boolean }) {
  const [params, setParams] = useSearchParams();
  const isPreview = import.meta.env.DEV && preview;
  const example = isPreview && params.get('data') === 'example';
  const headingRef = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    const previousTitle = document.title;
    document.title = '소식 | DAYNOMY';
    headingRef.current?.focus({ preventScroll: true });
    return () => {
      document.title = previousTitle;
    };
  }, []);
  const [news, setNews] = useState<NewsListItem[]>([]);
  const [contents, setContents] = useState<ExternalContent[]>([]);
  const [scores, setScores] = useState<PopularityScores>(new Map());
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [rankingFailed, setRankingFailed] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [trendingIndex, setTrendingIndex] = useState(0);
  const people = useFeaturedPeople(isPreview ? 'news_home_preview' : 'news_home');
  useEffect(() => {
    let ignore = false;
    const controller = new AbortController();
    setLoading(true);
    setFailed(false);
    setRankingFailed(false);
    Promise.allSettled([
      getAllNews(),
      getAllContents(controller.signal),
      getContentPopularity(controller.signal),
    ]).then(([articles, external, ranking]) => {
      if (ignore) return;
      setNews(articles.status === 'fulfilled' ? articles.value.content : []);
      setContents(external.status === 'fulfilled' ? external.value.items : []);
      setScores(ranking.status === 'fulfilled' ? ranking.value : new Map());
      setFailed(articles.status === 'rejected' || external.status === 'rejected');
      setRankingFailed(ranking.status === 'rejected');
      setLoading(false);
    });
    return () => {
      ignore = true;
      controller.abort();
    };
  }, [attempt]);
  const feed = createFeed(news, people.people, contents);
  const ranked = sortByPopularity(feed, feedContentReference, scores).filter((item) =>
    scores.has(popularityKey(feedContentReference(item))),
  );
  const items = ranked.slice(0, 3);
  const latest = feed.slice(0, 5);
  const pending = loading || people.loading;
  const [trendingPaused, setTrendingPaused] = useState(false);
  const [trendingHovered, setTrendingHovered] = useState(false);
  const [trendingFocused, setTrendingFocused] = useState(false);
  useEffect(() => {
    if (
      example ||
      pending ||
      items.length < 2 ||
      trendingPaused ||
      trendingHovered ||
      trendingFocused
    )
      return;
    const timer = window.setInterval(() => {
      if (!document.hidden) setTrendingIndex((value) => (value + 1) % items.length);
    }, 3000);
    return () => window.clearInterval(timer);
  }, [example, pending, items.length, trendingPaused, trendingHovered, trendingFocused]);
  function retry() {
    setAttempt((value) => value + 1);
    people.retry();
  }
  return (
    <main className="issue-page briefing-preview">
      {isPreview && (
        <div className="briefing-preview-notice">
          <span>
            {example
              ? '가상 시나리오 · 기업 소식과 인기 순위는 실제 사실이 아닙니다.'
              : '디자인 미리보기 · 기존 서비스 데이터'}
          </span>
          <button
            onClick={() =>
              setParams((previous) => {
                const next = new URLSearchParams(previous);
                if (example) next.delete('data');
                else next.set('data', 'example');
                return next;
              })
            }
          >
            {example ? '실제 데이터 보기' : '예시 콘텐츠로 보기'}
          </button>
        </div>
      )}
      <header className="briefing-preview-heading">
        <div>
          <h1 ref={headingRef} tabIndex={-1}>
            소식
          </h1>
          <p>시장의 관심과 내 종목 소식.</p>
        </div>
      </header>
      <section className="briefing-portfolio-entry" aria-label="내 종목 소식">
        <Link
          to={
            example
              ? '/news/contents?scope=portfolio&data=example'
              : '/news/contents?scope=portfolio'
          }
        >
          내 종목 소식 바로 보기
        </Link>
      </section>
      <div className="briefing-discovery">
        <section
          className="briefing-trending briefing-trending-cards"
          aria-labelledby="briefing-trending-title"
          onMouseEnter={() => setTrendingHovered(true)}
          onMouseLeave={() => setTrendingHovered(false)}
          onFocusCapture={() => setTrendingFocused(true)}
          onBlurCapture={(event) => {
            if (!event.currentTarget.contains(event.relatedTarget)) setTrendingFocused(false);
          }}
        >
          <div className="briefing-section-heading">
            <div>
              <h2 id="briefing-trending-title">트렌딩</h2>
              <p>최근 7일간 데이노미 반응 기준</p>
            </div>
            {!example && !pending && items.length > 1 && (
              <div className="briefing-card-controls" aria-label="트렌딩 카드 탐색">
                <span>
                  {(trendingIndex % items.length) + 1} / {items.length}
                </span>
                <button
                  type="button"
                  aria-label={trendingPaused ? '트렌딩 자동 재생' : '트렌딩 자동 재생 일시정지'}
                  onClick={() => setTrendingPaused((value) => !value)}
                >
                  {trendingPaused ? '▶' : 'Ⅱ'}
                </button>
                <button
                  type="button"
                  aria-label="이전 트렌딩 소식"
                  onClick={() =>
                    setTrendingIndex(
                      (value) => ((value % items.length) + items.length - 1) % items.length,
                    )
                  }
                >
                  <Arrow />
                </button>
                <button
                  type="button"
                  aria-label="다음 트렌딩 소식"
                  onClick={() => setTrendingIndex((value) => (value + 1) % items.length)}
                >
                  <Arrow />
                </button>
              </div>
            )}
          </div>
          {example ? (
            <div className="briefing-trending-examples">
              {examples.map((item, index) => (
                <article key={item.title}>
                  <span className="briefing-rank" aria-label={`${index + 1}위`}>
                    {index + 1}
                  </span>
                  <details className="briefing-example-story">
                    <summary>
                      <img
                        className={`briefing-story-image${index === 1 ? ' briefing-story-image-platform' : ''}`}
                        src={item.image}
                        alt=""
                      />
                      <span className="briefing-example-meta">{item.source}</span>
                      <h3>{item.title}</h3>
                      <span className="briefing-example-action">예시 읽기</span>
                    </summary>
                    <p className="briefing-example-body">
                      화면 확인용 가상 콘텐츠입니다.{' '}
                      {index === 0
                        ? '금리와 물가, 환율을 함께 살펴보는 글이 이 자리에 표시됩니다.'
                        : index === 1
                          ? 'AI 산업의 성장에 대한 관점을 담은 인물의 글이 이 자리에 표시됩니다.'
                          : '환율을 이해하기 위한 영상 콘텐츠가 이 자리에 표시됩니다.'}{' '}
                      실제 콘텐츠에서는 기존 기사·글·영상 보기로 연결됩니다.
                    </p>
                  </details>
                </article>
              ))}
            </div>
          ) : pending ? (
            <p className="issue-state" role="status">
              소식을 불러오고 있습니다.
            </p>
          ) : (
            <>
              {(failed || people.error || rankingFailed) && (
                <div className="briefing-data-warning" role="alert">
                  <span>
                    {rankingFailed
                      ? '인기 순위를 불러오지 못했습니다.'
                      : '일부 소식을 불러오지 못했습니다.'}
                  </span>
                  <button onClick={retry}>다시 시도</button>
                </div>
              )}
              {items.length ? (
                <div className="briefing-card-stage">
                  <IssueFeed
                    key={items[trendingIndex % items.length].key}
                    items={[items[trendingIndex % items.length]]}
                    offset={trendingIndex % items.length}
                    surface="news_home"
                  />
                </div>
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
        <section className="briefing-people" aria-labelledby="briefing-people-title">
          <div className="briefing-section-heading">
            <div>
              <h2 id="briefing-people-title">인물의 글</h2>
            </div>
            <Link to="/people">인물 모두 보기</Link>
          </div>
          {example ? (
            <div className="briefing-people-original">
              {[
                {
                  name: '김민준',
                  source: 'X',
                  title: 'AI의 성장, 투자 규모보다 수익으로 이어지는 속도를 봅니다.',
                },
                {
                  name: '이서연',
                  source: 'Threads',
                  title: '좋은 기업을 고르는 것만큼, 얼마나 보유할지도 중요합니다.',
                },
              ].map((person) => (
                <article className="briefing-person-preview" key={person.name}>
                  <span className="issue-person-avatar" aria-hidden="true">
                    {person.name[0]}
                  </span>
                  <div className="issue-person-content">
                    <div className="issue-meta">
                      <strong>{person.name}</strong>
                      <span>{person.source} · 가상 인물</span>
                    </div>
                    <details className="briefing-person-example">
                      <summary className="issue-person-title">
                        <h3>{person.title}</h3>
                      </summary>
                      <p className="briefing-example-body">화면 확인용 가상 인물의 글입니다.</p>
                    </details>
                    <div className="issue-person-footer">예시 글</div>
                  </div>
                </article>
              ))}
            </div>
          ) : people.loading ? (
            <p role="status">인물의 글을 불러오고 있습니다.</p>
          ) : people.error ? (
            <p role="alert">
              {people.error} <button onClick={people.retry}>다시 시도</button>
            </p>
          ) : people.people.some((person) => person.posts.length) ? (
            <div className="briefing-people-original">
              {people.people
                .filter((person) => person.posts.length)
                .slice(0, 4)
                .map((person) => {
                  const post = [...person.posts].sort(
                    (a, b) =>
                      (Date.parse(b.publishedAt ?? b.createdAt ?? '') || 0) -
                      (Date.parse(a.publishedAt ?? a.createdAt ?? '') || 0),
                  )[0];
                  return (
                    <Link
                      className="briefing-person-preview"
                      key={person.id}
                      to={`/people?person=${person.id}&view=all`}
                    >
                      <span className="issue-person-avatar" aria-hidden="true">
                        {person.name[0]}
                        {person.profileImageUrl && (
                          <img
                            src={person.profileImageUrl}
                            alt=""
                            onError={(event) => {
                              event.currentTarget.hidden = true;
                            }}
                          />
                        )}
                      </span>
                      <div className="issue-person-content">
                        <div className="issue-meta">
                          <strong>{person.name}</strong>
                          <span>{post.sourceType}</span>
                        </div>
                        <div className="issue-person-title">
                          <h3>{post.title}</h3>
                        </div>
                        <div className="issue-person-footer">
                          {feedDate(post.createdAt, '등록')}
                        </div>
                      </div>
                    </Link>
                  );
                })}
            </div>
          ) : (
            <p className="issue-state">아직 등록된 인물의 글이 없어요.</p>
          )}
        </section>
      </div>
      <div className="briefing-layout">
        <section
          className="briefing-latest"
          id="briefing-latest"
          aria-labelledby="briefing-latest-title"
        >
          <div className="briefing-section-heading">
            <div>
              <h2 id="briefing-latest-title">전체 소식</h2>
            </div>
          </div>
          {example ? (
            <ul className="briefing-example-latest">
              <li>
                <details className="briefing-latest-example">
                  <summary>
                    <img className="briefing-latest-image" src={dollarImage} alt="" />
                    <span className="briefing-example-meta">DAYNOMY Issue · 외화·환율</span>
                    <h3>
                      환율 상승이 수출 기업의 매출에 반영되더라도 원자재 수입 비용과 해외 생산
                      비중을 함께 확인해야 하는 이유
                    </h3>
                    <span className="briefing-example-action">기사 읽기</span>
                  </summary>
                  <p className="briefing-example-body">
                    화면 확인용 예시입니다. 실제 기사·글·영상은 ‘소식 더 보기’에서 확인할 수
                    있습니다.
                  </p>
                </details>
              </li>
              <li>
                <details className="briefing-latest-example">
                  <summary>
                    <img
                      className="briefing-latest-image briefing-story-image-platform"
                      src={threadsImage}
                      alt=""
                    />
                    <span className="briefing-example-meta">인물의 글 · Threads</span>
                    <h3>
                      장기 투자에서 좋은 기업을 고르는 것만큼 중요한 것, 비슷한 산업에 속한 종목들의
                      보유 비중을 함께 살펴보기
                    </h3>
                    <span className="briefing-example-action">글 펼치기</span>
                  </summary>
                  <p className="briefing-example-body">
                    화면 확인용 예시입니다. 실제 기사·글·영상은 ‘소식 더 보기’에서 확인할 수
                    있습니다.
                  </p>
                </details>
              </li>
              <li>
                <details className="briefing-latest-example">
                  <summary>
                    <img className="briefing-latest-image" src={bankImage} alt="" />
                    <span className="briefing-example-meta">YouTube · ETF</span>
                    <h3>
                      같은 지수를 추종하는 ETF라도 총보수와 환헤지 여부, 분배금 지급 방식에 따라
                      장기 투자 결과가 달라질 수 있을까?
                    </h3>
                    <span className="briefing-example-action">영상 보기</span>
                  </summary>
                  <p className="briefing-example-body">
                    화면 확인용 예시입니다. 실제 기사·글·영상은 ‘소식 더 보기’에서 확인할 수
                    있습니다.
                  </p>
                </details>
              </li>
            </ul>
          ) : pending ? (
            <p className="issue-state" role="status">
              최신 소식을 불러오고 있습니다.
            </p>
          ) : latest.length ? (
            <IssueFeed items={latest} surface="news_home" />
          ) : (
            <p className="issue-state">
              {failed || people.error
                ? '소식을 불러오지 못했어요. 상단에서 다시 시도해 주세요.'
                : feed.length
                  ? '최근 콘텐츠는 위 인기 소식에서 확인할 수 있어요.'
                  : '아직 등록된 소식이 없어요'}
            </p>
          )}
          <Link className="briefing-browse-more" to="/news/contents">
            소식 더 보기
          </Link>
        </section>
        <div id="briefing-calendar" className="briefing-calendar-content">
          <InvestmentCalendarPage embedded />
        </div>
      </div>
    </main>
  );
}
