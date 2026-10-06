import { useContext, useEffect, useMemo, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { AuthContext } from '../../auth/AuthContext';
import { loadAccountSteps, recommendPaymentTool } from './financialPlanning';
import { loadLearningProgress, saveLearningProgress } from './learningApi';
import { findFinancialGuide, FINANCIAL_GUIDES } from './financialGuides';
import type { LearningProgress } from './learningTypes';
import './financialPlanning.css';
import './financial-overview.css';
import './financial-guide.css';

const categories = ['전체', '시작', '생활금융', '투자', '세금·보호'] as const;

function progressFor(progress: LearningProgress[], id: string) {
  return progress.find((item) => item.itemKey === `guide-${id}`);
}

function CreditCardDecision() {
  const [input, setInput] = useState({
    canPayInFull: true,
    tracksSpending: true,
    monthlyCardSpending: 500000,
    expectedMonthlyBenefit: 10000,
    annualFee: 15000,
    usesRevolving: false,
  });
  const [result, setResult] = useState<ReturnType<typeof recommendPaymentTool> | null>(null);

  function submit(event: FormEvent) {
    event.preventDefault();
    setResult(recommendPaymentTool(input));
  }

  return (
    <section className="finance-decision-tool">
      <div className="finance-section-heading">
        <span>나에게 맞는 결제수단 진단</span>
        <h2>혜택보다 결제 습관을 먼저 확인해요</h2>
      </div>
      <form className="finance-form" onSubmit={submit}>
        <div className="finance-choice-grid">
          <label>
            매달 전액 결제가 가능한가요?
            <select
              value={input.canPayInFull ? 'yes' : 'no'}
              onChange={(event) =>
                setInput({ ...input, canPayInFull: event.target.value === 'yes' })
              }
            >
              <option value="yes">네, 항상 전액 결제해요</option>
              <option value="no">가끔 다음 달로 미뤄요</option>
            </select>
          </label>
          <label>
            소비 내역을 주 1회 확인하나요?
            <select
              value={input.tracksSpending ? 'yes' : 'no'}
              onChange={(event) =>
                setInput({ ...input, tracksSpending: event.target.value === 'yes' })
              }
            >
              <option value="yes">확인해요</option>
              <option value="no">거의 확인하지 않아요</option>
            </select>
          </label>
          <label>
            한 달 카드 예상 지출
            <input
              type="number"
              min="0"
              step="10000"
              value={input.monthlyCardSpending}
              onChange={(event) =>
                setInput({ ...input, monthlyCardSpending: Number(event.target.value) })
              }
            />
          </label>
          <label>
            월 예상 할인·적립
            <input
              type="number"
              min="0"
              step="1000"
              value={input.expectedMonthlyBenefit}
              onChange={(event) =>
                setInput({ ...input, expectedMonthlyBenefit: Number(event.target.value) })
              }
            />
          </label>
          <label>
            연회비
            <input
              type="number"
              min="0"
              step="1000"
              value={input.annualFee}
              onChange={(event) => setInput({ ...input, annualFee: Number(event.target.value) })}
            />
          </label>
          <label>
            리볼빙을 사용 중인가요?
            <select
              value={input.usesRevolving ? 'yes' : 'no'}
              onChange={(event) =>
                setInput({ ...input, usesRevolving: event.target.value === 'yes' })
              }
            >
              <option value="no">아니요</option>
              <option value="yes">네</option>
            </select>
          </label>
        </div>
        <button type="submit">내 결제수단 진단하기</button>
      </form>
      {result && (
        <article
          className={`finance-decision-result ${result.recommendation.toLowerCase()}`}
          aria-live="polite"
        >
          <span>진단 결과</span>
          <h3>{result.title}</h3>
          <p>{result.reason}</p>
          <dl>
            <div>
              <dt>예상 연간 순혜택</dt>
              <dd>{Math.round(result.netBenefit).toLocaleString('ko-KR')}원</dd>
            </div>
            <div>
              <dt>먼저 지킬 규칙</dt>
              <dd>월 한도 설정 · 전액 결제 · 리볼빙 미사용</dd>
            </div>
          </dl>
          <small>교육용 진단이며 카드 발급 심사나 개인 신용평가를 대신하지 않습니다.</small>
        </article>
      )}
    </section>
  );
}

const accountSteps = [
  { id: 'goal', title: '계좌 목적 정하기', description: '생활비·저축·투자 중 용도를 정해요.' },
  { id: 'compare', title: '조건 비교하기', description: '우대 조건과 중도해지 조건을 확인해요.' },
  { id: 'prepare', title: '준비물 챙기기', description: '신분증과 본인 명의 휴대폰을 준비해요.' },
  { id: 'protect', title: '보안 설정하기', description: '이체 한도와 입출금 알림을 확인해요.' },
];

export function FinancialGuidePage() {
  const { guideId } = useParams();
  const guide = findFinancialGuide(guideId);
  const auth = useContext(AuthContext);
  const isLoggedIn = auth?.isLoggedIn ?? false;
  const authLoading = auth?.loading ?? false;
  const [category, setCategory] = useState<(typeof categories)[number]>('전체');
  const [query, setQuery] = useState('');
  const [savedOnly, setSavedOnly] = useState(false);
  const [progress, setProgress] = useState<LearningProgress[]>([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    if (authLoading) return;
    const controller = new AbortController();
    setLoading(true);
    setError('');
    setProgress([]);
    loadLearningProgress(isLoggedIn, controller.signal)
      .then((items) => {
        if (!controller.signal.aborted) setProgress(items);
      })
      .catch(() => {
        if (!controller.signal.aborted)
          setError('학습 기록을 불러오지 못했어요. 가이드는 계속 읽을 수 있습니다.');
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [authLoading, isLoggedIn, retry]);

  const visibleGuides = useMemo(
    () =>
      FINANCIAL_GUIDES.filter(
        (item) =>
          (category === '전체' || item.category === category) &&
          (!savedOnly || progressFor(progress, item.id)?.bookmarked) &&
          (item.title + item.question + item.summary).includes(query.trim()),
      ),
    [category, savedOnly, query, progress],
  );

  async function update(next: LearningProgress) {
    if (saving || loading || authLoading || error) return;
    setSaving(true);
    setError('');
    try {
      const saved = await saveLearningProgress(isLoggedIn, next);
      setProgress((items) => [saved, ...items.filter((item) => item.itemKey !== saved.itemKey)]);
    } catch {
      setError('학습 기록을 저장하지 못했어요. 다시 불러온 뒤 시도해 주세요.');
    } finally {
      setSaving(false);
    }
  }
  function updateGuide(id: string, field: 'completed' | 'bookmarked') {
    const current = progressFor(progress, id);
    void update({
      itemKey: `guide-${id}`,
      itemType: 'GUIDE',
      completed: field === 'completed' ? !current?.completed : (current?.completed ?? false),
      bookmarked: field === 'bookmarked' ? !current?.bookmarked : (current?.bookmarked ?? false),
    });
  }
  const state = guide ? progressFor(progress, guide.id) : undefined;
  const disabled = loading || authLoading || saving || Boolean(error);
  const status = (
    <>
      {error && (
        <div className="finance-form-error" role="alert">
          <p>{error}</p>
          <button type="button" disabled={saving} onClick={() => setRetry((n) => n + 1)}>
            학습 기록 다시 불러오기
          </button>
        </div>
      )}
      <p className="finance-guide-storage">
        {loading
          ? '학습 기록을 불러오는 중…'
          : isLoggedIn
            ? '읽음·저장·준비 상태는 내 계정에 보관됩니다.'
            : '읽음·저장·준비 상태는 이 브라우저에 보관됩니다.'}
      </p>
    </>
  );

  if (guideId && !guide)
    return (
      <main className="finance-page">
        <h1>가이드를 찾을 수 없어요</h1>
        <Link to="/finance/guides">전체 가이드 보기</Link>
      </main>
    );

  if (guide)
    return (
      <main className="finance-page finance-knowledge">
        <article className="finance-guide-detail">
          <Link className="finance-back-link" to="/finance/guides">
            ← 초보 가이드 목록
          </Link>
          <header>
            <span>{guide.category}</span>
            <h1>{guide.title}</h1>
            <p>{guide.question}</p>
            <div className="finance-guide-actions">
              <button
                disabled={disabled}
                type="button"
                aria-pressed={state?.bookmarked ?? false}
                onClick={() => updateGuide(guide.id, 'bookmarked')}
              >
                {state?.bookmarked ? '저장됨' : '나중에 보기'}
              </button>
              <button
                disabled={disabled}
                type="button"
                aria-pressed={state?.completed ?? false}
                onClick={() => updateGuide(guide.id, 'completed')}
              >
                {state?.completed ? '읽음 완료' : '읽음으로 표시'}
              </button>
            </div>
          </header>
          {status}
          <nav className="finance-article-index" aria-label="이 글에서 알아볼 것">
            <a href="#guide-summary">핵심 정리</a>
            <a href="#guide-audience">장점·주의점</a>
            <a href="#guide-steps">확인 순서</a>
          </nav>
          <section id="guide-summary" className="finance-guide-summary">
            <h2>핵심 정리</h2>
            <p>{guide.summary}</p>
          </section>
          <section id="guide-audience" className="finance-guide-columns">
            <section>
              <h2>이런 사람에게 적합해요</h2>
              <ul>
                {guide.goodFor.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </section>
            <section>
              <h2>선택 전에 주의하세요</h2>
              <ul>
                {guide.caution.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </section>
          </section>
          <section id="guide-steps" className="finance-guide-checks">
            <h2>무엇부터 확인할까요?</h2>
            <ol>
              {guide.checklist.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ol>
          </section>
          {guide.id === 'first-account' && (
            <section className="finance-account-preparation">
              <h2>계좌 개설 준비 상태</h2>
              <p>
                실제 계좌 개설은 금융회사 공식 앱에서 진행해요. 여기서는 준비한 항목만 기록합니다.
              </p>
              {accountSteps.map((step) => {
                const key = `account-${step.id}`;
                const saved = progress.find((item) => item.itemKey === key);
                const completed =
                  saved?.completed ?? (!isLoggedIn && loadAccountSteps().includes(step.id));
                return (
                  <label key={key}>
                    <input
                      type="checkbox"
                      disabled={disabled}
                      checked={completed}
                      onChange={() =>
                        void update({
                          itemKey: key,
                          itemType: 'MISSION',
                          completed: !completed,
                          bookmarked: false,
                        })
                      }
                    />
                    <span>
                      <strong>{step.title}</strong>
                      <small>{step.description}</small>
                    </span>
                  </label>
                );
              })}
            </section>
          )}
          <footer>
            자료 기준일 {guide.asOf} ·{' '}
            <a href={guide.sourceUrl} target="_blank" rel="noreferrer">
              {guide.sourceLabel}에서 최신 조건 확인
            </a>
          </footer>
        </article>
        {guide.id === 'credit-card-20s' && <CreditCardDecision />}
        <section className="finance-next">
          <h2>알아봤다면 내 상황에 적용해 보세요</h2>
          <p>금액 계산과 실행 기록은 별도 화면에서 관리합니다.</p>
          <div>
            <Link to="/finance/plan">저축·투자 계획 계산</Link>
            <Link to="/finance/weekly-records">주간 실천 기록</Link>
            <Link to="/">내 포트폴리오</Link>
          </div>
        </section>
      </main>
    );

  return (
    <main className="finance-page finance-knowledge">
      <header className="finance-hero">
        <h1>초보 가이드</h1>
        <p>통장 개설부터 저축·투자까지, 궁금한 주제를 골라 읽으세요.</p>
      </header>
      <nav className="finance-purpose-links" aria-label="돈 관리 기능 안내">
        <span>여기서는 금융 지식을 알아봐요.</span>
        <Link to="/finance/plan">얼마를 모을지 계산 →</Link>
        <Link to="/finance/weekly-records">계획 실천 기록 →</Link>
      </nav>
      <section className="finance-section">
        <div className="finance-guide-tools">
          <label>
            가이드 검색
            <input
              type="search"
              value={query}
              placeholder="계좌, 카드, 세금 등"
              onChange={(e) => setQuery(e.target.value)}
            />
          </label>
          <button
            type="button"
            aria-pressed={savedOnly}
            onClick={() => setSavedOnly((value) => !value)}
          >
            {savedOnly ? '전체 글 보기' : '나중에 보기만'}
          </button>
        </div>
        <div className="finance-filter-tabs" aria-label="가이드 주제">
          {categories.map((item) => (
            <button
              key={item}
              type="button"
              className={category === item ? 'active' : ''}
              aria-pressed={category === item}
              onClick={() => setCategory(item)}
            >
              {item}
            </button>
          ))}
        </div>
        {status}
        <div className="finance-guide-list">
          {visibleGuides.map((item) => {
            const saved = progressFor(progress, item.id);
            return (
              <article key={item.id}>
                <span>
                  {item.category}
                  {saved?.completed ? ' · 읽음' : ''}
                  {saved?.bookmarked ? ' · 저장됨' : ''}
                </span>
                <h2>
                  <Link to={`/finance/guides/${item.id}`}>{item.title}</Link>
                </h2>
                <p>{item.question}</p>
                <small>{item.summary}</small>
                <Link className="finance-guide-read-link" to={`/finance/guides/${item.id}`}>
                  자세히 알아보기 →
                </Link>
              </article>
            );
          })}
        </div>
        {visibleGuides.length === 0 && (
          <p className="finance-state">
            {savedOnly
              ? '저장한 글이 없어요. 글 상세에서 나중에 보기를 누르세요.'
              : '해당 주제의 글이 없어요. 검색어나 주제를 바꿔 보세요.'}
          </p>
        )}
      </section>
    </main>
  );
}
