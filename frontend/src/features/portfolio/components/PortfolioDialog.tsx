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
    dialog?.showModal();
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
    const target =
      previous?.isConnected && !previous.closest('[hidden]')
        ? previous
        : Array.from(
            dialog.querySelectorAll<HTMLElement>(
              '[autofocus], button:not(:disabled), input, select',
            ),
          ).find((element) => !element.closest('[hidden]'));
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
