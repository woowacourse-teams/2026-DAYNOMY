import { useEffect, useMemo, useState } from 'react';
import { ApiError } from '../../api/client';
import {
  createFeaturedPerson,
  createFeaturedPersonPost,
  deleteFeaturedPerson,
  deleteFeaturedPersonPost,
  getAdminFeaturedPeople,
  searchFeaturedPersonPosts,
  updateFeaturedPerson,
} from '../people/api';
import { SocialPostEmbed } from '../people/SocialPostEmbed';
import {
  platformLabel,
  type FeaturedPerson,
  type SocialPlatform,
  type SocialPostSearchItem,
} from '../people/types';
import '../people/people.css';

function getErrorMessage(error: unknown, fallback: string) {
  return error instanceof ApiError || error instanceof Error ? error.message : fallback;
}

function emptyToNull(value: string) {
  const trimmed = value.trim();
  return trimmed || null;
}

function isSupportedPostUrl(platform: SocialPlatform, value: string) {
  try {
    const url = new URL(value);
    const host = url.hostname.toLowerCase();
    if (url.protocol !== 'https:') return false;
    if (platform === 'X') {
      return (
        ['x.com', 'www.x.com', 'twitter.com', 'www.twitter.com'].includes(host) &&
        /^\/[^/]+\/status\/\d+\/?$/.test(url.pathname)
      );
    }
    return (
      ['threads.net', 'www.threads.net', 'threads.com', 'www.threads.com'].includes(host) &&
      (/^\/@[^/]+\/post\/[^/]+\/?$/.test(url.pathname) || /^\/t\/[^/]+\/?$/.test(url.pathname))
    );
  } catch {
    return false;
  }
}

