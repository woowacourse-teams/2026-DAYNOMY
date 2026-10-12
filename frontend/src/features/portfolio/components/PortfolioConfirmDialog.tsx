import { useState, type ReactNode } from 'react';

export function PortfolioConfirmDialog({
  title,
  children,
  onClose,
  onConfirm,
}: {
  title: string;
  children: ReactNode;
  onClose: () => void;
  onConfirm: () => Promise<void>;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function confirm() {
    if (busy) return;
    setBusy(true);
    setError('');
    try {
      await onConfirm();
    } catch (caught) {
      setError(
        caught instanceof Error ? caught.message : '삭제하지 못했습니다. 다시 시도해 주세요.',
      );
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <header className="portfolio-sheet-heading">
        <h2 id="portfolio-confirm-title">{title}</h2>
        <button
          autoFocus
          type="button"
          className="portfolio-icon-button"
          aria-label="닫기"
          disabled={busy}
          onClick={onClose}
        >
          ×
        </button>
      </header>
      <div className="portfolio-confirm-copy">{children}</div>
      {error && (
        <p role="alert" className="portfolio-form-error">
          {error}
        </p>
      )}
      <footer className="portfolio-sheet-footer">
        <button
          type="button"
          className="portfolio-secondary-button"
          onClick={onClose}
          disabled={busy}
        >
          취소
        </button>
        <button
          type="button"
          className="portfolio-danger-button"
          onClick={() => void confirm()}
          disabled={busy}
        >
          {busy ? '처리 중…' : title}
        </button>
      </footer>
    </>
  );
}
