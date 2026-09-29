/** @vitest-environment jsdom */

import { cleanup, fireEvent, render } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AdminStockSyncPage } from '../../src/features/admin/AdminStockSyncPage';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('관리자 주식·ETF 동기화 화면', () => {
  it('종목 정보와 최근 종가를 각각 동기화하고 결과를 표시한다', async () => {
    const calls: string[] = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        calls.push(url);

        if (url.endsWith('/api/auth/csrf')) {
          return jsonResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' });
        }

        if (url.endsWith('/api/admin/stocks/prices/sync')) {
          return jsonResponse({
            baseDate: '2026-09-28',
            receivedCount: 3500,
            createdCount: 3400,
            updatedCount: 50,
            skippedCount: 50,
          });
        }

        return jsonResponse({
          baseDate: '2026-09-28',
          syncedCount: 3500,
          createdCount: 20,
          updatedCount: 3470,
          delistedCount: 10,
        });
      }),
    );

    const view = render(<AdminStockSyncPage />);

    fireEvent.click(view.getByRole('button', { name: '종목 정보 동기화' }));
    expect(await view.findByText('3,500개')).toBeTruthy();

    fireEvent.click(view.getByRole('button', { name: '최근 종가 동기화' }));
    expect(await view.findByText('3,500건')).toBeTruthy();

    expect(calls.some((url) => url.endsWith('/api/admin/stocks/sync'))).toBe(true);
    expect(calls.some((url) => url.endsWith('/api/admin/stocks/prices/sync'))).toBe(true);
  });

  it('동기화 실패 시 오류를 표시하고 다시 시도할 수 있다', async () => {
    let syncRequestCount = 0;
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        if (url.endsWith('/api/auth/csrf')) {
          return jsonResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' });
        }

        syncRequestCount += 1;
        if (syncRequestCount === 1) {
          return jsonResponse({ message: '외부 데이터를 불러오지 못했습니다.' }, 502);
        }

        return jsonResponse({
          baseDate: '2026-09-28',
          syncedCount: 3500,
          createdCount: 20,
          updatedCount: 3470,
          delistedCount: 10,
        });
      }),
    );

    const view = render(<AdminStockSyncPage />);
    fireEvent.click(view.getByRole('button', { name: '종목 정보 동기화' }));

    expect((await view.findByRole('alert')).textContent).toContain(
      '외부 데이터를 불러오지 못했습니다.',
    );
    fireEvent.click(view.getByRole('button', { name: '다시 시도' }));

    expect(await view.findByText('3,500개')).toBeTruthy();
    expect(syncRequestCount).toBe(2);
  });
});
