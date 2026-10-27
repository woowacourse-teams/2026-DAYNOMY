import { useRef, useState, type ReactNode } from 'react';
import { CalendarBlankIcon } from '@phosphor-icons/react/dist/csr/CalendarBlank';
import { ClockIcon } from '@phosphor-icons/react/dist/csr/Clock';
import { BackspaceIcon } from '@phosphor-icons/react/dist/csr/Backspace';
import type { TransactionFields } from '../transactionForm';
import { PortfolioTradeDateTimePicker } from './PortfolioTradeDateTimePicker';
import { PortfolioExchangeRateDialog } from './PortfolioExchangeRateDialog';

type NumberField = 'quantity' | 'unitPrice';

export function PortfolioQuickTradeFields({
  name,
  code,
  currency,
  fields,
  priceLoading,
  rateLoading,
  rateError,
  rateBaseDate,
  options,
  onChange,
  onChangePrice,
  onChangeStock,
}: {
  name: string;
  code: string;
  currency: 'KRW' | 'USD';
  fields: TransactionFields;
  priceLoading: boolean;
  rateLoading: boolean;
  rateError: string;
  rateBaseDate: string;
  options: ReactNode;
  onChange: (key: keyof TransactionFields, value: string) => void;
  onChangePrice: (input: HTMLInputElement) => void;
  onChangeStock: () => void;
}) {
  const [active, setActive] = useState<NumberField>('quantity');
  const [dateEditor, setDateEditor] = useState<'tradeDate' | 'tradeTime' | null>(null);
  const [rateEditor, setRateEditor] = useState(false);

  const quantityRef = useRef<HTMLInputElement>(null);
  const priceRef = useRef<HTMLInputElement>(null);
  const formattedPrice = fields.unitPrice.replace(/^\d+(?=\.|$)/, (integer) =>
    integer.replace(/\B(?=(\d{3})+(?!\d))/g, ','),
  );

  function enter(key: string) {
    const input = active === 'quantity' ? quantityRef.current : priceRef.current;
    if (!input) return;
    const raw = fields[active];
    const start = input.value
      .slice(0, input.selectionStart ?? input.value.length)
      .replace(/,/g, '').length;
    const end = input.value
      .slice(0, input.selectionEnd ?? input.value.length)
      .replace(/,/g, '').length;
    const from = key === 'delete' && start === end ? Math.max(0, start - 1) : start;
    let next = raw.slice(0, from) + (key === 'delete' ? '' : key) + raw.slice(end);
    if (next.startsWith('.')) next = `0${next}`;
    if (!(active === 'quantity' ? /^\d{0,15}$/ : /^\d{0,15}(\.\d{0,4})?$/).test(next)) return;
    onChange(active, next);
    const position = from + (key === 'delete' ? 0 : next.startsWith('0.') && from === 0 ? 2 : 1);
    window.requestAnimationFrame(() => {
      input.focus();
      let caret = 0;
      let count = 0;
      while (caret < input.value.length && count < position) {
        if (input.value[caret] !== ',') count += 1;
        caret += 1;
      }
      input.setSelectionRange(caret, caret);
    });
  }

  return (
    <>
      <div className="portfolio-quick-stock">
        <div>
          <strong>{name}</strong>
          <small>{code}</small>
        </div>
        <button type="button" onClick={onChangeStock}>
          종목 변경
        </button>
      </div>
      <div className="portfolio-quick-values">
        <label className={active === 'quantity' ? 'is-active' : undefined}>
          <span>수량</span>
          <div>
            <input
              ref={quantityRef}
              aria-label="수량 (주)"
              name="quantity"
              type="text"
              inputMode="numeric"
              autoFocus
              data-autofocus
              value={fields.quantity}
              placeholder="1"
              onFocus={(event) => {
                setActive('quantity');
                event.currentTarget.select();
              }}
              onChange={(event) => onChange('quantity', event.target.value)}
            />
            <small>주</small>
          </div>
        </label>
        <label className={active === 'unitPrice' ? 'is-active' : undefined}>
          <span>매수 단가</span>
          <div>
            <input
              ref={priceRef}
              aria-label={`거래 단가 (${currency === 'USD' ? 'USD' : '원'})`}
              aria-busy={priceLoading}
              name="unitPrice"
              type="text"
              inputMode="decimal"
              value={formattedPrice}
              placeholder="0"
              onFocus={(event) => {
                setActive('unitPrice');
                event.currentTarget.select();
              }}
              onChange={(event) => onChangePrice(event.currentTarget)}
            />
            <small>{currency}</small>
          </div>
        </label>
      </div>
      <div className="portfolio-quick-datetime">
        <button
          type="button"
          className="portfolio-quick-date-chip portfolio-quick-rate-chip"
          aria-label="환율 확인 및 수정"
          aria-haspopup="dialog"
          aria-expanded={rateEditor}
          title={currency === 'KRW' ? '국내 종목 환율 0원' : '1 USD당 적용 환율'}
          onClick={() => setRateEditor(true)}
        >
          <span className="portfolio-quick-won" aria-hidden="true">
            ₩
          </span>
        </button>
        <button
          type="button"
          className="portfolio-quick-date-chip"
          aria-label="거래일 수정"
          aria-expanded={dateEditor === 'tradeDate'}
          aria-haspopup="dialog"
          onClick={() => setDateEditor('tradeDate')}
        >
          <CalendarBlankIcon size={16} weight="regular" aria-hidden="true" />
          <span>{fields.tradeDate ? fields.tradeDate.replaceAll('-', '. ') + '.' : '날짜'}</span>
        </button>
        <button
          type="button"
          className="portfolio-quick-date-chip"
          aria-label="거래 시간 수정"
          aria-expanded={dateEditor === 'tradeTime'}
          aria-haspopup="dialog"
          onClick={() => setDateEditor('tradeTime')}
        >
          <ClockIcon size={16} weight="regular" aria-hidden="true" />
          <span>{fields.tradeTime || '시간'}</span>
        </button>
      </div>
      {rateEditor && (
        <PortfolioExchangeRateDialog
          currency={currency}
          value={fields.exchangeRate ?? ''}
          tradeDate={fields.tradeDate}
          baseDate={rateBaseDate}
          loading={rateLoading}
          error={rateError}
          onClose={() => setRateEditor(false)}
          onConfirm={(value) => {
            if (currency === 'USD') onChange('exchangeRate', value);
            setRateEditor(false);
          }}
        />
      )}
      {dateEditor && (
        <PortfolioTradeDateTimePicker
          field={dateEditor}
          value={fields[dateEditor] ?? ''}
          onClose={() => setDateEditor(null)}
          onConfirm={(value) => {
            onChange(dateEditor, value);
            setDateEditor(null);
          }}
        />
      )}
      <div className="portfolio-quick-keypad" role="group" aria-label="숫자 입력">
        {['1', '2', '3', '4', '5', '6', '7', '8', '9', '.', '0', 'delete'].map((key) => (
          <button
            key={key}
            type="button"
            aria-label={key === 'delete' ? '한 자리 지우기' : key === '.' ? '소수점' : key}
            disabled={key === '.' && active === 'quantity'}
            onPointerDown={(event) => event.preventDefault()}
            onClick={() => enter(key)}
          >
            {key === 'delete' ? (
              <BackspaceIcon size={24} weight="regular" aria-hidden="true" />
            ) : (
              key
            )}
          </button>
        ))}
      </div>
      {currency === 'USD' && rateError && !fields.exchangeRate && (
        <p className="portfolio-field-note" role="status">
          ₩ 버튼에서 적용 환율을 입력해 주세요.
        </p>
      )}
      {options}
    </>
  );
}
