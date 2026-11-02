import { useId, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { XIcon } from '@phosphor-icons/react/dist/csr/X';
import { todayInSeoul } from '../transactionForm';
import { PortfolioDialog } from './PortfolioDialog';

const ROW_HEIGHT = 32;
const range = (start: number, end: number) =>
  Array.from({ length: end - start + 1 }, (_, index) => start + index);
const pad = (value: number) => String(value).padStart(2, '0');

function PickerWheel({
  label,
  values,
  value,
  format,
  onChange,
}: {
  label: string;
  values: number[];
  value: number;
  format: (value: number) => string;
  onChange: (value: number) => void;
}) {
  const id = useId();
  const ref = useRef<HTMLDivElement>(null);
  const reportedValue = useRef<number | null>(null);
  const index = Math.max(0, values.indexOf(value));
  useLayoutEffect(() => {
    if (ref.current && reportedValue.current !== value) {
      const frame = window.requestAnimationFrame(() => {
        if (!ref.current) return;
        ref.current.scrollTop = index * ROW_HEIGHT;
        reportedValue.current = value;
      });
      return () => window.cancelAnimationFrame(frame);
    }
  }, [index, value]);

  function select(nextIndex: number) {
    const next = Math.max(0, Math.min(values.length - 1, nextIndex));
    if (ref.current) ref.current.scrollTop = next * ROW_HEIGHT;
    reportedValue.current = values[next];
    onChange(values[next]);
  }

  return (
    <div
      ref={ref}
      className="portfolio-picker-wheel"
      role="listbox"
      aria-label={label}
      aria-activedescendant={`${id}-${value}`}
      tabIndex={0}
      onScroll={(event) => {
        const next = Math.max(
          0,
          Math.min(values.length - 1, Math.round(event.currentTarget.scrollTop / ROW_HEIGHT)),
        );
        if (values[next] !== reportedValue.current) {
          reportedValue.current = values[next];
          onChange(values[next]);
        }
      }}
      onKeyDown={(event) => {
        const offsets: Record<string, number> = {
          ArrowUp: -1,
          ArrowDown: 1,
          PageUp: -5,
          PageDown: 5,
        };
        const offset = offsets[event.key];
        if (offset !== undefined || event.key === 'Home' || event.key === 'End') {
          event.preventDefault();
          select(
            event.key === 'Home'
              ? 0
              : event.key === 'End'
                ? values.length - 1
                : index + (offset ?? 0),
          );
        }
      }}
    >
      {values.map((item, itemIndex) => {
        const distance = Math.min(3, Math.abs(itemIndex - index));
        return (
          <div
            key={item}
            id={`${id}-${item}`}
            role="option"
            aria-selected={item === value}
            className={`portfolio-picker-option portfolio-picker-distance-${distance}`}
            onClick={() => {
              ref.current?.focus();
              select(itemIndex);
            }}
          >
            {format(item)}
          </div>
        );
      })}
    </div>
  );
}

export function PortfolioTradeDateTimePicker({
  field,
  value,
  onClose,
  onConfirm,
}: {
  field: 'tradeDate' | 'tradeTime';
  value: string;
  onClose: () => void;
  onConfirm: (value: string) => void;
}) {
  const titleId = useId();
  const dateMode = field === 'tradeDate';
  const today = todayInSeoul();
  const currentYear = Number(today.slice(0, 4));
  const [draft, setDraft] = useState(() =>
    (value || (dateMode ? today : '00:00')).split(dateMode ? '-' : ':').map(Number),
  );
  const [year, month, day] = draft;
  const maxDay = new Date(Date.UTC(year, month, 0)).getUTCDate();
  const selectedDate = `${year}-${pad(month)}-${pad(day)}`;
  const futureDate = dateMode && selectedDate > today;
  function changeDate(part: number, next: number) {
    const updated = [...draft];
    updated[part] = next;
    updated[2] = Math.min(updated[2], new Date(Date.UTC(updated[0], updated[1], 0)).getUTCDate());
    setDraft(updated);
  }
  return createPortal(
    <div onKeyDown={(event) => event.stopPropagation()}>
      <PortfolioDialog titleId={titleId} onClose={onClose} className="portfolio-datetime-picker">
        <header className="portfolio-picker-heading">
          <h2 id={titleId}>{dateMode ? '날짜 선택' : '시간 선택'}</h2>
          <button type="button" aria-label="선택 창 닫기" onClick={onClose}>
            <XIcon size={26} weight="thin" aria-hidden="true" />
          </button>
        </header>
        <div className={`portfolio-picker-wheels${dateMode ? '' : ' is-time'}`}>
          {dateMode ? (
            <>
              <PickerWheel
                label="연도"
                values={range(Math.min(1900, year), currentYear + 10)}
                value={year}
                format={(item) => `${item}년`}
                onChange={(item) => changeDate(0, item)}
              />
              <PickerWheel
                label="월"
                values={range(1, 12)}
                value={month}
                format={(item) => `${item}월`}
                onChange={(item) => changeDate(1, item)}
              />
              <PickerWheel
                label="일"
                values={range(1, maxDay)}
                value={day}
                format={(item) => `${item}일`}
                onChange={(item) => changeDate(2, item)}
              />
            </>
          ) : (
            <>
              <PickerWheel
                label="시 (한국 시간)"
                values={range(0, 23)}
                value={draft[0]}
                format={pad}
                onChange={(item) => setDraft([item, draft[1]])}
              />
              <span className="portfolio-picker-colon" aria-hidden="true">
                :
              </span>
              <PickerWheel
                label="분"
                values={range(0, 59)}
                value={draft[1]}
                format={pad}
                onChange={(item) => setDraft([draft[0], item])}
              />
            </>
          )}
        </div>
        {futureDate && (
          <p className="portfolio-picker-error" role="status">
            오늘 이후의 거래일은 선택할 수 없어요.
          </p>
        )}
        <button
          type="button"
          className="portfolio-picker-confirm"
          disabled={futureDate}
          onClick={() => onConfirm(dateMode ? selectedDate : draft.map(pad).join(':'))}
        >
          확인
        </button>
      </PortfolioDialog>
    </div>,
    document.body,
  );
}
