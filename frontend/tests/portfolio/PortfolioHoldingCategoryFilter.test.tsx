/** @vitest-environment jsdom */
import { cleanup, fireEvent, render } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { PortfolioHoldingCategoryFilter } from '../../src/features/portfolio/components/PortfolioHoldingCategoryFilter';

afterEach(cleanup);

describe('보유 종목 헤더 필터', () => {
  it('키보드로 메뉴를 열고 선택지를 이동하며 Escape로 닫고 포커스를 돌려준다', () => {
    const onChange = vi.fn();
    const view = render(<PortfolioHoldingCategoryFilter value="ALL" onChange={onChange} />);
    const trigger = view.getByRole('button', { name: '종목 유형: 전체' });
    fireEvent.keyDown(trigger, { key: 'ArrowDown' });
    const all = view.getByRole('menuitemradio', { name: '전체' });
    const stock = view.getByRole('menuitemradio', { name: '주식' });
    const etf = view.getByRole('menuitemradio', { name: 'ETF' });
    expect(all.getAttribute('aria-checked')).toBe('true');
    expect(document.activeElement).toBe(all);
    fireEvent.keyDown(all, { key: 'ArrowDown' });
    expect(document.activeElement).toBe(stock);
    fireEvent.keyDown(stock, { key: 'End' });
    expect(document.activeElement).toBe(etf);
    fireEvent.keyDown(etf, { key: 'ArrowDown' });
    expect(document.activeElement).toBe(all);
    fireEvent.keyDown(all, { key: 'Escape' });
    expect(view.queryByRole('menu')).toBeNull();
    expect(document.activeElement).toBe(trigger);
    expect(onChange).not.toHaveBeenCalled();
  });

  it('선택을 저장한 뒤 메뉴를 닫고 바깥 클릭과 스크롤로도 닫힌다', () => {
    const onChange = vi.fn();
    const view = render(<PortfolioHoldingCategoryFilter value="STOCK" onChange={onChange} />);
    const trigger = view.getByRole('button', { name: '종목 유형: 주식' });
    fireEvent.click(trigger);
    expect(document.activeElement).toBe(view.getByRole('menuitemradio', { name: '주식' }));
    fireEvent.click(view.getByRole('menuitemradio', { name: 'ETF' }));
    expect(onChange).toHaveBeenCalledWith('ETF');
    expect(view.queryByRole('menu')).toBeNull();
    expect(document.activeElement).toBe(trigger);
    fireEvent.click(trigger);
    fireEvent.pointerDown(document.body);
    expect(view.queryByRole('menu')).toBeNull();
    fireEvent.click(trigger);
    fireEvent.scroll(window);
    expect(view.queryByRole('menu')).toBeNull();
  });
});
