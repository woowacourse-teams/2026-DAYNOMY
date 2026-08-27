import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import './SearchOverlay.css';

type SearchOverlayProps = {
  open: boolean;
  onClose: () => void;
};

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
  const navigate = useNavigate();

  function closeDialog() {
    dialogRef.current?.close();
  }

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;

    if (open && !dialog.open) {
      setQuery('');
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

            navigate(`/search?${new URLSearchParams({ q: keyword, category: 'ALL', page: '1' })}`);
            closeDialog();
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
          <p>최근 검색어가 없습니다.</p>
        </section>
      </div>
    </dialog>
  );
}
