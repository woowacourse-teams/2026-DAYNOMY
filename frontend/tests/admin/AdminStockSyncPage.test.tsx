/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
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

it('선택 기간을 재수집하고 실행 중 모든 작업을 잠근 뒤 리그 재집계를 제공한다', async () => {
  let finish!: (response: Response) => void;
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/auth/csrf'))
        return jsonResponse({ token: 'token', headerName: 'X-CSRF-TOKEN' });
      if (url.includes('/backfill?'))
        return new Promise<Response>((resolve) => {
          finish = resolve;
        });
      return jsonResponse({ calculatedCount: 12 });
    }),
  );
  const view = render(<AdminStockSyncPage />);
  fireEvent.change(view.getByLabelText('시작일'), { target: { value: '2026-10-01' } });
  fireEvent.change(view.getByLabelText('종료일'), { target: { value: '2026-10-02' } });
  fireEvent.submit(view.getByRole('form', { name: '기간별 종가 재수집' }));
  await waitFor(() => expect(finish).toBeTypeOf('function'));
  for (const button of view.getAllByRole('button')) expect(button).toHaveProperty('disabled', true);
  finish(
    jsonResponse({
      from: '2026-10-01',
      to: '2026-10-02',
      synchronizedDateCount: 2,
      receivedCount: 100,
      createdCount: 90,
      updatedCount: 10,
      skippedCount: 0,
    }),
  );
  expect(await view.findByText('2026.10.01 ~ 2026.10.02 재수집 결과')).toBeTruthy();
  expect(view.getByText('2일')).toBeTruthy();
  fireEvent.click(view.getByRole('button', { name: '리그 성과 재집계' }));
  expect(await view.findByText('일간 기록 12건 처리')).toBeTruthy();
  fireEvent.change(view.getByLabelText('종료일'), { target: { value: '2026-10-03' } });
  expect(view.queryByText('2026.10.01 ~ 2026.10.02 재수집 결과')).toBeNull();
});

it('기간 오류와 재수집 실패 후에도 입력을 유지하고 다시 실행할 수 있다', async () => {
  let writes = 0;
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      if (String(input).endsWith('/api/auth/csrf'))
        return jsonResponse({ token: 'token', headerName: 'X-CSRF-TOKEN' });
      writes++;
      return writes === 1
        ? jsonResponse({ message: '수집 공급원 오류' }, 502)
        : jsonResponse({
            from: '2026-10-01',
            to: '2026-10-02',
            synchronizedDateCount: 0,
            receivedCount: 0,
            createdCount: 0,
            updatedCount: 0,
            skippedCount: 0,
          });
    }),
  );
  const view = render(<AdminStockSyncPage />);
  const form = view.getByRole('form', { name: '기간별 종가 재수집' });
  fireEvent.change(view.getByLabelText('시작일'), { target: { value: '2026-10-01' } });
  fireEvent.change(view.getByLabelText('종료일'), { target: { value: '2026-11-01' } });
  fireEvent.submit(form);
  expect((await view.findByRole('alert')).textContent).toContain('31일 이내');
  expect(writes).toBe(0);
  fireEvent.change(view.getByLabelText('종료일'), { target: { value: '2026-10-02' } });
  fireEvent.submit(form);
  expect(await view.findByText('수집 공급원 오류')).toBeTruthy();
  expect(view.getByLabelText('시작일')).toHaveProperty('value', '2026-10-01');
  fireEvent.submit(form);
  expect(await view.findByText('2026.10.01 ~ 2026.10.02 재수집 결과')).toBeTruthy();
  expect(writes).toBe(2);
});

it('리그 재집계 실패를 표시하고 다시 실행한다', async () => {
  let writes = 0;
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      if (String(input).endsWith('/api/auth/csrf'))
        return jsonResponse({ token: 'token', headerName: 'X-CSRF-TOKEN' });
      return ++writes === 1
        ? jsonResponse({ message: '재집계 실패' }, 500)
        : jsonResponse({ calculatedCount: 0 });
    }),
  );
  const view = render(<AdminStockSyncPage />);
  fireEvent.click(view.getByRole('button', { name: '리그 성과 재집계' }));
  expect(await view.findByText('재집계 실패')).toBeTruthy();
  fireEvent.click(view.getByRole('button', { name: '리그 성과 재집계' }));
  expect(await view.findByText('일간 기록 0건 처리')).toBeTruthy();
  expect(view.queryByRole('alert')).toBeNull();
});

