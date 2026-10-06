import { useContext, useEffect, useMemo, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { AuthContext } from '../../auth/AuthContext';
import {
  loadConsultations,
  calculateCheckInStreak,
  weeklyTargetsFromPlan,
  type Consultation,
} from './financialPlanning';
import { loadWeeklyCheckIns, saveWeeklyCheckIn } from './learningApi';
import { deleteFinancialPlan, loadFinancialPlans, saveFinancialPlan } from './financialPlanApi';
import type { WeeklyCheckIn } from './learningTypes';
import './financialPlanning.css';
import './financial-overview.css';

const won = new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 0 });
const date = new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium' });

function localDate(dateValue: Date) {
  const year = dateValue.getFullYear();
  const month = String(dateValue.getMonth() + 1).padStart(2, '0');
  const day = String(dateValue.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function currentMonday() {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  today.setDate(today.getDate() - ((today.getDay() + 6) % 7));
  return localDate(today);
}

function blankCheckIn(): WeeklyCheckIn {
  return {
    weekStart: currentMonday(),
    targetSavings: 100000,
    targetInvestment: 50000,
    targetDebtPayment: 0,
    actualSavings: 0,
    actualInvestment: 0,
    actualDebtPayment: 0,
    note: null,
  };
}

function totalTarget(checkIn: WeeklyCheckIn) {
  return checkIn.targetSavings + checkIn.targetInvestment + checkIn.targetDebtPayment;
}

function totalActual(checkIn: WeeklyCheckIn) {
  return checkIn.actualSavings + checkIn.actualInvestment + checkIn.actualDebtPayment;
}

function nextMission(checkIns: WeeklyCheckIn[]) {
  const latest = checkIns[0];
  if (!latest) return '이번 주 목표 금액과 실제 실행 금액을 처음 기록해 보세요.';
  const target = totalTarget(latest);
  const actual = totalActual(latest);
  if (target === 0) return '저축·투자·부채 상환 중 하나에 작은 목표를 정해 보세요.';
  if (actual >= target) return '목표를 달성했어요. 다음 주에도 같은 금액을 유지해 보세요.';
  if (actual >= target * 0.7) return '조금만 더 하면 돼요. 남은 금액을 자동이체로 예약해 보세요.';
  return '목표를 낮추더라도 이번 주에 한 번 실행해 연속 기록을 이어가 보세요.';
}

export function WeeklyPracticePage() {
  const auth = useContext(AuthContext);
  const isLoggedIn = auth?.isLoggedIn ?? false;
  const authLoading = auth?.loading ?? false;
  const [consultations, setConsultations] = useState<Consultation[]>(() =>
    isLoggedIn || authLoading ? [] : loadConsultations(),
  );
  const [plansLoading, setPlansLoading] = useState(true);
  const [plansError, setPlansError] = useState('');
  const [plansBusy, setPlansBusy] = useState(false);
  const [planRemoving, setPlanRemoving] = useState<string | null>(null);
  const [checkIns, setCheckIns] = useState<WeeklyCheckIn[]>([]);
  const [form, setForm] = useState(blankCheckIn);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [loadError, setLoadError] = useState('');
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    if (authLoading) return;
    let active = true;
    setLoading(true);
    setLoadError('');
    setMessage('');
    setError('');
    setCheckIns([]);
    setForm(blankCheckIn());
    loadWeeklyCheckIns(isLoggedIn)
      .then((values) => {
        if (!active) return;
        setCheckIns(values);
        const current = values.find((value) => value.weekStart === currentMonday());
        if (current) setForm(current);
      })
      .catch(() => active && setLoadError('주간 기록을 불러오지 못했습니다.'))
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [authLoading, isLoggedIn, retry]);

  useEffect(() => {
    if (authLoading) return;
    const controller = new AbortController();
    setPlansLoading(true);
    setPlansError('');
    setConsultations([]);
    loadFinancialPlans(isLoggedIn, controller.signal)
      .then((items) => {
        if (!controller.signal.aborted) setConsultations(items);
      })
      .catch(() => {
        if (!controller.signal.aborted) setPlansError('저장한 계획을 불러오지 못했어요.');
      })
      .finally(() => {
        if (!controller.signal.aborted) setPlansLoading(false);
      });
    return () => controller.abort();
  }, [authLoading, isLoggedIn, retry]);

  const streak = useMemo(
    () =>
      calculateCheckInStreak(
        checkIns.map((item) => item.weekStart),
        currentMonday(),
      ),
    [checkIns],
  );
  const recent = checkIns.slice(0, 8).reverse();
  const monthlyPlan = consultations.find((item) => item.monthlyPlan)?.monthlyPlan;
  const currentMonth = localDate(new Date()).slice(0, 7);
  const monthlyRecords = checkIns.filter((item) => item.weekStart.startsWith(currentMonth));
  const monthTotals = monthlyRecords.reduce(
    (total, item) => ({
      savings: total.savings + item.actualSavings,
      investment: total.investment + item.actualInvestment,
      debt: total.debt + item.actualDebtPayment,
    }),
    { savings: 0, investment: 0, debt: 0 },
  );

  function applyWeeklyPlan() {
    if (!monthlyPlan) return;
    try {
      setForm({ ...form, ...weeklyTargetsFromPlan(monthlyPlan, form.weekStart) });
      setError('');
      setMessage(
        '선택한 달의 월요일 수로 월 계획을 나눴어요. 목표를 확인한 뒤 기록을 저장해 주세요.',
      );
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : '주 시작일을 확인해 주세요.');
    }
  }

  async function toggleAction(plan: Consultation, actionId: string) {
    setPlansBusy(true);
    setPlansError('');
    const next = {
      ...plan,
      actions: plan.actions?.map((action) =>
        action.id === actionId ? { ...action, completed: !action.completed } : action,
      ),
    };
    try {
      const saved = await saveFinancialPlan(isLoggedIn, next);
      setConsultations((items) => items.map((item) => (item.id === saved.id ? saved : item)));
    } catch {
      setPlansError('할 일을 저장하지 못했어요. 다시 시도해 주세요.');
    } finally {
      setPlansBusy(false);
    }
  }
  async function removePlan(id: string) {
    setPlansBusy(true);
    setPlansError('');
    try {
      await deleteFinancialPlan(isLoggedIn, id);
      setConsultations((items) => items.filter((item) => item.id !== id));
      setPlanRemoving(null);
    } catch {
      setPlansError('계획을 삭제하지 못했어요. 다시 시도해 주세요.');
    } finally {
      setPlansBusy(false);
    }
  }

  function changeAmount(key: keyof WeeklyCheckIn, value: string) {
    setForm({ ...form, [key]: Math.max(0, Number(value)) });
  }

  async function submitCheckIn(event: FormEvent) {
    event.preventDefault();
    setError('');
    setMessage('');
    if (new Date(`${form.weekStart}T00:00:00Z`).getUTCDay() !== 1) {
      setError('주 시작일은 월요일로 선택해 주세요.');
      return;
    }
    setSaving(true);
    try {
      const saved = await saveWeeklyCheckIn(isLoggedIn, form);
      setCheckIns((current) =>
        [saved, ...current.filter((item) => item.weekStart !== saved.weekStart)].sort((a, b) =>
          b.weekStart.localeCompare(a.weekStart),
        ),
      );
      setMessage(
        isLoggedIn
          ? '계정에 이번 주 기록을 저장했어요.'
          : '이 브라우저에 이번 주 기록을 저장했어요.',
      );
    } catch {
      setError('주간 기록을 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.');
    } finally {
      setSaving(false);
    }
  }

  return (
    <main className="finance-page finance-history-page">
      <section className="finance-hero finance-dashboard-hero">
        <h1>이번 주 돈 관리 기록</h1>
        <p>이번 주 목표와 실제 저축·투자 금액을 입력하고, 매주 달라지는 흐름을 확인해요.</p>
        <div className="finance-page-shortcuts">
          <a className="finance-start-link" href="#finance-weekly-input">
            이번 주 기록하기 ↓
          </a>
          <a href="#finance-saved-plans">저장한 계획 {consultations.length}개 보기</a>
        </div>
        <div className="finance-dashboard-summary">
          <span>
            연속 기록 <strong>{streak}주</strong>
          </span>
          <span>
            누적 체크인 <strong>{checkIns.length}회</strong>
          </span>
          <span>{isLoggedIn ? '주간 기록은 계정에 저장' : '현재 브라우저에 저장'}</span>
        </div>
      </section>

      {monthlyPlan && (
        <section className="finance-section" aria-labelledby="finance-monthly-title">
          <div className="finance-section-heading">
            <h2 id="finance-monthly-title">{Number(currentMonth.slice(5))}월 계획과 실천</h2>
            <p>가장 최근 저장한 월 계획과 이번 달 주간 기록을 비교해요.</p>
          </div>
          {loading ? (
            <p className="finance-state">주간 기록을 불러오는 중입니다.</p>
          ) : loadError ? (
            <p className="finance-budget-warning">{loadError} 새로고침한 뒤 다시 확인해 주세요.</p>
          ) : (
            <div className="finance-budget-table-wrap">
              <table className="finance-budget-table">
                <caption>이번 달 {monthlyRecords.length}개 주간 기록 기준</caption>
                <thead>
                  <tr>
                    <th scope="col">항목</th>
                    <th scope="col">월 계획</th>
                    <th scope="col">실제</th>
                    <th scope="col">남은 금액</th>
                  </tr>
                </thead>
                <tbody>
                  {[
                    {
                      label: '저축·비상금',
                      target: monthlyPlan.savings + monthlyPlan.emergency,
                      actual: monthTotals.savings,
                    },
                    {
                      label: '투자',
                      target: monthlyPlan.investment,
                      actual: monthTotals.investment,
                    },
                    { label: '추가 부채 상환', target: monthlyPlan.debt, actual: monthTotals.debt },
                  ].map((item) => (
                    <tr key={item.label}>
                      <th scope="row">{item.label}</th>
                      <td>{won.format(item.target)}원</td>
                      <td>{won.format(item.actual)}원</td>
                      <td>{won.format(Math.max(0, item.target - item.actual))}원</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <p className="finance-source-note">
            불러온 주간 기록 중 주 시작일이 이번 달인 기록만 합산합니다. 저축에는 비상금이 포함되며,
            목표 자금 잔액이나 투자 수익률을 뜻하지 않습니다.
          </p>
          <Link className="finance-back-link" to="/finance/plan">
            월급·지출이 바뀌었다면 계획 다시 만들기 →
          </Link>
        </section>
      )}

      <section className="finance-section finance-routine-layout">
        <form
          className="finance-form finance-checkin-form"
          id="finance-weekly-input"
          onSubmit={submitCheckIn}
        >
          <div className="finance-section-heading">
            <span>01 · 이번 주 체크인</span>
            <h2>얼마를 계획하고 실행했나요?</h2>
            <p>
              원 단위로 입력하세요. 저축에는 비상금을 포함하고, 부채는 고정 지출 외 추가 상환만
              기록해요.
            </p>
          </div>
          <label>
            주 시작일
            <input
              type="date"
              value={form.weekStart}
              disabled={loading || saving || Boolean(loadError)}
              onChange={(event) => {
                const weekStart = event.target.value;
                setForm(
                  checkIns.find((item) => item.weekStart === weekStart) ?? {
                    ...blankCheckIn(),
                    weekStart,
                  },
                );
                setMessage('');
                setError('');
              }}
              required
            />
          </label>
          {monthlyPlan && (
            <button
              className="finance-plan-import"
              type="button"
              disabled={
                loading || saving || plansLoading || Boolean(loadError) || Boolean(plansError)
              }
              onClick={applyWeeklyPlan}
            >
              월 계획을 이번 주 목표로 가져오기
            </button>
          )}
          <fieldset>
            <legend>저축</legend>
            <label>
              목표
              <input
                type="number"
                min="0"
                step="1"
                value={form.targetSavings}
                onChange={(event) => changeAmount('targetSavings', event.target.value)}
              />
            </label>
            <label>
              실제
              <input
                type="number"
                min="0"
                step="1"
                value={form.actualSavings}
                onChange={(event) => changeAmount('actualSavings', event.target.value)}
              />
            </label>
          </fieldset>
          <fieldset>
            <legend>투자</legend>
            <label>
              목표
              <input
                type="number"
                min="0"
                step="1"
                value={form.targetInvestment}
                onChange={(event) => changeAmount('targetInvestment', event.target.value)}
              />
            </label>
            <label>
              실제
              <input
                type="number"
                min="0"
                step="1"
                value={form.actualInvestment}
                onChange={(event) => changeAmount('actualInvestment', event.target.value)}
              />
            </label>
          </fieldset>
          <fieldset>
            <legend>부채 상환</legend>
            <label>
              목표
              <input
                type="number"
                min="0"
                step="1"
                value={form.targetDebtPayment}
                onChange={(event) => changeAmount('targetDebtPayment', event.target.value)}
              />
            </label>
            <label>
              실제
              <input
                type="number"
                min="0"
                step="1"
                value={form.actualDebtPayment}
                onChange={(event) => changeAmount('actualDebtPayment', event.target.value)}
              />
            </label>
          </fieldset>
          <label>
            이번 주 메모
            <input
              maxLength={200}
              placeholder="잘한 점이나 막힌 이유를 남겨보세요"
              value={form.note ?? ''}
              onChange={(event) => setForm({ ...form, note: event.target.value || null })}
            />
          </label>
          <button type="submit" disabled={loading || saving || Boolean(loadError)}>
            {saving ? '저장 중…' : '이번 주 기록 저장'}
          </button>
          {message && (
            <p className="finance-form-message" role="status">
              {message}
            </p>
          )}
          {(error || loadError) && (
            <p className="finance-form-error" role="alert">
              {error || loadError}
            </p>
          )}
          {loadError && (
            <button
              type="button"
              className="finance-plan-import"
              onClick={() => setRetry((value) => value + 1)}
            >
              주간 기록 다시 불러오기
            </button>
          )}
        </form>

        <aside className="finance-next-mission">
          <span>금융 학습의 다음 미션</span>
          <h2>
            {loadError ? '기록을 다시 불러온 뒤 다음 할 일을 확인하세요.' : nextMission(checkIns)}
          </h2>
          <p>기록 결과에 따라 다음 주에 실천할 수 있는 행동을 한 가지씩 제안해요.</p>
          <Link to="/finance/guides">초보 가이드에서 방법 찾기</Link>
        </aside>
      </section>

      <section className="finance-section">
        <div className="finance-section-heading">
          <span>02 · 최근 8주</span>
          <h2>최근 8주 목표 달성률</h2>
        </div>
        {loading ? (
          <p className="finance-state" aria-live="polite">
            기록을 불러오는 중입니다.
          </p>
        ) : loadError ? (
          <p className="finance-state">기록을 불러오지 못해 달성률을 표시할 수 없어요.</p>
        ) : recent.length === 0 ? (
          <p className="finance-state">첫 체크인을 저장하면 주간 그래프가 만들어져요.</p>
        ) : (
          <div className="finance-week-chart" aria-label="최근 8주 목표 달성률">
            {recent.map((checkIn) => {
              const target = totalTarget(checkIn);
              const actual = totalActual(checkIn);
              const rate = target === 0 ? 0 : Math.min(100, Math.round((actual / target) * 100));
              return (
                <article key={checkIn.weekStart}>
                  <div>
                    <i style={{ height: `${Math.max(5, rate)}%` }} />
                  </div>
                  <strong>{rate}%</strong>
                  <time dateTime={checkIn.weekStart}>{checkIn.weekStart.slice(5)}</time>
                  <small>{won.format(actual)}원</small>
                </article>
              );
            })}
          </div>
        )}
      </section>

      <section className="finance-section" id="finance-saved-plans">
        <div className="finance-section-heading">
          <span>03 · 저장한 계획</span>
          <h2>저장한 계획 실행하기</h2>
          <p>
            저축·투자 계획에서 정한 할 일을 체크하세요.{' '}
            {isLoggedIn
              ? '내 계정에 저장됩니다. 비로그인 때의 브라우저 기록은 자동으로 가져오지 않습니다.'
              : '현재 브라우저에 보관됩니다.'}
          </p>
        </div>
        {plansError && (
          <p role="alert" className="finance-form-error">
            {plansError}
          </p>
        )}
        {plansError && (
          <button type="button" onClick={() => setRetry((n) => n + 1)}>
            저장한 계획 다시 불러오기
          </button>
        )}
        {plansLoading ? (
          <p role="status">저장한 계획을 불러오는 중입니다.</p>
        ) : plansError && consultations.length === 0 ? null : consultations.length === 0 ? (
          <div className="finance-empty">
            <span aria-hidden="true">✓</span>
            <h2>아직 저장한 계획이 없어요</h2>
            <p>저축과 투자 계획을 만든 뒤 저장해 보세요.</p>
            <Link to="/finance/plan">첫 계획 만들기</Link>
          </div>
        ) : (
          <div className="finance-history-list" aria-label="저장한 상담 결과">
            {consultations.map((consultation) => (
              <article key={consultation.id} className="finance-history-card">
                <div>
                  <span>{consultation.topic}</span>
                  <time dateTime={consultation.createdAt}>
                    {date.format(new Date(consultation.createdAt))}
                  </time>
                </div>
                <h2>{consultation.title}</h2>
                <strong>{consultation.summary}</strong>
                <ul>
                  {consultation.details.map((detail) => (
                    <li key={detail}>{detail}</li>
                  ))}
                </ul>
                {consultation.actions && (
                  <div className="finance-action-list">
                    {consultation.actions.map((action) => (
                      <label key={action.id}>
                        <input
                          type="checkbox"
                          checked={action.completed}
                          disabled={plansBusy}
                          onChange={() => void toggleAction(consultation, action.id)}
                        />
                        <span>{action.label}</span>
                      </label>
                    ))}
                  </div>
                )}
                <button
                  type="button"
                  disabled={plansBusy}
                  onClick={() => setPlanRemoving(consultation.id)}
                >
                  기록 삭제
                </button>
                {planRemoving === consultation.id && (
                  <div className="finance-budget-warning">
                    <p>
                      이 계획과 할 일을 삭제할까요? 주간 실천 기록과 원본 포트폴리오는 유지됩니다.
                    </p>
                    <button
                      type="button"
                      disabled={plansBusy}
                      onClick={() => void removePlan(consultation.id)}
                    >
                      삭제 확인
                    </button>
                    <button
                      type="button"
                      disabled={plansBusy}
                      onClick={() => setPlanRemoving(null)}
                    >
                      취소
                    </button>
                  </div>
                )}
              </article>
            ))}
          </div>
        )}
      </section>
    </main>
  );
}
