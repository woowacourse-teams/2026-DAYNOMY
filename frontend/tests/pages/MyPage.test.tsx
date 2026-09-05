/** @vitest-environment jsdom */

import { cleanup, render } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import MyPage from '../../src/features/pages/components/MyPage';

afterEach(cleanup);

describe('마이페이지', () => {
  it('로그인 정보 없이 관심자산 빈 상태를 표시한다', () => {
    const view = render(<MyPage />);

    expect(view.getByRole('heading', { name: '관심자산' })).toBeTruthy();
    expect(view.getByText('0개')).toBeTruthy();
    expect(view.getByText('아직 저장한 관심자산이 없습니다.')).toBeTruthy();
  });
});
