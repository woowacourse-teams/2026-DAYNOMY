/** @vitest-environment jsdom */

import { cleanup, render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it } from 'vitest';
import { Header } from '../../src/components/Header';

afterEach(cleanup);

describe('헤더', () => {
  it('마이페이지 링크를 표시하지 않는다', () => {
    const view = render(
      <MemoryRouter>
        <Header />
      </MemoryRouter>,
    );

    expect(view.queryByRole('link', { name: '마이페이지' })).toBeNull();
    expect(view.getByRole('link', { name: '포트폴리오' }).getAttribute('href')).toBe('/portfolio');
    expect(view.queryByRole('link', { name: '로그인' })).toBeNull();
  });
});
