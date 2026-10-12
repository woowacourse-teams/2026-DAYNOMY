import { useEffect, useState } from 'react';
import { getStockRelatedContents } from '../../stock-content/api';
import { STOCK_CONTENT_SOURCE_LABELS, type StockRelatedContent } from '../../stock-content/types';
import { PortfolioHistory } from './PortfolioHistory';
import type {
  PortfolioHoldingHistory,
  PortfolioHoldingInput,
  PortfolioHoldingResult,
  PortfolioTransactionType,
  StockSearchItem,
} from '../types';

type Props = {
  stock: StockSearchItem;
  holding?: PortfolioHoldingInput;
  valuation?: PortfolioHoldingResult;
  revision: number;
  isLoggedIn: boolean;
  amountsHidden: boolean;
  busy: boolean;
  onClose: () => void;
  onTrade: (type: PortfolioTransactionType) => void;
  onAdjust: (holding: PortfolioHoldingInput) => void;
  onRemove: (holding: PortfolioHoldingInput) => void;
  onEditTransaction: (transaction: PortfolioHoldingHistory) => void;
  onDeleteTransaction: (transaction: PortfolioHoldingHistory) => void;
};

const RELATED_CONTENT_PREVIEW_COUNT = 10;

function getSocialAccount(content: StockRelatedContent) {
  if (content.sourceType !== 'THREADS' && content.sourceType !== 'X') return null;

  try {
    const account = new URL(content.url).pathname.split('/').filter(Boolean)[0];
    if (!account) return null;
    return account.startsWith('@') ? account : `@${account}`;
  } catch {
    return null;
  }
}

function getRelatedContentPreview(content: StockRelatedContent) {
  const title = content.title.trim();
  if (title !== 'X 게시물' && title !== 'Threads 게시물') return title;
  return '게시물 내용을 원문에서 확인하세요.';
}

