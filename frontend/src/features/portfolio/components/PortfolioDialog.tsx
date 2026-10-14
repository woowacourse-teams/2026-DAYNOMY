import { useEffect, useRef, type ReactNode } from 'react';

type Props = {
  titleId: string;
  onClose: () => void;
  busy?: boolean;
  children: ReactNode;
  className?: string;
};
export function PortfolioDialog({
  titleId,
  onClose,
  busy = false,
  children,
  className = '',
}: Props) {
  const ref = useRef<HTMLDialogElement>(null);
  const viewFocus = useRef(new Map<string, HTMLElement>());
  useEffect(() => {
    const dialog = ref.current;
    const trigger = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      dialog?.close();
      document.body.style.overflow = overflow;
      if (trigger?.isConnected) trigger.focus();
    };
  }, []);
  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    dialog.scrollTop = 0;
    const previous = viewFocus.current.get(titleId);
    if (!dialog.open) dialog.showModal();
    const controls = Array.from(
      dialog.querySelectorAll<HTMLElement>(
        'button, a[href], input, select, textarea, summary, [tabindex]:not([tabindex="-1"])',
      ),
    ).filter((element) => !element.matches(':disabled') && element.getClientRects().length > 0);
    const target =
      previous && controls.includes(previous)
        ? previous
        : (controls.find((element) => element.hasAttribute('data-autofocus')) ??
          controls.find((element) => element === document.activeElement) ??
          controls[0]);
    target?.focus();
  }, [titleId]);
  return (
    <dialog
      ref={ref}
      className={`portfolio-sheet ${className}`}
      aria-labelledby={titleId}
      aria-busy={busy}
      onFocusCapture={(event) => {
        if (event.target instanceof HTMLElement && event.target !== event.currentTarget)
          viewFocus.current.set(titleId, event.target);
      }}
      onCancel={(event) => {
        event.preventDefault();
        if (!busy) onClose();
      }}
      onKeyDown={(event) => {
        if (event.key !== 'Tab') return;
        const controls = Array.from(
          event.currentTarget.querySelectorAll<HTMLElement>(
            'button, a[href], input, select, textarea, summary, [tabindex]:not([tabindex="-1"])',
          ),
        ).filter((element) => !element.matches(':disabled') && element.getClientRects().length > 0);
        const first = controls[0];
        const last = controls.at(-1);
        if (!first || !last) {
          event.preventDefault();
          return;
        }
        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault();
          last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault();
          first.focus();
        }
      }}
      onClick={(event) => {
        if (event.target === event.currentTarget && !busy) onClose();
      }}
    >
      <div className="portfolio-sheet-content">{children}</div>
    </dialog>
  );
}
