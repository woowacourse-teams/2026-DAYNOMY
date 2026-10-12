import { useEffect, useMemo, useState } from 'react';
import { getFeaturedPeople } from './api';
import { SocialPostEmbed } from './SocialPostEmbed';
import { platformLabel, type FeaturedPerson, type FeaturedPersonPost } from './types';
import './people.css';

type RecentPost = {
  person: FeaturedPerson;
  post: FeaturedPersonPost;
};

function ArrowUpRightIcon() {
  return (
    <svg viewBox="0 0 16 16" aria-hidden="true">
      <path d="M4.5 11.5 11.75 4.25M6.25 4.25h5.5v5.5" />
    </svg>
  );
}

function SearchIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="10.5" cy="10.5" r="6.5" />
      <path d="m15.5 15.5 4.5 4.5" />
    </svg>
  );
}

function postTimestamp(post: FeaturedPersonPost) {
  if (!post.createdAt) return Number.NEGATIVE_INFINITY;
  const timestamp = Date.parse(post.createdAt);
  return Number.isNaN(timestamp) ? Number.NEGATIVE_INFINITY : timestamp;
}

function formatPostDate(createdAt: string | null) {
  if (!createdAt) return '최근 등록';
  const date = new Date(createdAt);
  if (Number.isNaN(date.getTime())) return '최근 등록';
  return new Intl.DateTimeFormat('ko-KR', { month: 'numeric', day: 'numeric' }).format(date);
}

function personPlatformLabel(person: FeaturedPerson) {
  const platforms = new Set(person.posts.map((post) => platformLabel(post.sourceType)));
  if (person.xProfileUrl) platforms.add('X');
  if (person.threadsProfileUrl) platforms.add('Threads');
  return [...platforms].join(' · ') || '공개 프로필 준비 중';
}

