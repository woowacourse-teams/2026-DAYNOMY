import { useCallback, useEffect, useRef, useState } from 'react';
import {
  deleteInvestmentCalendarNotification,
  getInvestmentCalendarNotification,
  requestNotificationEmailVerification,
  updateInvestmentCalendarNotification,
} from './notificationApi';
import type {
  InvestmentCalendarNotificationScope,
  InvestmentCalendarNotificationSetting,
  InvestmentCalendarNotificationTiming,
} from './notificationTypes';

export const NOTIFICATION_PROMPT_DISMISSED_KEY =
  'daynomy:investment-calendar-notification-prompt-dismissed';

const DEFAULT_SETTING: InvestmentCalendarNotificationSetting = {
  email: '',
  emailVerified: false,
  scopes: ['PORTFOLIO', 'MAJOR_ECONOMIC'],
  timings: ['ONE_DAY_BEFORE', 'SAME_DAY_MORNING'],
  sendBeforeAnnouncement: true,
  sendAfterAnnouncement: true,
};

const SCOPE_OPTIONS: { value: InvestmentCalendarNotificationScope; label: string }[] = [
  { value: 'PORTFOLIO', label: '내 포트폴리오' },
  { value: 'WATCHLIST', label: '관심 종목' },
  { value: 'MAJOR_ECONOMIC', label: '주요 경제 일정' },
];

const TIMING_OPTIONS: { value: InvestmentCalendarNotificationTiming; label: string }[] = [
  { value: 'SEVEN_DAYS_BEFORE', label: '7일 전' },
  { value: 'ONE_DAY_BEFORE', label: '하루 전' },
  { value: 'SAME_DAY_MORNING', label: '당일 아침' },
];

function BellIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9" />
      <path d="M10 21h4" />
    </svg>
  );
}

function CloseIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="m6 6 12 12M18 6 6 18" />
    </svg>
  );
}

function updateSelection<T>(values: T[], value: T, checked: boolean) {
  return checked ? [...values, value] : values.filter((item) => item !== value);
}

