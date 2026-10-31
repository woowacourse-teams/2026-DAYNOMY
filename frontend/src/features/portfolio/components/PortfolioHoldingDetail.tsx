import { CaretRightIcon } from '@phosphor-icons/react/dist/csr/CaretRight';
import { ChartLineUpIcon } from '@phosphor-icons/react/dist/csr/ChartLineUp';
import { DotsThreeIcon } from '@phosphor-icons/react/dist/csr/DotsThree';
import { XIcon } from '@phosphor-icons/react/dist/csr/X';
import { stockCurrency } from '../../stocks/types';
import { PortfolioHistory } from './PortfolioHistory';
import type {
  PortfolioHoldingHistory,
  PortfolioHoldingInput,
  PortfolioHoldingResult,
  PortfolioTransactionType,
} from '../types';
import type { StockSearchItem } from '../../stocks/types';

type Props = {
  stock: StockSearchItem;
  holding?: PortfolioHoldingInput;
  valuation?: PortfolioHoldingResult;
  revision: number;
  localHistories?: PortfolioHoldingHistory[];
  amountsHidden: boolean;
  busy: boolean;
  onClose: () => void;
  onTrade: (type: PortfolioTransactionType) => void;
  onAdjust: (holding: PortfolioHoldingInput) => void;
  onRemove: (holding: PortfolioHoldingInput) => void;
  onEditTransaction: (transaction: PortfolioHoldingHistory) => void;
};

export function PortfolioHoldingDetail({
  stock,
  holding,
  valuation,
  revision,
  localHistories,
  amountsHidden,
  busy,
  onClose,
  onTrade,
  onAdjust,
  onRemove,
  onEditTransaction,
}: Props) {
  const won = (value: number | null | undefined, signed = false) => {
    if (amountsHidden) return '••••••원';
    if (value == null) return '—';
    return `${signed && value > 0 ? '+' : ''}${value.toLocaleString('ko-KR', { maximumFractionDigits: 2 })}원`;
  };
  const profit = holding ? valuation?.profitLoss : 0;
  const rate = holding ? valuation?.returnRate : 0;
  const profitClass =
    profit == null ? '' : profit > 0 ? 'portfolio-gain' : profit < 0 ? 'portfolio-loss' : '';
  return (
    <>
      <header className="portfolio-holding-detail-heading">
        <span className="portfolio-detail-monogram" aria-hidden="true">
          <ChartLineUpIcon size={24} weight="bold" />
        </span>
        <div>
          <h2 id="holding-detail-title" aria-label={`${stock.name} 보유 상세`}>
            <a href={`/stocks/${stock.assetId}`} aria-label={`${stock.name} 종목 정보 보기`}>
              {stock.name}
              <CaretRightIcon size={18} aria-hidden="true" />
            </a>
            <span className="sr-only">보유 상세</span>
          </h2>
          <p>
            {stock.assetCode} · {stock.category === 'ETF' ? 'ETF' : '주식'} · {stock.market}
          </p>
        </div>
        {holding && (
          <details className="portfolio-detail-settings">
            <summary aria-label="보유정보 편집">
              <DotsThreeIcon size={24} weight="bold" aria-hidden="true" />
              <span className="sr-only">보유정보 편집</span>
            </summary>
            <div>
              <button type="button" disabled={busy} onClick={() => onAdjust(holding)}>
                보유정보 정정
              </button>
              <button type="button" disabled={busy} onClick={() => onRemove(holding)}>
                보유자산 제거
              </button>
            </div>
          </details>
        )}
        <button
          type="button"
          className="portfolio-icon-button"
          aria-label="보유 상세 닫기"
          onClick={onClose}
        >
          <XIcon size={22} aria-hidden="true" />
        </button>
      </header>
      <div className="portfolio-detail-scroll">
        <section className="portfolio-detail-value" aria-label="종목 보유 현황" aria-busy={busy}>
          <div className="portfolio-detail-value-heading">
            <div>
              <span className="portfolio-detail-caption sr-only">평가금액</span>
              <strong className="portfolio-detail-total">
                {won(holding ? valuation?.evaluationAmount : 0)}
              </strong>
            </div>
            <span className="portfolio-detail-currency" aria-label="평가 통화 KRW">
              KRW
            </span>
          </div>
          <p>
            원금 <span>{won(holding ? holding.quantity * holding.averagePurchasePrice : 0)}</span>
          </p>
          <dl className="portfolio-detail-metrics">
            <div>
              <dt>수량</dt>
              <dd>{amountsHidden ? '•••' : (holding?.quantity ?? 0).toLocaleString('ko-KR')}주</dd>
            </div>
            <div>
              <dt>구매가{stockCurrency(stock) === 'USD' ? ' (원화)' : ''}</dt>
              <dd>{won(holding?.averagePurchasePrice ?? 0)}</dd>
            </div>
            <div>
              <dt title="최근 확인된 종가 기준">현재가</dt>
              <dd>
                {valuation?.closePrice == null
                  ? '—'
                  : `${valuation.closePrice.toLocaleString('ko-KR', { maximumFractionDigits: 4 })}${stockCurrency(stock) === 'USD' ? ' USD' : '원'}`}
              </dd>
            </div>
            <div className={profitClass}>
              <dt title="현재 보유분의 평가손익 기준">총 수익</dt>
              <dd>
                <span>{won(profit, true)}</span>
                {rate != null && <small>{`(${rate > 0 ? '+' : ''}${rate.toFixed(2)}%)`}</small>}
              </dd>
            </div>
            <div>
              <dt>일간 수익</dt>
              <dd aria-label="일간 수익 정보 없음">—</dd>
            </div>
          </dl>
          <p className="portfolio-detail-price-note">
            {!holding
              ? '현재 보유 중인 수량이 없어요. 이전 거래 내역은 계속 확인할 수 있어요.'
              : valuation?.priceAvailable
                ? `${valuation.baseDate?.replaceAll('-', '.')} 종가 기준${stockCurrency(stock) === 'USD' ? ' · 원화 평가는 참고 환율을 적용합니다.' : ''}`
                : '시세를 확인할 수 없어 평가금액과 손익을 표시하지 않았어요.'}
          </p>
        </section>
        <section className="portfolio-detail-transactions">
          <h3 id="holding-transactions-title">거래 내역</h3>
          <PortfolioHistory
            assetId={stock.assetId}
            revision={revision}
            localHistories={localHistories}
            amountsHidden={amountsHidden}
            onEdit={onEditTransaction}
            onCreate={() => onTrade('BUY')}
          />
        </section>
      </div>
      <footer className="portfolio-detail-footer">
        <button
          type="button"
          className="portfolio-secondary-button"
          disabled={busy || !holding}
          onClick={() => onTrade('SELL')}
        >
          매도
        </button>
        <button
          type="button"
          className="portfolio-primary-button"
          disabled={busy}
          onClick={() => onTrade('BUY')}
        >
          매수
        </button>
      </footer>
    </>
  );
}
