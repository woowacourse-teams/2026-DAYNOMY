/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { afterEach, beforeAll, describe, expect, it } from 'vitest';
import { AuthContext } from '../../src/auth/AuthContext';
import { Header } from '../../src/components/Header';

beforeAll(() => {
  HTMLDialogElement.prototype.showModal = function showModal() {
    this.setAttribute('open', '');
  };
  HTMLDialogElement.prototype.close = function close() {
    this.removeAttribute('open');
    this.dispatchEvent(new Event('close'));
  };
});

afterEach(cleanup);

function renderHeader() {
  const router = createMemoryRouter(
    [
      {
        path: '*',
        element: (
          <>
            <Header />
            <input aria-label="다른 입력창" />
          </>
        ),
      },
    ],
    { initialEntries: ['/'] },
  );

  const view = render(
    <AuthContext.Provider value={{ isLoggedIn: false, loading: false }}>
      <RouterProvider router={router} />
    </AuthContext.Provider>,
  );

  return { router, ...view };
}

describe('검색 오버레이', () => {
  it('헤더 버튼과 / 단축키로 열고 입력창에 포커스한다', async () => {
    const view = renderHeader();
    const trigger = view.getByRole('button', { name: '검색 열기' });

    fireEvent.click(trigger);

    const input = view.getByRole('textbox', { name: '뉴스 또는 종목 검색' });
    await waitFor(() => expect(document.activeElement).toBe(input));
    expect(view.getByText('최근 검색어가 없습니다.')).toBeTruthy();

    fireEvent.click(view.getByRole('button', { name: '검색 닫기' }));
    await waitFor(() => expect(document.activeElement).toBe(trigger));

    fireEvent.keyDown(document, { key: '/' });
    await waitFor(() => expect(document.activeElement).toBe(input));

    fireEvent(input.closest('dialog') as HTMLDialogElement, new Event('cancel'));
    await waitFor(() => expect(document.activeElement).toBe(trigger));
  });

  it('다른 입력창을 작성할 때 / 단축키로 열리지 않는다', () => {
    const view = renderHeader();
    const otherInput = view.getByRole('textbox', { name: '다른 입력창' });

    fireEvent.keyDown(otherInput, { key: '/' });

    expect(view.queryByRole('dialog')).toBeNull();
  });

  it('검색어를 입력하면 전체 검색 결과 주소로 이동한다', async () => {
    const view = renderHeader();
    fireEvent.click(view.getByRole('button', { name: '검색 열기' }));

    const input = view.getByRole('textbox', { name: '뉴스 또는 종목 검색' });
    fireEvent.change(input, { target: { value: ' 기준금리 ' } });
    fireEvent.submit(input.closest('form') as HTMLFormElement);

    await waitFor(() => expect(view.router.state.location.pathname).toBe('/search'));
    expect(view.router.state.location.search).toBe(
      '?q=%EA%B8%B0%EC%A4%80%EA%B8%88%EB%A6%AC&category=ALL&page=1',
    );
  });
});
