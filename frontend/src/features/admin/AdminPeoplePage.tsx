import { memo, useCallback, useEffect, useLayoutEffect, useMemo, useState } from 'react';
import { ApiError } from '../../api/client';
import { searchWikimediaImages } from './api';
import type { AdminWikimediaImageCandidate } from './types';
import {
  approveAllFeaturedPersonPostCandidates,
  approveFeaturedPersonPostCandidate,
  approveFeaturedPersonPostCandidates,
  collectFeaturedPersonPosts,
  createFeaturedPerson,
  createFeaturedPersonPost,
  deleteFeaturedPerson,
  deleteFeaturedPersonPost,
  getAdminFeaturedPeople,
  getAllFeaturedPersonPostCandidates,
  getFeaturedPersonPostCandidates,
  rejectAllFeaturedPersonPostCandidates,
  rejectFeaturedPersonPostCandidate,
  rejectFeaturedPersonPostCandidates,
  updateFeaturedPerson,
} from '../people/api';
import { SocialPostEmbed } from '../people/SocialPostEmbed';
import {
  platformLabel,
  type FeaturedPerson,
  type FeaturedPersonPostCandidate,
  type FeaturedPersonPostCandidateInboxItem,
  type SocialPlatform,
} from '../people/types';
import '../people/people.css';

function getErrorMessage(error: unknown, fallback: string) {
  return error instanceof ApiError || error instanceof Error ? error.message : fallback;
}

function emptyToNull(value: string) {
  const trimmed = value.trim();
  return trimmed || null;
}

