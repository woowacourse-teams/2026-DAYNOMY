import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import bankImage from '../../../assets/news-home-card-2.jpg';
import dollarImage from '../../../assets/news-home-card-3.jpg';
import cityImage from '../../../assets/news-home-card-4.jpg';
import goldImage from '../../../assets/news-home-card-5.jpg';
import { useAuth } from '../../../hooks/useLoginStatus';
import { InvestmentCalendarPage } from '../../investment-calendar/InvestmentCalendarPage';
import { useFeaturedPeople } from '../../people/useFeaturedPeople';
import { usePortfolioHoldings } from '../../portfolio/hooks/usePortfolioHoldings';
import type { NewsListItem } from '../newslist/types';
import { getAllContents } from './contents';
import { createFeed, feedDate, type ExternalContent, type FeedItem } from './feed';
import { IssueFeed } from './IssueFeed';
import {
  feedContentReference,
  getContentPopularity,
  sortByPopularity,
  type PopularityScores,
} from './popularity';
import { getAllNews } from './todayNews';
import './newsHomePreview.css';

const examples = [
  {
    image: cityImage,
    source: 'DAYNOMY Issue · 주식',
    title: '미국 증시, 금리 경로에 주목… 기술주 중심으로 투자 심리 회복',
    summary:
      '금리 전망과 기업 실적 기대가 함께 움직이면서 대형 기술주를 중심으로 매수세가 이어지고 있습니다.',
  },
  {
    image: bankImage,
    source: 'DAYNOMY Issue · 경제지표',
    title: '반도체 업황 회복 기대감, 국내 증시의 다음 변수가 될까',
    summary: '수요 회복 시점과 기업별 투자 계획을 함께 확인할 필요가 있습니다.',
  },
  {
    image: goldImage,
    source: 'DAYNOMY Issue · 금',
    title: '안전자산 선호가 이어지며 금 가격 변동성 확대',
    summary: '달러와 국채 금리의 움직임이 귀금속 시장에 미치는 영향을 살펴봅니다.',
  },
];

const exampleLatest = [
  {
    image: dollarImage,
    source: '외화·환율',
    title: '환율 상승이 수출 기업의 실적에 미치는 영향, 비용 구조도 함께 봐야 하는 이유',
  },
  {
    image: bankImage,
    source: '경제지표',
    title: '기준금리 전망이 바뀔 때 예금과 채권 시장에서 먼저 확인할 지표',
  },
  {
    image: cityImage,
    source: '주식',
    title: '대형 기술주 강세가 이어질 때 업종별 실적 차이를 읽는 방법',
  },
];

function ExampleTrending() {
  return (
    <div className="briefing-trending-grid briefing-trending-grid--example">
      <details className="briefing-feature-story">
        <summary>
          <img src={examples[0].image} alt="" />
          <span className="briefing-story-shade" />
          <span className="briefing-story-copy">
            <span className="briefing-story-kicker">트렌딩 01</span>
            <strong>{examples[0].title}</strong>
            <span>{examples[0].summary}</span>
            <small>데이노미 · 예시 기사</small>
          </span>
        </summary>
        <p>화면 확인용 가상 콘텐츠입니다. 실제 서비스에서는 등록된 기사로 이동합니다.</p>
      </details>
      <div className="briefing-secondary-stories">
        {examples.slice(1).map((item, index) => (
          <details className="briefing-secondary-story" key={item.title}>
            <summary>
              <span className="briefing-secondary-copy">
                <span className="briefing-story-kicker">트렌딩 0{index + 2}</span>
                <strong>{item.title}</strong>
                <span>{item.summary}</span>
                <small>데이노미 · 예시 기사</small>
              </span>
              <img src={item.image} alt="" />
            </summary>
            <p>화면 확인용 가상 콘텐츠입니다.</p>
          </details>
        ))}
      </div>
    </div>
  );
}

function LiveTrending({ items }: { items: FeedItem[] }) {
  if (!items.length) return null;
  return (
    <div className={`briefing-trending-grid briefing-trending-count-${items.length}`}>
      <div className="briefing-feature-story briefing-live-story">
        <IssueFeed items={[items[0]]} surface="news_home" />
      </div>
      {items.length > 1 ? (
        <div className="briefing-secondary-stories">
          {items.slice(1).map((item, index) => (
            <div className="briefing-secondary-story briefing-live-story" key={item.key}>
              <span className="briefing-story-kicker">트렌딩 0{index + 2}</span>
              <IssueFeed items={[item]} surface="news_home" offset={index + 1} />
            </div>
          ))}
        </div>
      ) : null}
    </div>
  );
}

