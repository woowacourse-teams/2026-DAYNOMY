/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthContext } from '../../src/auth/AuthContext';
import { StockListPage } from '../../src/features/stocks/StockListPage';
import { STOCK_BOOKMARK_STORAGE_KEY } from '../../src/features/stocks/constants';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

function renderStocks() {
  return render(
    <AuthContext.Provider value={{ isLoggedIn: true, loading: false, role: 'USER' }}>
      <MemoryRouter>
        <StockListPage />
      </MemoryRouter>
    </AuthContext.Provider>,
  );
}

afterEach(() => {
  cleanup();
  localStorage.clear();
  sessionStorage.clear();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe('관심 종목 화면', () => {
  it('기존 북마크를 초기화 전 빈 배열로 덮어쓰지 않는다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        jsonResponse({
          baseDate: '2026-08-27',
          rankings: [{ rank: 1, code: '005930', name: '삼성전자' }],
        }),
      ),
    );
    localStorage.setItem(STOCK_BOOKMARK_STORAGE_KEY, JSON.stringify(['005930']));
    const setItem = vi.spyOn(Storage.prototype, 'setItem');

    const view = renderStocks();
    const bookmarkButton = await view.findByRole('button', {
      name: '삼성전자 북마크 해제',
    });

    expect(setItem).not.toHaveBeenCalledWith(STOCK_BOOKMARK_STORAGE_KEY, '[]');
    expect(localStorage.getItem(STOCK_BOOKMARK_STORAGE_KEY)).toBe('["005930"]');
    expect(bookmarkButton).toBeTruthy();
  });

  it('북마크를 추가하고 해제한 상태를 저장한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        jsonResponse({
          baseDate: '2026-08-27',
          rankings: [{ rank: 1, code: '005930', name: '삼성전자' }],
        }),
      ),
    );

    const view = renderStocks();
    const addButton = (await view.findByRole('button', {
      name: '삼성전자 북마크 추가',
    })) as HTMLButtonElement;

    fireEvent.click(addButton);
    await waitFor(() => expect(localStorage.getItem('daynomy:stock-bookmarks')).toBe('["005930"]'));
    expect(view.getByRole('button', { name: '삼성전자 북마크 해제' })).toBeTruthy();

    fireEvent.click(view.getByRole('button', { name: '삼성전자 북마크 해제' }));
    await waitFor(() => expect(localStorage.getItem('daynomy:stock-bookmarks')).toBe('[]'));
  });

  it('종목 API 실패 시 오류 상태와 대체 데이터를 안내한다', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse({}, 500)),
    );

    const view = renderStocks();

    expect((await view.findByRole('status')).textContent).toContain(
      '종목 목록을 불러오지 못했습니다.',
    );
    expect(view.getByText('mock')).toBeTruthy();
    expect(view.getByText('에코프로비엠')).toBeTruthy();
  });
});
