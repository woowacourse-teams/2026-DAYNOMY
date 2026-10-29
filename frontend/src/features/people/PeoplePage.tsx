import { trackEvent } from '../../analytics';
import xMark from '../../assets/platform-x.svg';
import threadsMark from '../../assets/platform-threads.svg';
import { useContentView } from '../news/issue/analytics';
import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useFeaturedPeople } from './useFeaturedPeople';
import { peopleFeed } from '../news/issue/feed';
import { IssueFeed } from '../news/issue/IssueFeed';
import { IssuePagination } from '../news/issue/IssuePagination';
import './people.css';
import '../news/issue/issue.css';

function PeopleFinderSearch({
  people,
  selectedId,
  searchRef,
  onSelect,
  onClose,
}: {
  people: ReturnType<typeof useFeaturedPeople>['people'];
  selectedId: string | null;
  searchRef: React.RefObject<HTMLInputElement | null>;
  onSelect: (id: string) => void;
  onClose: () => void;
}) {
  const [search, setSearch] = useState('');
  const foundPeople = people.filter((person) =>
    person.name.toLocaleLowerCase().includes(search.trim().toLocaleLowerCase()),
  );
  return (
    <>
      <label className="sr-only" htmlFor="people-search">
        인물 이름 검색
      </label>
      <div className="people-finder-search">
        <input
          ref={searchRef}
          id="people-search"
          type="search"
          placeholder="이름으로 검색"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
        <button
          type="button"
          className="people-finder-close"
          aria-label="인물 찾기 닫기"
          onClick={onClose}
        >
          ×
        </button>
      </div>
      <p className="people-finder-label">전체 인물</p>
      <div className="people-search-results">
        {foundPeople.map((person) => (
          <button
            key={person.id}
            aria-pressed={selectedId === String(person.id)}
            onClick={() => {
              onSelect(String(person.id));
              onClose();
            }}
          >
            <span className="people-selector-avatar" aria-hidden="true">
              <span>{person.name.slice(0, 1)}</span>
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
            <span>{person.name}</span>
            <span className="people-result-arrow" aria-hidden="true">
              ›
            </span>
          </button>
        ))}
        {!foundPeople.length && <p role="status">검색한 인물이 없습니다.</p>}
      </div>
    </>
  );
}