export default function NewsHomePreview({ preview = false }: { preview?: boolean }) {
  const [params, setParams] = useSearchParams();
  const isPreview = import.meta.env.DEV && preview;
  const example = isPreview && params.get('data') === 'example';
  const headingRef = useRef<HTMLHeadingElement>(null);
  const { isLoggedIn } = useAuth();
  const portfolio = usePortfolioHoldings(isLoggedIn);
  const [news, setNews] = useState<NewsListItem[]>([]);
  const [contents, setContents] = useState<ExternalContent[]>([]);
  const [scores, setScores] = useState<PopularityScores>(new Map());
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [rankingFailed, setRankingFailed] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const people = useFeaturedPeople(isPreview ? 'news_home_preview' : 'news_home');

  useEffect(() => {
    const previousTitle = document.title;
    document.title = '소식 | DAYNOMY';
    headingRef.current?.focus({ preventScroll: true });
    return () => {
      document.title = previousTitle;
    };
  }, []);

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
  const trendingItems = rankingFailed
    ? []
    : sortByPopularity(
        feed.filter((item) => 'article' in item),
        feedContentReference,
        scores,
      ).slice(0, 3);
  const latest = feed.slice(0, 5);
  const pending = loading || people.loading;

  function retry() {
    setAttempt((value) => value + 1);
    people.retry();
  }

  return (
    <main className="issue-page briefing-preview">
      <header className="briefing-preview-heading">
        <h1 ref={headingRef} tabIndex={-1}>
          소식
        </h1>
        <p>시장의 관심과 내 종목 소식.</p>
      </header>

      {isPreview ? (
        <div className="briefing-preview-notice">
          <span>
            {example
              ? '가상 시나리오 · 기사와 인기 순위는 실제 사실이 아닙니다.'
              : '디자인 미리보기 · 기존 서비스 데이터'}
          </span>
          <button
            type="button"
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
      ) : null}

      <section className="briefing-trending" aria-labelledby="briefing-trending-title">
        <div className="briefing-section-heading">
          <div>
            <h2 id="briefing-trending-title">트렌딩</h2>
            <p>최근 7일간 데이노미 반응 기준</p>
          </div>
        </div>
        {example ? (
          <ExampleTrending />
        ) : pending ? (
          <p className="issue-state" role="status">
            소식을 불러오고 있습니다.
          </p>
        ) : (
          <>
            {failed || people.error || rankingFailed ? (
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
            to={
              example
                ? '/news/contents?scope=portfolio&data=example'
                : '/news/contents?scope=portfolio'
            }
          >
            전체 보기
          </Link>
        </div>
        {example ? (
          <ul className="briefing-holdings">
            {['삼성전자', '엔비디아', '테슬라', 'NAVER'].map((name, index) => (
              <li key={name}>
                <span>{name.slice(0, 1)}</span>
                <strong>{name}</strong>
                <small>{index % 3 === 2 ? '-1.2%' : `+${(index + 1) * 0.6}%`}</small>
              </li>
            ))}
          </ul>
        ) : portfolio.loading ? (
          <p className="briefing-holdings-state">보유 종목을 불러오고 있습니다.</p>
        ) : portfolio.error ? (
          <p className="briefing-holdings-state">보유 종목을 불러오지 못했습니다.</p>
        ) : portfolio.holdings.length ? (
          <ul className="briefing-holdings">
            {portfolio.holdings.slice(0, 4).map((holding) => (
              <li key={holding.assetId}>
                <Link to={`/news/contents?scope=portfolio&asset=${holding.assetId}`}>
                  <span>{holding.name.slice(0, 1)}</span>
                  <strong>{holding.name}</strong>
                  <small>{holding.market}</small>
                </Link>
              </li>
            ))}
          </ul>
        ) : (
          <p className="briefing-holdings-state">
            자산을 등록하면 관련 소식을 이곳에서 빠르게 확인할 수 있습니다.
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
            <Link aria-label="전체 소식 전체 보기" to="/news/contents">
              전체 보기
            </Link>
          </div>
          {example ? (
            <ul className="issue-feed briefing-example-latest">
              {exampleLatest.map((item) => (
                <li key={item.title}>
                  <article className="issue-feed-row issue-feed-row--visual issue-feed-row--news">
                    <span className="issue-feed-thumbnail" aria-hidden="true">
                      <img src={item.image} alt="" />
                    </span>
                    <div className="issue-meta">
                      <span className="issue-source">데이노미</span>
                      <span>{item.source}</span>
                      <span>예시 기사</span>
                    </div>
                    <div className="issue-row-title">
                      <h3>{item.title}</h3>
                      <span className="issue-expand-label">
                        기사 읽기
                        <svg
                          className="issue-expand-chevron"
                          viewBox="0 0 16 16"
                          aria-hidden="true"
                        >
                          <path
                            d="M3 8h10m-4-4 4 4-4 4"
                            fill="none"
                            stroke="currentColor"
                            strokeWidth="1.5"
                          />
                        </svg>
                      </span>
                    </div>
                  </article>
                </li>
              ))}
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
                : '아직 등록된 소식이 없어요'}
            </p>
          )}
        </section>

        <aside className="briefing-sidebar" aria-label="소식 부가 정보">
          <section className="briefing-people" aria-labelledby="briefing-people-title">
            <div className="briefing-section-heading">
              <h2 id="briefing-people-title">인물의 글</h2>
              <Link aria-label="인물의 글 전체 보기" to="/people">
                전체 보기
              </Link>
            </div>
            {example ? (
              <div className="briefing-people-list">
                {[
                  ['김민준', '테크 애널리스트', 'AI 이후의 반도체 사이클, 무엇이 달라졌나'],
                  ['이서연', '글로벌 전략가', '미국 금리와 달러, 지금 다시 보는 이유'],
                  ['박정호', '가치투자 연구원', '좋은 기업을 더 오래 보유하는 법'],
                ].map(([name, role, title]) => (
                  <article className="briefing-person-preview" key={name}>
                    <span className="issue-person-avatar" aria-hidden="true">
                      {name[0]}
                    </span>
                    <div>
                      <p>
                        <strong>{name}</strong> <span>{role}</span>
                      </p>
                      <h3>{title}</h3>
                      <small>예시 글</small>
                    </div>
                  </article>
                ))}
              </div>
            ) : people.loading ? (
              <p role="status">인물의 글을 불러오고 있습니다.</p>
            ) : people.error ? (
              <p role="alert">
                {people.error}{' '}
                <button type="button" onClick={people.retry}>
                  다시 시도
                </button>
              </p>
            ) : people.people.some((person) => person.posts.length) ? (
              <div className="briefing-people-list">
                {people.people
                  .filter((person) => person.posts.length)
                  .slice(0, 3)
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
            <InvestmentCalendarPage embedded compact upcomingOnly={!example} />
          </div>
        </aside>
      </div>
    </main>
  );
}
