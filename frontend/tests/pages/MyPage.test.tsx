/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import MyPage from '../../src/features/pages/components/MyPage';
import {
  STOCK_BOOKMARK_DETAILS_STORAGE_KEY,
  STOCK_BOOKMARK_STORAGE_KEY,
} from '../../src/features/stocks/constants';

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
