import { useState } from 'react';
import type { PortfolioHoldingInput } from '../types';

function normalizePriceInput(value: string) {
  return value.replace(/,/g, '');
}

function formatPriceInput(value: string) {
  const [integer, fraction] = value.split('.');
  return (
    integer.replace(/\B(?=(\d{3})+(?!\d))/g, ',') + (fraction !== undefined ? `.${fraction}` : '')
  );
}

type PortfolioEditorProps = {
  isLoggedIn?: boolean;
  holding: PortfolioHoldingInput;
  onClose: () => void;
  onSave: (holding: PortfolioHoldingInput) => Promise<void>;
};

export function PortfolioEditor({
  isLoggedIn = true,
  holding,
  onClose,
  onSave,
}: PortfolioEditorProps) {
  const [quantity, setQuantity] = useState(String(holding.quantity));
  const [averagePrice, setAveragePrice] = useState(String(holding.averagePurchasePrice));
  const [saveLoading, setSaveLoading] = useState(false);
  const [saveError, setSaveError] = useState('');
  const quantityNumber = Number(quantity);
  const averagePriceNumber = Number(averagePrice);
  const canAdjustAveragePrice =
    Number.isFinite(averagePriceNumber) &&
    averagePriceNumber >= 0.01 &&
    /^\d{1,15}(\.\d{1,2})?$/.test(averagePrice);
  const canSave =
    Number.isSafeInteger(quantityNumber) && quantityNumber > 0 && canAdjustAveragePrice;
  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canSave || saveLoading) return;
    setSaveLoading(true);
    setSaveError('');
    try {
      await onSave({
        ...holding,
        quantity: quantityNumber,
        averagePurchasePrice: averagePriceNumber,
      });
    } catch (caughtError) {
      setSaveError(
        caughtError instanceof Error ? caughtError.message : '보유자산을 저장하지 못했습니다.',
      );
    } finally {
      setSaveLoading(false);
    }
  }
  function adjustAveragePrice(amount: number) {
    if (!canAdjustAveragePrice) return;
    setAveragePrice(String(Math.max(0.01, Math.round((averagePriceNumber + amount) * 100) / 100)));
  }
  return (
    <section className="portfolio-dialog">
      <form className="is-details-step" onSubmit={submit}>
        <div className="portfolio-dialog-title">
          <div>
            <div>
              <h2 id="portfolio-dialog-title">보유정보 정정</h2>
              <p>
                {isLoggedIn
                  ? '현재 보유 수량과 평균 매입가를 정정합니다. 매수·매도로 기록되지 않아요.'
                  : '보유 수량과 평균 매입가를 입력하세요. 현재 탭에만 임시 보관돼요.'}
              </p>
            </div>
          </div>
          <button
            type="button"
            className="portfolio-dialog-close"
            aria-label="닫기"
            disabled={saveLoading}
            onClick={onClose}
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="m6 6 12 12M18 6 6 18" />
            </svg>
          </button>
        </div>
        <div className="portfolio-selected-stock">
          <span className="portfolio-monogram">{holding.name.slice(0, 1)}</span>
          <div>
            <strong>{holding.name}</strong>
            <small>
              {holding.category === 'ETF' ? 'ETF' : '주식'} · {holding.assetCode} · {holding.market}
            </small>
          </div>
        </div>
        <div className="portfolio-form-grid">
          <div className="portfolio-field">
            <label htmlFor="portfolio-quantity-input">보유수량</label>
            <div className="portfolio-input-with-unit">
              <input
                id="portfolio-quantity-input"
                type="number"
                min="1"
                step="1"
                inputMode="numeric"
                placeholder="0"
                value={quantity}
                onChange={(event) => setQuantity(event.target.value)}
              />
              <span>주</span>
            </div>
          </div>
          <div className="portfolio-field">
            <label id="average-price-label" htmlFor="average-price-input">
              평균 매수가
            </label>
            <div className="portfolio-price-control" role="group" aria-label="평균 매수가 조절">
              <button
                type="button"
                aria-label="평균 매수가 1,000원 내리기"
                disabled={!canAdjustAveragePrice}
                onClick={() => adjustAveragePrice(-1000)}
              >
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M6 12h12" />
                </svg>
              </button>
              <div className="portfolio-input-with-unit">
                <input
                  id="average-price-input"
                  type="text"
                  inputMode="decimal"
                  pattern="[0-9,]+(\.[0-9]{1,2})?"
                  placeholder="0"
                  value={formatPriceInput(averagePrice)}
                  onChange={(event) => setAveragePrice(normalizePriceInput(event.target.value))}
                />
                <span>원</span>
              </div>
              <button
                type="button"
                aria-label="평균 매수가 1,000원 올리기"
                disabled={!canAdjustAveragePrice}
                onClick={() => adjustAveragePrice(1000)}
              >
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M12 6v12M6 12h12" />
                </svg>
              </button>
            </div>
          </div>
        </div>
        <div className="portfolio-dialog-actions">
          {saveError ? <p role="alert">{saveError}</p> : null}
          <button
            type="button"
            className="portfolio-secondary-button"
            disabled={saveLoading}
            onClick={onClose}
          >
            취소
          </button>
          <button
            type="submit"
            className="portfolio-primary-button"
            disabled={!canSave || saveLoading}
          >
            {saveLoading ? '저장 중' : '수정하기'}
          </button>
        </div>
      </form>
    </section>
  );
}
