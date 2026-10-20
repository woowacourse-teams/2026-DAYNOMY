import { trackEvent } from '../../../analytics';
import xMark from '../../../assets/platform-x.svg';
import threadsMark from '../../../assets/platform-threads.svg';
import { trackLoadResult, useContentView } from './analytics';
import { IssuePagination } from './IssuePagination';
import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { PortfolioContentsPage } from './PortfolioContentsPage';
import { useFeaturedPeople } from '../../people/useFeaturedPeople';
import { InvestmentCalendarPage } from '../../investment-calendar/InvestmentCalendarPage';
import type { NewsListItem } from '../newslist/types';
import { createFeed, feedDate, isToday, sourceLabel, type FeedItem } from './feed';
import { getAllContents } from './contents';
import type { ExternalContent } from './feed';
import { getAllNews } from './todayNews';
import { IssueFeed } from './IssueFeed';
import '../../people/people.css';
import './issue.css';

export function IssueHomePage({ fullFeed = false }: { fullFeed?: boolean }) {
  const [params] = useSearchParams();
  return fullFeed && params.get('scope') === 'portfolio' ? (
    <PortfolioContentsPage />
  ) : (
    <PublicIssueHomePage fullFeed={fullFeed} />
  );
}

function PublicIssueHomePage({ fullFeed }: { fullFeed: boolean }) {
  const headingRef = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    const previousTitle = document.title;
    document.title = `${fullFeed ? '전체 콘텐츠' : '소식'} | DAYNOMY`;
    headingRef.current?.focus({ preventScroll: true });
    return () => {
      document.title = previousTitle;
    };
  }, [fullFeed]);
  const surface = fullFeed ? 'news_contents' : 'news_home';
  const people = useFeaturedPeople(surface);
  const [contents, setContents] = useState<ExternalContent[]>([]);
  const [contentsLoading, setContentsLoading] = useState(true);
  const [contentsError, setContentsError] = useState('');
  const [news, setNews] = useState<NewsListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  const [page, setPage] = useState(1);
  const [source, setSource] = useState<'ALL' | FeedItem['source']>('ALL');

  useEffect(() => {
    const startedAt = performance.now();
    let ignore = false;
    setLoading(true);
    setError('');
    getAllNews()
      .then((result) => {
        if (ignore) return;
        setNews(result.content);
        trackLoadResult(surface, 'news', startedAt, 'success', result.content.length, attempt);
      })
      .catch(() => {
        if (!ignore) {
          setError('데이노미 이슈를 불러오지 못했습니다.');
          trackLoadResult(surface, 'news', startedAt, 'error', 0, attempt);
        }
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });
    return () => {
      ignore = true;
    };
  }, [attempt, fullFeed, surface]);

  useEffect(() => {
    const startedAt = performance.now();
    const controller = new AbortController();
    setContentsLoading(true);
    setContentsError('');
    getAllContents(controller.signal)
      .then((result) => {
        if (controller.signal.aborted) return;
        setContents(result.items);
        trackLoadResult(surface, 'external', startedAt, 'success', result.items.length, attempt);
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          setContentsError('YouTube·Threads·X 자료를 불러오지 못했습니다.');
          trackLoadResult(surface, 'external', startedAt, 'error', 0, attempt);
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setContentsLoading(false);
      });
    return () => controller.abort();
  }, [attempt, fullFeed, surface]);

  // shortcut: full archive is merged client-side; use a unified paginated API when the archive grows.
  const items = createFeed(news, people.people, contents).filter(
    (item) => source === 'ALL' || item.source === source,
  );
  const totalPages = Math.max(1, Math.ceil(items.length / 12));
  const currentPage = Math.min(page, totalPages);
  const featured = people.people
    .flatMap((person) => person.posts.map((post) => ({ person, post })))
    .sort(
      (a, b) =>
        (Date.parse(b.post.publishedAt ?? b.post.createdAt ?? '') || 0) -
        (Date.parse(a.post.publishedAt ?? a.post.createdAt ?? '') || 0),
    )
    .slice(0, 3);
  const settled = !loading && !contentsLoading && !people.loading;
  const failedAreas = [error, contentsError, people.error].filter(Boolean).length;
  const listStatus = failedAreas === 3 ? 'error' : failedAreas ? 'partial_error' : 'ready';
  useContentView(settled, 'view_content_list', {
    surface,
    source_type: source,
    result_count: items.length,
    page_number: currentPage,
    status: listStatus,
  });
  useContentView(settled && listStatus === 'ready' && !items.length, 'view_content_empty', {
    surface,
    source_type: source,
    reason: source !== 'ALL' ? 'filter_empty' : 'no_content',
  });
  useContentView(
    !people.loading && !people.error && !featured.length && !fullFeed,
    'view_content_empty',
    { surface: 'person_preview', reason: 'no_content' },
  );
  const retryPeople = () => {
    trackEvent('retry_content_load', { surface, area: 'people' });
    people.retry();
  };
  const retry = () => {
    trackEvent('retry_content_load', { surface, area: 'all', attempt: attempt + 1 });
    setAttempt((value) => value + 1);
    people.retry();
  };

  return (
    <main className="issue-page">
      {fullFeed && (
        <Link className="issue-back" to="/news">
          ← 소식 홈
        </Link>
      )}
      {fullFeed ? (
        <header className="issue-heading">
          <h1 ref={headingRef} tabIndex={-1}>
            전체 콘텐츠
          </h1>
          <p>데이노미, Threads, YouTube, X의 소식을 한곳에서.</p>
        </header>
      ) : (
        <h1 className="sr-only" ref={headingRef} tabIndex={-1}>
          소식
        </h1>
      )}
      {!fullFeed && (
        <div className="issue-briefing">
          <InvestmentCalendarPage embedded />
          <section className="issue-people" aria-labelledby="issue-people-heading">
            <div className="issue-section-heading">
              <h2 id="issue-people-heading">주목받는 인물의 글</h2>
              <Link
                onClick={() =>
                  trackEvent('click_content_more', {
                    surface: 'person_preview',
                    destination: 'people',
                  })
                }
                to="/people"
                className="issue-people-more"
                aria-label="주목받는 인물의 글 전체 보기"
              >
                전체 보기
              </Link>
            </div>
            {people.loading ? (
              <p className="issue-state" role="status">
                인물의 글을 불러오는 중입니다.
              </p>
            ) : people.error ? (
              <p className="issue-state" role="alert">
                {people.error} <button onClick={retryPeople}>다시 시도</button>
              </p>
            ) : featured.length ? (
              <div className="issue-people-preview">
                {featured.map(({ person, post }, index) => (
                  <Link
                    onClick={() =>
                      trackEvent('click_person_preview', {
                        surface: 'person_preview',
                        person_id: person.id,
                        position: index + 1,
                      })
                    }
                    to={`/people?person=${person.id}${isToday(post.createdAt) ? '' : '&view=all'}`}
                    key={`${person.id}-${post.id}`}
                  >
                    <span className="issue-person-avatar" aria-hidden="true">
                      {person.profileImageUrl ? (
                        <img src={person.profileImageUrl} alt="" />
                      ) : (
                        <svg viewBox="0 0 48 48" fill="none">
                          <circle cx="24" cy="18" r="8" fill="currentColor" />
                          <path d="M9 43v-4a15 15 0 0 1 30 0v4" fill="currentColor" />
                        </svg>
                      )}
                    </span>
                    <div className="issue-person-content">
                      <div className="issue-meta">
                        <strong>{person.name}</strong>
                        <div className="issue-person-details">
                          <span className="issue-person-platform">
                            {post.sourceType === 'YOUTUBE' ? (
                              <svg viewBox="0 0 28 20" aria-label="YouTube">
                                <path
                                  fill="#f03"
                                  d="M27.4 3.1A3.5 3.5 0 0 0 24.9.6C22.7 0 14 0 14 0S5.3 0 3.1.6A3.5 3.5 0 0 0 .6 3.1C0 5.3 0 10 0 10s0 4.7.6 6.9a3.5 3.5 0 0 0 2.5 2.5C5.3 20 14 20s8.7 0 10.9-.6a3.5 3.5 0 0 0 2.5-2.5C28 14.7 28 10 28 10s0-4.7-.6-6.9Z"
                                />
                                <path fill="#fff" d="m11.2 14.3 7.3-4.3-7.3-4.3z" />
                              </svg>
                            ) : (
                              <img
                                src={post.sourceType === 'X' ? xMark : threadsMark}
                                alt={post.sourceType === 'X' ? 'X' : ''}
                              />
                            )}
                            {post.sourceType !== 'X' && sourceLabel(post.sourceType)}
                          </span>
                          <span aria-hidden="true">·</span>
                          <span>{feedDate(post.createdAt, '등록')}</span>
                        </div>
                      </div>
                    </div>
                    <div className="issue-person-title">
                      <h3>{post.title}</h3>
                    </div>
                    <span className="issue-person-footer">
                      이 인물의 글 보기
                      <svg
                        width="16"
                        height="16"
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="1.5"
                        aria-hidden="true"
                      >
                        <path d="M4 12h16m-6-6 6 6-6 6" />
                      </svg>
                    </span>
                  </Link>
                ))}
              </div>
            ) : (
              <p className="issue-state">아직 등록된 인물의 글이 없습니다.</p>
            )}
          </section>
        </div>
      )}
      <section aria-labelledby="issue-feed-heading">
        <div className="issue-section-heading issue-feed-heading">
          <h2 id="issue-feed-heading">{fullFeed ? '콘텐츠 목록' : '전체 콘텐츠'}</h2>
          <div className="issue-feed-controls">
            <div className="issue-source-filters" role="group" aria-label="콘텐츠 출처">
              {(
                [
                  ['ALL', '전체'],
                  ['DAYNOMY', '데이노미'],
                  ['X', 'X'],
                  ['THREADS', 'Threads'],
                  ['YOUTUBE', 'YouTube'],
                ] as const
              ).map(([value, label]) => (
                <button
                  key={value}
                  type="button"
                  aria-pressed={source === value}
                  onClick={() => {
                    if (value === source) return;
                    trackEvent('filter_content_source', {
                      surface,
                      previous_source: source,
                      source_type: value,
                      result_count: createFeed(news, people.people, contents).filter(
                        (item) => value === 'ALL' || item.source === value,
                      ).length,
                    });
                    setSource(value);
                    setPage(1);
                  }}
                >
                  {label}
                </button>
              ))}
            </div>
            <span>최신순</span>
          </div>
        </div>
        {contentsError && (
          <p className="issue-state" role="alert">
            {contentsError} <button onClick={retry}>다시 시도</button>
          </p>
        )}
        {error && (
          <p className="issue-state" role="alert">
            {error} <button onClick={retry}>다시 시도</button>
          </p>
        )}
        {fullFeed && people.error && (
          <p className="issue-state" role="alert">
            {people.error} <button onClick={retryPeople}>다시 시도</button>
          </p>
        )}
        {loading || people.loading || contentsLoading ? (
          <p className="issue-state" role="status">
            콘텐츠를 불러오는 중입니다.
          </p>
        ) : (
          !items.length &&
          !error &&
          !people.error &&
          !contentsError && <p className="issue-state">표시할 콘텐츠가 없습니다.</p>
        )}
        <IssueFeed
          surface={surface}
          offset={(currentPage - 1) * 12}
          key={`${source}-${currentPage}`}
          items={items.slice((currentPage - 1) * 12, currentPage * 12)}
        />
        {!loading &&
          !contentsLoading &&
          !people.loading &&
          !error &&
          !contentsError &&
          !people.error &&
          items.length > 0 && (
            <IssuePagination
              analytics={{ surface, source_type: source }}
              page={currentPage}
              totalPages={totalPages}
              onChange={setPage}
            />
          )}
      </section>
    </main>
  );
}