export function PeoplePage() {
  const [params, setParams] = useSearchParams();
  const selectedId = params.get('person');
  const selectedNumber = selectedId === null ? undefined : Number(selectedId);
  const personId =
    selectedNumber !== undefined && Number.isSafeInteger(selectedNumber) && selectedNumber > 0
      ? selectedNumber
      : undefined;
  const [pagination, setPagination] = useState({ person: selectedId, page: 1 });
  // Reset before committing a view so a URL change cannot request the previous person's page.
  if (pagination.person !== selectedId) setPagination({ person: selectedId, page: 1 });
  const page = pagination.person === selectedId ? pagination.page : 1;
  const {
    people,
    loading,
    error,
    retry,
    totalElements,
    totalPages: responseTotalPages,
  } = useFeaturedPeople('people', page, 6, personId);
  const [finderOpen, setFinderOpen] = useState(false);
  const [finderLeft, setFinderLeft] = useState(0);
  const selectorRef = useRef<HTMLDivElement>(null);
  const peopleStripRef = useRef<HTMLElement>(null);
  const [scrollEdges, setScrollEdges] = useState({ previous: false, next: false });

  function updateScrollEdges() {
    const strip = peopleStripRef.current;
    if (!strip) return;
    const bar = strip.parentElement!;
    const gap = parseFloat(getComputedStyle(bar).columnGap) || 0;
    const arrowSpace = Array.from(bar.querySelectorAll<HTMLElement>('.people-scroll-arrow')).reduce(
      (space, arrow) => space + (arrow.offsetWidth ? arrow.offsetWidth + gap : 0),
      0,
    );
    // Measure the room available without arrows so resizing can hide them again.
    const overflows = strip.scrollWidth > strip.clientWidth + arrowSpace + 1;
    setScrollEdges({
      previous: overflows && strip.scrollLeft > 1,
      next: overflows && strip.scrollLeft + strip.clientWidth < strip.scrollWidth - 1,
    });
  }

  function scrollPeople(direction: number) {
    const strip = peopleStripRef.current;
    if (!strip) return;
    strip.scrollBy({
      left: direction * strip.clientWidth * 0.8,
      behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
    });
  }

  useEffect(() => {
    const strip = peopleStripRef.current;
    if (!strip) return;
    const observer = new ResizeObserver(updateScrollEdges);
    observer.observe(strip);
    updateScrollEdges();
    return () => observer.disconnect();
  }, [people.length]);

  useEffect(() => {
    const strip = peopleStripRef.current;
    const active = strip?.querySelector<HTMLElement>('[aria-pressed="true"]');
    if (!strip || !active) return;
    const bounds = strip.getBoundingClientRect();
    const item = active.getBoundingClientRect();
    if (item.left < bounds.left + 6) strip.scrollLeft += item.left - bounds.left - 6;
    else if (item.right > bounds.right - 6) strip.scrollLeft += item.right - bounds.right + 6;
    updateScrollEdges();
  }, [selectedId, people]);
  const finderRef = useRef<HTMLElement>(null);
  const finderButtonRef = useRef<HTMLButtonElement>(null);
  const searchRef = useRef<HTMLInputElement>(null);

  function closeFinder() {
    setFinderOpen(false);
    finderButtonRef.current?.focus();
  }

  useEffect(() => {
    if (!finderOpen) return;
    function positionFinder() {
      const container = selectorRef.current?.getBoundingClientRect();
      const button = finderButtonRef.current?.getBoundingClientRect();
      if (container && button) {
        setFinderLeft(Math.max(0, Math.min(button.left - container.left, container.width - 320)));
      }
    }
    function dismissOutside(event: Event) {
      const target = event.target as Node;
      if (!finderRef.current?.contains(target) && !finderButtonRef.current?.contains(target)) {
        setFinderOpen(false);
      }
    }
    function dismissEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        event.preventDefault();
        closeFinder();
      }
    }
    positionFinder();
    searchRef.current?.focus();
    document.addEventListener('pointerdown', dismissOutside);
    document.addEventListener('focusin', dismissOutside);
    document.addEventListener('keydown', dismissEscape);
    window.addEventListener('resize', positionFinder);
    window.addEventListener('scroll', positionFinder, true);
    return () => {
      document.removeEventListener('pointerdown', dismissOutside);
      document.removeEventListener('focusin', dismissOutside);
      document.removeEventListener('keydown', dismissEscape);
      window.removeEventListener('resize', positionFinder);
      window.removeEventListener('scroll', positionFinder, true);
    };
  }, [finderOpen]);

  const setPage = (next: number) => setPagination({ person: selectedId, page: next });
  const selected = people.find((person) => String(person.id) === selectedId);
  const items = useMemo(
    () =>
      peopleFeed(people.filter((person) => !selectedId || String(person.id) === selectedId)).sort(
        (a, b) =>
          (Date.parse(b.date ?? '') || 0) - (Date.parse(a.date ?? '') || 0) ||
          a.key.localeCompare(b.key),
      ),
    [people, selectedId],
  );
  function selectPerson(id: string) {
    if ((selectedId ?? '') === id) return;
    trackEvent('select_featured_person', {
      surface: 'people',
      person_id: id || 'all',
      trigger: 'button',
    });
    const next = new URLSearchParams(params);
    if (id) next.set('person', id);
    else next.delete('person');
    setParams(next);
    setPagination({ person: id || null, page: 1 });
  }
  const totalPages = Math.max(1, responseTotalPages);
  const currentPage = Math.min(page, totalPages);

  useContentView(!loading && !error, 'view_content_list', {
    surface: 'people',
    person_id: selected?.id ?? 'all',
    selection_valid: !selectedId || !!selected,
    result_count: totalElements,
    page_number: currentPage,
  });
  useContentView(
    !loading && !error && !items.length && (!selectedId || !!selected),
    'view_content_empty',
    { surface: 'people', reason: 'no_content' },
  );

  return (
    <main className="issue-page issue-people-page">
      <Link className="issue-back" to="/news">
        ← 소식 홈
      </Link>
      <header className="issue-heading issue-people-heading">
        <div>
          <h1>인물의 글</h1>
        </div>
      </header>
      <div className="people-selector-wrap" ref={selectorRef}>
        <div className="people-selector-bar">
          <button
            className="people-scroll-arrow"
            hidden={!scrollEdges.previous && !scrollEdges.next}
            aria-label="이전 인물 보기"
            aria-controls="people-strip"
            disabled={!scrollEdges.previous}
            onClick={() => scrollPeople(-1)}
          >
            ‹
          </button>
          <nav
            ref={peopleStripRef}
            id="people-strip"
            data-more-people={scrollEdges.next}
            className="people-selector"
            aria-label="인물 선택"
            onScroll={updateScrollEdges}
          >
            <button
              className="people-selector-item"
              aria-pressed={!selectedId}
              onClick={() => selectPerson('')}
            >
              <span className="people-selector-avatar people-selector-all" aria-hidden="true">
                <svg viewBox="0 0 24 24">
                  <circle cx="7" cy="7" r="3" />
                  <circle cx="17" cy="7" r="3" />
                  <circle cx="7" cy="17" r="3" />
                  <circle cx="17" cy="17" r="3" />
                </svg>
              </span>
              <span>전체</span>
            </button>
            {people.map((person) => (
              <button
                className="people-selector-item"
                key={person.id}
                aria-pressed={selectedId === String(person.id)}
                onClick={() => selectPerson(String(person.id))}
              >
                <span className="people-selector-avatar" aria-hidden="true">
                  <span>{person.name.slice(0, 1)}</span>
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
                <span>{person.name}</span>
              </button>
            ))}
          </nav>
          <button
            className="people-scroll-arrow"
            hidden={!scrollEdges.previous && !scrollEdges.next}
            aria-label="다음 인물 보기"
            aria-controls="people-strip"
            disabled={!scrollEdges.next}
            onClick={() => scrollPeople(1)}
          >
            ›
          </button>
          <button
            ref={finderButtonRef}
            className="people-selector-item people-find-link"
            aria-expanded={finderOpen}
            aria-controls="people-finder"
            onClick={() => setFinderOpen(!finderOpen)}
          >
            <span className="people-selector-avatar" aria-hidden="true">
              <svg viewBox="0 0 24 24">
                <circle cx="10.5" cy="10.5" r="6.5" />
                <path d="m16 16 5 5" />
              </svg>
            </span>
            <span>인물 찾기</span>
          </button>
        </div>
        <section
          ref={finderRef}
          style={{ left: finderLeft }}
          className="people-finder"
          id="people-finder"
          hidden={!finderOpen}
          aria-label="전체 인물 검색"
        >
          <PeopleFinderSearch
            people={people}
            selectedId={selectedId}
            searchRef={searchRef}
            onSelect={selectPerson}
            onClose={closeFinder}
          />
        </section>
      </div>
      {loading ? (
        <p className="issue-state" role="status">
          인물의 글을 불러오는 중입니다.
        </p>
      ) : error ? (
        <p className="issue-state" role="alert">
          {error}{' '}
          <button
            onClick={() => {
              trackEvent('retry_content_load', { surface: 'people', area: 'people' });
              retry();
            }}
          >
            다시 시도
          </button>
        </p>
      ) : (
        <>
          {(!selectedId || selected) && (
            <div className="people-feed-heading">
              <div className="people-feed-identity">
                <h2>{selected ? `${selected.name}의 글` : '전체 인물의 글'}</h2>
                {selected && (
                  <div className="issue-profile-links">
                    {selected.xProfileUrl && (
                      <a
                        href={selected.xProfileUrl}
                        target="_blank"
                        rel="noreferrer"
                        aria-label="X 프로필 보기 (새 탭)"
                      >
                        <img src={xMark} alt="X" />
                        <span className="people-profile-label">프로필 보기</span>
                        <span className="people-profile-platform">X</span>
                        <svg
                          viewBox="0 0 24 24"
                          aria-hidden="true"
                          fill="none"
                          stroke="currentColor"
                          strokeWidth="1.7"
                          strokeLinecap="round"
                          strokeLinejoin="round"
                        >
                          <path d="M7 17 17 7M7 7h10v10" />
                        </svg>
                      </a>
                    )}
                    {selected.threadsProfileUrl && (
                      <a
                        href={selected.threadsProfileUrl}
                        target="_blank"
                        rel="noreferrer"
                        aria-label="Threads 프로필 보기 (새 탭)"
                      >
                        <img src={threadsMark} alt="Threads" />
                        <span className="people-profile-label">프로필 보기</span>
                        <span className="people-profile-platform">Threads</span>
                        <svg
                          viewBox="0 0 24 24"
                          aria-hidden="true"
                          fill="none"
                          stroke="currentColor"
                          strokeWidth="1.7"
                          strokeLinecap="round"
                          strokeLinejoin="round"
                        >
                          <path d="M7 17 17 7M7 7h10v10" />
                        </svg>
                      </a>
                    )}
                    {selected.youtubeProfileUrl && (
                      <a
                        href={selected.youtubeProfileUrl}
                        target="_blank"
                        rel="noreferrer"
                        aria-label="YouTube 채널 보기 (새 탭)"
                      >
                        <svg viewBox="0 0 28 20" role="img" aria-label="YouTube">
                          <rect x="1" y="1" width="26" height="18" rx="5" fill="currentColor" />
                          <path d="m11 6 7 4-7 4z" fill="#0a0a0a" />
                        </svg>
                        <span className="people-profile-label">채널 보기</span>
                        <span className="people-profile-platform">YouTube</span>
                        <svg
                          viewBox="0 0 24 24"
                          aria-hidden="true"
                          fill="none"
                          stroke="currentColor"
                          strokeWidth="1.7"
                          strokeLinecap="round"
                          strokeLinejoin="round"
                        >
                          <path d="M7 17 17 7M7 7h10v10" />
                        </svg>
                      </a>
                    )}
                  </div>
                )}
              </div>
              {items.length > 0 && <span>최근 게시순</span>}
            </div>
          )}
          {selectedId && !selected ? (
            <p className="issue-state">
              해당 인물을 찾을 수 없습니다. <Link to="/people">전체 인물 보기</Link>
            </p>
          ) : items.length ? (
            <>
              <IssueFeed
                surface="people"
                key={`${selectedId ?? 'all'}-${currentPage}`}
                offset={(currentPage - 1) * 6}
                items={items}
              />
            </>
          ) : (
            <p className="issue-state">아직 등록된 게시물이 없습니다.</p>
          )}
          {totalElements > 0 && (
            <IssuePagination
              analytics={{ surface: 'people', person_id: selected?.id ?? 'all' }}
              page={currentPage}
              totalPages={totalPages}
              onChange={setPage}
            />
          )}
        </>
      )}
    </main>
  );
}
