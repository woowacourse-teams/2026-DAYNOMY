/** @vitest-environment jsdom */
import { cleanup, fireEvent, render, within } from '@testing-library/react';
import { afterAll, beforeAll, afterEach, expect, it, vi } from 'vitest';
import { PortfolioTradeDateTimePicker } from '../../src/features/portfolio/components/PortfolioTradeDateTimePicker';
import { todayInSeoul } from '../../src/features/portfolio/transactionForm';

const originalShowModal = HTMLDialogElement.prototype.showModal;
const originalClose = HTMLDialogElement.prototype.close;
beforeAll(() => {
  HTMLDialogElement.prototype.showModal = function () {
    this.open = true;
  };
  HTMLDialogElement.prototype.close = function () {
    this.open = false;
  };
});
afterAll(() => {
  HTMLDialogElement.prototype.showModal = originalShowModal;
  HTMLDialogElement.prototype.close = originalClose;
});

afterEach(cleanup);

it('윤년의 말일에서 연도와 월을 바꾸면 유효한 말일로 맞추고 확인할 때만 반영한다', () => {
  const confirm = vi.fn();
  const view = render(
    <PortfolioTradeDateTimePicker
      field="tradeDate"
      value="2024-02-29"
      onClose={vi.fn()}
      onConfirm={confirm}
    />,
  );
  fireEvent.click(view.getByRole('option', { name: '2025년' }));
  expect(view.getByRole('option', { name: '28일' }).getAttribute('aria-selected')).toBe('true');
  expect(confirm).not.toHaveBeenCalled();
  fireEvent.click(view.getByRole('option', { name: '1월' }));
  fireEvent.click(view.getByRole('option', { name: '31일' }));
  fireEvent.click(view.getByRole('option', { name: '4월' }));
  expect(view.getByRole('option', { name: '30일' }).getAttribute('aria-selected')).toBe('true');
  fireEvent.click(view.getByRole('button', { name: '확인' }));
  expect(confirm).toHaveBeenCalledWith('2025-04-30');
});

it('미래 거래일은 확인할 수 없다', () => {
  const confirm = vi.fn();
  const view = render(
    <PortfolioTradeDateTimePicker
      field="tradeDate"
      value={todayInSeoul()}
      onClose={vi.fn()}
      onConfirm={confirm}
    />,
  );
  fireEvent.click(
    view.getByRole('option', { name: `${Number(todayInSeoul().slice(0, 4)) + 1}년` }),
  );
  expect(view.getByRole('status').textContent).toContain('오늘 이후');
  expect(view.getByRole('button', { name: '확인' })).toHaveProperty('disabled', true);
  fireEvent.click(view.getByRole('button', { name: '확인' }));
  expect(confirm).not.toHaveBeenCalled();
});

it('키보드와 스크롤로 시간을 고르고 닫으면 변경을 적용하지 않는다', () => {
  const confirm = vi.fn();
  const close = vi.fn();
  const view = render(
    <PortfolioTradeDateTimePicker
      field="tradeTime"
      value="09:35"
      onClose={close}
      onConfirm={confirm}
    />,
  );
  const hour = view.getByRole('listbox', { name: '시 (한국 시간)' });
  fireEvent.keyDown(hour, { key: 'End' });
  expect(within(hour).getByRole('option', { selected: true }).textContent).toBe('23');
  fireEvent.keyDown(hour, { key: 'ArrowDown' });
  expect(within(hour).getByRole('option', { selected: true }).textContent).toBe('23');
  const minute = view.getByRole('listbox', { name: '분' });
  fireEvent.scroll(minute, { target: { scrollTop: 59 * 32 } });
  expect(within(minute).getByRole('option', { selected: true }).textContent).toBe('59');
  fireEvent.click(view.getByRole('button', { name: '선택 창 닫기' }));
  expect(close).toHaveBeenCalledOnce();
  expect(confirm).not.toHaveBeenCalled();
});

it('Esc는 선택 창만 닫고 확인된 시각을 덮어쓰지 않는다', () => {
  const close = vi.fn();
  const outerCancel = vi.fn();
  const confirm = vi.fn();
  const view = render(
    <dialog open onCancel={outerCancel}>
      <PortfolioTradeDateTimePicker
        field="tradeTime"
        value="00:00"
        onClose={close}
        onConfirm={confirm}
      />
    </dialog>,
  );
  fireEvent.keyDown(view.getByRole('listbox', { name: '분' }), { key: 'PageDown' });
  fireEvent(
    view.getByRole('dialog', { name: '시간 선택' }),
    new Event('cancel', { bubbles: true, cancelable: true }),
  );
  expect(close).toHaveBeenCalledOnce();
  expect(outerCancel).not.toHaveBeenCalled();
  expect(confirm).not.toHaveBeenCalled();
});
