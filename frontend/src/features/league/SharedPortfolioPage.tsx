import { useEffect, useId, useRef, useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import {
  changeSharedHoldingVisibility,
  getSharedPortfolio,
  importSharedHoldings,
  readSourceHoldings,
  removeSharedHolding,
  type SharedPortfolio,
  type SourceHolding,
} from './sharedPortfolioApi';
import './league.css';
import './shared-portfolio.css';
import { HoldingDecisions } from './HoldingDecisions';
import { getMyInvestorProfile } from './api';
import { leagueErrorType, trackLeagueEvent } from './analytics';
import type { InvestorProfile, InvestmentDecisionInput, InvestmentDecisionRecord } from './types';
import { useLeaguePageHeading } from './useLeaguePageHeading';

const won = (value: number | null) =>
  value === null
    ? '가격 확인 중'
    : `${value.toLocaleString('ko-KR', { maximumFractionDigits: 0 })}원`;
const errorMessage = (error: unknown) =>
  error instanceof Error ? error.message : '요청을 처리하지 못했습니다. 다시 시도해 주세요.';

export function SharedPortfolioPage() {
  const { hash } = useLocation();
  const navigate = useNavigate();
  const [data, setData] = useState<SharedPortfolio | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [retry, setRetry] = useState(0);
  const [source, setSource] = useState<SourceHolding[] | null>(null);
  const [selection, setSelection] = useState<number[]>([]);
  const [overwrite, setOverwrite] = useState(false);
  const [records, setRecords] = useState<InvestmentDecisionRecord[] | null>(null);
  const [recordRefresh, setRecordRefresh] = useState(0);
  const [importReasons, setImportReasons] = useState<Record<number, string>>({});
  const [profileRetry, setProfileRetry] = useState(0);
  const [profile, setProfile] = useState<InvestorProfile | null>(null);
  const [profileLoading, setProfileLoading] = useState(true);
  const [profileError, setProfileError] = useState(false);
  const panelHeading = useRef<HTMLHeadingElement>(null);
  const holdingsHeading = useLeaguePageHeading('리그 종목 관리');
  const wasImportOpen = useRef(false);
  const importGuidanceId = useId();
  useEffect(() => {
    if (loading) return;
    if (source) {
      panelHeading.current?.focus({ preventScroll: true });
      panelHeading.current?.scrollIntoView?.({ block: 'start' });
    } else if (wasImportOpen.current) {
      const heading = holdingsHeading.current;
      heading?.focus({ preventScroll: true });
      heading?.scrollIntoView?.({ block: 'start' });
    }
    wasImportOpen.current = source !== null;
  }, [loading, source, holdingsHeading]);

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
    let surface: 'holdings' | 'source_holdings' = 'holdings';
    getSharedPortfolio(controller.signal)
      .then(async (value) => {
        if (controller.signal.aborted) return;
        setData(value);
        trackLeagueEvent('league_load_result', {
          surface: 'holdings',
          status: 'success',
          result_count: value.holdings.length,
        });
        if (hash === '#select') {
          surface = 'source_holdings';
          const values = await readSourceHoldings();
          if (controller.signal.aborted) return;
          setSource(values);
          trackLeagueEvent('league_load_result', {
            surface: 'source_holdings',
            status: 'success',
            result_count: values.length,
          });
          setSelection(
            values
              .filter((h) => !value.holdings.some((s) => s.assetId === h.assetId))
              .map((h) => h.assetId),
          );
        }
      })
      .catch((caught) => {
        if (!controller.signal.aborted) {
          setError(errorMessage(caught));
          trackLeagueEvent('league_load_result', {
            surface,
            status: 'error',
            error_type: leagueErrorType(caught),
          });
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [retry, hash]);

  async function mutate(
    action: () => Promise<SharedPortfolio>,
    success: string,
    operation: 'import_holdings' | 'change_visibility' | 'remove_holding',
    context: { hidden?: boolean; overwrite_existing?: boolean } = {},
  ) {
    if (busy || loading || !data) return false;
    setBusy(true);
    setError('');
    setMessage('');
    trackLeagueEvent('league_action', {
      surface: 'holdings',
      action: operation,
      status: 'submitted',
      ...context,
    });
    try {
      const result = await action();
      setData(result);
      trackLeagueEvent('league_action', {
        surface: 'holdings',
        action: operation,
        status: 'success',
        result_count: result.holdings.length,
        ...context,
      });
      if (
        operation === 'import_holdings' &&
        hash === '#select' &&
        !profileLoading &&
        !profileError &&
        !profile?.leagueEnabled &&
        result.holdings.length > 0 &&
        result.holdings.every(
          (h) =>
            h.reason.trim() ||
            hasReason(h.assetId) ||
            (selection.includes(h.assetId) && importReasons[h.assetId]?.trim()),
        )
      )
        navigate('/portfolio/publication?join=true');
      setMessage(success);
      setSource(null);
      return true;
    } catch (caught) {
      setError(errorMessage(caught));
      trackLeagueEvent('league_action', {
        surface: 'holdings',
        action: operation,
        status: 'error',
        error_type: leagueErrorType(caught),
        ...context,
      });
      return false;
    } finally {
      setBusy(false);
    }
  }
  async function openImport() {
    if (busy || loading || !data) return;
    setBusy(true);
    setError('');
    setMessage('');
    trackLeagueEvent('league_action', {
      surface: 'source_holdings',
      action: 'open_import',
      status: 'submitted',
    });
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
      trackLeagueEvent('league_action', {
        surface: 'source_holdings',
        action: 'open_import',
        status: 'success',
        result_count: values.length,
      });
    } catch (caught) {
      setError(errorMessage(caught));
      trackLeagueEvent('league_action', {
        surface: 'source_holdings',
        action: 'open_import',
        status: 'error',
        error_type: leagueErrorType(caught),
      });
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
    <main className="league-page shared-page league-holdings-page">
      <header className="league-hero">
        <h1 ref={holdingsHeading} tabIndex={-1}>
          종목 관리
        </h1>
        <Link className="league-text-link" to="/portfolio/publication">
          공개 설정
        </Link>
      </header>
      {loading ? (
        <p className="league-state" role="status">
          종목을 불러오는 중이에요.
        </p>
      ) : !data ? (
        <section className="league-state">
          <p role="alert">{error}</p>
          <button
            onClick={() => {
              trackLeagueEvent('league_action', {
                surface: 'holdings',
                action: 'retry',
                status: 'clicked',
              });
              setRetry((value) => value + 1);
            }}
          >
            다시 불러오기
          </button>
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
          {profileError ? (
            <p role="alert" className="league-inline-error">
              공개 상태 확인 실패{' '}
              <button onClick={() => setProfileRetry((value) => value + 1)}>다시 확인</button>
            </p>
          ) : null}
          {source ? (
            <section
              className="shared-panel shared-import-panel"
              aria-labelledby="shared-import-title"
            >
              <h2 id="shared-import-title" ref={panelHeading} tabIndex={-1}>
                종목 추가
              </h2>
              {detailsPublic ? (
                <p className="shared-privacy-note">추가한 종목과 보유 이유가 공개됩니다.</p>
              ) : null}
              {source.length === 0 ? (
                <p>
                  포트폴리오에 종목이 없어요. <Link to="/">포트폴리오 입력하기</Link>
                </p>
              ) : (
                <>
                  <fieldset disabled={busy}>
                    <legend>내 포트폴리오에서 선택</legend>
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
                                {existing ? (
                                  <small className="shared-import-badge">등록됨</small>
                                ) : null}
                              </strong>
                              <small>
                                {h.quantity}주 · 평균 {won(h.averagePurchasePrice)}
                              </small>
                              {existing ? (
                                <small>
                                  리그: {existing.quantity}주 · 평균{' '}
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
                                aria-describedby={importGuidanceId}
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
                  <p id={importGuidanceId} role="status">
                    {selected.length === 0
                      ? '추가할 종목을 선택해 주세요.'
                      : missingImportReasons.length > 0
                        ? `${missingImportReasons.length}개 종목의 보유 이유를 작성해 주세요.`
                        : '선택한 종목을 추가할 수 있어요.'}
                  </p>
                  {conflicts.length > 0 ? (
                    <label className="shared-confirm">
                      <input
                        type="checkbox"
                        disabled={busy}
                        checked={overwrite}
                        onChange={(event) => setOverwrite(event.target.checked)}
                      />
                      등록된 {conflicts.length}개 종목의 수량·평균 매수가를 업데이트합니다. 공개
                      여부와 보유 이유는 유지됩니다.
                    </label>
                  ) : null}
                </>
              )}
              <div className="shared-form-actions">
                {source.length > 0 ? (
                  <button
                    className="shared-primary-button"
                    aria-describedby={importGuidanceId}
                    disabled={
                      busy ||
                      records === null ||
                      profileLoading ||
                      profileError ||
                      selected.length === 0 ||
                      missingImportReasons.length > 0 ||
                      (conflicts.length > 0 && !overwrite)
                    }
                    onClick={() =>
                      void mutate(
                        importSelection,
                        `${selected.length}개 종목을 추가했습니다.`,
                        'import_holdings',
                        { overwrite_existing: overwrite },
                      )
                    }
                  >
                    {busy ? '추가 중…' : `${selected.length}개 종목 추가`}
                  </button>
                ) : null}
                <button disabled={busy} onClick={() => setSource(null)}>
                  취소
                </button>
              </div>
            </section>
          ) : null}
          <div hidden={source !== null}>
            <section className="shared-list" aria-labelledby="shared-holdings-title">
              <header className="shared-section-heading">
                <div>
                  <h2 id="shared-holdings-title" className="league-sr-only">
                    등록한 종목 {data.holdings.length}개
                  </h2>
                  <p>
                    {data.holdings.length
                      ? `총 ${data.holdings.length}개`
                      : '아직 등록한 종목이 없어요.'}
                  </p>
                </div>
                <button
                  className="shared-primary-button"
                  disabled={busy || records === null}
                  aria-busy={busy}
                  onClick={() => void openImport()}
                >
                  종목 추가
                </button>
              </header>
              <HoldingDecisions
                holdings={data.holdings}
                portfolioBusy={busy}
                publicationStatus={selectedStatus}
                refreshKey={recordRefresh}
                onRecordsChange={setRecords}
                onRemove={(assetId) =>
                  mutate(
                    async () => {
                      const saved = await removeSharedHolding(assetId);
                      setProfileRetry((value) => value + 1);
                      return saved;
                    },
                    '종목을 삭제했습니다.',
                    'remove_holding',
                  )
                }
                onVisibilityChange={(assetId, hidden) =>
                  void mutate(
                    () => changeSharedHoldingVisibility(assetId, hidden),
                    hidden ? '공개에서 제외했습니다.' : '공개 대상으로 선택했습니다.',
                    'change_visibility',
                    { hidden },
                  )
                }
              />
            </section>
            {!profileLoading &&
            !profileError &&
            !profile?.leagueEnabled &&
            data.holdings.length > 0 &&
            records !== null &&
            missingCount === 0 ? (
              <Link className="league-primary-link" to="/portfolio/publication?join=true">
                리그 참여하기
              </Link>
            ) : null}
          </div>
        </>
      )}
    </main>
  );
}