export function PortfolioHoldingDetail({
  stock,
  holding,
  valuation,
  revision,
  isLoggedIn,
  amountsHidden,
  busy,
  onClose,
  onTrade,
  onAdjust,
  onRemove,
  onEditTransaction,
  onDeleteTransaction,
}: Props) {
  const [relatedContents, setRelatedContents] = useState<StockRelatedContent[]>([]);
  const [relatedContentLoading, setRelatedContentLoading] = useState(true);
  const [relatedContentError, setRelatedContentError] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    setRelatedContents([]);
    setRelatedContentLoading(true);
    setRelatedContentError('');
    getStockRelatedContents(stock.assetId, controller.signal)
      .then((response) => {
        if (!controller.signal.aborted) setRelatedContents(response.contents);
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setRelatedContentError(
            error instanceof Error ? error.message : '소식을 불러오지 못했습니다.',
          );
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setRelatedContentLoading(false);
      });

    return () => controller.abort();
  }, [stock.assetId]);

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
          {stock.name.slice(0, 1)}
        </span>
        <div>
          <h2 id="holding-detail-title">
            {stock.name} <span className="sr-only">보유 상세</span>
          </h2>
          <p>
            {stock.assetCode} · {stock.category === 'ETF' ? 'ETF' : '주식'} · {stock.market}
          </p>
        </div>
        <button
          type="button"
          className="portfolio-icon-button"
          aria-label="보유 상세 닫기"
          onClick={onClose}
        >
          ×
        </button>
      </header>
      <div className="portfolio-detail-scroll">
        <section className="portfolio-detail-value" aria-label="종목 보유 현황" aria-busy={busy}>
          <span className="portfolio-detail-caption">평가금액</span>
          <strong className="portfolio-detail-total">
            {won(holding ? valuation?.evaluationAmount : 0)}
          </strong>
          <p>
            매입금액{' '}
            <span>{won(holding ? holding.quantity * holding.averagePurchasePrice : 0)}</span>
          </p>
          <dl className="portfolio-detail-metrics">
            <div>
              <dt>보유 수량</dt>
              <dd>{amountsHidden ? '•••' : (holding?.quantity ?? 0).toLocaleString('ko-KR')}주</dd>
            </div>
            <div>
              <dt>평균 매수가</dt>
              <dd>{won(holding?.averagePurchasePrice ?? 0)}</dd>
            </div>
            <div>
              <dt>최근 종가</dt>
              <dd>
                {valuation?.closePrice == null
                  ? '—'
                  : `${valuation.closePrice.toLocaleString('ko-KR')}원`}
              </dd>
            </div>
            <div className={profitClass}>
              <dt>평가손익</dt>
              <dd>{won(profit, true)}</dd>
            </div>
            <div className={profitClass}>
              <dt>수익률</dt>
              <dd>{rate == null ? '—' : `${rate > 0 ? '+' : ''}${rate.toFixed(2)}%`}</dd>
            </div>
            <div>
              <dt>자산 비중</dt>
              <dd>
                {holding
                  ? valuation?.weight == null
                    ? '—'
                    : `${valuation.weight.toFixed(2)}%`
                  : '0.00%'}
              </dd>
            </div>
          </dl>
          <p className="portfolio-detail-price-note">
            {!holding
              ? '현재 보유 중인 수량이 없어요. 이전 거래 내역은 계속 확인할 수 있어요.'
              : valuation?.priceAvailable
                ? `${valuation.baseDate?.replaceAll('-', '.')} 종가 기준`
                : '시세를 확인할 수 없어 평가금액과 손익을 표시하지 않았어요.'}
          </p>
          <div className="portfolio-detail-tools">
            <a href={`/stocks/${stock.assetId}`}>
              종목 정보 보기 <span aria-hidden="true">↗</span>
            </a>
            {holding && (
              <details className="portfolio-detail-settings">
                <summary>보유정보 편집</summary>
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
          </div>
        </section>
        <aside className="portfolio-related-content-panel" aria-label="소식">
          <div className="portfolio-related-content-heading">
            <strong>소식</strong>
            {relatedContents.length > 0 ? <span>{relatedContents.length}건</span> : null}
          </div>
          {relatedContentLoading ? <p role="status">소식을 불러오는 중입니다.</p> : null}
          {relatedContentError ? <p role="alert">{relatedContentError}</p> : null}
          {!relatedContentLoading && !relatedContentError && relatedContents.length === 0 ? (
            <p>등록된 소식이 없습니다.</p>
          ) : null}
          {relatedContents.length > 0 ? (
            <ul>
              {relatedContents.slice(0, RELATED_CONTENT_PREVIEW_COUNT).map((content) => {
                const sourceLabel = STOCK_CONTENT_SOURCE_LABELS[content.sourceType];
                const account = getSocialAccount(content);
                const opensInNewTab = content.sourceType !== 'INTERNAL_NEWS';

                return (
                  <li key={content.id}>
                    <a
                      className="portfolio-related-content-card"
                      href={content.url}
                      target={opensInNewTab ? '_blank' : undefined}
                      rel={opensInNewTab ? 'noreferrer' : undefined}
                    >
                      {content.imageUrl ? (
                        <img src={content.imageUrl} alt="" loading="lazy" />
                      ) : (
                        <span className="portfolio-related-content-source-icon" aria-hidden="true">
                          {sourceLabel}
                        </span>
                      )}
                      <span className="portfolio-related-content-copy">
                        <span className="portfolio-related-content-meta">
                          <strong>{sourceLabel}</strong>
                          {account ? <small>{account}</small> : null}
                        </span>
                        <span className="portfolio-related-content-title">
                          {getRelatedContentPreview(content)}
                        </span>
                        <small className="portfolio-related-content-action">
                          {opensInNewTab ? '원문 보기 ↗' : '소식 보기'}
                        </small>
                      </span>
                    </a>
                  </li>
                );
              })}
            </ul>
          ) : null}
          {relatedContents.length > 0 ? (
            <a className="portfolio-related-content-more" href={`/stocks/${stock.assetId}`}>
              전체 소식 보기
            </a>
          ) : null}
        </aside>
        {isLoggedIn && (
          <section
            className="portfolio-detail-transactions"
            aria-labelledby="holding-transactions-title"
          >
            <h3 id="holding-transactions-title">거래 내역</h3>
            <p>내역을 펼쳐 거래를 수정하거나 삭제할 수 있어요.</p>
            <PortfolioHistory
              assetId={stock.assetId}
              revision={revision}
              amountsHidden={amountsHidden}
              onEdit={onEditTransaction}
              onDelete={onDeleteTransaction}
              onCreate={() => onTrade('BUY')}
            />
          </section>
        )}
      </div>
      {isLoggedIn && (
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
      )}
    </>
  );
}
