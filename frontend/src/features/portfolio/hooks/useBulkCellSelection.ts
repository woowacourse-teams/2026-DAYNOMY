import {
  useCallback,
  useEffect,
  useId,
  useLayoutEffect,
  useRef,
  useState,
  type ClipboardEvent,
  type FocusEvent,
  type KeyboardEvent,
  type PointerEvent,
  type RefObject,
} from 'react';
import { parseDelimited } from '../bulkEditorModel';

export type BulkCell = { row: number; column: number };
type Selection = { anchor: BulkCell; end: BulkCell };
const bounds = ({ anchor, end }: Selection) => ({
  top: Math.min(anchor.row, end.row),
  bottom: Math.max(anchor.row, end.row),
  left: Math.min(anchor.column, end.column),
  right: Math.max(anchor.column, end.column),
});
const multiple = (range: Selection) =>
  range.anchor.row !== range.end.row || range.anchor.column !== range.end.column;

// Only the bulk modal's body cells participate; search popups and row actions keep native input.
export function useBulkCellSelection({
  tableRef,
  rowKey,
  disabled,
  onPaste,
  onError,
}: {
  tableRef: RefObject<HTMLDivElement | null>;
  rowKey: string;
  disabled: boolean;
  onPaste: (start: BulkCell, values: string[][]) => void;
  onError: (message: string) => void;
}) {
  const descriptionId = useId();
  const [selection, setSelection] = useState<Selection | null>(null);
  const selected = useRef<Selection | null>(null);
  const dragging = useRef<{ x: number; y: number; moved: boolean } | null>(null);
  const select = useCallback((range: Selection | null) => {
    selected.current = range;
    setSelection(range);
  }, []);
  function rows() {
    return Array.from(tableRef.current?.querySelectorAll<HTMLTableRowElement>('tbody tr') ?? []);
  }
  function cellAt(point: BulkCell) {
    if (point.row < 0 || point.column < 0) return undefined;
    return rows()[point.row]?.cells[point.column + 1];
  }
  const locate = useCallback(
    (target: EventTarget | null): BulkCell | null => {
      if (!(target instanceof Element) || !tableRef.current?.contains(target)) return null;
      if (target.closest('button, [role="combobox"], [role="listbox"]')) return null;
      const cell = target.closest<HTMLTableCellElement>('td, th');
      const row = cell?.parentElement;
      if (!cell || !(row instanceof HTMLTableRowElement) || row.parentElement?.tagName !== 'TBODY')
        return null;
      if (cell.cellIndex === 0) return null;
      return { row: row.sectionRowIndex, column: cell.cellIndex - 1 };
    },
    [tableRef],
  );
  function valueAt(point: BulkCell) {
    const cell = cellAt(point);
    return (
      cell?.dataset.copyValue ??
      cell?.querySelector('input')?.value ??
      cell?.textContent?.trim().replace(/\s+/g, ' ').replace(/\(/g, ' (') ??
      ''
    );
  }
  useEffect(() => {
    selected.current = null;
    setSelection(null);
    dragging.current = null;
  }, [rowKey, disabled]);
  useLayoutEffect(() => {
    const range = selection && bounds(selection);
    rows().forEach((row, rowIndex) =>
      Array.from(row.cells).forEach((cell, index) => {
        const column = index - 1;
        if (
          range &&
          rowIndex >= range.top &&
          rowIndex <= range.bottom &&
          column >= range.left &&
          column <= range.right
        ) {
          cell.dataset.bulkSelected = [
            rowIndex === range.top && 'top',
            rowIndex === range.bottom && 'bottom',
            column === range.left && 'left',
            column === range.right && 'right',
          ]
            .filter(Boolean)
            .join(' ');
        } else delete cell.dataset.bulkSelected;
      }),
    );
  });
  useEffect(() => {
    let frame = 0;
    function extend(target: EventTarget | null) {
      const point = locate(target);
      const current = selected.current;
      if (!point || !current || !dragging.current) return;
      if (point.row === current.end.row && point.column === current.end.column) return;
      dragging.current.moved = true;
      select({ anchor: current.anchor, end: point });
      window.getSelection()?.removeAllRanges();
      tableRef.current?.focus({ preventScroll: true });
    }
    function scroll() {
      const drag = dragging.current;
      const region = tableRef.current;
      if (!drag || !region) return;
      const rect = region.getBoundingClientRect();
      const dx = drag.x < rect.left + 28 ? -16 : drag.x > rect.right - 28 ? 16 : 0;
      const dy = drag.y < rect.top + 28 ? -12 : drag.y > rect.bottom - 28 ? 12 : 0;
      if (dx || dy) {
        region.scrollLeft += dx;
        region.scrollTop += dy;
        extend(
          document.elementFromPoint(
            Math.max(rect.left + 2, Math.min(drag.x, rect.right - 2)),
            Math.max(rect.top + 38, Math.min(drag.y, rect.bottom - 2)),
          ),
        );
      }
      frame = requestAnimationFrame(scroll);
    }
    function move(event: globalThis.PointerEvent) {
      const drag = dragging.current;
      if (!drag) return;
      // A mouse released outside the browser must not leave selection dragging active.
      if (event.buttons === 0) return stop();
      drag.x = event.clientX;
      drag.y = event.clientY;
      extend(event.target);
      if (drag.moved) event.preventDefault();
      if (!frame) frame = requestAnimationFrame(scroll);
    }
    function stop() {
      dragging.current = null;
      cancelAnimationFrame(frame);
      frame = 0;
    }
    document.addEventListener('pointermove', move, { passive: false });
    document.addEventListener('pointerup', stop);
    document.addEventListener('pointercancel', stop);
    window.addEventListener('blur', stop);
    return () => {
      stop();
      document.removeEventListener('pointermove', move);
      document.removeEventListener('pointerup', stop);
      document.removeEventListener('pointercancel', stop);
      window.removeEventListener('blur', stop);
    };
    // Row structure is the only layout dependency; values are read from current DOM cells.
  }, [tableRef, rowKey, disabled, locate, select]);

  function paste(event: ClipboardEvent<HTMLDivElement>) {
    if (disabled) return;
    const point = locate(event.target);
    if (!point && event.target !== tableRef.current) return;
    const current = selected.current;
    const destination = current && (!point || multiple(current)) ? bounds(current) : null;
    const start = destination ? { row: destination.top, column: destination.left } : point;
    if (!start) return;
    event.preventDefault();
    event.stopPropagation();
    try {
      const source = parseDelimited(event.clipboardData.getData('text/plain'), '\t', true).map(
        (row) => row.cells,
      );
      if (!source.length) return;
      const width = source[0].length;
      if (source.some((row) => row.length !== width))
        throw new Error('같은 열 수의 사각형 범위를 붙여넣어 주세요.');
      const values =
        destination && source.length === 1 && width === 1
          ? Array.from({ length: destination.bottom - destination.top + 1 }, () =>
              Array.from({ length: destination.right - destination.left + 1 }, () => source[0][0]),
            )
          : source;
      onPaste(start, values);
      select({
        anchor: start,
        end: { row: start.row + values.length - 1, column: start.column + values[0].length - 1 },
      });
    } catch (caught) {
      onError(caught instanceof Error ? caught.message : '붙여넣을 값을 확인해 주세요.');
    }
  }
  return {
    descriptionId,
    description: selection
      ? `${bounds(selection).bottom - bounds(selection).top + 1}행 × ${bounds(selection).right - bounds(selection).left + 1}열 선택됨. 복사하거나 수정 가능한 칸에 붙여넣을 수 있습니다.`
      : '셀을 드래그하거나 Shift와 방향키로 범위를 선택할 수 있습니다.',
    handlers: {
      onPointerDownCapture(event: PointerEvent<HTMLDivElement>) {
        const point = locate(event.target);
        if (disabled || !point) {
          select(null);
          return;
        }
        if (event.button !== 0 || event.pointerType === 'touch') return;
        const anchor = event.shiftKey && selected.current ? selected.current.anchor : point;
        select({ anchor, end: point });
        dragging.current = { x: event.clientX, y: event.clientY, moved: false };
        if (event.shiftKey || !(event.target instanceof HTMLInputElement)) {
          event.preventDefault();
          tableRef.current?.focus({ preventScroll: true });
        }
      },
      onFocusCapture(event: FocusEvent<HTMLDivElement>) {
        const point = locate(event.target);
        if (point && !dragging.current) select({ anchor: point, end: point });
      },
      onCopy(event: ClipboardEvent<HTMLDivElement>) {
        const current = selected.current;
        if (disabled || !current || (!locate(event.target) && event.target !== tableRef.current))
          return;
        if (
          !multiple(current) &&
          event.target instanceof HTMLInputElement &&
          event.target.selectionStart !== event.target.selectionEnd
        )
          return;
        const area = bounds(current);
        const text = Array.from({ length: area.bottom - area.top + 1 }, (_, row) =>
          Array.from({ length: area.right - area.left + 1 }, (_, column) => {
            const value = valueAt({ row: area.top + row, column: area.left + column });
            return /[\t\r\n"]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value;
          }).join('\t'),
        ).join('\n');
        event.preventDefault();
        event.clipboardData.setData('text/plain', text);
      },
      onPasteCapture: paste,
      onKeyDownCapture(event: KeyboardEvent<HTMLDivElement>) {
        const current = selected.current;
        if (disabled || !current || (!locate(event.target) && event.target !== tableRef.current))
          return;
        if (event.key === 'Escape') {
          event.preventDefault();
          event.stopPropagation();
          select(null);
          return;
        }
        if (
          (event.metaKey || event.ctrlKey) &&
          event.key.toLowerCase() === 'd' &&
          event.target === tableRef.current
        ) {
          event.preventDefault();
          event.stopPropagation();
          const area = bounds(current);
          const sourceRow = area.bottom > area.top ? area.top : area.top - 1;
          const targetRow = area.bottom > area.top ? area.top + 1 : area.top;
          if (sourceRow < 0) return;
          const source = Array.from({ length: area.right - area.left + 1 }, (_, column) =>
            valueAt({ row: sourceRow, column: area.left + column }),
          );
          try {
            onPaste(
              { row: targetRow, column: area.left },
              Array.from({ length: area.bottom - targetRow + 1 }, () => [...source]),
            );
          } catch (caught) {
            onError(caught instanceof Error ? caught.message : '채울 값을 확인해 주세요.');
          }
          return;
        }
        if (
          event.key.startsWith('Arrow') &&
          (event.shiftKey || event.target === tableRef.current)
        ) {
          const end = {
            row:
              current.end.row + (event.key === 'ArrowDown' ? 1 : event.key === 'ArrowUp' ? -1 : 0),
            column:
              current.end.column +
              (event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1 : 0),
          };
          event.preventDefault();
          event.stopPropagation();
          if (cellAt(end)) {
            select({ anchor: event.shiftKey ? current.anchor : end, end });
            cellAt(end)?.scrollIntoView?.({ block: 'nearest', inline: 'nearest' });
            tableRef.current?.focus({ preventScroll: true });
          }
        } else if (event.key === 'Enter' && event.target === tableRef.current) {
          const input = cellAt(current.end)?.querySelector('input');
          if (input) {
            event.preventDefault();
            input.focus();
            input.select();
          }
        }
      },
    },
  };
}
