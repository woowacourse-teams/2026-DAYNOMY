import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useLocation, useParams, useSearchParams } from 'react-router-dom';
import { ApiError } from '../../api/client';
import {
  getAdminEmailAnalyses,
  getAdminEmailAnalysis,
  isEmailAnalysisStatus,
  type AdminEmailAnalysisDetailResponse,
  type AdminEmailAnalysisPageResponse,
  type EmailAnalysisStatus,
} from './emailAnalysisApi';
import './adminEmailAnalyses.css';

const STATUS_LABELS: Record<EmailAnalysisStatus, string> = {
  PENDING: '검증 대기',
  VERIFIED: '검증 완료',
  REJECTED: '검증 실패',
  INVALIDATED: '무효화',
};
const STATUS_STYLES: Record<EmailAnalysisStatus, string> = {
  PENDING: 'pending',
  VERIFIED: 'published',
  REJECTED: 'rejected',
  INVALIDATED: 'deleted',
};
const dateTime = new Intl.DateTimeFormat('ko-KR', {
  dateStyle: 'medium',
  timeStyle: 'short',
  timeZone: 'Asia/Seoul',
  hourCycle: 'h23',
});
function formatTime(value: string | null) {
  return value ? dateTime.format(new Date(value)) : '정보 없음';
}
function positiveId(value: string) {
  return /^[1-9]\d*$/.test(value) && Number.isSafeInteger(Number(value));
}
function errorMessage(error: unknown) {
  if (error instanceof ApiError && error.status === 404) return '분석을 찾을 수 없습니다.';
  if (error instanceof ApiError && error.status === 403) return '관리자 권한이 필요합니다.';
  return error instanceof Error ? error.message : '분석을 불러오지 못했습니다.';
}
function Status({ value }: { value: EmailAnalysisStatus }) {
  return (
    <span className={`admin-status admin-status-${STATUS_STYLES[value]}`}>
      {STATUS_LABELS[value]}
    </span>
  );
}
function SourceLink({ url }: { url: string | null }) {
  let safe = false;
  try {
    const value = new URL(url ?? '');
    safe = ['http:', 'https:'].includes(value.protocol) && !value.username && !value.password;
  } catch {
    /* Invalid stored sources remain visible without an active link. */
  }
  return safe ? (
    <a href={url!} target="_blank" rel="noopener noreferrer">
      {url}
    </a>
  ) : (
    <span>{url || '출처 주소 없음'} (링크를 열 수 없습니다)</span>
  );
}

