/** @vitest-environment jsdom */

import { cleanup, render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it } from 'vitest';
import { Footer } from '../../src/components/Footer';
import { InfoPage } from '../../src/features/pages/components/InfoPage';

const pages = [
  ['회사소개', '/about', 'about'],
  ['이용약관', '/terms', 'terms'],
  ['개인정보처리방침', '/privacy', 'privacy'],
  ['DAYNOMY Std.', '/standard', 'standard'],
] as const;

afterEach(() => cleanup());

describe('푸터 안내 페이지', () => {
  it('안내 메뉴를 각 페이지 주소로 연결한다', () => {
    const view = render(
      <MemoryRouter>
        <Footer />
      </MemoryRouter>,
    );

    for (const [label, path] of pages) {
      expect(view.getByRole('link', { name: label }).getAttribute('href')).toBe(path);
    }

    expect(view.getByRole('link', { name: '메일문의' }).getAttribute('href')).toBe(
      'mailto:paperchoigo@gmail.com',
    );
  });

  it.each(pages)('%s 페이지에 내용을 표시한다', (title, _path, page) => {
    const view = render(
      <MemoryRouter>
        <InfoPage page={page} />
      </MemoryRouter>,
    );

    expect(view.getByRole('heading', { level: 1, name: title })).toBeTruthy();
    expect(view.getByRole('link', { name: 'DAYNOMY 홈' })).toBeTruthy();
  });
});
