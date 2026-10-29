import { useEffect, useId, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { XIcon } from '@phosphor-icons/react/dist/csr/X';
import { ArrowClockwiseIcon } from '@phosphor-icons/react/dist/csr/ArrowClockwise';
import { BackspaceIcon } from '@phosphor-icons/react/dist/csr/Backspace';
import { getUsdKrwRate } from '../../stocks/api';
import { PortfolioDialog } from './PortfolioDialog';

const format = (value: string) =>
  value.replace(/^\d+(?=\.|$)/, (integer) => integer.replace(/\B(?=(\d{3})+(?!\d))/g, ','));

export function PortfolioExchangeRateDialog({
  currency,
  value,
  tradeDate,
  baseDate,
  loading,
  error,
  onClose,
  onConfirm,
}: {
  currency: 'KRW' | 'USD';
  value: string;
  tradeDate: string;
  baseDate: string;
  loading: boolean;
  error: string;
  onClose: () => void;
  onConfirm: (value: string) => void;
}) {
  const domestic = currency === 'KRW';
  const titleId = useId();
  const noteId = useId();
  const [draft, setDraft] = useState(domestic ? '0' : value);
  const [quoteDate, setQuoteDate] = useState(baseDate);
  const [refreshing, setRefreshing] = useState(false);
  const [refreshError, setRefreshError] = useState<string | null>(null);
  const edited = useRef(false);
  const input = useRef<HTMLInputElement>(null);
  const controller = useRef<AbortController | null>(null);
  useEffect(() => () => controller.current?.abort(), []);
  useEffect(() => {
    if (!domestic && !edited.current) {
      setDraft(value);
      setQuoteDate(baseDate);
    }
  }, [value, baseDate, domestic]);
  const valid = domestic || (/^\d{1,10}(\.\d{1,6})?$/.test(draft) && Number(draft) > 0);
  const message = refreshError ?? error;
  const busy = !domestic && (refreshing || (loading && !edited.current));

  function update(raw: string, position: number) {
    if (!/^\d{0,10}(\.\d{0,6})?$/.test(raw)) return;
    controller.current?.abort();
    setRefreshing(false);
    edited.current = true;
    setDraft(raw);
    window.requestAnimationFrame(() => {
      if (!input.current) return;
      input.current.focus();
      let caret = 0;
      let digits = 0;
      while (caret < input.current.value.length && digits < position) {
        if (input.current.value[caret] !== ',') digits++;
        caret++;
      }
      input.current.setSelectionRange(caret, caret);
    });
  }
  function enter(key: string) {
    const field = input.current;
    if (!field || domestic) return;
    const start = field.value
      .slice(0, field.selectionStart ?? field.value.length)
      .replace(/,/g, '').length;
    const end = field.value
      .slice(0, field.selectionEnd ?? field.value.length)
      .replace(/,/g, '').length;
    const from = key === 'delete' && start === end ? Math.max(0, start - 1) : start;
    let next = draft.slice(0, from) + (key === 'delete' ? '' : key) + draft.slice(end);
    const prefixZero = next.startsWith('.');
    if (prefixZero) next = `0${next}`;
    update(next, from + (key === 'delete' ? 0 : prefixZero && from === 0 ? 2 : 1));
  }
  async function refresh() {
    if (domestic) return;
    controller.current?.abort();
    const request = new AbortController();
    controller.current = request;
    edited.current = true;
    setRefreshing(true);
    setRefreshError('');
    try {
      const quote = await getUsdKrwRate(tradeDate, request.signal);
      if (!request.signal.aborted) {
        setDraft(String(quote.rate));
        setQuoteDate(quote.baseDate);
      }
    } catch {
      if (!request.signal.aborted) setRefreshError('환율을 가져오지 못했어요. 직접 입력해 주세요.');
    } finally {
      if (!request.signal.aborted) setRefreshing(false);
    }
  }
  return createPortal(
    <div onKeyDown={(event) => event.stopPropagation()}>
      <PortfolioDialog titleId={titleId} onClose={onClose} className="portfolio-exchange-picker">
        <header className="portfolio-picker-heading">
          <h2 id={titleId}>당시 환율</h2>
          <button type="button" aria-label="환율 창 닫기" onClick={onClose}>
            <XIcon size={26} weight="thin" aria-hidden="true" />
          </button>
        </header>
        <p id={noteId} className="portfolio-exchange-description">
          {domestic
            ? '국내 주식·ETF는 환율을 적용하지 않아요.'
            : '거래 시점의 1 USD당 원화 환율을 입력해 주세요.'}
        </p>
        <label className="portfolio-exchange-value">
          <span className="sr-only">
            {domestic ? '적용 환율 (국내 종목)' : '적용 환율 (1 USD당 원)'}
          </span>
          <input
            ref={input}
            name="exchangeRate"
            inputMode="decimal"
            type="text"
            value={format(draft)}
            style={{ width: `${Math.max(1, format(draft).length)}ch` }}
            placeholder="0"
            readOnly={domestic}
            data-autofocus={!domestic || undefined}
            aria-describedby={noteId}
            aria-busy={busy}
            onFocus={(event) => event.currentTarget.select()}
            onChange={(event) =>
              update(
                event.currentTarget.value.replace(/,/g, ''),
                event.currentTarget.value
                  .slice(0, event.currentTarget.selectionStart ?? 0)
                  .replace(/,/g, '').length,
              )
            }
          />
          <span aria-hidden="true">KRW</span>
        </label>
        <div className="portfolio-exchange-reference">
          <span>
            {domestic
              ? '적용 환율 0원'
              : busy
                ? '참고 환율을 불러오는 중…'
                : `${quoteDate ? '참고 환율' : '거래일'} (${(quoteDate || tradeDate).replaceAll('-', '. ')}.)`}
          </span>
          {!domestic && (
            <button
              type="button"
              aria-label="거래일 환율 다시 조회"
              disabled={refreshing}
              onClick={refresh}
            >
              <ArrowClockwiseIcon size={16} aria-hidden="true" />
            </button>
          )}
        </div>
        {!domestic && message && (
          <p className="portfolio-exchange-error" role="status">
            {message}
          </p>
        )}
        <div className="portfolio-quick-keypad" role="group" aria-label="환율 숫자 입력">
          {['1', '2', '3', '4', '5', '6', '7', '8', '9', '.', '0', 'delete'].map((key) => (
            <button
              key={key}
              type="button"
              aria-label={key === 'delete' ? '한 자리 지우기' : key === '.' ? '소수점' : key}
              disabled={domestic}
              onPointerDown={(event) => event.preventDefault()}
              onClick={() => enter(key)}
            >
              {key === 'delete' ? <BackspaceIcon size={24} aria-hidden="true" /> : key}
            </button>
          ))}
        </div>
        <button
          type="button"
          className="portfolio-picker-confirm"
          disabled={!valid}
          onClick={() => onConfirm(domestic ? '0' : draft)}
        >
          {domestic ? '확인' : '저장'}
        </button>
      </PortfolioDialog>
    </div>,
    document.body,
  );
}