export function AdminEmailAnalysesPage() {
  const [params, setParams] = useSearchParams();
  const status = params.get('status') ?? '';
  const assetId = params.get('assetId') ?? '';
  const rawPage = params.get('page') ?? '0';
  const page = Number(rawPage);
  const valid =
    (status === '' || isEmailAnalysisStatus(status)) &&
    (assetId === '' || positiveId(assetId)) &&
    /^\d+$/.test(rawPage) &&
    Number.isSafeInteger(page);
  const [draftStatus, setDraftStatus] = useState(status);
  const [draftAsset, setDraftAsset] = useState(assetId);
  const [inputError, setInputError] = useState('');
  const [result, setResult] = useState<AdminEmailAnalysisPageResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    setDraftStatus(status);
    setDraftAsset(assetId);
    setInputError('');
  }, [status, assetId]);
  useEffect(() => {
    if (!valid) return;
    const controller = new AbortController();
    setLoading(true);
    setError('');
    setResult(null);
    getAdminEmailAnalyses(
      {
        page,
        status: isEmailAnalysisStatus(status) ? status : undefined,
        assetId: assetId ? Number(assetId) : undefined,
      },
      controller.signal,
    )
      .then((value) => {
        if (controller.signal.aborted) return;
        const lastPage = Math.max(value.totalPages - 1, 0);
        if (page > lastPage) {
          setParams(
            (previous) => {
              const next = new URLSearchParams(previous);
              next.set('page', String(lastPage));
              return next;
            },
            { replace: true },
          );
        } else setResult(value);
      })
      .catch((caught: unknown) => {
        if (!controller.signal.aborted) setError(errorMessage(caught));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [status, assetId, page, valid, retry, setParams]);
  function apply(event: FormEvent) {
    event.preventDefault();
    const value = draftAsset.trim();
    if (value && !positiveId(value)) {
      setInputError('자산 ID는 1 이상의 정수로 입력해 주세요.');
      return;
    }
    const next = new URLSearchParams();
    if (draftStatus) next.set('status', draftStatus);
    if (value) next.set('assetId', value);
    setInputError('');
    setParams(next);
  }
  function changePage(nextPage: number) {
    setParams((previous) => {
      const next = new URLSearchParams(previous);
      next.set('page', String(nextPage));
      return next;
    });
  }
  return (
    <main className="admin-content admin-email-page">
      <div className="admin-page-heading">
        <div>
          <h1>이메일 알림 관리</h1>
          <p>이메일에 사용하는 종목 분석과 원문 검증 결과를 확인하세요.</p>
        </div>
      </div>
      <form className="admin-toolbar" onSubmit={apply} aria-label="분석 목록 필터">
        <label>
          <span>상태</span>
          <select value={draftStatus} onChange={(event) => setDraftStatus(event.target.value)}>
            <option value="">전체 상태</option>
            {Object.entries(STATUS_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <label>
          <span>자산 ID</span>
          <input
            value={draftAsset}
            onChange={(event) => setDraftAsset(event.target.value)}
            inputMode="numeric"
            placeholder="예: 1"
            aria-invalid={!!inputError}
            aria-describedby={inputError ? 'email-analysis-filter-error' : undefined}
          />
        </label>
        <button type="submit" className="admin-primary-button">
          조회
        </button>
        <button
          type="button"
          className="admin-secondary-button"
          onClick={() => {
            setDraftStatus('');
            setDraftAsset('');
            setInputError('');
            setParams({});
          }}
        >
          초기화
        </button>
        {result && (
          <p className="admin-total-count">
            전체 <strong>{result.totalElements.toLocaleString('ko-KR')}</strong>건
          </p>
        )}
      </form>
      {inputError && (
        <p id="email-analysis-filter-error" className="admin-alert" role="alert">
          {inputError}
        </p>
      )}
      {!valid ? (
        <section className="admin-alert" role="alert">
          조회 주소의 상태·자산 ID·페이지를 확인하거나 초기화를 눌러 주세요.
        </section>
      ) : error ? (
        <section className="admin-alert" role="alert">
          <strong>{error}</strong>
          <button type="button" onClick={() => setRetry((value) => value + 1)}>
            다시 시도
          </button>
        </section>
      ) : (
        <>
          <section className="admin-table-panel" aria-label="이메일 분석 목록" aria-busy={loading}>
            {loading ? (
              <p className="admin-table-loading" role="status">
                분석을 불러오는 중입니다.
              </p>
            ) : !result?.items.length ? (
              <div className="admin-empty-state">
                <strong>조회된 분석이 없습니다.</strong>
                <p>조회 조건을 바꾸거나 분석이 저장된 후 다시 확인해 주세요.</p>
              </div>
            ) : (
              <div className="admin-table-scroll">
                <table className="admin-news-table admin-email-table">
                  <thead>
                    <tr>
                      <th scope="col">종목</th>
                      <th scope="col">분석 ID</th>
                      <th scope="col">상태</th>
                      <th scope="col">기준 시각 (한국 시간)</th>
                      <th scope="col">분석 시각 (한국 시간)</th>
                    </tr>
                  </thead>
                  <tbody>
                    {result.items.map((item) => (
                      <tr key={item.id}>
                        <td data-label="종목">
                          <Link
                            className="admin-news-title"
                            aria-label={`${item.assetName} 분석 #${item.id} 상세`}
                            to={`/admin/email-analyses/${item.id}${params.size ? `?${params}` : ''}`}
                          >
                            {item.assetName}
                          </Link>
                          <small className="admin-email-stock-code">
                            {item.assetCode} · {item.market} ·{' '}
                            {item.category === 'ETF' ? 'ETF' : '주식'}
                          </small>
                        </td>
                        <td data-label="분석 ID">#{item.id}</td>
                        <td data-label="상태">
                          <Status value={item.status} />
                        </td>
                        <td data-label="기준 시각">{formatTime(item.asOf)}</td>
                        <td data-label="분석 시각">{formatTime(item.analyzedAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
          {result && result.totalPages > 1 && (
            <nav className="admin-pagination" aria-label="분석 목록 페이지">
              <button
                type="button"
                disabled={loading || page === 0}
                onClick={() => changePage(page - 1)}
                aria-label="이전 페이지"
              >
                이전
              </button>
              <span className="admin-email-page-count">
                {page + 1} / {result.totalPages}
              </span>
              <button
                type="button"
                disabled={loading || !result.hasNext}
                onClick={() => changePage(page + 1)}
                aria-label="다음 페이지"
              >
                다음
              </button>
            </nav>
          )}
        </>
      )}
    </main>
  );
}

export function AdminEmailAnalysisDetailPage() {
  const { analysisId = '' } = useParams();
  const { search } = useLocation();
  return <EmailAnalysisDetail key={analysisId} id={analysisId} listSearch={search} />;
}
function EmailAnalysisDetail({ id, listSearch }: { id: string; listSearch: string }) {
  const [detail, setDetail] = useState<AdminEmailAnalysisDetailResponse | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [retry, setRetry] = useState(0);
  const heading = useRef<HTMLHeadingElement>(null);
  const valid = positiveId(id);
  useEffect(() => {
    if (!valid) return;
    const controller = new AbortController();
    setLoading(true);
    setError('');
    setDetail(null);
    getAdminEmailAnalysis(Number(id), controller.signal)
      .then((value) => {
        if (!controller.signal.aborted) setDetail(value);
      })
      .catch((caught: unknown) => {
        if (!controller.signal.aborted) setError(errorMessage(caught));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [id, retry, valid]);
  useEffect(() => {
    heading.current?.focus({ preventScroll: true });
  }, [detail, error]);
  const content = detail?.content;
  return (
    <main className="admin-content admin-email-page">
      <Link className="admin-back-link" to={`/admin/email-analyses${listSearch}`}>
        목록으로 돌아가기
      </Link>
      <div className="admin-page-heading">
        <div>
          <h1 ref={heading} tabIndex={-1}>
            {detail ? `${detail.analysis.assetName} 분석` : '이메일 분석 상세'}
          </h1>
          <p>종목 차원의 공통 분석입니다. 회원별 보유 정보는 포함하지 않습니다.</p>
        </div>
        {detail && <Status value={detail.analysis.status} />}
      </div>
      {!valid ? (
        <section className="admin-alert" role="alert">
          올바른 분석 ID가 필요합니다.
        </section>
      ) : error ? (
        <section className="admin-alert" role="alert">
          <strong>{error}</strong>
          <button type="button" onClick={() => setRetry((value) => value + 1)}>
            다시 시도
          </button>
        </section>
      ) : loading ? (
        <p className="admin-table-loading" role="status">
          분석 상세를 불러오는 중입니다.
        </p>
      ) : (
        detail &&
        content && (
          <>
            <section className="admin-email-detail-panel" aria-labelledby="email-analysis-metadata">
              <h2 id="email-analysis-metadata">분석 정보</h2>
              <dl className="admin-email-metadata">
                {[
                  ['분석 ID', `#${detail.analysis.id}`],
                  ['자산 ID', String(detail.analysis.assetId)],
                  ['종목 코드 · 시장', `${detail.analysis.assetCode} · ${detail.analysis.market}`],
                  ['자산 유형', detail.analysis.category === 'ETF' ? 'ETF' : '주식'],
                  ['기준 시각 (한국 시간)', formatTime(detail.analysis.asOf)],
                  ['분석 시각 (한국 시간)', formatTime(detail.analysis.analyzedAt)],
                  ['모델', detail.model],
                  [
                    '분석 버전 · 근거 버전',
                    `${detail.analysisVersion} · ${detail.evidenceVersion}`,
                  ],
                ].map(([label, value]) => (
                  <div key={label}>
                    <dt>{label}</dt>
                    <dd>{value}</dd>
                  </div>
                ))}
              </dl>
              {detail.analysis.status === 'INVALIDATED' && (
                <div className="admin-email-invalidation">
                  <h3>무효화 정보</h3>
                  <p>{detail.invalidationReason || '사유 정보 없음'}</p>
                  <p>{formatTime(detail.analysis.invalidatedAt)} · 한국 시간</p>
                </div>
              )}
            </section>
            <section className="admin-email-detail-panel" aria-labelledby="email-analysis-content">
              <h2 id="email-analysis-content">종목 분석</h2>
              <p className="admin-email-impact">
                영향 방향:{' '}
                {content.direction
                  ? { POSITIVE: '긍정', NEGATIVE: '부정', NEUTRAL: '중립' }[content.direction]
                  : '정보 없음'}{' '}
                · 영향 수준:{' '}
                {content.impactLevel
                  ? { HIGH: '높음', MEDIUM: '보통', LOW: '낮음' }[content.impactLevel]
                  : '정보 없음'}
              </p>
              <dl className="admin-email-body">
                {[
                  ['핵심 소식', content.issueSummary],
                  ['예상 반응', content.expectedReaction],
                  ['전망', content.outlook],
                  ['분석 근거', content.reason],
                ].map(([label, value]) => (
                  <div key={label}>
                    <dt>{label}</dt>
                    <dd>{value || '정보 없음'}</dd>
                  </div>
                ))}
              </dl>
            </section>
            <section className="admin-email-detail-panel" aria-labelledby="email-analysis-sources">
              <h2 id="email-analysis-sources">근거 및 출처</h2>
              {content.sources.length === 0 ? (
                <p>등록된 출처가 없습니다.</p>
              ) : (
                <ol className="admin-email-sources">
                  {content.sources.map((source, index) => (
                    <li key={index}>
                      <h3>{source.title || '제목 정보 없음'}</h3>
                      <SourceLink url={source.url} />
                      <p className="admin-email-source-dates">
                        게시일: {source.publishedOn || '미확인'} · 사건 발생일:{' '}
                        {source.occurredOn || '미확인'}
                      </p>
                      {source.excerpt && <p>{source.excerpt}</p>}
                      {source.factQuote && (
                        <>
                          <h4>원문 인용</h4>
                          <blockquote>{source.factQuote}</blockquote>
                        </>
                      )}
                    </li>
                  ))}
                </ol>
              )}
            </section>
            <section
              className="admin-email-detail-panel"
              aria-labelledby="email-analysis-verification"
            >
              <h2 id="email-analysis-verification">원문 검증 결과</h2>
              {detail.verification === null ? (
                <p>저장된 원문 검증 결과가 없습니다.</p>
              ) : (
                <>
                  <p className="admin-email-source-dates">
                    검증 시각: {formatTime(detail.verification.checkedAt)} · 한국 시간
                  </p>
                  {detail.verification.sources.length === 0 ? (
                    <p>검증한 출처가 없습니다.</p>
                  ) : (
                    <ul className="admin-email-checks">
                      {detail.verification.sources.map((check, index) => (
                        <li key={index}>
                          <SourceLink url={check.url} />
                          <dl className="admin-email-metadata">
                            {[
                              ['대상 기업', check.companyMatched],
                              ['날짜', check.datesMatched],
                              ['수치', check.numbersMatched],
                              ['원문 인용', check.quoteMatched],
                              ['종합 결과', check.passed],
                            ].map(([label, value]) => (
                              <div key={String(label)}>
                                <dt>{label}</dt>
                                <dd>{value ? '통과' : '미통과'}</dd>
                              </div>
                            ))}
                          </dl>
                        </li>
                      ))}
                    </ul>
                  )}
                </>
              )}
            </section>
          </>
        )
      )}
    </main>
  );
}
