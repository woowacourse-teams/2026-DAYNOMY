/** @vitest-environment jsdom */

import { cleanup, render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it } from 'vitest';
import { Header } from '../../src/components/Header';

afterEach(cleanup);

describe('헤더', () => {
  it('로그인 여부와 관계없이 마이페이지 링크를 표시한다', () => {
    const view = render(
      <MemoryRouter>
        <Header />
      </MemoryRouter>,
    );

    const myPageLink = view.getByRole('link', { name: '마이페이지' });

    expect(myPageLink.getAttribute('href')).toBe('/mypage');
    expect(view.queryByRole('link', { name: '로그인' })).toBeNull();
  });
});