it('미국 종목 적재와 최신·지정일 종가 수집을 국내 요청과 구분하고 결과를 표시한다', async () => {
  const calls: string[] = [];
  let finish!: (response: Response) => void;
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      calls.push(url);
      if (url.endsWith('/api/auth/csrf'))
        return jsonResponse({ token: 'token', headerName: 'X-CSRF-TOKEN' });
      if (url.endsWith('/us/sync'))
        return new Promise<Response>((resolve) => {
          finish = resolve;
        });
      return jsonResponse({
        baseDate: '2020-10-16',
        receivedCount: 11439,
        createdCount: 11400,
        updatedCount: 9,
        skippedCount: 30,
        synchronizedDateCount: 1,
        pendingDateCount: 0,
        failedDateCount: 0,
      });
    }),
  );
  const view = render(<AdminStockSyncPage />);
  fireEvent.click(view.getByRole('button', { name: '미국 종목 적재' }));
  await waitFor(() => expect(finish).toBeTypeOf('function'));
  for (const button of view.getAllByRole('button')) expect(button).toHaveProperty('disabled', true);
  expect(view.getByLabelText('미국 거래일 (선택)')).toHaveProperty('disabled', true);
  finish(
    jsonResponse({
      baseDate: '2020-10-19',
      syncedCount: 11559,
      createdCount: 11500,
      updatedCount: 59,
      delistedCount: 0,
      koreanNames: {
        downloadedCount: 100,
        matchedCount: 80,
        updatedCount: 60,
        unmatchedCount: 10,
        skippedCount: 10,
      },
      koreanNameErrorCode: null,
    }),
  );
  expect(await view.findByText('11,559개')).toBeTruthy();
  expect(view.getByText(/한글명 보충 완료/)).toBeTruthy();
  expect(view.getByText('한글명 변경')).toBeTruthy();
  fireEvent.click(view.getByRole('button', { name: '미국 종가 가져오기' }));
  expect(await view.findByText('2020.10.16 기준 미국 종가 수집 완료 · USD')).toBeTruthy();
  expect(view.getByText('11,439건')).toBeTruthy();
  fireEvent.change(view.getByLabelText('미국 거래일 (선택)'), { target: { value: '2020-10-16' } });
  expect(view.queryByText('11,439건')).toBeNull();
  fireEvent.click(view.getByRole('button', { name: '미국 종가 가져오기' }));
  expect(await view.findByText('11,439건')).toBeTruthy();
  expect(
    calls
      .filter((url) => !url.endsWith('/csrf'))
      .map((url) => {
        const parsed = new URL(url, 'http://localhost');
        return parsed.pathname + parsed.search;
      }),
  ).toEqual([
    '/api/admin/stocks/us/sync',
    '/api/admin/stocks/us/prices/sync',
    '/api/admin/stocks/us/prices/sync?date=2020-10-16',
  ]);
});

it('한글명 실패를 구분하고 종목을 재적재하지 않고 한글명만 다시 시도한다', async () => {
  const writes: string[] = [];
  let retries = 0;
  let finish!: (response: Response) => void;
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/csrf'))
        return jsonResponse({ token: 'token', headerName: 'X-CSRF-TOKEN' });
      writes.push(url);
      if (url.endsWith('/us/sync'))
        return jsonResponse({
          baseDate: '2026-10-27',
          syncedCount: 123,
          createdCount: 2,
          updatedCount: 1,
          delistedCount: 0,
          koreanNames: null,
          koreanNameErrorCode: 'KIS_STOCK_MASTER_REQUEST_FAILED',
        });
      if (++retries === 1)
        return jsonResponse({ message: '한글명 제공처에 연결할 수 없습니다.' }, 502);
      return new Promise<Response>((resolve) => {
        finish = resolve;
      });
    }),
  );
  const view = render(<AdminStockSyncPage />);
  fireEvent.click(view.getByRole('button', { name: '미국 종목 적재' }));
  expect(await view.findByText(/한글명 보충 실패/)).toBeTruthy();
  expect(view.getByText('123개')).toBeTruthy();
  fireEvent.click(view.getByRole('button', { name: '한글명 다시 시도' }));
  expect(await view.findByText('한글명 제공처에 연결할 수 없습니다.')).toBeTruthy();
  expect(view.getByText('123개')).toBeTruthy();
  fireEvent.click(view.getByRole('button', { name: '한글명 다시 시도' }));
  await waitFor(() => expect(finish).toBeTypeOf('function'));
  for (const button of view.getAllByRole('button')) expect(button).toHaveProperty('disabled', true);
  finish(
    jsonResponse({
      downloadedCount: 90,
      matchedCount: 70,
      updatedCount: 60,
      unmatchedCount: 10,
      skippedCount: 10,
    }),
  );
  expect(await view.findByText(/한글명 보충 완료/)).toBeTruthy();
  expect(view.queryByRole('alert')).toBeNull();
  expect(view.getByText('123개')).toBeTruthy();
  expect(writes.map((url) => new URL(url, 'http://localhost').pathname)).toEqual([
    '/api/admin/stocks/us/sync',
    '/api/admin/stocks/us/korean-names/sync',
    '/api/admin/stocks/us/korean-names/sync',
  ]);
});

it('미국 종가 실패 후 날짜를 유지하고 같은 날짜로 다시 수집한다', async () => {
  let writes = 0;
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      if (String(input).endsWith('/csrf'))
        return jsonResponse({ token: 'token', headerName: 'X-CSRF-TOKEN' });
      return ++writes === 1
        ? jsonResponse({ message: '미국 종목 목록을 먼저 동기화해주세요.' }, 409)
        : jsonResponse({
            baseDate: '2020-10-16',
            receivedCount: 5,
            createdCount: 5,
            updatedCount: 0,
            skippedCount: 0,
            synchronizedDateCount: 1,
            pendingDateCount: 0,
            failedDateCount: 0,
          });
    }),
  );
  const view = render(<AdminStockSyncPage />);
  const date = view.getByLabelText('미국 거래일 (선택)');
  fireEvent.change(date, { target: { value: '2020-10-16' } });
  fireEvent.click(view.getByRole('button', { name: '미국 종가 가져오기' }));
  expect((await view.findByRole('alert')).textContent).toContain('미국 종목 목록을 먼저');
  expect(date).toHaveProperty('value', '2020-10-16');
  fireEvent.click(view.getByRole('button', { name: '미국 종가 가져오기' }));
  expect(await view.findByText('2020.10.16 기준 미국 종가 수집 완료 · USD')).toBeTruthy();
  expect(view.queryByRole('alert')).toBeNull();
});