export function AdminPeoplePage() {
  const [people, setPeople] = useState<FeaturedPerson[]>([]);
  const [selectedPersonId, setSelectedPersonId] = useState<number | null>(null);
  const [newName, setNewName] = useState('');
  const [newXProfileUrl, setNewXProfileUrl] = useState('');
  const [newThreadsProfileUrl, setNewThreadsProfileUrl] = useState('');
  const [editName, setEditName] = useState('');
  const [editXProfileUrl, setEditXProfileUrl] = useState('');
  const [editThreadsProfileUrl, setEditThreadsProfileUrl] = useState('');
  const [postSourceType, setPostSourceType] = useState<SocialPlatform>('X');
  const [postTitle, setPostTitle] = useState('');
  const [postUrl, setPostUrl] = useState('');
  const [searchResults, setSearchResults] = useState<SocialPostSearchItem[] | null>(null);
  const [searching, setSearching] = useState(false);
  const [registeringUrl, setRegisteringUrl] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    getAdminFeaturedPeople(controller.signal)
      .then((response) => {
        if (controller.signal.aborted) return;
        setPeople(response.people);
        setSelectedPersonId(response.people[0]?.id ?? null);
      })
      .catch((caughtError) => {
        if (!controller.signal.aborted) {
          setError(getErrorMessage(caughtError, '주요 인물을 불러오지 못했습니다.'));
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, []);

  const selectedPerson = useMemo(
    () => people.find((person) => person.id === selectedPersonId) ?? null,
    [people, selectedPersonId],
  );
  const previewVisible = isSupportedPostUrl(postSourceType, postUrl.trim());

  useEffect(() => {
    if (!selectedPerson) return;
    setEditName(selectedPerson.name);
    setEditXProfileUrl(selectedPerson.xProfileUrl ?? '');
    setEditThreadsProfileUrl(selectedPerson.threadsProfileUrl ?? '');
  }, [selectedPerson]);

  useEffect(() => {
    setPostSourceType('X');
    setPostTitle('');
    setPostUrl('');
    setSearchResults(null);
  }, [selectedPersonId]);

  async function submitPerson(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!newName.trim() || saving) return;
    setSaving(true);
    setError('');
    try {
      const person = await createFeaturedPerson({
        name: newName.trim(),
        xProfileUrl: emptyToNull(newXProfileUrl),
        threadsProfileUrl: emptyToNull(newThreadsProfileUrl),
      });
      setPeople((current) => [...current, person]);
      setSelectedPersonId(person.id);
      setNewName('');
      setNewXProfileUrl('');
      setNewThreadsProfileUrl('');
    } catch (caughtError) {
      setError(getErrorMessage(caughtError, '주요 인물을 등록하지 못했습니다.'));
    } finally {
      setSaving(false);
    }
  }

  async function submitEdit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedPerson || !editName.trim() || saving) return;
    setSaving(true);
    setError('');
    try {
      const person = await updateFeaturedPerson(selectedPerson.id, {
        name: editName.trim(),
        xProfileUrl: emptyToNull(editXProfileUrl),
        threadsProfileUrl: emptyToNull(editThreadsProfileUrl),
      });
      setPeople((current) => current.map((item) => (item.id === person.id ? person : item)));
    } catch (caughtError) {
      setError(getErrorMessage(caughtError, '인물 정보를 수정하지 못했습니다.'));
    } finally {
      setSaving(false);
    }
  }

  async function removePerson() {
    if (!selectedPerson || !window.confirm(`${selectedPerson.name}을(를) 삭제할까요?`)) return;
    setSaving(true);
    setError('');
    try {
      await deleteFeaturedPerson(selectedPerson.id);
      const nextPeople = people.filter((person) => person.id !== selectedPerson.id);
      setPeople(nextPeople);
      setSelectedPersonId(nextPeople[0]?.id ?? null);
    } catch (caughtError) {
      setError(getErrorMessage(caughtError, '주요 인물을 삭제하지 못했습니다.'));
    } finally {
      setSaving(false);
    }
  }

  async function submitPost(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedPerson || !previewVisible || saving) return;
    setSaving(true);
    setError('');
    try {
      const post = await createFeaturedPersonPost(selectedPerson.id, {
        sourceType: postSourceType,
        title: emptyToNull(postTitle),
        url: postUrl.trim(),
      });
      setPeople((current) =>
        current.map((person) =>
          person.id === selectedPerson.id ? { ...person, posts: [post, ...person.posts] } : person,
        ),
      );
      setPostTitle('');
      setPostUrl('');
    } catch (caughtError) {
      setError(getErrorMessage(caughtError, '게시물을 등록하지 못했습니다.'));
    } finally {
      setSaving(false);
    }
  }

  async function searchPersonPosts() {
    if (!selectedPerson || searching) return;
    setSearching(true);
    setError('');
    try {
      setSearchResults(await searchFeaturedPersonPosts(selectedPerson.id));
    } catch (caughtError) {
      setError(getErrorMessage(caughtError, 'Threads·X 게시물을 검색하지 못했습니다.'));
    } finally {
      setSearching(false);
    }
  }

  async function registerSearchResult(result: SocialPostSearchItem) {
    if (!selectedPerson || registeringUrl) return;
    setRegisteringUrl(result.url);
    setError('');
    try {
      const post = await createFeaturedPersonPost(selectedPerson.id, {
        sourceType: result.sourceType,
        title: result.title,
        url: result.url,
      });
      setPeople((current) =>
        current.map((person) =>
          person.id === selectedPerson.id ? { ...person, posts: [post, ...person.posts] } : person,
        ),
      );
    } catch (caughtError) {
      setError(getErrorMessage(caughtError, '검색한 게시물을 등록하지 못했습니다.'));
    } finally {
      setRegisteringUrl('');
    }
  }

  async function removePost(postId: number) {
    if (!selectedPerson || !window.confirm('이 게시물을 삭제할까요?')) return;
    setSaving(true);
    setError('');
    try {
      await deleteFeaturedPersonPost(selectedPerson.id, postId);
      setPeople((current) =>
        current.map((person) =>
          person.id === selectedPerson.id
            ? { ...person, posts: person.posts.filter((post) => post.id !== postId) }
            : person,
        ),
      );
    } catch (caughtError) {
      setError(getErrorMessage(caughtError, '게시물을 삭제하지 못했습니다.'));
    } finally {
      setSaving(false);
    }
  }

  return (
    <main className="admin-content admin-people-page">
      <div className="admin-page-heading">
        <div>
          <p className="admin-kicker">주요 인물</p>
          <h1>인물·게시물 관리</h1>
          <p>공식 계정으로 이동해 게시물 URL을 붙여넣고 미리보기 후 등록합니다.</p>
        </div>
      </div>

      <form className="admin-person-create-form" onSubmit={submitPerson}>
        <label className="admin-field">
          <span>인물 이름</span>
          <input
            value={newName}
            maxLength={100}
            placeholder="예: 일론 머스크"
            onChange={(event) => setNewName(event.target.value)}
          />
        </label>
        <label className="admin-field">
          <span>X 프로필 URL</span>
          <input
            type="url"
            value={newXProfileUrl}
            placeholder="https://x.com/elonmusk"
            onChange={(event) => setNewXProfileUrl(event.target.value)}
          />
        </label>
        <label className="admin-field">
          <span>Threads 프로필 URL</span>
          <input
            type="url"
            value={newThreadsProfileUrl}
            placeholder="https://www.threads.net/@username"
            onChange={(event) => setNewThreadsProfileUrl(event.target.value)}
          />
        </label>
        <button className="admin-primary-button" type="submit" disabled={saving || !newName.trim()}>
          인물 추가
        </button>
      </form>

      {loading ? (
        <p className="admin-people-state" aria-busy="true">
          불러오는 중입니다.
        </p>
      ) : null}

      {!loading && people.length > 0 ? (
        <div className="admin-people-layout">
          <aside className="admin-person-list" aria-label="등록된 주요 인물">
            {people.map((person) => (
              <button
                type="button"
                key={person.id}
                aria-pressed={person.id === selectedPersonId}
                onClick={() => setSelectedPersonId(person.id)}
              >
                <strong>{person.name}</strong>
                <span>{person.posts.length}개 게시물</span>
              </button>
            ))}
          </aside>

          {selectedPerson ? (
            <div className="admin-person-workspace">
              <form className="admin-person-profile-form" onSubmit={submitEdit}>
                <div className="admin-person-section-heading">
                  <div>
                    <h2>{selectedPerson.name}</h2>
                    <p>프로필 URL을 열어 등록할 게시물을 선택하세요.</p>
                  </div>
                  <div>
                    {selectedPerson.xProfileUrl ? (
                      <a
                        className="admin-secondary-button"
                        href={selectedPerson.xProfileUrl}
                        target="_blank"
                        rel="noreferrer"
                      >
                        X 프로필 열기 ↗
                      </a>
                    ) : null}
                    {selectedPerson.threadsProfileUrl ? (
                      <a
                        className="admin-secondary-button"
                        href={selectedPerson.threadsProfileUrl}
                        target="_blank"
                        rel="noreferrer"
                      >
                        Threads 프로필 열기 ↗
                      </a>
                    ) : null}
                  </div>
                </div>
                <div className="admin-person-profile-fields">
                  <label className="admin-field">
                    <span>인물 이름</span>
                    <input
                      value={editName}
                      maxLength={100}
                      onChange={(event) => setEditName(event.target.value)}
                    />
                  </label>
                  <label className="admin-field">
                    <span>X 프로필 URL</span>
                    <input
                      type="url"
                      value={editXProfileUrl}
                      onChange={(event) => setEditXProfileUrl(event.target.value)}
                    />
                  </label>
                  <label className="admin-field">
                    <span>Threads 프로필 URL</span>
                    <input
                      type="url"
                      value={editThreadsProfileUrl}
                      onChange={(event) => setEditThreadsProfileUrl(event.target.value)}
                    />
                  </label>
                  <button
                    className="admin-secondary-button"
                    type="submit"
                    disabled={saving || !editName.trim()}
                  >
                    정보 수정
                  </button>
                  <button
                    className="admin-person-delete"
                    type="button"
                    disabled={saving}
                    onClick={() => void removePerson()}
                  >
                    인물 삭제
                  </button>
                </div>
              </form>

              <section className="admin-person-post-section" aria-labelledby="person-post-title">
                <div className="admin-person-section-heading">
                  <div>
                    <h2 id="person-post-title">게시물 등록</h2>
                    <p>AI로 X·Threads를 검색하거나 게시물 URL을 직접 붙여넣을 수 있습니다.</p>
                  </div>
                  <div>
                    <button
                      className="admin-secondary-button"
                      type="button"
                      disabled={searching}
                      onClick={() => void searchPersonPosts()}
                    >
                      {searching ? 'AI 검색 중…' : 'AI로 X·Threads 검색'}
                    </button>
                  </div>
                </div>
                <form className="admin-person-post-form" onSubmit={submitPost}>
                  <label className="admin-field">
                    <span>출처</span>
                    <select
                      value={postSourceType}
                      onChange={(event) => setPostSourceType(event.target.value as SocialPlatform)}
                    >
                      <option value="X">X</option>
                      <option value="THREADS">Threads</option>
                    </select>
                  </label>
                  <label className="admin-field">
                    <span>제목 · 선택</span>
                    <input
                      value={postTitle}
                      maxLength={255}
                      placeholder={`${selectedPerson.name} ${platformLabel(postSourceType)} 게시물`}
                      onChange={(event) => setPostTitle(event.target.value)}
                    />
                  </label>
                  <label className="admin-field admin-person-post-url">
                    <span>게시물 URL</span>
                    <input
                      type="url"
                      value={postUrl}
                      placeholder={
                        postSourceType === 'X'
                          ? 'https://x.com/username/status/123...'
                          : 'https://www.threads.net/@username/post/...'
                      }
                      onChange={(event) => setPostUrl(event.target.value)}
                    />
                  </label>
                  <button
                    className="admin-primary-button"
                    type="submit"
                    disabled={saving || !previewVisible}
                  >
                    게시물 저장
                  </button>
                </form>

                {postUrl.trim() && !previewVisible ? (
                  <small className="admin-field-error">
                    선택한 출처의 게시물 URL을 입력해주세요.
                  </small>
                ) : null}
                {previewVisible ? (
                  <div className="admin-person-post-preview" aria-label="게시물 미리보기">
                    <SocialPostEmbed
                      post={{
                        sourceType: postSourceType,
                        title:
                          postTitle.trim() ||
                          `${selectedPerson.name} ${platformLabel(postSourceType)} 게시물`,
                        url: postUrl.trim(),
                      }}
                    />
                  </div>
                ) : null}

                {searchResults ? (
                  <div className="admin-person-search-results" aria-live="polite">
                    <div className="admin-person-search-results-heading">
                      <h3>AI 검색 결과</h3>
                      <span>{searchResults.length}건</span>
                    </div>
                    {searchResults.length > 0 ? (
                      <ul>
                        {searchResults.map((result) => {
                          const registered = selectedPerson.posts.some(
                            (post) => post.url === result.url,
                          );
                          return (
                            <li key={result.url}>
                              <div className="admin-person-search-result-meta">
                                <strong>{platformLabel(result.sourceType)}</strong>
                                {result.publishedDate ? <span>{result.publishedDate}</span> : null}
                              </div>
                              <SocialPostEmbed post={result} />
                              <button
                                className="admin-primary-button"
                                type="button"
                                disabled={registered || Boolean(registeringUrl)}
                                onClick={() => void registerSearchResult(result)}
                              >
                                {registered
                                  ? '등록됨'
                                  : registeringUrl === result.url
                                    ? '등록 중…'
                                    : '이 게시물 등록'}
                              </button>
                            </li>
                          );
                        })}
                      </ul>
                    ) : (
                      <p className="admin-people-state">검색 결과가 없습니다.</p>
                    )}
                  </div>
                ) : null}
              </section>

              <section className="admin-person-saved-posts" aria-labelledby="saved-posts-title">
                <div className="admin-person-section-heading">
                  <div>
                    <h2 id="saved-posts-title">등록된 게시물</h2>
                    <p>{selectedPerson.posts.length}건 · 최근 등록순</p>
                  </div>
                </div>
                {selectedPerson.posts.length > 0 ? (
                  <ul>
                    {selectedPerson.posts.map((post) => (
                      <li key={post.id}>
                        <div>
                          <span>{platformLabel(post.sourceType)}</span>
                          <strong>{post.title}</strong>
                          <a href={post.url} target="_blank" rel="noreferrer">
                            {post.url}
                          </a>
                        </div>
                        <button
                          type="button"
                          disabled={saving}
                          onClick={() => void removePost(post.id)}
                        >
                          삭제
                        </button>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="admin-people-state">등록된 게시물이 없습니다.</p>
                )}
              </section>
            </div>
          ) : null}
        </div>
      ) : null}

      {!loading && people.length === 0 ? (
        <p className="admin-people-state">먼저 주요 인물을 추가해주세요.</p>
      ) : null}

      {error ? (
        <div className="admin-alert" role="alert">
          <strong>{error}</strong>
        </div>
      ) : null}
    </main>
  );
}
