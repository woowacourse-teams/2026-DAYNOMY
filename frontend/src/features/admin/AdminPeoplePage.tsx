import { useEffect, useLayoutEffect, useMemo, useState } from 'react';
import { ApiError } from '../../api/client';
import { searchWikimediaImages } from './api';
import type { AdminWikimediaImageCandidate } from './types';
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
    if (platform === 'THREADS')
      return (
        ['threads.net', 'www.threads.net', 'threads.com', 'www.threads.com'].includes(host) &&
        (/^\/@[^/]+\/post\/[^/]+\/?$/.test(url.pathname) || /^\/t\/[^/]+\/?$/.test(url.pathname))
      );
    const normalizedHost = host.replace(/^www\./, '').replace(/^m\./, '');
    return (
      (normalizedHost === 'youtu.be' && /^\/[A-Za-z0-9_-]{11}\/?$/.test(url.pathname)) ||
      (normalizedHost === 'youtube.com' &&
        (/^\/(embed|shorts|live)\/[A-Za-z0-9_-]{11}\/?$/.test(url.pathname) ||
          (url.pathname === '/watch' &&
            /^[A-Za-z0-9_-]{11}$/.test(url.searchParams.get('v') ?? ''))))
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
  const [newYoutubeProfileUrl, setNewYoutubeProfileUrl] = useState('');
  const [editName, setEditName] = useState('');
  const [editXProfileUrl, setEditXProfileUrl] = useState('');
  const [editThreadsProfileUrl, setEditThreadsProfileUrl] = useState('');
  const [editYoutubeProfileUrl, setEditYoutubeProfileUrl] = useState('');
  const [imageKeyword, setImageKeyword] = useState('');
  const [imageCandidates, setImageCandidates] = useState<AdminWikimediaImageCandidate[]>([]);
  const [selectedImage, setSelectedImage] = useState<AdminWikimediaImageCandidate | null>(null);
  const [searchingImages, setSearchingImages] = useState(false);
  const [postSourceType, setPostSourceType] = useState<SocialPlatform>('X');
  const [postTitle, setPostTitle] = useState('');
  const [postUrl, setPostUrl] = useState('');
  const [searchResults, setSearchResults] = useState<SocialPostSearchItem[] | null>(null);
  const [searching, setSearching] = useState(false);
  const [registeringUrl, setRegisteringUrl] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [personQuery, setPersonQuery] = useState('');
  const [workspace, setWorkspace] = useState<'posts' | 'add' | 'profile'>('posts');
  const [previewedResult, setPreviewedResult] = useState<string | null>(null);
  const [addMethod, setAddMethod] = useState<'url' | 'search'>('url');
  const [postQuery, setPostQuery] = useState('');
  const [postFilter, setPostFilter] = useState<SocialPlatform | 'ALL'>('ALL');
  const [expandedPostId, setExpandedPostId] = useState<number | null>(null);
  const busy = saving || searching || searchingImages || Boolean(registeringUrl);

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
  const visiblePosts = (selectedPerson?.posts ?? []).filter(
    (post) =>
      (postFilter === 'ALL' || post.sourceType === postFilter) &&
      `${post.title} ${post.url}`
        .toLocaleLowerCase()
        .includes(postQuery.trim().toLocaleLowerCase()),
  );
  const previewVisible = isSupportedPostUrl(postSourceType, postUrl.trim());

  const personName = selectedPerson?.name;
  const personXUrl = selectedPerson?.xProfileUrl;
  const personThreadsUrl = selectedPerson?.threadsProfileUrl;
  const personYoutubeUrl = selectedPerson?.youtubeProfileUrl;
  const personImageUrl = selectedPerson?.profileImageUrl;

  useEffect(() => {
    if (personName === undefined) return;
    setEditName(personName);
    setEditXProfileUrl(personXUrl ?? '');
    setEditThreadsProfileUrl(personThreadsUrl ?? '');
    setEditYoutubeProfileUrl(personYoutubeUrl ?? '');
    setSelectedImage(null);
    setImageCandidates([]);
    setImageKeyword(personName);
  }, [
    selectedPersonId,
    personName,
    personXUrl,
    personThreadsUrl,
    personYoutubeUrl,
    personImageUrl,
  ]);

  useLayoutEffect(() => {
    setPostQuery('');
    setPostFilter('ALL');
    setExpandedPostId(null);
    setPostSourceType('X');
    setPostTitle('');
    setPostUrl('');
    setSearchResults(null);
    setWorkspace('posts');
    setAddMethod('url');
    setPreviewedResult(null);
  }, [selectedPersonId]);

  function selectPerson(id: number) {
    if (id === selectedPersonId || busy) return;
    if (!confirmDiscard()) return;
    setError('');
    setNotice('');
    setSelectedPersonId(id);
  }

  function confirmDiscard() {
    const dirty =
      selectedPerson &&
      (editName !== selectedPerson.name ||
        editXProfileUrl !== (selectedPerson.xProfileUrl ?? '') ||
        editThreadsProfileUrl !== (selectedPerson.threadsProfileUrl ?? '') ||
        editYoutubeProfileUrl !== (selectedPerson.youtubeProfileUrl ?? '') ||
        selectedImage ||
        postTitle.trim() ||
        postUrl.trim());
    return (
      !dirty ||
      window.confirm('저장하지 않은 입력이 있습니다. 내용을 버리고 다른 인물로 이동할까요?')
    );
  }

  async function submitPerson(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!newName.trim() || busy || !confirmDiscard()) return;
    setSaving(true);
    setError('');
    setNotice('');
    try {
      const person = await createFeaturedPerson({
        name: newName.trim(),
        xProfileUrl: emptyToNull(newXProfileUrl),
        threadsProfileUrl: emptyToNull(newThreadsProfileUrl),
        youtubeProfileUrl: emptyToNull(newYoutubeProfileUrl),
      });
      setPeople((current) => [...current, person]);
      setPersonQuery('');
      setSelectedPersonId(person.id);
      setNewName('');
      setNewXProfileUrl('');
      setNewThreadsProfileUrl('');
      setNewYoutubeProfileUrl('');
      setNotice(`${person.name}을(를) 등록했습니다.`);
    } catch (caughtError) {
      setError(getErrorMessage(caughtError, '주요 인물을 등록하지 못했습니다.'));
    } finally {
      setSaving(false);
    }
  }

  async function submitEdit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedPerson || !editName.trim() || busy) return;
    setSaving(true);
    setError('');
    setNotice('');
    try {
      const person = await updateFeaturedPerson(selectedPerson.id, {
        name: editName.trim(),
        xProfileUrl: emptyToNull(editXProfileUrl),
        threadsProfileUrl: emptyToNull(editThreadsProfileUrl),
        youtubeProfileUrl: emptyToNull(editYoutubeProfileUrl),
        profileImageSelection: selectedImage ? { title: selectedImage.title } : null,
      });
      setPeople((current) => current.map((item) => (item.id === person.id ? person : item)));
      setSelectedImage(null);
      setNotice('프로필을 저장했습니다.');
    } catch (caughtError) {
      setError(getErrorMessage(caughtError, '인물 정보를 수정하지 못했습니다.'));
    } finally {
      setSaving(false);
    }
  }

  async function searchProfileImages() {
    const keyword = imageKeyword.trim();
    if (keyword.length < 2 || busy) return;
    setSearchingImages(true);
    setError('');
    setNotice('');
    try {
      setImageCandidates(await searchWikimediaImages(keyword));
    } catch (caughtError) {
      setError(getErrorMessage(caughtError, 'Wikimedia Commons 이미지 검색에 실패했습니다.'));
    } finally {
      setSearchingImages(false);
    }
  }

  async function removePerson() {
    if (!selectedPerson || busy || !window.confirm(`${selectedPerson.name}을(를) 삭제할까요?`))
      return;
    setSaving(true);
    setError('');
    setNotice('');
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
    if (!selectedPerson || !previewVisible || busy) return;
    setSaving(true);
    setError('');
    setNotice('');
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
      setNotice('게시물을 등록했습니다. 등록된 게시물에서 확인할 수 있습니다.');
    } catch (caughtError) {
      setError(getErrorMessage(caughtError, '게시물을 등록하지 못했습니다.'));
    } finally {
      setSaving(false);
    }
  }

  async function searchPersonPosts() {
    if (!selectedPerson || busy) return;
    setSearching(true);
    setError('');
    setNotice('');
    try {
      setSearchResults(await searchFeaturedPersonPosts(selectedPerson.id));
    } catch (caughtError) {
      setError(getErrorMessage(caughtError, 'Threads·X 게시물을 검색하지 못했습니다.'));
    } finally {
      setSearching(false);
    }
  }

  async function registerSearchResult(result: SocialPostSearchItem) {
    if (!selectedPerson || busy) return;
    setRegisteringUrl(result.url);
    setError('');
    setNotice('');
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
      setNotice('게시물을 등록했습니다. 등록된 게시물에서 확인할 수 있습니다.');
    } catch (caughtError) {
      setError(getErrorMessage(caughtError, '검색한 게시물을 등록하지 못했습니다.'));
    } finally {
      setRegisteringUrl('');
    }
  }

  async function removePost(postId: number) {
    if (!selectedPerson || busy || !window.confirm('이 게시물을 삭제할까요?')) return;
    setSaving(true);
    setError('');
    setNotice('');
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
          <h1>인물·게시물 관리</h1>
          <p>공식 계정으로 이동해 게시물 URL을 붙여넣고 미리보기 후 등록합니다.</p>
        </div>
      </div>

      <details className="admin-create-disclosure">
        <summary>새 인물 등록</summary>
        <form className="admin-person-create-form" onSubmit={submitPerson}>
          <label className="admin-field">
            <span>인물 이름</span>
            <input
              disabled={busy}
              value={newName}
              maxLength={100}
              placeholder="예: 일론 머스크"
              onChange={(event) => setNewName(event.target.value)}
            />
          </label>
          <label className="admin-field">
            <span>X 프로필 URL</span>
            <input
              disabled={busy}
              type="url"
              value={newXProfileUrl}
              placeholder="https://x.com/elonmusk"
              onChange={(event) => setNewXProfileUrl(event.target.value)}
            />
          </label>
          <label className="admin-field">
            <span>Threads 프로필 URL</span>
            <input
              disabled={busy}
              type="url"
              value={newThreadsProfileUrl}
              placeholder="https://www.threads.net/@username"
              onChange={(event) => setNewThreadsProfileUrl(event.target.value)}
            />
          </label>
          <label className="admin-field">
            <span>YouTube 채널 URL</span>
            <input
              disabled={busy}
              type="url"
              value={newYoutubeProfileUrl}
              placeholder="https://www.youtube.com/@channel"
              onChange={(event) => setNewYoutubeProfileUrl(event.target.value)}
            />
          </label>
          <button className="admin-primary-button" type="submit" disabled={busy || !newName.trim()}>
            인물 추가
          </button>
        </form>
      </details>

      {loading ? (
        <p className="admin-people-state" aria-busy="true">
          불러오는 중입니다.
        </p>
      ) : null}

      {!loading && people.length > 0 ? (
        <div className="admin-people-layout">
          <aside className="admin-target-picker" aria-label="등록된 주요 인물">
            <label className="admin-field">
              <span>
                인물 찾기 <small>{people.length}명</small>
              </span>
              <input
                disabled={busy}
                type="search"
                value={personQuery}
                onChange={(event) => setPersonQuery(event.target.value)}
                placeholder="이름으로 찾기"
              />
            </label>
            <div className="admin-person-list">
              {people
                .filter((person) =>
                  person.name.toLocaleLowerCase().includes(personQuery.trim().toLocaleLowerCase()),
                )
                .map((person) => (
                  <button
                    type="button"
                    key={person.id}
                    aria-pressed={person.id === selectedPersonId}
                    disabled={busy}
                    onClick={() => selectPerson(person.id)}
                  >
                    <span className="admin-person-avatar" aria-hidden="true">
                      {person.profileImageUrl ? <img src={person.profileImageUrl} alt="" /> : '인'}
                    </span>
                    <strong>{person.name}</strong>
                    <span>{person.posts.length}개 게시물</span>
                  </button>
                ))}
            </div>
            {people.every(
              (person) =>
                !person.name.toLocaleLowerCase().includes(personQuery.trim().toLocaleLowerCase()),
            ) ? (
              <p className="admin-picker-empty">일치하는 인물이 없습니다.</p>
            ) : null}
          </aside>

          {selectedPerson ? (
            <div className="admin-person-workspace">
              <div className="admin-workspace-heading">
                <div className="admin-person-section-heading">
                  <div>
                    <h2>{selectedPerson.name}</h2>
                    <p>
                      {workspace === 'profile'
                        ? '이름, 연결 계정과 프로필 이미지를 관리하세요.'
                        : '프로필 URL을 열어 등록할 게시물을 선택하세요.'}
                    </p>
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
                    {selectedPerson.youtubeProfileUrl ? (
                      <a
                        className="admin-secondary-button"
                        href={selectedPerson.youtubeProfileUrl}
                        target="_blank"
                        rel="noreferrer"
                      >
                        YouTube 채널 열기 ↗
                      </a>
                    ) : null}
                  </div>
                </div>
              </div>
              <div className="admin-workspace-switch" role="group" aria-label="인물 작업 선택">
                <button
                  type="button"
                  aria-pressed={workspace === 'posts'}
                  onClick={() => setWorkspace('posts')}
                >
                  등록된 게시물 <span>{selectedPerson.posts.length}</span>
                </button>
                <button
                  type="button"
                  aria-pressed={workspace === 'add'}
                  onClick={() => setWorkspace('add')}
                >
                  게시물 추가
                </button>
                <button
                  type="button"
                  aria-pressed={workspace === 'profile'}
                  onClick={() => setWorkspace('profile')}
                >
                  프로필 수정
                </button>
              </div>
              {workspace === 'profile' ? (
                <form className="admin-person-profile-form" onSubmit={submitEdit}>
                  <div className="admin-profile-editor">
                    <div className="admin-profile-details">
                      <section
                        className="admin-profile-section"
                        aria-labelledby="profile-basic-title"
                      >
                        <h2 id="profile-basic-title">기본 정보</h2>
                        <p>서비스에 표시할 인물 이름을 입력하세요.</p>
                        <label className="admin-field">
                          <span>인물 이름</span>
                          <input
                            required
                            disabled={busy}
                            value={editName}
                            maxLength={100}
                            onChange={(event) => setEditName(event.target.value)}
                          />
                        </label>
                      </section>
                      <section
                        className="admin-profile-section"
                        aria-labelledby="profile-accounts-title"
                      >
                        <h2 id="profile-accounts-title">연결 계정</h2>
                        <p>공식 프로필 주소를 입력하세요. 사용하지 않는 계정은 비워두면 됩니다.</p>
                        <label className="admin-field">
                          <span>X 프로필 URL</span>
                          <input
                            disabled={busy}
                            type="url"
                            placeholder="https://x.com/username"
                            value={editXProfileUrl}
                            onChange={(event) => setEditXProfileUrl(event.target.value)}
                          />
                        </label>
                        <label className="admin-field">
                          <span>Threads 프로필 URL</span>
                          <input
                            disabled={busy}
                            type="url"
                            placeholder="https://www.threads.net/@username"
                            value={editThreadsProfileUrl}
                            onChange={(event) => setEditThreadsProfileUrl(event.target.value)}
                          />
                        </label>
                        <label className="admin-field">
                          <span>YouTube 채널 URL</span>
                          <input
                            disabled={busy}
                            type="url"
                            value={editYoutubeProfileUrl}
                            placeholder="https://www.youtube.com/@channel"
                            onChange={(event) => setEditYoutubeProfileUrl(event.target.value)}
                          />
                        </label>
                      </section>
                    </div>
                    <section
                      className="admin-person-image-picker admin-profile-photo"
                      aria-labelledby="profile-photo-title"
                    >
                      <div className="admin-person-image-picker-heading">
                        <div>
                          <h2 id="profile-photo-title">프로필 이미지</h2>
                          <small>등록하지 않으면 기본 이미지가 표시됩니다.</small>
                        </div>
                        <div
                          className="admin-person-image-preview"
                          aria-label="선택된 프로필 이미지"
                        >
                          {selectedImage ? (
                            <img src={selectedImage.thumbnailUrl} alt={selectedImage.title} />
                          ) : selectedPerson.profileImageUrl ? (
                            <img
                              src={selectedPerson.profileImageUrl}
                              alt={`${selectedPerson.name} 프로필`}
                            />
                          ) : (
                            <span aria-hidden="true">인</span>
                          )}
                        </div>
                      </div>
                      <div className="admin-field admin-profile-image-query">
                        <label htmlFor="profile-image-keyword">이미지 검색어</label>
                        <small className="admin-field-hint">
                          한글 또는 영문 이름으로 Wikimedia Commons에서 찾습니다.
                        </small>
                        <div className="admin-person-image-search">
                          <input
                            disabled={busy}
                            id="profile-image-keyword"
                            aria-label="프로필 이미지 검색어"
                            value={imageKeyword}
                            minLength={2}
                            maxLength={100}
                            placeholder="예: 일론 머스크"
                            onChange={(event) => setImageKeyword(event.target.value)}
                            onKeyDown={(event) => {
                              if (event.key === 'Enter') {
                                event.preventDefault();
                                void searchProfileImages();
                              }
                            }}
                          />
                          <button
                            className="admin-secondary-button"
                            type="button"
                            disabled={busy || imageKeyword.trim().length < 2}
                            onClick={() => void searchProfileImages()}
                          >
                            {searchingImages ? '검색 중…' : 'Wikimedia 검색'}
                          </button>
                        </div>
                      </div>
                      {selectedImage ? (
                        <div className="admin-profile-image-selection">
                          <p>
                            <strong>저장할 이미지</strong>
                            <span>{selectedImage.title.replace(/^File:/, '')}</span>
                            <small>
                              {selectedImage.author || '저작자 정보 없음'} · {selectedImage.license}
                            </small>
                          </p>
                          <button
                            type="button"
                            className="admin-secondary-button"
                            disabled={busy}
                            onClick={() => setSelectedImage(null)}
                          >
                            선택 취소
                          </button>
                        </div>
                      ) : null}
                      {imageCandidates.length > 0 ? (
                        <div
                          className="admin-person-image-candidates"
                          aria-label="Wikimedia Commons 이미지 검색 결과"
                        >
                          {imageCandidates.map((candidate) => (
                            <button
                              key={candidate.title}
                              type="button"
                              disabled={busy}
                              aria-pressed={selectedImage?.title === candidate.title}
                              onClick={() => setSelectedImage(candidate)}
                            >
                              <img src={candidate.thumbnailUrl} alt={candidate.title} />
                              <span>{candidate.title.replace(/^File:/, '')}</span>
                            </button>
                          ))}
                        </div>
                      ) : null}
                    </section>
                  </div>
                  <div className="admin-profile-savebar">
                    <p>변경한 정보와 선택한 이미지는 저장 후 반영됩니다.</p>
                    <button
                      className="admin-primary-button"
                      type="submit"
                      disabled={busy || !editName.trim()}
                    >
                      {saving ? '저장 중…' : '변경사항 저장'}
                    </button>
                  </div>
                  <details className="admin-profile-danger">
                    <summary>인물 삭제</summary>
                    <div>
                      <p>
                        이 인물과 등록된 게시물 {selectedPerson.posts.length}건을 삭제합니다. 삭제
                        전 다시 확인합니다.
                      </p>
                      <button
                        className="admin-person-delete"
                        type="button"
                        disabled={busy}
                        onClick={() => void removePerson()}
                      >
                        인물 삭제
                      </button>
                    </div>
                  </details>
                </form>
              ) : null}

              {workspace === 'add' ? (
                <section className="admin-person-post-section" aria-labelledby="person-post-title">
                  <div className="admin-method-switch" role="group" aria-label="게시물 추가 방법">
                    <button
                      type="button"
                      aria-pressed={addMethod === 'url'}
                      onClick={() => setAddMethod('url')}
                    >
                      URL 직접 등록
                    </button>
                    <button
                      type="button"
                      aria-pressed={addMethod === 'search'}
                      onClick={() => setAddMethod('search')}
                    >
                      AI로 찾아 등록
                    </button>
                  </div>
                  <div className="admin-person-section-heading">
                    <div>
                      <h2 id="person-post-title">
                        {addMethod === 'url' ? '게시물 URL 붙여넣기' : '게시물 검색'}
                      </h2>
                      <p>
                        {addMethod === 'url'
                          ? `${selectedPerson.name}에게 연결할 게시물 주소를 입력하세요.`
                          : '검색 결과의 내용을 확인하고 필요한 게시물만 등록하세요.'}
                      </p>
                    </div>
                    {addMethod === 'search' ? (
                      <div>
                        <button
                          className="admin-secondary-button"
                          type="button"
                          disabled={busy}
                          onClick={() => void searchPersonPosts()}
                        >
                          {searching ? 'AI 검색 중…' : 'AI로 X·Threads 검색'}
                        </button>
                      </div>
                    ) : null}
                  </div>
                  {addMethod === 'url' ? (
                    <div className="admin-post-compose">
                      <form
                        id="person-post-form"
                        className="admin-person-post-form"
                        onSubmit={submitPost}
                      >
                        <label className="admin-field admin-person-post-url">
                          <span>게시물 URL</span>
                          <input
                            disabled={busy}
                            type="url"
                            value={postUrl}
                            placeholder={
                              postSourceType === 'X'
                                ? 'https://x.com/username/status/123...'
                                : postSourceType === 'THREADS'
                                  ? 'https://www.threads.net/@username/post/...'
                                  : 'https://www.youtube.com/watch?v=...'
                            }
                            aria-invalid={Boolean(postUrl.trim()) && !previewVisible}
                            aria-describedby="admin-post-url-help"
                            onChange={(event) => {
                              const value = event.target.value;
                              setPostUrl(value);
                              const detected = (['X', 'THREADS', 'YOUTUBE'] as const).find(
                                (platform) => isSupportedPostUrl(platform, value.trim()),
                              );
                              if (detected) setPostSourceType(detected);
                            }}
                          />
                        </label>
                        <p
                          id="admin-post-url-help"
                          className={
                            postUrl.trim() && !previewVisible
                              ? 'admin-field-error admin-post-help'
                              : 'admin-field-hint admin-post-help'
                          }
                        >
                          {postUrl.trim() && !previewVisible
                            ? '게시물 주소를 확인해 주세요. 프로필 주소 대신 X·Threads 게시물 또는 YouTube 영상의 https 주소를 입력하세요.'
                            : '게시물 주소를 붙여넣으면 출처를 자동으로 선택합니다. 제목은 비워두어도 됩니다.'}
                        </p>
                        <label className="admin-field">
                          <span>출처</span>
                          <select
                            disabled={busy}
                            value={postSourceType}
                            onChange={(event) =>
                              setPostSourceType(event.target.value as SocialPlatform)
                            }
                          >
                            <option value="X">X</option>
                            <option value="THREADS">Threads</option>
                            <option value="YOUTUBE">YouTube</option>
                          </select>
                        </label>
                        <label className="admin-field">
                          <span>제목 · 선택</span>
                          <input
                            disabled={busy}
                            value={postTitle}
                            maxLength={255}
                            placeholder={`${selectedPerson.name} ${platformLabel(postSourceType)} 게시물`}
                            onChange={(event) => setPostTitle(event.target.value)}
                          />
                        </label>
                      </form>

                      <div className="admin-compose-preview">
                        <h3>등록 전 미리보기</h3>
                        <p>내용을 확인한 뒤 {selectedPerson.name}에게 연결하세요.</p>
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
                        ) : (
                          <div className="admin-compose-preview-empty">
                            X·Threads 게시물 또는 YouTube 영상 주소를 입력하면 미리보기가
                            표시됩니다.
                          </div>
                        )}
                      </div>
                      <div className="admin-post-compose-actions">
                        <p>
                          {previewVisible
                            ? `${platformLabel(postSourceType)} 게시물을 등록할 준비가 되었습니다.`
                            : '게시물 주소를 입력해 주세요.'}
                        </p>
                        <button
                          className="admin-primary-button"
                          type="submit"
                          form="person-post-form"
                          disabled={busy || !previewVisible}
                        >
                          {saving ? '게시물 저장 중…' : '게시물 저장'}
                        </button>
                      </div>
                    </div>
                  ) : null}
                  {addMethod === 'search' && searchResults ? (
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
                                  {result.publishedDate ? (
                                    <span>{result.publishedDate}</span>
                                  ) : null}
                                </div>
                                <strong className="admin-result-title">{result.title}</strong>
                                <div className="admin-post-result-actions">
                                  <a
                                    className="admin-source-link"
                                    href={result.url}
                                    target="_blank"
                                    rel="noreferrer"
                                  >
                                    원문 보기
                                  </a>
                                  <button
                                    className="admin-secondary-button"
                                    type="button"
                                    aria-expanded={previewedResult === result.url}
                                    onClick={() =>
                                      setPreviewedResult(
                                        previewedResult === result.url ? null : result.url,
                                      )
                                    }
                                  >
                                    미리보기
                                  </button>

                                  <button
                                    className="admin-primary-button"
                                    type="button"
                                    disabled={registered || busy}
                                    onClick={() => void registerSearchResult(result)}
                                  >
                                    {registered
                                      ? '등록됨'
                                      : registeringUrl === result.url
                                        ? '등록 중…'
                                        : '이 게시물 등록'}
                                  </button>
                                </div>
                                {previewedResult === result.url ? (
                                  <SocialPostEmbed post={result} />
                                ) : null}
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
              ) : null}

              {workspace === 'posts' ? (
                <section className="admin-person-saved-posts" aria-labelledby="saved-posts-title">
                  <div className="admin-person-section-heading">
                    <div>
                      <h2 id="saved-posts-title">등록된 게시물</h2>
                      <p>{selectedPerson.posts.length}건 · 최근 등록순</p>
                    </div>
                  </div>
                  {selectedPerson.posts.length > 0 ? (
                    <>
                      <div className="admin-saved-post-tools">
                        <label className="admin-field">
                          <span className="sr-only">등록된 게시물 검색</span>
                          <input
                            type="search"
                            placeholder="제목 또는 주소로 찾기"
                            value={postQuery}
                            onChange={(event) => setPostQuery(event.target.value)}
                          />
                        </label>
                        <button
                          className="admin-secondary-button"
                          type="button"
                          onClick={() => setWorkspace('add')}
                        >
                          새 게시물 추가
                        </button>
                      </div>
                      <div
                        className="admin-related-content-filters admin-saved-post-filters"
                        role="group"
                        aria-label="게시물 출처 필터"
                      >
                        {(['ALL', 'X', 'THREADS', 'YOUTUBE'] as const).map((source) => (
                          <button
                            className="admin-related-content-filter"
                            type="button"
                            key={source}
                            aria-pressed={postFilter === source}
                            onClick={() => {
                              setPostFilter(source);
                              setExpandedPostId(null);
                            }}
                          >
                            {source === 'ALL' ? '전체' : platformLabel(source)}{' '}
                            <small>
                              {
                                selectedPerson.posts.filter(
                                  (post) => source === 'ALL' || post.sourceType === source,
                                ).length
                              }
                            </small>
                          </button>
                        ))}
                      </div>
                      <ul className="admin-saved-post-list">
                        {visiblePosts.map((post) => (
                          <li key={post.id}>
                            <div>
                              <span>{platformLabel(post.sourceType)}</span>
                              <strong>{post.title}</strong>
                              <a href={post.url} target="_blank" rel="noreferrer">
                                {post.url}
                              </a>
                            </div>
                            <div className="admin-saved-post-actions">
                              <button
                                className="admin-secondary-button"
                                type="button"
                                aria-expanded={expandedPostId === post.id}
                                onClick={() =>
                                  setExpandedPostId(expandedPostId === post.id ? null : post.id)
                                }
                              >
                                미리보기
                              </button>
                              <button
                                className="admin-person-delete"
                                type="button"
                                disabled={busy}
                                aria-label={`${post.title} 삭제`}
                                onClick={() => void removePost(post.id)}
                              >
                                삭제
                              </button>
                            </div>
                            {expandedPostId === post.id ? (
                              <div className="admin-saved-post-preview">
                                <SocialPostEmbed post={post} />
                              </div>
                            ) : null}
                          </li>
                        ))}
                      </ul>
                      {visiblePosts.length === 0 ? (
                        <div className="admin-empty-state">
                          <strong>조건에 맞는 게시물이 없습니다.</strong>
                          <button
                            className="admin-secondary-button"
                            type="button"
                            onClick={() => {
                              setPostQuery('');
                              setPostFilter('ALL');
                            }}
                          >
                            검색·필터 초기화
                          </button>
                        </div>
                      ) : null}
                    </>
                  ) : (
                    <div className="admin-empty-state">
                      <strong>등록된 게시물이 없습니다.</strong>
                      <p>{selectedPerson.name}의 첫 게시물을 연결해 보세요.</p>
                      <button
                        type="button"
                        className="admin-secondary-button"
                        onClick={() => setWorkspace('add')}
                      >
                        첫 게시물 추가
                      </button>
                    </div>
                  )}
                </section>
              ) : null}
            </div>
          ) : null}
        </div>
      ) : null}

      {!loading && people.length === 0 ? (
        <p className="admin-people-state">먼저 주요 인물을 추가해주세요.</p>
      ) : null}

      {error ? (
        <div className="admin-alert admin-operation-notice" role="alert">
          <strong>{error}</strong>
          <button type="button" onClick={() => setError('')} aria-label="오류 안내 닫기">
            닫기
          </button>
        </div>
      ) : null}
      {notice && !error ? (
        <div className="admin-operation-notice admin-operation-success" role="status">
          <span>{notice}</span>
          {workspace === 'add' ? (
            <button type="button" onClick={() => setWorkspace('posts')}>
              등록된 게시물 보기
            </button>
          ) : null}
          <button type="button" onClick={() => setNotice('')} aria-label="완료 안내 닫기">
            닫기
          </button>
        </div>
      ) : null}
    </main>
  );
}