function isValidEmail(email: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

type InvestmentCalendarNotificationProps = {
  isLoggedIn: boolean;
  authLoading: boolean;
};

export function InvestmentCalendarNotification({
  isLoggedIn,
  authLoading,
}: InvestmentCalendarNotificationProps) {
  const [setting, setSetting] = useState(DEFAULT_SETTING);
  const [savedSetting, setSavedSetting] = useState<InvestmentCalendarNotificationSetting | null>(
    null,
  );
  const [recommendationOpen, setRecommendationOpen] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [verificationRequested, setVerificationRequested] = useState(false);
  const [message, setMessage] = useState('');
  const [toast, setToast] = useState('');
  const closeButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (authLoading) return;
    const dismissed = localStorage.getItem(NOTIFICATION_PROMPT_DISMISSED_KEY) === 'true';

    if (!isLoggedIn) {
      setRecommendationOpen(!dismissed);
      return;
    }

    const controller = new AbortController();
    setLoading(true);
    void getInvestmentCalendarNotification(controller.signal).then(
      (response) => {
        if (response) {
          setSetting(response);
          setSavedSetting(response);
          setRecommendationOpen(false);
        } else {
          setRecommendationOpen(!dismissed);
        }
        setLoading(false);
      },
      (error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') return;
        setLoading(false);
        setRecommendationOpen(!dismissed);
      },
    );
    return () => controller.abort();
  }, [authLoading, isLoggedIn]);

  const closeSettings = useCallback(() => {
    setSetting(savedSetting ?? DEFAULT_SETTING);
    setVerificationRequested(false);
    setMessage('');
    setModalOpen(false);
  }, [savedSetting]);

  useEffect(() => {
    if (!modalOpen) return;
    closeButtonRef.current?.focus();
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') closeSettings();
    };
    window.addEventListener('keydown', closeOnEscape);
    return () => window.removeEventListener('keydown', closeOnEscape);
  }, [closeSettings, modalOpen]);

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(''), 2200);
    return () => window.clearTimeout(timer);
  }, [toast]);

  const openSettings = () => {
    setRecommendationOpen(false);
    setMessage('');
    setModalOpen(true);
  };

  const dismissRecommendation = () => {
    localStorage.setItem(NOTIFICATION_PROMPT_DISMISSED_KEY, 'true');
    setRecommendationOpen(false);
  };

  const changeEmail = (email: string) => {
    setSetting((current) => ({ ...current, email, emailVerified: false }));
    setVerificationRequested(false);
    setMessage('');
  };

  const verifyEmail = async () => {
    const email = setting.email.trim();
    if (!isValidEmail(email)) {
      setMessage('이메일 주소를 정확히 입력해 주세요.');
      return;
    }

    setSubmitting(true);
    setMessage('');
    try {
      await requestNotificationEmailVerification({ email });
      setSetting((current) => ({ ...current, email }));
      setVerificationRequested(true);
      setMessage('인증 메일을 보냈어요. 메일에서 인증을 완료해 주세요.');
    } catch {
      setMessage('인증 메일을 보내지 못했어요. 잠시 후 다시 시도해 주세요.');
    } finally {
      setSubmitting(false);
    }
  };

  const saveSetting = async () => {
    const email = setting.email.trim();
    if (!isValidEmail(email)) {
      setMessage('이메일 주소를 정확히 입력해 주세요.');
      return;
    }
    if (!setting.emailVerified && !verificationRequested) {
      setMessage('이메일 인증을 먼저 요청해 주세요.');
      return;
    }
    if (setting.scopes.length === 0 || setting.timings.length === 0) {
      setMessage('받을 일정과 알림 시점을 하나 이상 선택해 주세요.');
      return;
    }
    if (!setting.sendBeforeAnnouncement && !setting.sendAfterAnnouncement) {
      setMessage('받을 이메일 종류를 하나 이상 선택해 주세요.');
      return;
    }

    setSubmitting(true);
    setMessage('');
    try {
      const saved = await updateInvestmentCalendarNotification({
        email,
        scopes: setting.scopes,
        timings: setting.timings,
        sendBeforeAnnouncement: setting.sendBeforeAnnouncement,
        sendAfterAnnouncement: setting.sendAfterAnnouncement,
      });
      setSetting(saved);
      setSavedSetting(saved);
      setVerificationRequested(false);
      setModalOpen(false);
      setToast('이메일 알림 설정을 저장했어요.');
    } catch {
      setMessage('알림 설정을 저장하지 못했어요. 잠시 후 다시 시도해 주세요.');
    } finally {
      setSubmitting(false);
    }
  };

  const unsubscribe = async () => {
    setSubmitting(true);
    setMessage('');
    try {
      await deleteInvestmentCalendarNotification();
      setSetting(DEFAULT_SETTING);
      setSavedSetting(null);
      setVerificationRequested(false);
      setModalOpen(false);
      setToast('이메일 알림을 해지했어요.');
    } catch {
      setMessage('알림을 해지하지 못했어요. 잠시 후 다시 시도해 주세요.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="investment-calendar-notification">
      <button
        className="investment-calendar-notification-button"
        type="button"
        onClick={openSettings}
      >
        <BellIcon />
        일정 알림 설정
      </button>

      {recommendationOpen ? (
        <section
          className="investment-calendar-notification-recommendation"
          aria-labelledby="notification-recommendation-title"
        >
          <div className="investment-calendar-notification-recommendation-head">
            <BellIcon />
            <h2 id="notification-recommendation-title">투자 일정, 이메일로 미리 받아보세요</h2>
            <button
              className="investment-calendar-notification-popover-close"
              type="button"
              aria-label="추천 팝업 닫기"
              onClick={() => setRecommendationOpen(false)}
            >
              <CloseIcon />
            </button>
          </div>
          <p>내 자산 관련 발표 일정과 결과를 원하는 이메일로 알려드려요.</p>
          <div className="investment-calendar-notification-recommendation-actions">
            <button type="button" onClick={dismissRecommendation}>
              다시 보지 않기
            </button>
            <button type="button" onClick={openSettings}>
              알림 설정
            </button>
          </div>
        </section>
      ) : null}

      {modalOpen ? (
        <div
          className="investment-calendar-notification-overlay"
          role="presentation"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) closeSettings();
          }}
        >
          <section
            className="investment-calendar-notification-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="notification-modal-title"
          >
            <header>
              <div>
                <div className="investment-calendar-notification-modal-title">
                  <BellIcon />
                  <h2 id="notification-modal-title">일정 알림 설정</h2>
                </div>
                <p>원하는 이메일로 필요한 투자 일정을 보내드려요.</p>
              </div>
              <button
                ref={closeButtonRef}
                className="investment-calendar-notification-close"
                type="button"
                aria-label="알림 설정 닫기"
                onClick={closeSettings}
              >
                <CloseIcon />
              </button>
            </header>

            {authLoading || loading ? (
              <div className="investment-calendar-notification-state" aria-busy="true">
                알림 설정을 불러오는 중입니다.
              </div>
            ) : !isLoggedIn ? (
              <div className="investment-calendar-notification-state">
                <h3>로그인 후 알림을 설정할 수 있어요</h3>
                <p>로그인한 뒤 알림 받을 이메일을 직접 선택할 수 있어요.</p>
                <a
                  href="/login"
                  onClick={() =>
                    sessionStorage.setItem('daynomy:post-login-path', '/investment-calendar')
                  }
                >
                  로그인 후 설정하기
                </a>
              </div>
            ) : (
              <>
                <div className="investment-calendar-notification-body">
                  <div className="investment-calendar-notification-email">
                    <label htmlFor="investment-calendar-notification-email">알림 받을 이메일</label>
                    <div>
                      <input
                        id="investment-calendar-notification-email"
                        type="email"
                        value={setting.email}
                        autoComplete="email"
                        placeholder="name@example.com"
                        onChange={(event) => changeEmail(event.target.value)}
                      />
                      <button
                        type="button"
                        disabled={submitting}
                        onClick={() => void verifyEmail()}
                      >
                        {setting.emailVerified ? '인증 완료' : '인증하기'}
                      </button>
                    </div>
                    <p>Google 계정과 관계없이 원하는 이메일을 입력할 수 있어요.</p>
                  </div>

                  <fieldset>
                    <legend>어떤 일정을 받을까요?</legend>
                    <div className="investment-calendar-notification-options">
                      {SCOPE_OPTIONS.map((option) => (
                        <label key={option.value}>
                          <input
                            type="checkbox"
                            checked={setting.scopes.includes(option.value)}
                            onChange={(event) =>
                              setSetting((current) => ({
                                ...current,
                                scopes: updateSelection(
                                  current.scopes,
                                  option.value,
                                  event.target.checked,
                                ),
                              }))
                            }
                          />
                          {option.label}
                        </label>
                      ))}
                    </div>
                  </fieldset>

                  <fieldset>
                    <legend>언제 알려드릴까요?</legend>
                    <div className="investment-calendar-notification-options">
                      {TIMING_OPTIONS.map((option) => (
                        <label key={option.value}>
                          <input
                            type="checkbox"
                            checked={setting.timings.includes(option.value)}
                            onChange={(event) =>
                              setSetting((current) => ({
                                ...current,
                                timings: updateSelection(
                                  current.timings,
                                  option.value,
                                  event.target.checked,
                                ),
                              }))
                            }
                          />
                          {option.label}
                        </label>
                      ))}
                    </div>
                  </fieldset>

                  <fieldset>
                    <legend>어떤 이메일을 받을까요?</legend>
                    <div className="investment-calendar-notification-kinds">
                      <label>
                        <span>
                          <strong>발표 전 알림</strong>
                          확인할 지표와 내 자산의 과거 반응
                        </span>
                        <input
                          type="checkbox"
                          checked={setting.sendBeforeAnnouncement}
                          onChange={(event) =>
                            setSetting((current) => ({
                              ...current,
                              sendBeforeAnnouncement: event.target.checked,
                            }))
                          }
                        />
                      </label>
                      <label>
                        <span>
                          <strong>발표 결과 알림</strong>
                          실제 발표 결과와 공식 출처
                        </span>
                        <input
                          type="checkbox"
                          checked={setting.sendAfterAnnouncement}
                          onChange={(event) =>
                            setSetting((current) => ({
                              ...current,
                              sendAfterAnnouncement: event.target.checked,
                            }))
                          }
                        />
                      </label>
                    </div>
                  </fieldset>
                  {message ? (
                    <p className="investment-calendar-notification-message" role="status">
                      {message}
                    </p>
                  ) : null}
                </div>

                <footer>
                  {savedSetting ? (
                    <button type="button" disabled={submitting} onClick={() => void unsubscribe()}>
                      알림 해지
                    </button>
                  ) : (
                    <span />
                  )}
                  <div>
                    <button type="button" onClick={closeSettings}>
                      취소
                    </button>
                    <button type="button" disabled={submitting} onClick={() => void saveSetting()}>
                      {submitting ? '저장 중' : '설정 저장'}
                    </button>
                  </div>
                </footer>
              </>
            )}
          </section>
        </div>
      ) : null}

      {toast ? (
        <div className="investment-calendar-notification-toast" role="status" aria-live="polite">
          {toast}
        </div>
      ) : null}
    </div>
  );
}