export function PeoplePage() {
  const [people, setPeople] = useState<FeaturedPerson[]>([]);
  const [selectedPersonId, setSelectedPersonId] = useState<number | null>(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    getFeaturedPeople(controller.signal)
      .then((response) => {
        if (controller.signal.aborted) return;
        setPeople(response.people);
        setSelectedPersonId((current) => current ?? response.people[0]?.id ?? null);
      })
      .catch((caughtError) => {
        if (!controller.signal.aborted) {
          setError(
            caughtError instanceof Error ? caughtError.message : '주요 인물을 불러오지 못했습니다.',
          );
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, []);

  const recentPosts = useMemo<RecentPost[]>(
    () =>
      people
        .flatMap((person) => person.posts.map((post) => ({ person, post })))
        .sort(
          (left, right) =>
            postTimestamp(right.post) - postTimestamp(left.post) || right.post.id - left.post.id,
        )
        .slice(0, 3),
    [people],
  );

  const visiblePeople = useMemo(() => {
    const normalizedSearchTerm = searchTerm.trim().toLocaleLowerCase('ko-KR');
    if (!normalizedSearchTerm) return people;
    return people.filter((person) =>
      person.name.toLocaleLowerCase('ko-KR').includes(normalizedSearchTerm),
    );
  }, [people, searchTerm]);

  const selectedPerson = useMemo(
    () => people.find((person) => person.id === selectedPersonId) ?? people[0] ?? null,
    [people, selectedPersonId],
  );

  const leadPost = recentPosts[0];
  const supportingPosts = recentPosts.slice(1);

  return (
    <main className="people-page">
      <header className="people-heading">
        <h1>주요 인물</h1>
        <p>시장을 움직이는 발언과 인물을 한눈에 확인하세요.</p>
      </header>

      {loading ? (
        <p className="people-state" aria-busy="true">
          불러오는 중입니다.
        </p>
      ) : null}
      {!loading && error ? (
        <p className="people-state" role="alert">
          {error}
        </p>
      ) : null}
      {!loading && !error && people.length === 0 ? (
        <p className="people-state">아직 등록된 주요 인물이 없습니다.</p>
      ) : null}

      {!loading && !error && people.length > 0 ? (
        <>
          <section className="people-trends" aria-labelledby="people-trends-title">
            <div className="people-section-heading">
              <h2 id="people-trends-title">요즘 뜨는 말</h2>
              <span>최근 등록순</span>
            </div>

            {leadPost ? (
              <div
                className={
                  supportingPosts.length > 0
                    ? 'people-trend-grid'
                    : 'people-trend-grid people-trend-grid-single'
                }
              >
                <a
                  className="people-trend-primary"
                  href={leadPost.post.url}
                  target="_blank"
                  rel="noreferrer"
                >
                  <span className="people-trend-label">가장 최근 등록된 발언</span>
                  <blockquote>{leadPost.post.title}</blockquote>
                  <span className="people-trend-meta">
                    <strong>{leadPost.person.name}</strong>
                    <span aria-hidden="true">·</span>
                    <span>{platformLabel(leadPost.post.sourceType)}</span>
                    <span aria-hidden="true">·</span>
                    <time dateTime={leadPost.post.createdAt ?? undefined}>
                      {formatPostDate(leadPost.post.createdAt)}
                    </time>
                  </span>
                </a>

                {supportingPosts.length > 0 ? (
                  <ol className="people-trend-supporting">
                    {supportingPosts.map(({ person, post }, index) => (
                      <li key={post.id}>
                        <a href={post.url} target="_blank" rel="noreferrer">
                          <span className="people-trend-rank">0{index + 2}</span>
                          <strong>{post.title}</strong>
                          <span>
                            {person.name} · {platformLabel(post.sourceType)} ·{' '}
                            {formatPostDate(post.createdAt)}
                          </span>
                        </a>
                      </li>
                    ))}
                  </ol>
                ) : null}
              </div>
            ) : (
              <p className="people-trends-empty">아직 등록된 발언이 없습니다.</p>
            )}
          </section>

          <section className="people-directory-section" aria-labelledby="people-directory-title">
            <div className="people-directory-heading">
              <div>
                <h2 id="people-directory-title">전체 인물</h2>
                <span>{visiblePeople.length}</span>
              </div>
              <label className="people-search">
                <span className="sr-only">전체 인물 검색</span>
                <SearchIcon />
                <input
                  type="search"
                  value={searchTerm}
                  placeholder="이름 검색"
                  onChange={(event) => setSearchTerm(event.target.value)}
                />
              </label>
            </div>

            {visiblePeople.length > 0 ? (
              <div
                className={`people-directory people-directory-columns-${Math.min(
                  visiblePeople.length,
                  3,
                )}`}
                aria-label="주요 인물 목록"
              >
                {visiblePeople.map((person) => {
                  const latestPost = person.posts[0];
                  return (
                    <button
                      type="button"
                      key={person.id}
                      aria-pressed={person.id === selectedPerson?.id}
                      aria-controls="selected-person-posts"
                      onClick={() => setSelectedPersonId(person.id)}
                    >
                      <span className="people-directory-topline">
                        <span>
                          <strong>{person.name}</strong>
                          <small>{personPlatformLabel(person)}</small>
                        </span>
                        <ArrowUpRightIcon />
                      </span>
                      <span className="people-directory-latest">
                        {latestPost?.title ?? '아직 등록된 발언이 없습니다.'}
                      </span>
                    </button>
                  );
                })}
              </div>
            ) : (
              <p className="people-directory-empty">검색 결과가 없습니다.</p>
            )}
          </section>

          {selectedPerson ? (
            <section
              className="people-feed"
              id="selected-person-posts"
              aria-labelledby="selected-person-name"
            >
              <div className="people-profile-heading">
                <div>
                  <h2 id="selected-person-name">{selectedPerson.name}</h2>
                  <p>관리자가 확인한 공개 게시물입니다.</p>
                </div>
                <div className="people-profile-links">
                  {selectedPerson.xProfileUrl ? (
                    <a href={selectedPerson.xProfileUrl} target="_blank" rel="noreferrer">
                      X 프로필 <ArrowUpRightIcon />
                    </a>
                  ) : null}
                  {selectedPerson.threadsProfileUrl ? (
                    <a href={selectedPerson.threadsProfileUrl} target="_blank" rel="noreferrer">
                      Threads 프로필 <ArrowUpRightIcon />
                    </a>
                  ) : null}
                </div>
              </div>

              {selectedPerson.posts.length > 0 ? (
                <ul className="people-post-list">
                  {selectedPerson.posts.map((post) => (
                    <li key={post.id}>
                      <SocialPostEmbed post={post} />
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="people-feed-empty">아직 등록된 게시물이 없습니다.</p>
              )}
            </section>
          ) : null}
        </>
      ) : null}
    </main>
  );
}
