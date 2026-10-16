import { useState, type FormEvent } from 'react';
import { ApiError } from '../../api/client';
import {
  backfillAdminStockPrices,
  recalculateAdminLeague,
  syncAdminStockPrices,
  syncAdminStocks,
} from './api';
import type {
  AdminStockPriceBackfillResponse,
  AdminLeagueRecalculationResponse,
  AdminStockPriceSyncResponse,
  AdminStockSyncResponse,
} from './types';
import './admin.css';

type SyncTask = 'stocks' | 'prices' | 'backfill' | 'league';

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

  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [backfillResult, setBackfillResult] = useState<AdminStockPriceBackfillResponse | null>(
    null,
  );
  const [leagueResult, setLeagueResult] = useState<AdminLeagueRecalculationResponse | null>(null);
  const [backfillError, setBackfillError] = useState<string | null>(null);
  const [leagueError, setLeagueError] = useState<string | null>(null);

  async function handleBackfill(event: FormEvent) {
    event.preventDefault();
    if (runningTask !== null) return;
    setRunningTask('backfill');
    setBackfillError(null);
    setBackfillResult(null);
    try {
      setBackfillResult(await backfillAdminStockPrices(from, to));
    } catch (error) {
      setBackfillError(getErrorMessage(error, '기간별 종가 재수집에 실패했습니다.'));
    } finally {
      setRunningTask(null);
    }
  }

  async function handleLeagueRecalculation() {
    if (runningTask !== null) return;
    setRunningTask('league');
    setLeagueError(null);
    setLeagueResult(null);
    try {
      setLeagueResult(await recalculateAdminLeague());
    } catch (error) {
      setLeagueError(getErrorMessage(error, '리그 재집계에 실패했습니다.'));
    } finally {
      setRunningTask(null);
    }
  }

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
          <h1>주식·ETF 동기화</h1>
          <p>
            종목·종가 수집과 리그 집계는 자동으로 실행됩니다. 가격 정정이나 장애 복구가 필요할 때
            아래 기능을 사용하세요.
          </p>
        </div>
      </div>

      <div className="admin-sync-grid">
        <section className="admin-sync-card" aria-labelledby="stock-master-sync-title">
          <div>
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

        <section className="admin-sync-card" aria-labelledby="stock-backfill-title">
          <div>
            <p className="admin-panel-eyebrow">과거 가격 정정</p>
            <h2 id="stock-backfill-title">기간별 종가 재수집</h2>
            <p>
              이미 수집한 날짜도 다시 확인합니다. 시작일과 종료일을 포함해 최대 31일을 선택하세요.
              저장된 종가로 리그 성과도 다시 계산합니다.
            </p>
          </div>
          <form
            onSubmit={handleBackfill}
            className="admin-backfill-form"
            aria-label="기간별 종가 재수집"
          >
            <label className="admin-field">
              시작일
              <input
                type="date"
                required
                value={from}
                disabled={runningTask !== null}
                onChange={(event) => {
                  setFrom(event.target.value);
                  setBackfillResult(null);
                  setBackfillError(null);
                }}
              />
            </label>
            <label className="admin-field">
              종료일
              <input
                type="date"
                required
                min={from || undefined}
                value={to}
                disabled={runningTask !== null}
                onChange={(event) => {
                  setTo(event.target.value);
                  setBackfillResult(null);
                  setBackfillError(null);
                }}
              />
            </label>
            <button type="submit" className="admin-primary-button" disabled={runningTask !== null}>
              {runningTask === 'backfill' ? '재수집 중…' : '선택 기간 재수집'}
            </button>
          </form>
          {backfillResult ? (
            <div className="admin-sync-result" role="status">
              <strong>
                {formatBaseDate(backfillResult.from)} ~ {formatBaseDate(backfillResult.to)} 재수집
                결과
              </strong>
              <dl>
                <div>
                  <dt>수집 완료 날짜</dt>
                  <dd>{backfillResult.synchronizedDateCount.toLocaleString('ko-KR')}일</dd>
                </div>
                <div>
                  <dt>수신</dt>
                  <dd>{backfillResult.receivedCount.toLocaleString('ko-KR')}건</dd>
                </div>
                <div>
                  <dt>신규</dt>
                  <dd>{backfillResult.createdCount.toLocaleString('ko-KR')}건</dd>
                </div>
                <div>
                  <dt>수정</dt>
                  <dd>{backfillResult.updatedCount.toLocaleString('ko-KR')}건</dd>
                </div>
                <div>
                  <dt>건너뜀</dt>
                  <dd>{backfillResult.skippedCount.toLocaleString('ko-KR')}건</dd>
                </div>
              </dl>
              <p>휴장일과 공급원이 종가를 제공하지 않은 날짜는 수집 건수에 포함되지 않습니다.</p>
            </div>
          ) : null}
          {backfillError ? (
            <div className="admin-alert" role="alert">
              <strong>{backfillError}</strong>
              <p>
                일부 날짜는 이미 저장됐을 수 있습니다. 기간을 확인한 뒤 다시 실행할 수 있습니다.
              </p>
            </div>
          ) : null}
        </section>

        <section className="admin-sync-card" aria-labelledby="league-recalculate-title">
          <div>
            <p className="admin-panel-eyebrow">성과 복구</p>
            <h2 id="league-recalculate-title">리그 성과 재집계</h2>
            <p>
              저장된 종가와 보유 이력으로 전체 기간의 일간 성과와 주간 순위를 다시 계산합니다. 빠진
              종가는 먼저 수집해 주세요.
            </p>
          </div>
          <button
            type="button"
            className="admin-primary-button"
            disabled={runningTask !== null}
            onClick={handleLeagueRecalculation}
          >
            {runningTask === 'league' ? '재집계 중…' : '리그 성과 재집계'}
          </button>
          {leagueResult ? (
            <div className="admin-sync-result" role="status">
              <strong>리그 재집계 실행 완료</strong>
              <p>일간 기록 {leagueResult.calculatedCount.toLocaleString('ko-KR')}건 처리</p>
              <p>
                종가·보유 기록이 부족한 날짜는 집계 대기로 남습니다. 주간 확정 여부는 리그 화면에서
                확인하세요.
              </p>
            </div>
          ) : null}
          {leagueError ? (
            <div className="admin-alert" role="alert">
              <strong>{leagueError}</strong>
              <p>일부 참가자의 결과는 저장됐을 수 있습니다. 다시 실행하면 재확인합니다.</p>
            </div>
          ) : null}
        </section>
      </div>
    </main>
  );
}
