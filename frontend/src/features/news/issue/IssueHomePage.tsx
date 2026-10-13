import { IssuePagination } from './IssuePagination';
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useFeaturedPeople } from '../../people/useFeaturedPeople';
import { IssueStories } from './IssueStories';
import type { NewsListItem } from '../newslist/types';
import { createFeed, isToday, sourceLabel, timestamp, type FeedItem } from './feed';
import { getContentsThroughToday } from './contents';
import type { ExternalContent } from './feed';
import { getNewsThroughToday } from './todayNews';
import { IssueFeed } from './IssueFeed';
import '../../people/people.css';
import './issue.css';

export function IssueHomePage({ fullFeed = false }: { fullFeed?: boolean }) {
  const people = useFeaturedPeople();
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
    let ignore = false;
    setLoading(true);
    setError('');
    getNewsThroughToday(fullFeed)
      .then((result) => {
        if (ignore) return;
        setNews(result.content);
      })
      .catch(() => {
        if (!ignore) setError('데이노미 이슈를 불러오지 못했습니다.');
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });
    return () => {
      ignore = true;
    };
  }, [attempt, fullFeed]);

  useEffect(() => {
    const controller = new AbortController();
    setContentsLoading(true);
    setContentsError('');
    getContentsThroughToday(controller.signal, fullFeed)
      .then((result) => {
        if (controller.signal.aborted) return;
        setContents(result.items);
      })
      .catch(() => {
        if (!controller.signal.aborted)
          setContentsError('YouTube·Threads·X 자료를 불러오지 못했습니다.');
      })
      .finally(() => {
        if (!controller.signal.aborted) setContentsLoading(false);
      });
    return () => controller.abort();
  }, [attempt, fullFeed]);

  // shortcut: full archive is merged client-side; use a unified paginated API when the archive grows.
  const items = createFeed(news, people.people, contents).filter(
    (item) => (source === 'ALL' || item.source === source) && (fullFeed || isToday(item.date)),
  );
  const totalPages = Math.max(1, Math.ceil(items.length / 12));
  const currentPage = Math.min(page, totalPages);
  const featured = people.people
    .map((person) => ({
      person,
      post: [...person.posts].sort((a, b) => timestamp(b.createdAt) - timestamp(a.createdAt))[0],
    }))
    .filter((entry) => entry.post)
    .sort((a, b) => timestamp(b.post.createdAt) - timestamp(a.post.createdAt))
    .slice(0, 2);
  const retry = () => {
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
          <h1>전체 콘텐츠</h1>
          <p>데이노미, Threads, YouTube, X의 소식을 한곳에서.</p>
        </header>
      ) : (
        <h1 className="sr-only">소식</h1>
      )}
      {!fullFeed && (
        <div className="issue-briefing">
          <IssueStories news={news} loading={loading} error={error} onRetry={retry} />
          <section className="issue-people" aria-labelledby="issue-people-heading">
            <div className="issue-section-heading">
              <h2 id="issue-people-heading">주목받는 인물의 글</h2>
              <Link to="/people" aria-label="주목받는 인물의 글 더 보기">
                더 보기 ↗
              </Link>
            </div>
            {people.loading ? (
              <p className="issue-state" role="status">
                인물의 글을 불러오는 중입니다.
              </p>
            ) : people.error ? (
              <p className="issue-state" role="alert">
                {people.error} <button onClick={people.retry}>다시 시도</button>
              </p>
            ) : featured.length ? (
              <div className="issue-people-preview">
                {featured.map(({ person, post }) => (
                  <Link
                    to={`/people?person=${person.id}${isToday(post.createdAt) ? '' : '&view=all'}`}
                    key={person.id}
                  >
                    <div>
                      <div className="issue-meta">
                        <strong>{person.name}</strong>
                        <span>{sourceLabel(post.sourceType)}</span>
                      </div>
                      <div className="issue-person-title">
                        <h3>{post.title}</h3>
                        <span aria-hidden="true">↗</span>
                      </div>
                    </div>
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
          <h2 id="issue-feed-heading">{fullFeed ? '콘텐츠 목록' : '오늘 올라온 글'}</h2>
          <div className="issue-feed-controls">
            <label>
              <span className="sr-only">콘텐츠 출처</span>
              <select
                value={source}
                onChange={(event) => {
                  setSource(event.target.value as typeof source);
                  setPage(1);
                }}
              >
                <option value="ALL">모든 출처</option>
                <option value="DAYNOMY">데이노미</option>
                <option value="X">X</option>
                <option value="THREADS">Threads</option>
                <option value="YOUTUBE">YouTube</option>
              </select>
            </label>
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
            {people.error} <button onClick={people.retry}>다시 시도</button>
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
          !contentsError && (
            <p className="issue-state">
              {fullFeed
                ? '표시할 콘텐츠가 없습니다.'
                : '오늘 등록된 글이 없습니다. 전체 콘텐츠에서 지난 글을 확인하세요.'}
            </p>
          )
        )}
        <IssueFeed
          key={`${source}-${currentPage}`}
          items={fullFeed ? items.slice((currentPage - 1) * 12, currentPage * 12) : items}
        />
        {!fullFeed && (
          <Link className="issue-more" to="/news/contents">
            전체 콘텐츠 더 보기 <span aria-hidden="true">↓</span>
          </Link>
        )}
        {fullFeed &&
          !loading &&
          !contentsLoading &&
          !people.loading &&
          !error &&
          !contentsError &&
          !people.error &&
          items.length > 0 && (
            <IssuePagination page={currentPage} totalPages={totalPages} onChange={setPage} />
          )}
      </section>
    </main>
  );
}
