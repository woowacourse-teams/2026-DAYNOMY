import { useState } from 'react';
import { ApiError } from '../../api/client';
import { syncAdminAssetRankings } from './api';
import './admin.css';

function getErrorMessage(error: unknown, fallback: string) {
  return error instanceof ApiError || error instanceof Error ? error.message : fallback;
}

export function AdminAssetRankingPage() {
  const [syncing, setSyncing] = useState(false);
  const [savedCount, setSavedCount] = useState<number | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  async function handleSync() {
    setSyncing(true);
    setSavedCount(null);
    setErrorMessage(null);

    try {
      const response = await syncAdminAssetRankings();
      setSavedCount(response.savedCount);
    } catch (error) {
      setErrorMessage(getErrorMessage(error, '관심 자산 순위를 갱신하지 못했습니다.'));
    } finally {
      setSyncing(false);
    }
  }

  return (
    <main className="admin-content">
      <div className="admin-page-heading">
        <div>
          <p className="admin-kicker">자산 운영</p>
          <h1>관심 자산 순위 갱신</h1>
          <p>코스닥 시가총액 상위 150개 기준 데이터를 관리자 권한으로 즉시 갱신합니다.</p>
        </div>
      </div>

      <section className="admin-action-panel" aria-labelledby="asset-ranking-sync-title">
        <div>
          <p className="admin-panel-eyebrow">수동 동기화</p>
          <h2 id="asset-ranking-sync-title">코스닥 대표 종목 순위</h2>
          <p>
            자동 스케줄러 적재가 지연될 때 이 작업을 실행하면 현재 백엔드 수집 로직으로 순위
            데이터를 다시 저장합니다.
          </p>
        </div>
        <button
          type="button"
          className="admin-primary-button"
          disabled={syncing}
          onClick={handleSync}
        >
          {syncing ? '갱신 중' : '지금 갱신'}
        </button>
      </section>

      {savedCount !== null ? (
        <section className="admin-success-panel" role="status" aria-live="polite">
          <strong>관심 자산 순위 갱신이 완료되었습니다.</strong>
          <p>
            저장된 종목 수 <span>{savedCount.toLocaleString('ko-KR')}</span>개
          </p>
        </section>
      ) : null}

      {errorMessage ? (
        <section className="admin-alert" role="alert">
          <strong>{errorMessage}</strong>
          <button type="button" onClick={handleSync} disabled={syncing}>
            다시 시도
          </button>
        </section>
      ) : null}
    </main>
  );
}
