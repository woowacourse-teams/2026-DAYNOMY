import { useEffect, useState } from 'react';
import { portfolioErrorType, trackPortfolioEvent } from '../analytics';
import { getPortfolioHistories } from '../api';
import { todayInSeoul } from '../transactionForm';
import type { PortfolioHoldingChangeType, PortfolioHoldingHistory } from '../types';

const LABELS: Record<PortfolioHoldingChangeType, string> = {
  BUY: '매수',
  SELL: '매도',
  OPENING_BALANCE: '초기 보유 등록',
  ADDED: '초기 보유 등록',
  ADJUSTED: '보유정보 정정',
  UPDATED: '보유정보 정정',
  REMOVED: '보유자산 제거',
};
export function PortfolioHistory({
  revision,
  localHistories,
  assetId,
  amountsHidden,
  onEdit,
  onDelete,
  onCreate,
}: {
  revision: number;
  localHistories?: PortfolioHoldingHistory[];
  assetId?: number;
  amountsHidden: boolean;
  onEdit: (history: PortfolioHoldingHistory) => void;
  onDelete: (history: PortfolioHoldingHistory) => void;
  onCreate: () => void;
}) {
  const today = todayInSeoul();
  const [from, setFrom] = useState(`${today.slice(0, 4)}-01-01`);
  const [to, setTo] = useState(today);
  const [filter, setFilter] = useState('ALL');
  const [histories, setHistories] = useState<PortfolioHoldingHistory[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);
  const validPeriod = !!from && !!to && from <= to;
  useEffect(() => {
    if (localHistories !== undefined) {
      setHistories(localHistories);
      setLoading(false);
      setError('');
      return;
    }
    if (!validPeriod) return;
    const controller = new AbortController();
    setLoading(true);
    setError('');
    getPortfolioHistories(from, to, controller.signal)
      .then((items) => {
        if (!controller.signal.aborted) setHistories(items);
      })
      .catch((caught: unknown) => {
        if (!controller.signal.aborted) {
          trackPortfolioEvent('portfolio_dashboard_load_failed', {
            stage: 'history',
            error_type: portfolioErrorType(caught),
          });
          setError(caught instanceof Error ? caught.message : '내역을 불러오지 못했습니다.');
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [from, to, revision, retry, validPeriod, localHistories]);
  const visible = histories
    .filter(
      (item) =>
        item.effectiveDate >= from &&
        item.effectiveDate <= to &&
        (assetId === undefined || item.assetId === assetId) &&
        (filter === 'ALL' ||
          (filter === 'BALANCE'
            ? item.changeType !== 'BUY' && item.changeType !== 'SELL'
            : item.changeType === filter)),
    )
    .sort(
      (a, b) =>
        b.effectiveDate.localeCompare(a.effectiveDate) ||
        Date.parse(b.occurredAt) - Date.parse(a.occurredAt) ||
        b.historyId - a.historyId,
    );
  const won = (value: number | null) =>
    amountsHidden
      ? '••••••원'
      : value === null
        ? '—'
        : `${value.toLocaleString('ko-KR', { maximumFractionDigits: 2 })}원`;
  function changeRange(nextFrom: string, nextTo: string) {
    if (nextFrom === from && nextTo === to) return;
    if (nextFrom && nextTo && nextFrom <= nextTo) {
      const days = (Date.parse(nextTo) - Date.parse(nextFrom)) / 86400000 + 1;
      if (Number.isFinite(days)) {
        trackPortfolioEvent('portfolio_history_range_changed', { range_days: days });
      }
    }
    setFrom(nextFrom);
    setTo(nextTo);
  }
  const filters = (
    <div className="portfolio-history-toolbar">
      {assetId === undefined && (
        <p className="portfolio-history-order">전체 종목 · 거래일 최신순</p>
      )}
      <div className="portfolio-history-dates">
        <label>
          시작일
          <input
            type="date"
            value={from}
            onChange={(event) => changeRange(event.target.value, to)}
          />
        </label>
        <span aria-hidden="true">—</span>
        <label>
          종료일
          <input
            type="date"
            value={to}
            onChange={(event) => changeRange(from, event.target.value)}
          />
        </label>
      </div>
      <label className="portfolio-history-filter">
        내역 유형
        <select
          value={filter}
          onChange={(event) => {
            const next = event.target.value as 'ALL' | 'BUY' | 'SELL' | 'BALANCE';
            if (next === filter) return;
            trackPortfolioEvent('portfolio_history_filter_changed', { filter: next });
            setFilter(next);
          }}
        >
          <option value="ALL">전체 내역</option>
          <option value="BUY">매수</option>
          <option value="SELL">매도</option>
          <option value="BALANCE">보유정보 변경</option>
        </select>
      </label>
    </div>
  );
  return (
    <section className="portfolio-history" aria-label="거래·변경 내역">
      {assetId === undefined ? (
        filters
      ) : (
        <details className="portfolio-history-filters">
          <summary>
            조회 조건
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="m6 9 6 6 6-6" />
            </svg>
          </summary>
          {filters}
        </details>
      )}
      {!validPeriod ? (
        <p role="alert" className="portfolio-form-error">
          시작일과 종료일을 확인해 주세요. 시작일은 종료일보다 늦을 수 없어요.
        </p>
      ) : loading ? (
        <div className="portfolio-history-skeleton" role="status">
          거래 내역을 불러오고 있어요.
        </div>
      ) : error ? (
        <div className="portfolio-ledger-empty" role="alert">
          <p>{error}</p>
          <button
            className="portfolio-secondary-button"
            onClick={() => {
              trackPortfolioEvent('portfolio_retry_clicked', { target: 'history' });
              setRetry((value) => value + 1);
            }}
          >
            다시 시도
          </button>
        </div>
      ) : !visible.length ? (
        <div className="portfolio-ledger-empty portfolio-history-empty">
          <h3>이 기간에 표시할 내역이 없어요</h3>
          <p>조회 기간을 바꾸거나 매수·매도 거래를 기록해 보세요.</p>
          <button className="portfolio-secondary-button" onClick={onCreate}>
            거래 기록
          </button>
        </div>
      ) : (
        <div className="portfolio-history-list">
          {visible.map((item) => {
            const transaction = item.changeType === 'BUY' || item.changeType === 'SELL';
            const gross =
              transaction && item.transactionQuantity !== null && item.unitPrice !== null
                ? item.transactionQuantity * item.unitPrice
                : null;
            return (
              <details className="portfolio-history-row" key={item.historyId}>
                <summary>
                  <time dateTime={item.effectiveDate}>
                    <span>{item.effectiveDate.replaceAll('-', '.')}</span>
                    {assetId !== undefined && (
                      <small>
                        {transaction
                          ? `${amountsHidden ? '•••' : item.transactionQuantity}주 · ${won(item.unitPrice)}`
                          : '보유정보 변경'}
                      </small>
                    )}
                  </time>
                  <span className={`portfolio-history-type type-${item.changeType.toLowerCase()}`}>
                    {LABELS[item.changeType]}
                  </span>
                  <span className="portfolio-history-name">
                    <strong>{item.name}</strong>
                    <small>{item.assetCode}</small>
                  </span>
                  <span className="portfolio-history-amount">
                    <strong>
                      {gross !== null
                        ? won(gross)
                        : amountsHidden
                          ? '•••주'
                          : `${item.quantity.toLocaleString('ko-KR')}주`}
                    </strong>
                    <small>
                      {transaction
                        ? `${amountsHidden ? '•••' : item.transactionQuantity}주 · ${won(item.unitPrice)}`
                        : '보유정보 변경'}
                    </small>
                  </span>
                  <span className="portfolio-history-chevron" aria-hidden="true">
                    ⌄
                  </span>
                </summary>
                <div className="portfolio-history-detail">
                  <dl>
                    {transaction ? (
                      <>
                        <div>
                          <dt>수수료</dt>
                          <dd>{won(item.fee)}</dd>
                        </div>
                        <div>
                          <dt>세금</dt>
                          <dd>{won(item.tax)}</dd>
                        </div>
                        <div>
                          <dt>{item.changeType === 'BUY' ? '총 매수 지출액' : '총 매도 수령액'}</dt>
                          <dd>
                            {won(
                              gross === null
                                ? null
                                : item.changeType === 'BUY'
                                  ? gross + item.fee + item.tax
                                  : gross - item.fee - item.tax,
                            )}
                          </dd>
                        </div>
                      </>
                    ) : (
                      <div>
                        <dt>평균 매입가</dt>
                        <dd>{won(item.averagePurchasePrice)}</dd>
                      </div>
                    )}
                    <div>
                      <dt>기록 시각</dt>
                      <dd>
                        {new Date(item.occurredAt).toLocaleString('ko-KR', {
                          timeZone: 'Asia/Seoul',
                        })}
                      </dd>
                    </div>
                  </dl>
                  {transaction ? (
                    <div className="portfolio-history-actions">
                      <button onClick={() => onEdit(item)}>거래 수정</button>
                      <button onClick={() => onDelete(item)}>거래 삭제</button>
                    </div>
                  ) : (
                    <p className="portfolio-field-note">
                      보유정보 변경 기록입니다. 매수·매도 거래와 구분해 표시합니다.
                    </p>
                  )}
                </div>
              </details>
            );
          })}
        </div>
      )}
    </section>
  );
}
