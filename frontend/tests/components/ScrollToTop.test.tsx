/** @vitest-environment jsdom */

import { cleanup, fireEvent, render } from '@testing-library/react';
import { Link, MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ScrollToTop } from '../../src/App';

afterEach(() => cleanup());

describe('페이지 이동 스크롤', () => {
  it('푸터 링크처럼 다른 페이지로 이동하면 화면을 맨 위로 이동한다', () => {
    const scrollTo = vi.fn();
    vi.stubGlobal('scrollTo', scrollTo);

    const view = render(
      <MemoryRouter initialEntries={['/start']}>
        <ScrollToTop />
        <Link to="/about">회사소개</Link>
        <Routes>
          <Route path="*" element={null} />
        </Routes>
      </MemoryRouter>,
    );

    scrollTo.mockClear();
    fireEvent.click(view.getByRole('link', { name: '회사소개' }));

    expect(scrollTo).toHaveBeenCalledWith({ top: 0 });
  });
});
