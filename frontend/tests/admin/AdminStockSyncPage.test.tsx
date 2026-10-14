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
            synchronizedDateCount: 1,
            pendingDateCount: 0,
            failedDateCount: 0,
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

    fireEvent.click(view.getByRole('button', { name: '빠진 종가 동기화' }));
    expect(await view.findByText('3,500건')).toBeTruthy();

    expect(calls.some((url) => url.endsWith('/api/admin/stocks/sync'))).toBe(true);
    expect(calls.some((url) => url.endsWith('/api/admin/stocks/prices/sync'))).toBe(true);
  });

  it('수집할 종가가 없으면 기준일 없이 완료 상태를 안내한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) =>
        String(input).endsWith('/api/auth/csrf')
          ? jsonResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' })
          : jsonResponse({
              baseDate: null,
              receivedCount: 0,
              createdCount: 0,
              updatedCount: 0,
              skippedCount: 0,
              synchronizedDateCount: 0,
              pendingDateCount: 0,
              failedDateCount: 0,
            }),
      ),
    );
    const view = render(<AdminStockSyncPage />);
    fireEvent.click(view.getByRole('button', { name: '빠진 종가 동기화' }));
    expect(await view.findByText('새로 수집할 종가가 없습니다.')).toBeTruthy();
  });

  it('일부 날짜만 수집되면 전체 건수와 남은 날짜·실패 날짜를 표시한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) =>
        String(input).endsWith('/api/auth/csrf')
          ? jsonResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' })
          : jsonResponse({
              baseDate: '2026-10-13',
              receivedCount: 7000,
              createdCount: 6900,
              updatedCount: 50,
              skippedCount: 50,
              synchronizedDateCount: 2,
              pendingDateCount: 4,
              failedDateCount: 1,
            }),
      ),
    );

    const view = render(<AdminStockSyncPage />);
    fireEvent.click(view.getByRole('button', { name: '빠진 종가 동기화' }));

    expect(await view.findByText('2026.10.13 기준 일부 수집 완료')).toBeTruthy();
    expect(view.getByText('7,000건')).toBeTruthy();
    expect(view.getByText('2일')).toBeTruthy();
    expect(view.getByText('4일')).toBeTruthy();
    expect(view.getByText('1일')).toBeTruthy();
  });

  it('제공 전 미수집 거래일은 수집 완료로 안내하지 않는다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) =>
        String(input).endsWith('/api/auth/csrf')
          ? jsonResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' })
          : jsonResponse({
              baseDate: '2026-10-12',
              receivedCount: 0,
              createdCount: 0,
              updatedCount: 0,
              skippedCount: 0,
              synchronizedDateCount: 0,
              pendingDateCount: 1,
              failedDateCount: 0,
            }),
      ),
    );

    const view = render(<AdminStockSyncPage />);
    fireEvent.click(view.getByRole('button', { name: '빠진 종가 동기화' }));

    expect(await view.findByText('미수집 종가가 남아 있습니다.')).toBeTruthy();
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
