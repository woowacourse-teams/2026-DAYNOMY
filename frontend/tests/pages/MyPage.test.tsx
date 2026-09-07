/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import MyPage from '../../src/features/pages/components/MyPage';
import {
  STOCK_BOOKMARK_DETAILS_STORAGE_KEY,
  STOCK_BOOKMARK_STORAGE_KEY,
} from '../../src/features/stocks/constants';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

afterEach(() => {
  cleanup();
  localStorage.clear();
});

describe('마이페이지', () => {
  it('로그인 정보 없이 관심자산 빈 상태를 표시한다', () => {
    const view = render(<MyPage />);

    expect(view.getByRole('heading', { name: '관심자산' })).toBeTruthy();
    expect(view.getByText('0개')).toBeTruthy();
    expect(view.getByText('아직 저장한 관심자산이 없습니다.')).toBeTruthy();
  });

  it('로컬 스토리지에 저장된 관심자산을 표시한다', () => {
    localStorage.setItem(STOCK_BOOKMARK_STORAGE_KEY, JSON.stringify(['005930']));
    localStorage.setItem(
      STOCK_BOOKMARK_DETAILS_STORAGE_KEY,
      JSON.stringify({
        '005930': { rank: 1, code: '005930', name: '삼성전자' },
      }),
    );

    const view = render(<MyPage />);

    expect(view.getByText('1개')).toBeTruthy();
    expect(view.getByText('삼성전자')).toBeTruthy();
    expect(view.getByText('005930')).toBeTruthy();
  });

  it('기존 코드만 저장된 관심자산은 종목 목록 API로 이름을 보정한다', async () => {
    localStorage.setItem(STOCK_BOOKMARK_STORAGE_KEY, JSON.stringify(['005930']));
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        jsonResponse({
          baseDate: '2026-08-27',
          rankings: [{ rank: 1, code: '005930', name: '삼성전자' }],
          page: 1,
          size: 100,
          totalPages: 1,
          totalElements: 1,
          hasNext: false,
        }),
      ),
    );

    const view = render(<MyPage />);

    expect(await view.findByText('삼성전자')).toBeTruthy();
    expect(view.getByText('005930')).toBeTruthy();
    await waitFor(() =>
      expect(localStorage.getItem(STOCK_BOOKMARK_DETAILS_STORAGE_KEY)).toContain('삼성전자'),
    );
  });

  it('상세 정보의 저장 key와 종목 코드가 다르면 무시하고 다시 보정한다', async () => {
    localStorage.setItem(STOCK_BOOKMARK_STORAGE_KEY, JSON.stringify(['005930']));
    localStorage.setItem(
      STOCK_BOOKMARK_DETAILS_STORAGE_KEY,
      JSON.stringify({
        '005930': { rank: 2, code: '000660', name: 'SK하이닉스' },
      }),
    );
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        jsonResponse({
          baseDate: '2026-08-27',
          rankings: [{ rank: 1, code: '005930', name: '삼성전자' }],
          page: 1,
          size: 100,
          totalPages: 1,
          totalElements: 1,
          hasNext: false,
        }),
      ),
    );

    const view = render(<MyPage />);

    expect(await view.findByText('삼성전자')).toBeTruthy();
    expect(view.queryByText('SK하이닉스')).toBeNull();

    fireEvent.click(view.getByRole('button', { name: '삼성전자 북마크 해제' }));

    await waitFor(() => expect(localStorage.getItem(STOCK_BOOKMARK_STORAGE_KEY)).toBe('[]'));
  });

  it('기존 코드만 저장된 관심자산을 보정할 수 없으면 코드를 이름으로 표시한다', async () => {
    localStorage.setItem(STOCK_BOOKMARK_STORAGE_KEY, JSON.stringify(['000000']));
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        jsonResponse({
          baseDate: '2026-08-27',
          rankings: [],
          page: 1,
          size: 100,
          totalPages: 0,
          totalElements: 0,
          hasNext: false,
        }),
      ),
    );

    const view = render(<MyPage />);

    expect(view.getByRole('heading', { name: '000000' })).toBeTruthy();
    expect(view.getAllByText('000000')).toHaveLength(2);
  });

  it('관심자산 북마크를 해제하면 로컬 스토리지에서도 제거한다', async () => {
    localStorage.setItem(STOCK_BOOKMARK_STORAGE_KEY, JSON.stringify(['005930']));
    localStorage.setItem(
      STOCK_BOOKMARK_DETAILS_STORAGE_KEY,
      JSON.stringify({
        '005930': { rank: 1, code: '005930', name: '삼성전자' },
      }),
    );

    const view = render(<MyPage />);

    fireEvent.click(view.getByRole('button', { name: '삼성전자 북마크 해제' }));

    await waitFor(() => expect(localStorage.getItem(STOCK_BOOKMARK_STORAGE_KEY)).toBe('[]'));
    expect(view.getByText('0개')).toBeTruthy();
    expect(view.getByText('아직 저장한 관심자산이 없습니다.')).toBeTruthy();
  });
});
