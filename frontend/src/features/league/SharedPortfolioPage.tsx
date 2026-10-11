import { useEffect, useRef, useState } from 'react';
import { Link, Navigate, useLocation } from 'react-router-dom';
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
import { HoldingDecisions } from './HoldingDecisions';
import { getMyInvestorProfile } from './api';
import type { InvestorProfile, InvestmentDecisionInput, InvestmentTransaction } from './types';

const won = (value: number | null) =>
  value === null
    ? '가격 확인 중'
    : `${value.toLocaleString('ko-KR', { maximumFractionDigits: 0 })}원`;
const rate = (value: number | null) =>
  value === null ? '집계 전' : `${value > 0 ? '+' : ''}${value.toFixed(2)}%`;
const errorMessage = (error: unknown) =>
  error instanceof Error ? error.message : '요청을 처리하지 못했습니다. 다시 시도해 주세요.';

export function SharedPortfolioPage() {
  const { hash } = useLocation();
  const [data, setData] = useState<SharedPortfolio | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [retry, setRetry] = useState(0);
  const [source, setSource] = useState<SourceHolding[] | null>(null);
  const [selection, setSelection] = useState<number[]>([]);
  const [overwrite, setOverwrite] = useState(false);
  const [records, setRecords] = useState<InvestmentTransaction[] | null>(null);
  const [recordRefresh, setRecordRefresh] = useState(0);
  const [importReasons, setImportReasons] = useState<Record<number, string>>({});
  const [profileRetry, setProfileRetry] = useState(0);
  const [profile, setProfile] = useState<InvestorProfile | null>(null);
  const [profileLoading, setProfileLoading] = useState(true);
  const [profileError, setProfileError] = useState(false);
  const panelHeading = useRef<HTMLHeadingElement>(null);
  const holdingsHeading = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    if (source) {
      panelHeading.current?.focus({ preventScroll: true });
      panelHeading.current?.scrollIntoView?.({ block: 'start' });
    } else holdingsHeading.current?.focus({ preventScroll: true });
  }, [source]);

  useEffect(() => {
    const controller = new AbortController();
    setProfileLoading(true);
    setProfileError(false);
    getMyInvestorProfile(controller.signal)
      .then((value) => {
        if (!controller.signal.aborted) setProfile(value);
      })
      .catch(() => {
        if (!controller.signal.aborted) setProfileError(true);
      })
      .finally(() => {
        if (!controller.signal.aborted) setProfileLoading(false);
      });
    return () => controller.abort();
  }, [profileRetry]);

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
  async function openImport() {
    if (busy || loading || !data) return;
    setBusy(true);
    setError('');
    setMessage('');
    try {
      const values = await readSourceHoldings();
      setSource(values);
      setSelection(
        values
          .filter((h) => !data?.holdings.some((s) => s.assetId === h.assetId))
          .map((h) => h.assetId),
      );
      setOverwrite(false);
      setImportReasons({});
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setBusy(false);
    }
  }
  const selected = (source ?? []).filter((h) => selection.includes(h.assetId));
  const conflicts = selected.filter((h) => data?.holdings.some((s) => s.assetId === h.assetId));
  const detailsPublic = profile?.profilePublic && profile.detailPublic;
  const selectedStatus = profileLoading
    ? '공개 확인 중'
    : profileError
      ? '공개 확인 필요'
      : detailsPublic
        ? '공개 중'
        : '비공개';

  const hasReason = (assetId: number) =>
    Boolean(
      data?.holdings.find((h) => h.assetId === assetId)?.reason.trim() ||
      records?.some((r) => r.assetId === assetId && r.reason.trim()),
    );
  const missingImportReasons = selected.filter(
    (h) => !hasReason(h.assetId) && !importReasons[h.assetId]?.trim(),
  );
  const missingCount = data?.holdings.filter((h) => !hasReason(h.assetId)).length ?? 0;
  async function importSelection() {
    const decisions: Record<number, InvestmentDecisionInput> = {};
    for (const h of selected)
      if (!hasReason(h.assetId))
        decisions[h.assetId] = {
          reason: importReasons[h.assetId]?.trim() ?? '',
          expectedHoldingPeriod: null,
          expectedChange: '',
          invalidationCondition: '',
          maximumAcceptableLossRate: null,
        };
    const saved = await importSharedHoldings(selected, overwrite, decisions);
    setRecordRefresh((value) => value + 1);
    return saved;
  }
  if (hash === '#league-settings') return <Navigate to="/portfolio/publication" replace />;
  return (
    <main className="league-page shared-page">
      <header className="league-hero">
        <h1>내 리그</h1>
        <Link className="league-secondary-link" to="/portfolio/publication">
          리그 설정
        </Link>
      </header>
      {loading ? (
        <p className="league-state" role="status">
          공유 자산을 불러오는 중입니다.
        </p>
      ) : !data ? (
        <section className="league-state">
          <p role="alert">{error}</p>
          <button onClick={() => setRetry((value) => value + 1)}>다시 불러오기</button>
        </section>
      ) : (
        <>
          {message ? (
            <p role="status" className="shared-feedback">
              {message}
            </p>
          ) : null}
          {error ? (
            <p role="alert" className="league-form-error">
              {error}
            </p>
          ) : null}
          {source ? (
            <section
              className="shared-panel shared-import-panel"
              aria-labelledby="shared-import-title"
            >
              <h2 id="shared-import-title" ref={panelHeading} tabIndex={-1}>
                가져올 자산 선택
              </h2>
              {detailsPublic ? (
                <p className="shared-privacy-note">가져온 종목과 보유 이유가 공개됩니다.</p>
              ) : null}
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
                        <div key={h.assetId} className="shared-import-item">
                          <label className="shared-import-row">
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
                              <strong>
                                {h.assetName}{' '}
                                <small className="shared-import-badge">
                                  {existing ? '이미 가져온 종목' : '새 종목'}
                                </small>
                              </strong>
                              <small>
                                원본: {h.quantity}주 · 평균 {won(h.averagePurchasePrice)}
                              </small>
                              {existing ? (
                                <small>
                                  공유용: {existing.quantity}주 · 평균{' '}
                                  {won(existing.averagePurchasePrice)}
                                  {existing.hidden ? ' · 숨김' : ''}
                                </small>
                              ) : null}
                            </span>
                          </label>
                          {selection.includes(h.assetId) && !hasReason(h.assetId) ? (
                            <label className="shared-import-reason">
                              {h.assetName} 보유 이유 (필수)
                              <textarea
                                aria-label={`${h.assetName} 보유 이유`}
                                required
                                maxLength={500}
                                rows={3}
                                value={importReasons[h.assetId] ?? ''}
                                onChange={(event) =>
                                  setImportReasons({
                                    ...importReasons,
                                    [h.assetId]: event.target.value,
                                  })
                                }
                                placeholder="예: 실적이 좋아지고 있어 다음 분기까지 보유하려고 해요."
                              />
                            </label>
                          ) : null}
                        </div>
                      );
                    })}
                  </fieldset>
                  {selected.length === 0 ? (
                    <p>가져올 종목을 선택하면 아래 버튼이 활성화돼요.</p>
                  ) : missingImportReasons.length > 0 ? (
                    <p>{missingImportReasons.length}개 종목의 보유 이유를 작성해 주세요.</p>
                  ) : null}
                  {conflicts.length > 0 ? (
                    <label className="shared-confirm">
                      <input
                        type="checkbox"
                        disabled={busy}
                        checked={overwrite}
                        onChange={(event) => setOverwrite(event.target.checked)}
                      />
                      선택한 기존 {conflicts.length}종목의 수량·평균 매수가를 원본 값으로
                      갱신합니다. 숨김·판단 근거는 유지합니다.
                    </label>
                  ) : null}
                </>
              )}
              <div className="shared-form-actions">
                {source.length > 0 ? (
                  <button
                    className="shared-primary-button"
                    disabled={
                      busy ||
                      selected.length === 0 ||
                      missingImportReasons.length > 0 ||
                      (conflicts.length > 0 && !overwrite)
                    }
                    onClick={() =>
                      void mutate(importSelection, `${selected.length}종목을 가져왔습니다.`)
                    }
                  >
                    {busy ? '가져오는 중…' : `${selected.length}종목 가져오기`}
                  </button>
                ) : null}
                <button disabled={busy} onClick={() => setSource(null)}>
                  취소
                </button>
              </div>
            </section>
          ) : null}
          <div hidden={source !== null}>
            {data.holdings.length > 0 ? (
              <section className="shared-overview" aria-label="내 자산 요약">
                <header>
                  <h2>내 자산 요약</h2>
                  <span>나만 보기</span>
                </header>
                <dl>
                  <div>
                    <dt>평가금액</dt>
                    <dd>{won(data.pricesComplete ? data.totalEvaluationAmount : null)}</dd>
                  </div>
                  <div>
                    <dt>매수가 대비 수익률</dt>
                    <dd>{rate(data.pricesComplete ? data.totalReturnRate : null)}</dd>
                  </div>
                </dl>
                {!data.pricesComplete ? (
                  <p>가격이 없는 종목이 있어 전체 평가금액과 수익률은 표시하지 않았어요.</p>
                ) : null}
              </section>
            ) : null}

            <section className="shared-list" aria-labelledby="shared-holdings-title">
              <header className="shared-section-heading">
                <div>
                  <h2 id="shared-holdings-title" ref={holdingsHeading} tabIndex={-1}>
                    리그 종목 {data.holdings.length}개
                  </h2>
                  {data.holdings.length > 0 ? (
                    <span className="shared-progress">
                      보유 이유 {data.holdings.length - missingCount}/{data.holdings.length}
                    </span>
                  ) : null}
                </div>
                <button
                  className="shared-primary-button"
                  aria-label="내 포트폴리오에서 가져오기"
                  disabled={busy || records === null}
                  aria-busy={busy}
                  onClick={() => void openImport()}
                >
                  종목 가져오기
                </button>
              </header>
              {data.holdings.length === 0 ? (
                <div className="shared-empty-guide">
                  <strong>리그에 포함할 종목이 아직 없어요</strong>
                </div>
              ) : null}
              <HoldingDecisions
                holdings={data.holdings}
                portfolioBusy={busy}
                publicationStatus={selectedStatus}
                refreshKey={recordRefresh}
                onRecordsChange={setRecords}
                onVisibilityChange={(assetId, hidden) =>
                  void mutate(
                    () => changeSharedHoldingVisibility(assetId, hidden),
                    hidden ? '공개에서 제외했습니다.' : '공개 대상으로 선택했습니다.',
                  )
                }
              />
            </section>
            {profileError ? (
              <p role="alert" className="league-inline-error">
                공개 상태 확인 실패{' '}
                <button onClick={() => setProfileRetry((value) => value + 1)}>다시 확인</button>
              </p>
            ) : null}
            <Link className="league-text-link" to="/">
              원본 포트폴리오 수정 →
            </Link>
          </div>
        </>
      )}
    </main>
  );
}
