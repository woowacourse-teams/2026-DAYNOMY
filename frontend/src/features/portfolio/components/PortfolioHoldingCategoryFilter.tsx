import { CaretDownIcon } from '@phosphor-icons/react/dist/csr/CaretDown';
import { CheckIcon } from '@phosphor-icons/react/dist/csr/Check';
import { useEffect, useId, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import type { AssetCategory } from '../../stocks/types';

const categories = [
  ['ALL', '전체'],
  ['STOCK', '주식'],
  ['ETF', 'ETF'],
] as const;

type Props = {
  value: 'ALL' | AssetCategory;
  onChange: (category: 'ALL' | AssetCategory) => void;
};

export function PortfolioHoldingCategoryFilter({ value, onChange }: Props) {
  const [open, setOpen] = useState(false);
  const [position, setPosition] = useState({ top: 0, left: 0 });
  const triggerRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const menuId = useId();
  const label = categories.find(([category]) => category === value)![1];

  useEffect(() => {
    if (!open) return;
    menuRef.current?.querySelector<HTMLButtonElement>('[aria-checked="true"]')?.focus();
    const dismiss = () => setOpen(false);
    const onPointerDown = (event: PointerEvent) => {
      if (!(event.target instanceof Node)) return;
      if (!triggerRef.current?.contains(event.target) && !menuRef.current?.contains(event.target))
        dismiss();
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return;
      event.preventDefault();
      dismiss();
      triggerRef.current?.focus();
    };
    document.addEventListener('pointerdown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    window.addEventListener('resize', dismiss);
    window.addEventListener('scroll', dismiss, true);
    return () => {
      document.removeEventListener('pointerdown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
      window.removeEventListener('resize', dismiss);
      window.removeEventListener('scroll', dismiss, true);
    };
  }, [open]);

  function toggleMenu() {
    const rect = triggerRef.current!.getBoundingClientRect();
    const menuHeight = 136;
    setPosition({
      top:
        rect.bottom + 4 + menuHeight <= window.innerHeight
          ? rect.bottom + 4
          : Math.max(8, rect.top - menuHeight - 4),
      left: Math.max(8, Math.min(rect.left, window.innerWidth - 168)),
    });
    setOpen((current) => !current);
  }

  return (
    <>
      <button
        ref={triggerRef}
        type="button"
        className={`portfolio-column-filter${value !== 'ALL' ? ' is-active' : ''}`}
        aria-label={`종목 유형: ${label}`}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={open ? menuId : undefined}
        onClick={toggleMenu}
        onKeyDown={(event) => {
          if (event.key !== 'ArrowDown' || open) return;
          event.preventDefault();
          toggleMenu();
        }}
      >
        {value === 'ALL' ? '종목' : label}
        <CaretDownIcon size={14} aria-hidden="true" />
      </button>
      {open &&
        createPortal(
          <div
            ref={menuRef}
            id={menuId}
            role="menu"
            aria-label="자산 유형"
            className="portfolio-category-menu"
            style={position}
            onKeyDown={(event) => {
              const items = Array.from(
                menuRef.current!.querySelectorAll<HTMLButtonElement>('button'),
              );
              const index = items.indexOf(document.activeElement as HTMLButtonElement);
              if (event.key === 'Tab') {
                setOpen(false);
                triggerRef.current?.focus();
              } else if (['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) {
                event.preventDefault();
                const next =
                  event.key === 'Home'
                    ? 0
                    : event.key === 'End'
                      ? items.length - 1
                      : (index + (event.key === 'ArrowDown' ? 1 : -1) + items.length) %
                        items.length;
                items[next]?.focus();
              }
            }}
          >
            {categories.map(([category, text]) => (
              <button
                key={category}
                type="button"
                role="menuitemradio"
                aria-checked={value === category}
                onClick={() => {
                  onChange(category);
                  setOpen(false);
                  triggerRef.current?.focus();
                }}
              >
                {text}
                {value === category && <CheckIcon size={16} weight="bold" aria-hidden="true" />}
              </button>
            ))}
          </div>,
          document.body,
        )}
    </>
  );
}
