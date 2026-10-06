import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  changeSharedHoldingVisibility,
  getSharedPortfolio,
  importSharedHoldings,
  readSourceHoldings,
  type SharedPortfolio,
  type SourceHolding,
} from './sharedPortfolioApi';
import './league.css';
import './shared-portfolio.css';
import { isLeaguePreview } from './leaguePreviewState';
import { HoldingDecisions } from './HoldingDecisions';

const won = (value: number | null) =>
  value === null
    ? '가격 확인 중'
    : `${value.toLocaleString('ko-KR', { maximumFractionDigits: 0 })}원`;
const rate = (value: number | null) =>
  value === null ? '집계 전' : `${value > 0 ? '+' : ''}${value.toFixed(2)}%`;
const errorMessage = (error: unknown) =>
  error instanceof Error ? error.message : '요청을 처리하지 못했습니다. 다시 시도해 주세요.';

export function SharedPortfolioPage() {
  const preview = import.meta.env.DEV && isLeaguePreview();
  const [data, setData] = useState<SharedPortfolio | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [retry, setRetry] = useState(0);
  const [source, setSource] = useState<SourceHolding[] | null>(null);
  const [selection, setSelection] = useState<number[]>([]);
  const [overwrite, setOverwrite] = useState(false);
  const panelHeading = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    if (!source) return;
    panelHeading.current?.focus({ preventScroll: true });
    panelHeading.current?.scrollIntoView?.({ block: 'start' });
  }, [source]);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    setData(null);
    getSharedPortfolio(controller.signal)
      .then((value) => {
        if (!controller.signal.aborted) setData(value);
      })
      .catch((caught) => {
        if (!controller.signal.aborted) setError(errorMessage(caught));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [retry]);

  async function mutate(action: () => Promise<SharedPortfolio>, success: string) {
    if (busy || loading || !data) return;
    setBusy(true);
    setError('');
    setMessage('');
    try {
      setData(await action());
      setMessage(success);
      setSource(null);
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setBusy(false);
    }
  }
  function openImport() {
    setError('');
    setMessage('');
    try {
      const values = readSourceHoldings();
      setSource(values);
      setSelection(
        values
          .filter((h) => !data?.holdings.some((s) => s.assetId === h.assetId))
          .map((h) => h.assetId),
      );
      setOverwrite(false);
    } catch (caught) {
      setError(errorMessage(caught));
    }
  }
  const selected = (source ?? []).filter((h) => selection.includes(h.assetId));
  const conflicts = selected.filter((h) => data?.holdings.some((s) => s.assetId === h.assetId));

  return (
    <main className="league-page shared-page">
      <header className="league-hero">
        <div>
          <h1>공유 포트폴리오</h1>
          <p>원본 포트폴리오에서 종목을 가져오고, 공개할 종목만 선택하세요.</p>
        </div>
        {preview ? (
          <button type="button" disabled={loading || busy || !data} onClick={openImport}>
            샘플 원본 자산 보기
          </button>
        ) : (
          <Link to="/">원본 포트폴리오 보기 →</Link>
        )}
      </header>
      <p className="shared-boundary">
        여기서는 원본을 불러오고 종목을 숨기거나 다시 보여줄 수 있어요. 수량과 매수가는 원본에서
        수정한 뒤 다시 가져오세요.
      </p>
      {message && (
        <p role="status" className="shared-feedback">
          {message}
        </p>
      )}
      {error && (
        <p role="alert" className="league-form-error">
          {error}
        </p>
      )}
      {loading ? (
        <p role="status">공유 자산을 불러오는 중입니다.</p>
      ) : !data ? (
        <button onClick={() => setRetry((n) => n + 1)}>다시 불러오기</button>
      ) : (
        <>
          <section className="shared-overview" aria-label="공유용 자산 요약">
            <dl>
              <div>
                <dt>등록 종목</dt>
                <dd>{data.holdings.length}개</dd>
              </div>
              <div>
                <dt>공유용 평가금액</dt>
                <dd>{won(data.totalEvaluationAmount)}</dd>
              </div>
              <div>
                <dt>공유 포트폴리오 수익률</dt>
                <dd>{rate(data.totalReturnRate)}</dd>
              </div>
            </dl>
            <p>입력한 평균 매수가 대비 종가 평가입니다. 리그 주간 수익률과는 기준이 다릅니다.</p>
            {!data.pricesComplete && (
              <p>가격이 없는 종목이 있어 전체 평가금액과 수익률은 표시하지 않았어요.</p>
            )}
            <div className="shared-actions">
              <button disabled={busy} onClick={openImport}>
                내 포트폴리오에서 가져오기
              </button>
              <Link to="/portfolio/publication">공개 범위·리그 참여 설정 →</Link>
            </div>
          </section>
          {source && (
            <section className="shared-panel" aria-labelledby="shared-import-title">
              <h2 id="shared-import-title" ref={panelHeading} tabIndex={-1}>
                가져올 자산 선택
              </h2>
              <p>
                {preview
                  ? '미리보기용 샘플 원본입니다. 실제 브라우저의 원본 자산은 읽거나 변경하지 않습니다.'
                  : '이 브라우저의 원본을 읽었습니다.'}{' '}
                선택하지 않은 공유용 자산은 유지됩니다.
              </p>
              {source.length === 0 ? (
                <p>
                  원본에 등록된 자산이 없어요. <Link to="/">포트폴리오 입력하기</Link>
                </p>
              ) : (
                <>
                  <fieldset disabled={busy}>
                    <legend>원본 자산 목록</legend>
                    {source.map((h) => {
                      const existing = data.holdings.find((s) => s.assetId === h.assetId);
                      return (
                        <label key={h.assetId} className="shared-import-row">
                          <input
                            type="checkbox"
                            checked={selection.includes(h.assetId)}
                            onChange={() => {
                              setSelection((ids) =>
                                ids.includes(h.assetId)
                                  ? ids.filter((id) => id !== h.assetId)
                                  : [...ids, h.assetId],
                              );
                              setOverwrite(false);
                            }}
                          />
                          <span>
                            <strong>{h.assetName}</strong>
                            <small>
                              원본: {h.quantity}주 · 평균 {won(h.averagePurchasePrice)}
                            </small>
                            {existing && (
                              <small>
                                공유용: {existing.quantity}주 · 평균{' '}
                                {won(existing.averagePurchasePrice)}
                                {existing.hidden ? ' · 숨김' : ''}
                              </small>
                            )}
                          </span>
                        </label>
                      );
                    })}
                  </fieldset>
                  {conflicts.length > 0 && (
                    <label className="shared-confirm">
                      <input
                        type="checkbox"
                        disabled={busy}
                        checked={overwrite}
                        onChange={(e) => setOverwrite(e.target.checked)}
                      />
                      선택한 기존 {conflicts.length}종목의 수량·평균 매수가를 원본 값으로
                      갱신합니다. 숨김·판단 근거는 유지합니다.
                    </label>
                  )}
                  <button
                    disabled={busy || selected.length === 0 || (conflicts.length > 0 && !overwrite)}
                    onClick={() =>
                      void mutate(
                        () => importSharedHoldings(selected, overwrite),
                        `${selected.length}종목을 공유용에 가져왔어요. 원본은 그대로입니다.`,
                      )
                    }
                  >
                    {busy ? '가져오는 중…' : `${selected.length}종목 가져오기`}
                  </button>
                </>
              )}
              <button disabled={busy} onClick={() => setSource(null)}>
                취소
              </button>
            </section>
          )}
          <section className="shared-list" aria-labelledby="shared-holdings-title">
            <h2 id="shared-holdings-title">공유용 자산 {data.holdings.length}개</h2>
            <p>
              공개에 동의한 종목과 판단은 누구나 바로 볼 수 있습니다. 숨김은 공개만 제한하며
              계산에서 제외하지 않습니다.
            </p>
            {data.holdings.length === 0 && (
              <p className="league-state">
                아직 공유용 자산이 없어요. 위에서 원본 포트폴리오를 불러오세요.
              </p>
            )}
            {data.holdings.map((h) => (
              <article key={h.assetId} className="shared-holding">
                <header>
                  <div>
                    <h3>{h.assetName}</h3>
                    <small>
                      {h.category === 'ETF' ? 'ETF' : '주식'} · {h.assetCode} ·{' '}
                      {h.hidden ? '종목 숨김' : '공개 설정에 따름'}
                    </small>
                  </div>
                  <strong>{rate(h.returnRate)}</strong>
                </header>
                <dl>
                  <div>
                    <dt>보유수량</dt>
                    <dd>{h.quantity.toLocaleString()}주</dd>
                  </div>
                  <div>
                    <dt>평균 매수가</dt>
                    <dd>{won(h.averagePurchasePrice)}</dd>
                  </div>
                  <div>
                    <dt>평가금액</dt>
                    <dd>{won(h.evaluationAmount)}</dd>
                  </div>
                  <div>
                    <dt>종가 기준일</dt>
                    <dd>{h.baseDate ?? '가격 없음'}</dd>
                  </div>
                </dl>
                {h.reason && <p className="shared-reason-text">{h.reason}</p>}
                <div className="shared-actions">
                  <button
                    disabled={busy}
                    onClick={() =>
                      void mutate(
                        () => changeSharedHoldingVisibility(h.assetId, !h.hidden),
                        h.hidden
                          ? '종목 숨김을 해제했어요. 공개 설정에 따라 표시됩니다.'
                          : '종목과 판단 근거를 숨겼어요. 수익률 계산에는 계속 포함됩니다.',
                      )
                    }
                  >
                    {h.hidden ? '숨김 해제' : '숨기기'}
                  </button>
                </div>
              </article>
            ))}
          </section>
          <HoldingDecisions holdings={data.holdings} />
          <p className="league-calculation-note">
            자산 변경은 다음 거래일부터 리그 계산에 반영됩니다. 숨긴 종목도 계산에 포함되며, 과거
            순위를 유리하게 다시 쓰지 않습니다. 실계좌 인증 수익률이 아닙니다.
          </p>
        </>
      )}
    </main>
  );
}
