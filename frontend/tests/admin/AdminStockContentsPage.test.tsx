/** @vitest-environment jsdom */

import { cleanup, render } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { AdminStockContentsPage } from '../../src/features/admin/AdminStockContentsPage';

afterEach(() => {
  cleanup();
});

describe('관리자 종목 연결 화면', () => {
  it('종목 관련 자료 관리 패널을 별도 화면으로 표시한다', () => {
    const view = render(<AdminStockContentsPage />);

    expect(view.getByRole('heading', { name: '종목 관련 링크' })).toBeTruthy();
    expect(view.getByRole('searchbox', { name: '종목 검색' })).toBeTruthy();
  });
});
