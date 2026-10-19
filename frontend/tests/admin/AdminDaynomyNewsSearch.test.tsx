/** @vitest-environment jsdom */
import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AdminDaynomyNewsSearch } from '../../src/features/admin/components/AdminDaynomyNewsSearch';

function response(page = 1, title = '반도체 실적 전망') {
  return new Response(
    JSON.stringify({
      items: [
        {
          id: page,
          title,
          imageUrl: null,
          sources: [],
          category: 'STOCK',
          publishedAt: '2026-10-14T00:00:00Z',
          status: 'PUBLISHED',
          createdAt: '2026-10-14T00:00:00Z',
        },
      ],
      page,
      size: 20,
      totalPages: 2,
      totalElements: 21,
      hasNext: page === 1,
    }),
    { headers: { 'content-type': 'application/json' } },
  );
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('DAYNOMY 뉴스 찾기', () => {
  it('발행 뉴스 검색과 페이지 이동, 미리보기, 연결 및 중복 표시를 제공한다', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) =>
      response(
        new URL(String(input), 'https://daynomy.test').searchParams.get('page') === '2' ? 2 : 1,
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const onLink = vi.fn(async () => {});
    const props = {
      stockName: '삼성전자',
      hidden: false,
      saving: false,
      isRegistered: (url: string) => url === '/news/2',
      onLink,
    };
    const view = render(<AdminDaynomyNewsSearch {...props} />);
    expect(view.getByDisplayValue('삼성전자')).toBeTruthy();
    fireEvent.click(view.getByRole('button', { name: 'DAYNOMY 검색' }));
    await view.findByText('반도체 실적 전망');
    expect(String(fetchMock.mock.calls[0][0])).toContain('status=PUBLISHED');
    expect(
      new URL(String(fetchMock.mock.calls[0][0]), 'https://daynomy.test').searchParams.get('q'),
    ).toBe('삼성전자');
    expect(view.getByRole('link', { name: '뉴스 보기 ↗' }).getAttribute('href')).toBe('/news/1');
    fireEvent.click(view.getByRole('button', { name: '이 뉴스 연결' }));
    expect(onLink).toHaveBeenCalledWith(1);
    fireEvent.change(view.getByLabelText('뉴스 검색어'), {
      target: { value: '아직 검색하지 않은 단어' },
    });
    fireEvent.click(view.getByRole('button', { name: '다음' }));
    await view.findByText('2 / 2');
    expect(
      new URL(String(fetchMock.mock.calls[1][0]), 'https://daynomy.test').searchParams.get('q'),
    ).toBe('삼성전자');
    expect((view.getByRole('button', { name: '연결됨' }) as HTMLButtonElement).disabled).toBe(true);
    expect(onLink).toHaveBeenCalledTimes(1);
  });

  it('실패 후 재시도하고 빈 검색 결과를 안내한다', async () => {
    const fetchMock = vi
      .fn()
      .mockRejectedValueOnce(new Error('검색 실패'))
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            items: [],
            page: 1,
            size: 20,
            totalPages: 0,
            totalElements: 0,
            hasNext: false,
          }),
        ),
      );
    vi.stubGlobal('fetch', fetchMock);
    const view = render(
      <AdminDaynomyNewsSearch
        stockName="삼성전자"
        hidden={false}
        saving={false}
        isRegistered={() => false}
        onLink={async () => {}}
      />,
    );
    fireEvent.click(view.getByRole('button', { name: 'DAYNOMY 검색' }));
    expect(await view.findByRole('alert')).toBeTruthy();
    fireEvent.change(view.getByLabelText('뉴스 검색어'), { target: { value: '' } });
    fireEvent.click(view.getByRole('button', { name: 'DAYNOMY 검색' }));
    expect(
      await view.findByText('검색 결과가 없습니다. 검색어를 바꾸거나 비워서 다시 검색해 주세요.'),
    ).toBeTruthy();
    expect(String(fetchMock.mock.calls[1][0])).not.toContain('q=');
  });

  it('대상 변경 후 이전 검색 응답을 표시하지 않는다', async () => {
    let resolveOld!: (value: Response) => void;
    let oldSignal: AbortSignal | undefined;
    vi.stubGlobal(
      'fetch',
      vi.fn((_input: RequestInfo | URL, init?: RequestInit) => {
        oldSignal = init?.signal as AbortSignal;
        return new Promise<Response>((resolve) => {
          resolveOld = resolve;
        });
      }),
    );
    const props = {
      hidden: false,
      saving: false,
      isRegistered: () => false,
      onLink: async () => {},
    };
    const view = render(<AdminDaynomyNewsSearch key={1} stockName="삼성전자" {...props} />);
    fireEvent.click(view.getByRole('button', { name: 'DAYNOMY 검색' }));
    view.rerender(<AdminDaynomyNewsSearch key={2} stockName="SK하이닉스" {...props} />);
    expect(oldSignal?.aborted).toBe(true);
    resolveOld(response(1, '이전 종목 결과'));
    await waitFor(() => expect(view.getByDisplayValue('SK하이닉스')).toBeTruthy());
    expect(view.queryByText('이전 종목 결과')).toBeNull();
  });
});
