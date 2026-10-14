import { useState } from 'react';
import { ApiError } from '../../api/client';
import { syncAdminStockPrices, syncAdminStocks } from './api';
import type { AdminStockPriceSyncResponse, AdminStockSyncResponse } from './types';
import './admin.css';

type SyncTask = 'stocks' | 'prices';

function getErrorMessage(error: unknown, fallback: string) {
  return error instanceof ApiError || error instanceof Error ? error.message : fallback;
}

function formatBaseDate(value: string) {
  return value.replaceAll('-', '.');
}

export function AdminStockSyncPage() {
  const [runningTask, setRunningTask] = useState<SyncTask | null>(null);
  const [stockResult, setStockResult] = useState<AdminStockSyncResponse | null>(null);
  const [priceResult, setPriceResult] = useState<AdminStockPriceSyncResponse | null>(null);
  const [stockError, setStockError] = useState<string | null>(null);
  const [priceError, setPriceError] = useState<string | null>(null);

  async function handleStockSync() {
    setRunningTask('stocks');
    setStockError(null);
    setStockResult(null);

    try {
      setStockResult(await syncAdminStocks());
    } catch (error) {
      setStockError(getErrorMessage(error, '주식·ETF 종목 정보를 동기화하지 못했습니다.'));
    } finally {
      setRunningTask(null);
    }
  }

  async function handlePriceSync() {
    setRunningTask('prices');
    setPriceError(null);
    setPriceResult(null);

    try {
      setPriceResult(await syncAdminStockPrices());
    } catch (error) {
      setPriceError(getErrorMessage(error, '주식·ETF 최근 종가를 동기화하지 못했습니다.'));
    } finally {
      setRunningTask(null);
    }
  }

  return (
    <main className="admin-content">
      <div className="admin-page-heading">
        <div>
          <p className="admin-kicker">자산 데이터 운영</p>
          <h1>주식·ETF 동기화</h1>
          <p>종목 정보와 빠진 거래일 종가를 필요할 때 수동으로 다시 수집할 수 있습니다.</p>
        </div>
      </div>

      <div className="admin-sync-grid">
        <section className="admin-sync-card" aria-labelledby="stock-master-sync-title">
          <div>
            <p className="admin-panel-eyebrow">종목 마스터</p>
            <h2 id="stock-master-sync-title">종목 정보 동기화</h2>
            <p>KOSPI·KOSDAQ 주식과 ETF의 신규 상장, 종목명 변경, 상장폐지 정보를 반영합니다.</p>
          </div>
          <button
            type="button"
            className="admin-primary-button"
            disabled={runningTask !== null}
            onClick={handleStockSync}
          >
            {runningTask === 'stocks' ? '동기화 중…' : '종목 정보 동기화'}
          </button>

          {stockResult ? (
            <div className="admin-sync-result" role="status" aria-live="polite">
              <strong>{formatBaseDate(stockResult.baseDate)} 기준 동기화 완료</strong>
              <dl>
                <div>
                  <dt>전체</dt>
                  <dd>{stockResult.syncedCount.toLocaleString('ko-KR')}개</dd>
                </div>
                <div>
                  <dt>신규</dt>
                  <dd>{stockResult.createdCount.toLocaleString('ko-KR')}개</dd>
                </div>
                <div>
                  <dt>수정</dt>
                  <dd>{stockResult.updatedCount.toLocaleString('ko-KR')}개</dd>
                </div>
                <div>
                  <dt>상장폐지</dt>
                  <dd>{stockResult.delistedCount.toLocaleString('ko-KR')}개</dd>
                </div>
              </dl>
            </div>
          ) : null}

          {stockError ? (
            <div className="admin-alert" role="alert">
              <strong>{stockError}</strong>
              <button type="button" onClick={handleStockSync} disabled={runningTask !== null}>
                다시 시도
              </button>
            </div>
          ) : null}
        </section>

        <section className="admin-sync-card" aria-labelledby="stock-price-sync-title">
          <div>
            <p className="admin-panel-eyebrow">일별 시세</p>
            <h2 id="stock-price-sync-title">빠진 종가 동기화</h2>
            <p>거래일 달력에서 수집되지 않은 주식·ETF 종가를 다시 수집합니다.</p>
          </div>
          <button
            type="button"
            className="admin-primary-button"
            disabled={runningTask !== null}
            onClick={handlePriceSync}
          >
            {runningTask === 'prices' ? '동기화 중…' : '빠진 종가 동기화'}
          </button>

          {priceResult ? (
            <div className="admin-sync-result" role="status" aria-live="polite">
              <strong>
                {priceResult.synchronizedDateCount > 0 && priceResult.baseDate
                  ? `${formatBaseDate(priceResult.baseDate)} 기준 ${priceResult.pendingDateCount > 0 ? '일부 수집 완료' : '동기화 완료'}`
                  : priceResult.pendingDateCount > 0
                    ? '미수집 종가가 남아 있습니다.'
                    : '새로 수집할 종가가 없습니다.'}
              </strong>
              <dl>
                <div>
                  <dt>수집 완료 날짜</dt>
                  <dd>{priceResult.synchronizedDateCount.toLocaleString('ko-KR')}일</dd>
                </div>
                <div>
                  <dt>남은 미수집 날짜</dt>
                  <dd>{priceResult.pendingDateCount.toLocaleString('ko-KR')}일</dd>
                </div>
                {priceResult.failedDateCount > 0 ? (
                  <div>
                    <dt>이번 실행 실패 날짜</dt>
                    <dd>{priceResult.failedDateCount.toLocaleString('ko-KR')}일</dd>
                  </div>
                ) : null}
                <div>
                  <dt>수신</dt>
                  <dd>{priceResult.receivedCount.toLocaleString('ko-KR')}건</dd>
                </div>
                <div>
                  <dt>신규</dt>
                  <dd>{priceResult.createdCount.toLocaleString('ko-KR')}건</dd>
                </div>
                <div>
                  <dt>수정</dt>
                  <dd>{priceResult.updatedCount.toLocaleString('ko-KR')}건</dd>
                </div>
                <div>
                  <dt>건너뜀</dt>
                  <dd>{priceResult.skippedCount.toLocaleString('ko-KR')}건</dd>
                </div>
              </dl>
              {priceResult.pendingDateCount > 0 ? (
                <p>남은 날짜는 종가 제공 후 다음 자동 실행에서 다시 확인합니다.</p>
              ) : null}
            </div>
          ) : null}

          {priceError ? (
            <div className="admin-alert" role="alert">
              <strong>{priceError}</strong>
              <button type="button" onClick={handlePriceSync} disabled={runningTask !== null}>
                다시 시도
              </button>
            </div>
          ) : null}
        </section>
      </div>
    </main>
  );
}
