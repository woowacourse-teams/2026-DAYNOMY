import { useContext, useEffect, useMemo, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { AuthContext } from '../../auth/AuthContext';
import { calculateMockPortfolio, estimateStockTax } from './financialPlanning';
import {
  loadSimulatedTrades,
  loadLearningStockPrice,
  removeSimulatedTrade,
  saveSimulatedTrade,
  searchLearningStocks,
} from './learningApi';
import type { SimulatedTrade, LearningStock, SimulatedTradeType } from './learningTypes';
import './financialPlanning.css';
import './financial-overview.css';

const won = new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 0 });
const percent = new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 1 });

function today() {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function money(value: number) {
  return `${won.format(Math.round(value))}원`;
}

export function SimulatedInvestmentPage() {
  const auth = useContext(AuthContext);
  const isLoggedIn = auth?.isLoggedIn ?? false;
  const authLoading = auth?.loading ?? false;
  const [trades, setTrades] = useState<SimulatedTrade[]>([]);
  const [latestPrices, setLatestPrices] = useState<Record<number, number>>({});
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<LearningStock[]>([]);
  const [selected, setSelected] = useState<LearningStock | null>(null);
  const [tradeType, setTradeType] = useState<SimulatedTradeType>('BUY');
  const [quantity, setQuantity] = useState(1);
  const [price, setPrice] = useState(0);
  const [reason, setReason] = useState('');
  const [tradedOn, setTradedOn] = useState(today);
  const [loading, setLoading] = useState(true);
  const [searching, setSearching] = useState(false);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [taxMarket, setTaxMarket] = useState<'DOMESTIC' | 'OVERSEAS'>('OVERSEAS');
  const [taxProfit, setTaxProfit] = useState(3000000);
  const [taxDividends, setTaxDividends] = useState(100000);

  useEffect(() => {
    if (authLoading) return;
    let active = true;
    setLoading(true);
    loadSimulatedTrades(isLoggedIn)
      .then((items) => active && setTrades(items))
      .catch(() => active && setError('모의거래 기록을 불러오지 못했습니다.'))
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [authLoading, isLoggedIn]);

  useEffect(() => {
    const assetIds = [...new Set(trades.map((trade) => trade.assetId))];
    if (assetIds.length === 0) return;
    let active = true;
    Promise.all(
      assetIds.map((assetId) =>
        loadLearningStockPrice(assetId)
          .then((stockPrice) => [assetId, stockPrice.closePrice] as const)
          .catch(() => null),
      ),
    ).then((values) => {
      if (!active) return;
      setLatestPrices((current) => ({
        ...current,
        ...Object.fromEntries(
          values.filter((value): value is readonly [number, number] => value !== null),
        ),
      }));
    });
    return () => {
      active = false;
    };
  }, [trades]);

  const portfolio = useMemo(
    () => calculateMockPortfolio(trades, latestPrices),
    [latestPrices, trades],
  );
  const tax = estimateStockTax(taxMarket, taxProfit, taxDividends);
  const concentration = portfolio.holdings.find((holding) => holding.weight >= 50);

  async function search() {
    if (query.trim().length < 1) return;
    setSearching(true);
    setError('');
    setSelected(null);
    try {
      const stocks = await searchLearningStocks(query.trim());
      setResults(stocks);
      if (stocks.length === 0)
        setMessage('검색 결과가 없습니다. 종목명이나 종목코드를 확인해 주세요.');
    } catch {
      setError('종목을 검색하지 못했습니다. 잠시 후 다시 시도해 주세요.');
    } finally {
      setSearching(false);
    }
  }

  async function selectStock(stock: LearningStock) {
    setSelected(stock);
    setResults([]);
    setQuery(stock.assetName);
    setError('');
    try {
      const latest = await loadLearningStockPrice(stock.assetId);
      setPrice(latest.closePrice);
      setLatestPrices((current) => ({ ...current, [stock.assetId]: latest.closePrice }));
      setMessage(`${latest.baseDate} 종가를 불러왔어요. 원하는 연습 가격으로 바꿀 수 있습니다.`);
    } catch {
      setPrice(0);
      setMessage('최근 종가가 없어 연습할 가격을 직접 입력해 주세요.');
    }
  }

  async function addTrade(event: FormEvent) {
    event.preventDefault();
    if (!selected || quantity < 1 || price <= 0) {
      setError('종목과 수량, 가격을 확인해 주세요.');
      return;
    }
    const heldQuantity =
      portfolio.holdings.find((holding) => holding.assetId === selected.assetId)?.quantity ?? 0;
    if (tradeType === 'SELL' && quantity > heldQuantity) {
      setError(`현재 모의 보유수량 ${heldQuantity}주보다 많이 매도할 수 없습니다.`);
      return;
    }

    setSaving(true);
    setError('');
    try {
      const saved = await saveSimulatedTrade(isLoggedIn, {
        ...selected,
        tradeType,
        quantity,
        price,
        reason: reason.trim() || null,
        tradedOn,
      });
      setTrades((current) => [saved, ...current]);
      setReason('');
      setMessage(
        `${selected.assetName} ${tradeType === 'BUY' ? '매수' : '매도'} 연습을 기록했어요.`,
      );
    } catch {
      setError('모의거래를 저장하지 못했습니다. 보유수량과 입력값을 확인해 주세요.');
    } finally {
      setSaving(false);
    }
  }

  async function removeTrade(id: number) {
    setError('');
    const removing = trades.find((trade) => trade.id === id);
    const currentQuantity =
      portfolio.holdings.find((holding) => holding.assetId === removing?.assetId)?.quantity ?? 0;
    if (removing?.tradeType === 'BUY' && currentQuantity < removing.quantity) {
      setError(
        '이 매수 기록을 삭제하면 보유수량이 음수가 됩니다. 이후 매도 기록을 먼저 삭제해 주세요.',
      );
      return;
    }
    try {
      await removeSimulatedTrade(isLoggedIn, id);
      setTrades((current) => current.filter((trade) => trade.id !== id));
    } catch {
      setError('모의거래 기록을 삭제하지 못했습니다.');
    }
  }

  return (
    <main className="finance-page">
      <section className="finance-hero finance-mock-hero">
        <span className="finance-kicker">돈을 쓰지 않는 투자 연습장</span>
        <h1>실제 돈 없이 모의투자</h1>
        <p>
          종목 검색 → 가상 거래 기록 → 결과 확인. 실제 주문이나 리그 보유자산에는 반영되지 않아요.
        </p>
        <a className="finance-start-link" href="#finance-mock-trade">
          첫 거래 연습하기 ↓
        </a>
        {trades.length > 0 ? (
          <div className="finance-dashboard-summary">
            <span>
              모의 평가금 <strong>{money(portfolio.totalEvaluation)}</strong>
            </span>
            <span>
              평가 손익 <strong>{money(portfolio.totalUnrealizedProfit)}</strong>
            </span>
            <span>
              실현 손익 <strong>{money(portfolio.totalRealizedProfit)}</strong>
            </span>
          </div>
        ) : null}
      </section>

      <section className="finance-section finance-mock-layout">
        <form
          className="finance-form finance-trade-form"
          id="finance-mock-trade"
          onSubmit={addTrade}
        >
          <div className="finance-section-heading">
            <span>01 · 거래 연습</span>
            <h2>{selected ? '가상 거래를 입력하세요' : '먼저 종목을 검색하세요'}</h2>
          </div>
          <div className="finance-stock-search">
            <label>
              종목명 또는 코드
              <input
                value={query}
                onChange={(event) => {
                  setQuery(event.target.value);
                  setSelected(null);
                  setResults([]);
                }}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') {
                    event.preventDefault();
                    void search();
                  }
                }}
                placeholder="예: 삼성전자, 005930"
              />
            </label>
            <button
              type="button"
              onClick={() => void search()}
              disabled={searching || !query.trim()}
            >
              {searching ? '검색 중…' : '종목 검색'}
            </button>
            {results.length > 0 && (
              <ul>
                {results.map((stock) => (
                  <li key={stock.assetId}>
                    <button type="button" onClick={() => void selectStock(stock)}>
                      <strong>{stock.assetName}</strong>
                      <span>
                        {stock.market} · {stock.assetCode} · {stock.category}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
          {selected && (
            <p className="finance-selected-stock">
              <strong>{selected.assetName}</strong>을 선택했어요.
            </p>
          )}
          {selected ? (
            <>
              <div className="finance-trade-fields">
                <label>
                  구분
                  <select
                    value={tradeType}
                    onChange={(event) => setTradeType(event.target.value as SimulatedTradeType)}
                  >
                    <option value="BUY">가상 매수</option>
                    <option value="SELL">가상 매도</option>
                  </select>
                </label>
                <label>
                  수량
                  <input
                    type="number"
                    min="1"
                    step="1"
                    required
                    value={quantity}
                    onChange={(event) => setQuantity(Number(event.target.value))}
                  />
                </label>
                <label>
                  주당 가격
                  <input
                    type="number"
                    min="1"
                    step="1"
                    required
                    value={price}
                    onChange={(event) => setPrice(Number(event.target.value))}
                  />
                </label>
                <label>
                  거래일
                  <input
                    type="date"
                    max={today()}
                    required
                    value={tradedOn}
                    onChange={(event) => setTradedOn(event.target.value)}
                  />
                </label>
              </div>
              <label>
                왜 이 판단을 했나요?
                <input
                  maxLength={200}
                  value={reason}
                  onChange={(event) => setReason(event.target.value)}
                  placeholder="예: 한 종목 쏠림을 줄이기 위해"
                />
              </label>
              <button type="submit" disabled={saving}>
                {saving ? '기록 중…' : `${tradeType === 'BUY' ? '매수' : '매도'} 연습 기록`}
              </button>
            </>
          ) : null}
          {message && (
            <p className="finance-form-message" role="status">
              {message}
            </p>
          )}
          {error && (
            <p className="finance-form-error" role="alert">
              {error}
            </p>
          )}
        </form>
        <div>
          <div className="finance-section-heading">
            <span>02 · 모의 포트폴리오</span>
            <h2>보유 비중과 쏠림 확인</h2>
          </div>
          {loading ? (
            <p className="finance-state">모의거래를 불러오는 중입니다.</p>
          ) : portfolio.holdings.length === 0 ? (
            <div className="finance-state">
              첫 매수 연습을 기록하면 보유 종목과 손익이 여기에 보여요.
            </div>
          ) : (
            <div className="finance-holdings-table">
              {portfolio.holdings.map((holding) => (
                <article key={holding.assetId}>
                  <div>
                    <strong>{holding.assetName}</strong>
                    <small>
                      {holding.market} · {holding.assetCode}
                    </small>
                  </div>
                  <dl>
                    <div>
                      <dt>수량</dt>
                      <dd>{holding.quantity}주</dd>
                    </div>
                    <div>
                      <dt>평균단가</dt>
                      <dd>{money(holding.averagePrice)}</dd>
                    </div>
                    <div>
                      <dt>현재가</dt>
                      <dd>{money(holding.currentPrice)}</dd>
                    </div>
                    <div>
                      <dt>평가손익</dt>
                      <dd className={holding.unrealizedProfit >= 0 ? 'positive' : 'negative'}>
                        {money(holding.unrealizedProfit)} ({percent.format(holding.returnRate)}%)
                      </dd>
                    </div>
                    <div>
                      <dt>비중</dt>
                      <dd>{percent.format(holding.weight)}%</dd>
                    </div>
                  </dl>
                  <div className="finance-weight-bar">
                    <i style={{ width: `${holding.weight}%` }} />
                  </div>
                </article>
              ))}
            </div>
          )}
          {concentration && (
            <p className="finance-concentration-warning">
              {concentration.assetName} 비중이 {percent.format(concentration.weight)}%예요. 한
              종목의 가격 변화가 전체 결과에 크게 영향을 줄 수 있어요.
            </p>
          )}
        </div>
      </section>

      <section className="finance-section">
        <div className="finance-section-heading">
          <span>03 · 투자 일지</span>
          <h2>결과보다 당시의 이유를 복기해요</h2>
        </div>
        {trades.length === 0 ? (
          <p className="finance-state">아직 기록이 없습니다.</p>
        ) : (
          <div className="finance-trade-journal">
            {trades.map((trade) => (
              <article key={trade.id}>
                <span className={trade.tradeType.toLowerCase()}>
                  {trade.tradeType === 'BUY' ? '매수' : '매도'}
                </span>
                <div>
                  <strong>
                    {trade.assetName} {trade.quantity}주 · {money(trade.price)}
                  </strong>
                  <p>{trade.reason || '판단 근거를 남기지 않았어요.'}</p>
                  <small>{trade.tradedOn}</small>
                </div>
                <button type="button" onClick={() => void removeTrade(trade.id)}>
                  삭제
                </button>
              </article>
            ))}
          </div>
        )}
      </section>

      <section className="finance-section finance-tax-section">
        <details className="finance-result-details">
          <summary>
            투자 세금 미리 계산하기<span>국내·해외주식 세금 학습용 계산</span>
          </summary>
          <div className="finance-section-heading">
            <span>04 · 세금 미리보기</span>
            <h2>수익이 아니라 세후 금액을 생각해요</h2>
            <p>
              간단한 학습용 추정이며 실제 신고액은 상품·보유자 지위·외국 납부세액 등에 따라 달라질
              수 있습니다.
            </p>
          </div>
          <div className="finance-tax-calculator">
            <div className="finance-form">
              <label>
                시장
                <select
                  value={taxMarket}
                  onChange={(event) => setTaxMarket(event.target.value as 'DOMESTIC' | 'OVERSEAS')}
                >
                  <option value="DOMESTIC">국내 상장주식 일반 소액주주 가정</option>
                  <option value="OVERSEAS">해외주식</option>
                </select>
              </label>
              <label>
                연간 실현손익
                <input
                  type="number"
                  step="10000"
                  value={taxProfit}
                  onChange={(event) => setTaxProfit(Number(event.target.value))}
                />
              </label>
              <label>
                연간 배당금
                <input
                  type="number"
                  min="0"
                  step="10000"
                  value={taxDividends}
                  onChange={(event) => setTaxDividends(Number(event.target.value))}
                />
              </label>
            </div>
            <article>
              <span>예상 세금</span>
              <strong>{money(tax.capitalGainsTax + tax.dividendIncomeTax)}</strong>
              <dl>
                <div>
                  <dt>양도소득세 추정</dt>
                  <dd>{money(tax.capitalGainsTax)}</dd>
                </div>
                <div>
                  <dt>배당소득세 추정</dt>
                  <dd>{money(tax.dividendIncomeTax)}</dd>
                </div>
                <div>
                  <dt>해외주식 과세표준 추정</dt>
                  <dd>{money(tax.taxableCapitalGain)}</dd>
                </div>
              </dl>
              <small>
                해외주식은 연간 손익을 합산하고 기본공제 250만 원, 지방소득세를 포함한 22%를 단순
                적용했습니다. 국내주식은 일반 소액주주 가정으로 양도세를 0원 표시했으며 증권거래세
                등은 제외했습니다.
              </small>
            </article>
          </div>
          <p className="finance-source-note">
            기준일 2026년 10월 3일 ·{' '}
            <a
              href="https://j.nts.go.kr/nts/cm/cntnts/cntntsView.do?cntntsId=8800&mi=12274"
              target="_blank"
              rel="noreferrer"
            >
              국세청 양도소득세 안내
            </a>
          </p>
        </details>
      </section>

      <section className="finance-next">
        <h2>실제 투자 전, 기본 순서도 확인하세요</h2>
        <p>계좌 선택과 주문 방식, 분산투자 원칙을 초보 가이드에서 이어서 볼 수 있어요.</p>
        <div>
          <Link to="/finance/guides/brokerage-account">증권계좌 가이드</Link>
          <Link to="/finance/guides/first-order">첫 주문 가이드</Link>
        </div>
      </section>
    </main>
  );
}
