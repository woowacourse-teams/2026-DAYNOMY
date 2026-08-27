import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import './SearchOverlay.css';

type SearchOverlayProps = {
  open: boolean;
  onClose: () => void;
};

const RECENT_SEARCHES_KEY = 'daynomy:recent-searches';

function loadRecentSearches() {
  try {
    const searches: unknown = JSON.parse(localStorage.getItem(RECENT_SEARCHES_KEY) ?? '[]');
    return Array.isArray(searches)
      ? searches.filter((search): search is string => typeof search === 'string').slice(0, 3)
      : [];
  } catch {
    return [];
  }
}

function SearchIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="11" cy="11" r="6.5" />
      <path d="m16 16 4 4" />
    </svg>
  );
}

export function SearchOverlay({ open, onClose }: SearchOverlayProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const [query, setQuery] = useState('');
  const [recentSearches, setRecentSearches] = useState<string[]>([]);
  const navigate = useNavigate();

  function closeDialog() {
    dialogRef.current?.close();
  }

  function search(keyword: string) {
    const nextRecentSearches = [
      keyword,
      ...recentSearches.filter((recentSearch) => recentSearch !== keyword),
    ].slice(0, 3);

    setRecentSearches(nextRecentSearches);
    try {
      localStorage.setItem(RECENT_SEARCHES_KEY, JSON.stringify(nextRecentSearches));
    } catch {
      // 검색 이동은 브라우저 저장소 사용 가능 여부와 무관하게 동작한다.
    }

    navigate(`/search?${new URLSearchParams({ q: keyword, category: 'ALL', page: '1' })}`);
    closeDialog();
  }

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;

    if (open && !dialog.open) {
      setQuery('');
      setRecentSearches(loadRecentSearches());
      dialog.showModal();
      inputRef.current?.focus();
    } else if (!open && dialog.open) {
      dialog.close();
    }
  }, [open]);

  return (
    <dialog
      ref={dialogRef}
      className="search-overlay"
      aria-label="통합 검색"
      onCancel={(event) => {
        event.preventDefault();
        closeDialog();
      }}
      onClose={onClose}
      onClick={(event) => {
        if (event.target === event.currentTarget) closeDialog();
      }}
    >
      <button
        type="button"
        className="search-overlay-close"
        aria-label="검색 닫기"
        onClick={closeDialog}
      >
        ×
      </button>

      <div className="search-overlay-content">
        <form
          className="search-overlay-form"
          onSubmit={(event) => {
            event.preventDefault();
            const keyword = query.trim();
            if (!keyword || keyword.length > 100 || !/[\p{L}\p{N}]/u.test(keyword)) return;

            search(keyword);
          }}
        >
          <SearchIcon />
          <label className="search-overlay-label" htmlFor="overlay-search-input">
            뉴스 또는 종목 검색
          </label>
          <input
            ref={inputRef}
            id="overlay-search-input"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="뉴스나 종목을 검색해보세요"
            autoComplete="off"
            required
            maxLength={100}
            pattern=".*[\p{L}\p{N}].*"
            title="문자 또는 숫자를 포함한 100자 이하의 검색어를 입력해주세요."
          />
        </form>

        <section className="recent-searches" aria-labelledby="recent-searches-title">
          <h2 id="recent-searches-title">최근 검색</h2>
          {recentSearches.length === 0 ? (
            <p>최근 검색어가 없습니다.</p>
          ) : (
            <ul>
              {recentSearches.map((recentSearch) => (
                <li key={recentSearch}>
                  <button type="button" onClick={() => search(recentSearch)}>
                    <SearchIcon />
                    <span>{recentSearch}</span>
                    <span aria-hidden="true">›</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </dialog>
  );
}