function dateInputValue(daysAgo = 0) {
  const date = new Date();
  date.setDate(date.getDate() - daysAgo);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function collectionPeriodDays(from: string, to: string) {
  return Math.floor(
    (Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86_400_000 + 1,
  );
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

function PersonCreateForm({
  busy,
  onCreate,
}: {
  busy: boolean;
  onCreate: (input: {
    name: string;
    xProfileUrl: string;
    threadsProfileUrl: string;
    youtubeProfileUrl: string;
  }) => Promise<boolean>;
}) {
  const [newName, setNewName] = useState('');
  const [newXProfileUrl, setNewXProfileUrl] = useState('');
  const [newThreadsProfileUrl, setNewThreadsProfileUrl] = useState('');
  const [newYoutubeProfileUrl, setNewYoutubeProfileUrl] = useState('');
  return (
    <form
      className="admin-person-create-form"
      onSubmit={async (event) => {
        event.preventDefault();
        if (
          await onCreate({
            name: newName,
            xProfileUrl: newXProfileUrl,
            threadsProfileUrl: newThreadsProfileUrl,
            youtubeProfileUrl: newYoutubeProfileUrl,
          })
        ) {
          setNewName('');
          setNewXProfileUrl('');
          setNewThreadsProfileUrl('');
          setNewYoutubeProfileUrl('');
        }
      }}
    >
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
  );
}

const CandidateInboxRow = memo(function CandidateInboxRow({
  candidate,
  selected,
  previewed,
  busy,
  onSelect,
  onPreview,
  onProcess,
}: {
  candidate: FeaturedPersonPostCandidateInboxItem;
  selected: boolean;
  previewed: boolean;
  busy: boolean;
  onSelect: (id: number) => void;
  onPreview: (url: string | null) => void;
  onProcess: (
    action: 'approve' | 'reject',
    candidateIds: number[],
    confirmBulk?: boolean,
  ) => Promise<void>;
}) {
  return (
    <li>
      <div className="admin-candidate-person">
        <label className="admin-candidate-select">
          <input
            type="checkbox"
            aria-label={`${candidate.personName} ${candidate.title} 선택`}
            checked={selected}
            disabled={busy}
            onChange={() => onSelect(candidate.id)}
          />
        </label>
        <span className="admin-person-avatar" aria-hidden="true">
          {candidate.personProfileImageUrl ? (
            <img src={candidate.personProfileImageUrl} alt="" />
          ) : (
            '인'
          )}
        </span>
        <div>
          <strong>{candidate.personName}</strong>
          <span>
            {platformLabel(candidate.sourceType)}
            {candidate.publishedAt ? ` · ${candidate.publishedAt.slice(0, 10)}` : ''}
          </span>
        </div>
      </div>
      <strong className="admin-result-title">{candidate.title}</strong>
      <div className="admin-post-result-actions">
        <a className="admin-source-link" href={candidate.url} target="_blank" rel="noreferrer">
          원문 보기
        </a>
        <button
          className="admin-secondary-button"
          type="button"
          aria-expanded={previewed}
          onClick={() => onPreview(previewed ? null : candidate.url)}
        >
          미리보기
        </button>
        <button
          className="admin-primary-button"
          type="button"
          disabled={busy}
          onClick={() => void onProcess('approve', [candidate.id], false)}
        >
          승인
        </button>
        <button
          className="admin-secondary-button"
          type="button"
          disabled={busy}
          onClick={() => void onProcess('reject', [candidate.id], false)}
        >
          거절
        </button>
      </div>
      {previewed ? (
        <div className="admin-candidate-inbox-preview">
          <SocialPostEmbed post={candidate} />
        </div>
      ) : null}
    </li>
  );
});

export function AdminPeoplePage() {
  const [pageMode, setPageMode] = useState<'people' | 'candidates'>('candidates');
  const [people, setPeople] = useState<FeaturedPerson[]>([]);
  const [selectedPersonId, setSelectedPersonId] = useState<number | null>(null);
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
  const [searchResults, setSearchResults] = useState<FeaturedPersonPostCandidate[] | null>(null);
  const [selectedCandidateIds, setSelectedCandidateIds] = useState<number[]>([]);
  const [collectionFrom, setCollectionFrom] = useState(() => dateInputValue(2));
  const [collectionTo, setCollectionTo] = useState(() => dateInputValue());
  const [searching, setSearching] = useState(false);
  const [registeringUrl, setRegisteringUrl] = useState('');
  const [bulkAction, setBulkAction] = useState<'approve' | 'reject' | null>(null);
  const [globalCandidates, setGlobalCandidates] = useState<FeaturedPersonPostCandidateInboxItem[]>(
    [],
  );
  const [globalCandidateTotal, setGlobalCandidateTotal] = useState<number | null>(null);
  const [globalFilteredTotal, setGlobalFilteredTotal] = useState(0);
  const [globalTotalPages, setGlobalTotalPages] = useState(0);
  const [globalPage, setGlobalPage] = useState(1);
  const [globalPersonFilter, setGlobalPersonFilter] = useState('ALL');
  const [globalSourceFilter, setGlobalSourceFilter] = useState<SocialPlatform | 'ALL'>('ALL');
  const [globalSelectedIds, setGlobalSelectedIds] = useState<number[]>([]);
  const [globalBulkAction, setGlobalBulkAction] = useState<'approve' | 'reject' | null>(null);
  const [globalLoading, setGlobalLoading] = useState(false);
  const [globalRefreshKey, setGlobalRefreshKey] = useState(0);
  const [globalPreviewedResult, setGlobalPreviewedResult] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [personQuery, setPersonQuery] = useState('');
  const [workspace, setWorkspace] = useState<'posts' | 'candidates' | 'add' | 'profile'>('posts');
  const [previewedResult, setPreviewedResult] = useState<string | null>(null);
  const [postQuery, setPostQuery] = useState('');
  const [postFilter, setPostFilter] = useState<SocialPlatform | 'ALL'>('ALL');
  const [expandedPostId, setExpandedPostId] = useState<number | null>(null);
  const busy =
    saving ||
    searching ||
    searchingImages ||
    Boolean(registeringUrl) ||
    bulkAction !== null ||
    globalBulkAction !== null;

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

  useEffect(() => {
    const controller = new AbortController();
    getAllFeaturedPersonPostCandidates({ page: 1, size: 1 }, controller.signal)
      .then((response) => {
        if (!controller.signal.aborted) setGlobalCandidateTotal(response.totalElements);
      })
      .catch((caughtError) => {
        if (!controller.signal.aborted) {
          setError(getErrorMessage(caughtError, '전체 수집 후보 수를 불러오지 못했습니다.'));
        }
      });
    return () => controller.abort();
  }, [globalRefreshKey]);

  useEffect(() => {
    if (pageMode !== 'candidates') return;
    const controller = new AbortController();
    setGlobalLoading(true);
    getAllFeaturedPersonPostCandidates(
      {
        page: globalPage,
        size: 20,
        personId: globalPersonFilter === 'ALL' ? undefined : Number(globalPersonFilter),
        sourceType: globalSourceFilter === 'ALL' ? undefined : globalSourceFilter,
      },
      controller.signal,
    )
      .then((response) => {
        if (controller.signal.aborted) return;
        setGlobalCandidates(response.candidates);
        setGlobalFilteredTotal(response.totalElements);
        setGlobalTotalPages(response.totalPages);
        setGlobalSelectedIds((current) =>
          current.filter((id) => response.candidates.some((candidate) => candidate.id === id)),
        );
      })
      .catch((caughtError) => {
        if (!controller.signal.aborted) {
          setError(getErrorMessage(caughtError, '전체 수집 후보를 불러오지 못했습니다.'));
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setGlobalLoading(false);
      });
    return () => controller.abort();
  }, [globalPage, globalPersonFilter, globalRefreshKey, globalSourceFilter, pageMode]);

  const selectedPerson = useMemo(
    () => people.find((person) => person.id === selectedPersonId) ?? null,
    [people, selectedPersonId],
  );

  useEffect(() => {
    if (selectedPersonId === null) return;
    const controller = new AbortController();
    getFeaturedPersonPostCandidates(selectedPersonId, controller.signal)
      .then((candidates) => {
        if (!controller.signal.aborted) {
          setSearchResults(candidates);
          setSelectedCandidateIds([]);
        }
      })
      .catch((caughtError) => {
        if (!controller.signal.aborted) {
          setError(getErrorMessage(caughtError, '승인 대기 게시물을 불러오지 못했습니다.'));
        }
      });
    return () => controller.abort();
  }, [selectedPersonId]);
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

  async function submitPerson(input: {
    name: string;
    xProfileUrl: string;
    threadsProfileUrl: string;
    youtubeProfileUrl: string;
  }) {
    if (!input.name.trim() || busy || !confirmDiscard()) return false;
    setSaving(true);
    setError('');
    setNotice('');
    try {
      const person = await createFeaturedPerson({
        name: input.name.trim(),
        xProfileUrl: emptyToNull(input.xProfileUrl),
        threadsProfileUrl: emptyToNull(input.threadsProfileUrl),
        youtubeProfileUrl: emptyToNull(input.youtubeProfileUrl),
      });
      setPeople((current) => [...current, person]);
      setPersonQuery('');
      setSelectedPersonId(person.id);
      setNotice(`${person.name}을(를) 등록했습니다.`);
      return true;
    } catch (caughtError) {
      setError(getErrorMessage(caughtError, '주요 인물을 등록하지 못했습니다.'));
      return false;
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
    if (
      !collectionFrom ||
      !collectionTo ||
      collectionPeriodDays(collectionFrom, collectionTo) < 1
    ) {
      setError('수집 시작일은 종료일보다 늦을 수 없습니다.');
      return;
    }
    if (collectionPeriodDays(collectionFrom, collectionTo) > 31) {
      setError('수집 기간은 최대 31일까지 선택할 수 있습니다.');
      return;
    }
    setSearching(true);
    setError('');
    setNotice('');
    try {
      setSearchResults(
        await collectFeaturedPersonPosts(selectedPerson.id, collectionFrom, collectionTo),
      );
      setSelectedCandidateIds([]);
      setGlobalRefreshKey((current) => current + 1);
      setNotice('공식 프로필에서 게시물을 수집했습니다. 확인 후 승인해 주세요.');
    } catch (caughtError) {
      setError(getErrorMessage(caughtError, '공식 프로필 게시물을 수집하지 못했습니다.'));
    } finally {
      setSearching(false);
    }
  }

  async function registerSearchResult(result: FeaturedPersonPostCandidate) {
    if (!selectedPerson || busy) return;
    setRegisteringUrl(result.url);
    setError('');
    setNotice('');
    try {
      const post = await approveFeaturedPersonPostCandidate(selectedPerson.id, result.id);
      setPeople((current) =>
        current.map((person) =>
          person.id === selectedPerson.id ? { ...person, posts: [post, ...person.posts] } : person,
        ),
      );
      setSearchResults(
        (current) => current?.filter((candidate) => candidate.id !== result.id) ?? [],
      );
      setSelectedCandidateIds((current) => current.filter((id) => id !== result.id));
      setGlobalRefreshKey((current) => current + 1);
      setNotice('게시물을 승인했습니다. 등록된 게시물에서 확인할 수 있습니다.');
    } catch (caughtError) {
      setError(getErrorMessage(caughtError, '수집한 게시물을 승인하지 못했습니다.'));
    } finally {
      setRegisteringUrl('');
    }
  }

  async function rejectSearchResult(result: FeaturedPersonPostCandidate) {
    if (!selectedPerson || busy) return;
    setRegisteringUrl(result.url);
    setError('');
    setNotice('');
    try {
      await rejectFeaturedPersonPostCandidate(selectedPerson.id, result.id);
      setSearchResults(
        (current) => current?.filter((candidate) => candidate.id !== result.id) ?? [],
      );
      setSelectedCandidateIds((current) => current.filter((id) => id !== result.id));
      setGlobalRefreshKey((current) => current + 1);
      setNotice('게시물 후보를 거절했습니다.');
    } catch (caughtError) {
      setError(getErrorMessage(caughtError, '수집한 게시물을 거절하지 못했습니다.'));
    } finally {
      setRegisteringUrl('');
    }
  }

  function toggleCandidateSelection(candidateId: number) {
    setSelectedCandidateIds((current) =>
      current.includes(candidateId)
        ? current.filter((id) => id !== candidateId)
        : [...current, candidateId],
    );
  }

  function toggleAllCandidates() {
    const candidateIds = searchResults?.map((candidate) => candidate.id) ?? [];
    setSelectedCandidateIds(
      candidateIds.length > 0 && candidateIds.every((id) => selectedCandidateIds.includes(id))
        ? []
        : candidateIds,
    );
  }

  async function processSelectedCandidates(action: 'approve' | 'reject') {
    if (!selectedPerson || busy || selectedCandidateIds.length === 0) return;
    const label = action === 'approve' ? '승인' : '거절';
    if (!window.confirm(`선택한 ${selectedCandidateIds.length}건을 ${label}할까요?`)) return;

    setBulkAction(action);
    setError('');
    setNotice('');
    try {
      const response =
        action === 'approve'
          ? await approveFeaturedPersonPostCandidates(selectedPerson.id, selectedCandidateIds)
          : await rejectFeaturedPersonPostCandidates(selectedPerson.id, selectedCandidateIds);
      const succeededIds = new Set(response.succeeded.map((item) => item.candidateId));
      const failedIds = response.failed.map((item) => item.candidateId);
      const approvedPosts = response.succeeded.flatMap((item) => (item.post ? [item.post] : []));

      setSearchResults(
        (current) => current?.filter((candidate) => !succeededIds.has(candidate.id)) ?? [],
      );
      setSelectedCandidateIds(failedIds);
      if (approvedPosts.length > 0) {
        setPeople((current) =>
          current.map((person) =>
            person.id === selectedPerson.id
              ? { ...person, posts: [...approvedPosts, ...person.posts] }
              : person,
          ),
        );
      }
      setNotice(`${response.succeeded.length}건을 ${label}했습니다.`);
      if (response.failed.length > 0) {
        setError(
          `${response.failed.length}건을 처리하지 못했습니다. 실패한 항목은 선택 상태로 남겨두었습니다.`,
        );
      }
      if (response.succeeded.length > 0) setGlobalRefreshKey((current) => current + 1);
    } catch (caughtError) {
      setError(getErrorMessage(caughtError, `선택한 게시물을 일괄 ${label}하지 못했습니다.`));
    } finally {
      setBulkAction(null);
    }
  }

  const toggleGlobalCandidateSelection = useCallback((candidateId: number) => {
    setGlobalSelectedIds((current) =>
      current.includes(candidateId)
        ? current.filter((id) => id !== candidateId)
        : [...current, candidateId],
    );
  }, []);

  function toggleAllGlobalCandidates() {
    const candidateIds = globalCandidates.map((candidate) => candidate.id);
    setGlobalSelectedIds(
      candidateIds.length > 0 && candidateIds.every((id) => globalSelectedIds.includes(id))
        ? []
        : candidateIds,
    );
  }

  const processGlobalCandidates = useCallback(
    async (action: 'approve' | 'reject', candidateIds: number[], confirmBulk = true) => {
      if (busy || candidateIds.length === 0) return;
      const label = action === 'approve' ? '승인' : '거절';
      if (confirmBulk && !window.confirm(`선택한 ${candidateIds.length}건을 ${label}할까요?`))
        return;

      setGlobalBulkAction(action);
      setError('');
      setNotice('');
      try {
        const candidateById = new Map(
          globalCandidates.map((candidate) => [candidate.id, candidate]),
        );
        const response =
          action === 'approve'
            ? await approveAllFeaturedPersonPostCandidates(candidateIds)
            : await rejectAllFeaturedPersonPostCandidates(candidateIds);
        const succeededIds = new Set(response.succeeded.map((item) => item.candidateId));
        const failedIds = response.failed.map((item) => item.candidateId);

        setGlobalCandidates((current) =>
          current.filter((candidate) => !succeededIds.has(candidate.id)),
        );
        setGlobalSelectedIds(failedIds);
        setSearchResults(
          (current) => current?.filter((candidate) => !succeededIds.has(candidate.id)) ?? current,
        );
        setPeople((current) =>
          current.map((person) => {
            const approvedPosts = response.succeeded.flatMap((item) => {
              const candidate = candidateById.get(item.candidateId);
              return item.post && candidate?.personId === person.id ? [item.post] : [];
            });
            return approvedPosts.length > 0
              ? { ...person, posts: [...approvedPosts, ...person.posts] }
              : person;
          }),
        );
        setNotice(`${response.succeeded.length}건을 ${label}했습니다.`);
        if (response.failed.length > 0) {
          setError(
            `${response.failed.length}건을 처리하지 못했습니다. 실패한 항목은 선택 상태로 남겨두었습니다.`,
          );
        }
        if (response.succeeded.length > 0) {
          const remainingOnPage = globalCandidates.length - response.succeeded.length;
          if (remainingOnPage === 0 && globalPage > 1) setGlobalPage((current) => current - 1);
          else setGlobalRefreshKey((current) => current + 1);
        }
      } catch (caughtError) {
        setError(getErrorMessage(caughtError, `선택한 게시물을 일괄 ${label}하지 못했습니다.`));
      } finally {
        setGlobalBulkAction(null);
      }
    },
    [busy, globalCandidates, globalPage],
  );

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
          <p>공식 프로필에서 수집한 게시물을 검토하거나 URL을 직접 등록합니다.</p>
        </div>
      </div>

      <div className="admin-people-mode-switch" role="group" aria-label="인물 관리 화면 선택">
        <button
          type="button"
          className={globalCandidateTotal && globalCandidateTotal > 0 ? 'has-pending' : undefined}
          aria-pressed={pageMode === 'candidates'}
          onClick={() => setPageMode('candidates')}
        >
          전체 수집 후보 <span>{globalCandidateTotal ?? '…'}</span>
        </button>
        <button
          type="button"
          aria-pressed={pageMode === 'people'}
          onClick={() => setPageMode('people')}
        >
          인물별 관리
        </button>
      </div>

      <details className="admin-create-disclosure" hidden={pageMode !== 'people'}>
        <summary>새 인물 등록</summary>
        <PersonCreateForm busy={busy} onCreate={submitPerson} />
      </details>

      {pageMode === 'people' && loading ? (
        <p className="admin-people-state" aria-busy="true">
          불러오는 중입니다.
        </p>
      ) : null}

      {pageMode === 'people' && !loading && people.length > 0 ? (
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
                        : workspace === 'candidates'
                          ? '공식 프로필에서 수집한 게시물을 검토하세요.'
                          : workspace === 'add'
                            ? '게시물 URL을 직접 등록하세요.'
                            : '등록된 게시물을 확인하고 관리하세요.'}
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
                  className={searchResults && searchResults.length > 0 ? 'has-pending' : undefined}
                  aria-pressed={workspace === 'candidates'}
                  onClick={() => setWorkspace('candidates')}
                >
                  수집 후보 <span>{searchResults?.length ?? '…'}</span>
                </button>
                <button
                  type="button"
                  aria-pressed={workspace === 'add'}
                  onClick={() => setWorkspace('add')}
                >
                  게시물 직접 등록
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

              {workspace === 'add' || workspace === 'candidates' ? (
                <section className="admin-person-post-section" aria-labelledby="person-post-title">
                  <div className="admin-person-section-heading">
                    <div>
                      <h2 id="person-post-title">
                        {workspace === 'add' ? '게시물 URL 붙여넣기' : '승인 대기 게시물'}
                      </h2>
                      <p>
                        {workspace === 'add'
                          ? `${selectedPerson.name}에게 연결할 게시물 주소를 입력하세요.`
                          : '공식 프로필에서 수집한 게시물을 확인하고 승인하거나 거절하세요.'}
                      </p>
                    </div>
                    {workspace === 'candidates' ? (
                      <div className="admin-collection-controls">
                        <label className="admin-field">
                          <span>수집 시작일</span>
                          <input
                            type="date"
                            value={collectionFrom}
                            max={collectionTo}
                            disabled={busy}
                            onChange={(event) => setCollectionFrom(event.target.value)}
                          />
                        </label>
                        <label className="admin-field">
                          <span>수집 종료일</span>
                          <input
                            type="date"
                            value={collectionTo}
                            min={collectionFrom}
                            disabled={busy}
                            onChange={(event) => setCollectionTo(event.target.value)}
                          />
                        </label>
                        <button
                          className="admin-secondary-button"
                          type="button"
                          disabled={busy}
                          onClick={() => void searchPersonPosts()}
                        >
                          {searching ? '수집 중…' : '공식 프로필 지금 수집'}
                        </button>
                      </div>
                    ) : null}
                  </div>
                  {workspace === 'add' ? (
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
                  {workspace === 'candidates' && searchResults === null ? (
                    <p className="admin-people-state">승인 대기 게시물을 불러오는 중…</p>
                  ) : null}
                  {workspace === 'candidates' && searchResults ? (
                    <div className="admin-person-search-results" aria-live="polite">
                      <div className="admin-person-search-results-heading">
                        <div>
                          <h3>승인 대기</h3>
                          <span>{searchResults.length}건</span>
                        </div>
                        {searchResults.length > 0 ? (
                          <div className="admin-candidate-bulk-actions">
                            <label>
                              <input
                                type="checkbox"
                                checked={searchResults.every((candidate) =>
                                  selectedCandidateIds.includes(candidate.id),
                                )}
                                disabled={busy}
                                onChange={toggleAllCandidates}
                              />
                              현재 목록 전체 선택
                            </label>
                            <button
                              className="admin-primary-button"
                              type="button"
                              disabled={busy || selectedCandidateIds.length === 0}
                              onClick={() => void processSelectedCandidates('approve')}
                            >
                              {bulkAction === 'approve'
                                ? '일괄 승인 중…'
                                : `선택 승인 (${selectedCandidateIds.length})`}
                            </button>
                            <button
                              className="admin-secondary-button"
                              type="button"
                              disabled={busy || selectedCandidateIds.length === 0}
                              onClick={() => void processSelectedCandidates('reject')}
                            >
                              {bulkAction === 'reject'
                                ? '일괄 거절 중…'
                                : `선택 거절 (${selectedCandidateIds.length})`}
                            </button>
                          </div>
                        ) : null}
                      </div>
                      {searchResults.length > 0 ? (
                        <ul>
                          {searchResults.map((result) => {
                            return (
                              <li key={result.id}>
                                <div className="admin-person-search-result-meta">
                                  <div>
                                    <label className="admin-candidate-select">
                                      <input
                                        type="checkbox"
                                        aria-label={`${result.title} 선택`}
                                        checked={selectedCandidateIds.includes(result.id)}
                                        disabled={busy}
                                        onChange={() => toggleCandidateSelection(result.id)}
                                      />
                                    </label>
                                    <strong>{platformLabel(result.sourceType)}</strong>
                                  </div>
                                  {result.publishedAt ? (
                                    <span>{result.publishedAt.slice(0, 10)}</span>
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
                                    disabled={busy}
                                    onClick={() => void registerSearchResult(result)}
                                  >
                                    {registeringUrl === result.url ? '처리 중…' : '승인'}
                                  </button>
                                  <button
                                    className="admin-secondary-button"
                                    type="button"
                                    disabled={busy}
                                    onClick={() => void rejectSearchResult(result)}
                                  >
                                    거절
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
                        <p className="admin-people-state">승인 대기 중인 게시물이 없습니다.</p>
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
                          게시물 직접 등록
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
                        첫 게시물 직접 등록
                      </button>
                    </div>
                  )}
                </section>
              ) : null}
            </div>
          ) : null}
        </div>
      ) : null}

      {pageMode === 'candidates' ? (
        <section className="admin-candidate-inbox" aria-labelledby="candidate-inbox-title">
          <div className="admin-candidate-inbox-heading">
            <div>
              <h2 id="candidate-inbox-title">전체 수집 후보</h2>
              <p>인물을 오가지 않고 모든 승인 대기 게시물을 한곳에서 검토합니다.</p>
            </div>
            <strong>{globalFilteredTotal}건</strong>
          </div>

          <div className="admin-candidate-inbox-tools">
            <label className="admin-field">
              <span>인물</span>
              <select
                value={globalPersonFilter}
                disabled={busy}
                onChange={(event) => {
                  setGlobalPersonFilter(event.target.value);
                  setGlobalPage(1);
                  setGlobalSelectedIds([]);
                }}
              >
                <option value="ALL">전체 인물</option>
                {people.map((person) => (
                  <option key={person.id} value={person.id}>
                    {person.name}
                  </option>
                ))}
              </select>
            </label>
            <label className="admin-field">
              <span>출처</span>
              <select
                value={globalSourceFilter}
                disabled={busy}
                onChange={(event) => {
                  setGlobalSourceFilter(event.target.value as SocialPlatform | 'ALL');
                  setGlobalPage(1);
                  setGlobalSelectedIds([]);
                }}
              >
                <option value="ALL">전체 출처</option>
                <option value="X">X</option>
                <option value="THREADS">Threads</option>
                <option value="YOUTUBE">YouTube</option>
              </select>
            </label>
          </div>

          {globalCandidates.length > 0 ? (
            <div className="admin-candidate-inbox-bulk">
              <label>
                <input
                  type="checkbox"
                  checked={globalCandidates.every((candidate) =>
                    globalSelectedIds.includes(candidate.id),
                  )}
                  disabled={busy}
                  onChange={toggleAllGlobalCandidates}
                />
                현재 페이지 전체 선택
              </label>
              <button
                className="admin-primary-button"
                type="button"
                disabled={busy || globalSelectedIds.length === 0}
                onClick={() => void processGlobalCandidates('approve', globalSelectedIds)}
              >
                {globalBulkAction === 'approve'
                  ? '일괄 승인 중…'
                  : `선택 승인 (${globalSelectedIds.length})`}
              </button>
              <button
                className="admin-secondary-button"
                type="button"
                disabled={busy || globalSelectedIds.length === 0}
                onClick={() => void processGlobalCandidates('reject', globalSelectedIds)}
              >
                {globalBulkAction === 'reject'
                  ? '일괄 거절 중…'
                  : `선택 거절 (${globalSelectedIds.length})`}
              </button>
            </div>
          ) : null}

          {globalLoading ? (
            <p className="admin-people-state" aria-busy="true">
              수집 후보를 불러오는 중…
            </p>
          ) : globalCandidates.length > 0 ? (
            <ul className="admin-candidate-inbox-list" aria-live="polite">
              {globalCandidates.map((candidate) => (
                <CandidateInboxRow
                  key={candidate.id}
                  candidate={candidate}
                  selected={globalSelectedIds.includes(candidate.id)}
                  previewed={globalPreviewedResult === candidate.url}
                  busy={busy}
                  onSelect={toggleGlobalCandidateSelection}
                  onPreview={setGlobalPreviewedResult}
                  onProcess={processGlobalCandidates}
                />
              ))}
            </ul>
          ) : (
            <div className="admin-empty-state">
              <strong>조건에 맞는 승인 대기 게시물이 없습니다.</strong>
              <p>새 후보가 수집되면 이 목록에 표시됩니다.</p>
            </div>
          )}

          {!globalLoading && globalTotalPages > 1 ? (
            <nav className="admin-candidate-pagination" aria-label="전체 수집 후보 페이지">
              <button
                className="admin-secondary-button"
                type="button"
                disabled={busy || globalPage <= 1}
                onClick={() => {
                  setGlobalSelectedIds([]);
                  setGlobalPage((current) => current - 1);
                }}
              >
                이전
              </button>
              <span>
                {globalPage} / {globalTotalPages}
              </span>
              <button
                className="admin-secondary-button"
                type="button"
                disabled={busy || globalPage >= globalTotalPages}
                onClick={() => {
                  setGlobalSelectedIds([]);
                  setGlobalPage((current) => current + 1);
                }}
              >
                다음
              </button>
            </nav>
          ) : null}
        </section>
      ) : null}

      {pageMode === 'people' && !loading && people.length === 0 ? (
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
          {pageMode === 'people' && (workspace === 'add' || workspace === 'candidates') ? (
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
