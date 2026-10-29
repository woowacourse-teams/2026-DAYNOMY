import { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../hooks/useLoginStatus';
import { isStaffHostname } from '../../serviceDomain';
import { PortfolioDialog } from '../portfolio/components/PortfolioDialog';
import {
  dismissEmailSubscription,
  getEmailSubscription,
  subscribeToEmail,
  type EmailSubscriptionResponse,
} from './api';
import './emailSubscription.css';

export function EmailSubscriptionPrompt() {
  const { isLoggedIn, loading, memberId } = useAuth();
  const { pathname } = useLocation();
  if (
    loading ||
    !isLoggedIn ||
    isStaffHostname(window.location.hostname) ||
    /^\/(?:admin|login|signup|welcome|mypage)(?:\/|$)/.test(pathname)
  )
    return null;
  return <EmailSubscriptionDialog key={memberId} />;
}

function EmailSubscriptionDialog() {
  const navigate = useNavigate();
  const [subscription, setSubscription] = useState<EmailSubscriptionResponse | null>(null);
  const [confirmation, setConfirmation] = useState(false);
  const [pending, setPending] = useState<'subscribe' | 'dismiss' | null>(null);
  const [error, setError] = useState<string | null>(null);
  const controllerRef = useRef<AbortController | null>(null);
  const submitting = useRef(false);
  useEffect(() => {
    const controller = new AbortController();
    controllerRef.current = controller;
    getEmailSubscription(controller.signal)
      .then((value) => {
        if (!controller.signal.aborted) setSubscription(value);
      })
      .catch(() => {
        // Subscription lookup must not block login if the API is unavailable.
      });
    return () => controller.abort();
  }, []);

  async function choose(action: 'subscribe' | 'dismiss') {
    const controller = controllerRef.current;
    if (submitting.current || !controller || controller.signal.aborted) return;
    submitting.current = true;
    setPending(action);
    setError(null);
    try {
      const value = await (action === 'subscribe' ? subscribeToEmail : dismissEmailSubscription)(
        controller.signal,
      );
      if (controller.signal.aborted) return;
      setSubscription(value);
      if (action === 'subscribe') setConfirmation(true);
      else navigate('/', { replace: true });
    } catch {
      if (!controller.signal.aborted)
        setError('선택을 저장하지 못했어요. 잠시 후 다시 시도해 주세요.');
    } finally {
      submitting.current = false;
      if (!controller.signal.aborted) setPending(null);
    }
  }

  if (!subscription || (!subscription.promptRequired && !confirmation)) return null;

  const titleId = confirmation ? 'email-subscription-complete' : 'email-subscription-title';
  return (
    <PortfolioDialog
      titleId={titleId}
      className="email-subscription-dialog"
      busy={pending !== null}
      onClose={() => {
        if (confirmation) setConfirmation(false);
        else void choose('dismiss');
      }}
    >
      <span className="email-subscription-brand">DAYNOMY</span>
      <button
        type="button"
        className="email-subscription-close"
        aria-label="구독 안내 닫기"
        disabled={pending !== null}
        onClick={() => {
          if (confirmation) setConfirmation(false);
          else void choose('dismiss');
        }}
      >
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="m6 6 12 12M18 6 6 18" />
        </svg>
      </button>
      {confirmation ? (
        <>
          <h2 id={titleId}>이메일 알림 구독 완료</h2>
          <p className="email-subscription-description">
            중요한 소식이 생기면
            <br />
            <strong>{subscription.email}</strong>으로 알려드릴게요.
          </p>
          <button
            type="button"
            className="email-subscription-primary"
            data-autofocus
            onClick={() => setConfirmation(false)}
          >
            확인
          </button>
        </>
      ) : (
        <>
          <h2 id={titleId}>
            내 투자에 중요한 변화,
            <br />
            이메일로 받아보세요
          </h2>
          <p className="email-subscription-description">
            매번 확인하지 않아도,
            <br />
            놓치기 아쉬운 변화만 모아 알려드려요.
          </p>
          <ul className="email-subscription-benefits">
            <li>
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M3 17v4m5-8v8m5-11v11m5-15v15M2 12l6-6 5 4 9-9" />
              </svg>
              <span>내 자산의 변동</span>
            </li>
            <li>
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M13 2H5a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h14V8Zm0 0v6h6m-6 7 3 3" />
                <circle cx="10" cy="13" r="3" />
              </svg>
              <span>보유 종목의 가격 변화</span>
            </li>
            <li>
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <rect x="3" y="4" width="18" height="16" rx="2" />
                <path d="m10 9 5 3-5 3Z" />
              </svg>
              <span>새로 등록된 관련 영상과 글</span>
            </li>
          </ul>
          <div className="email-subscription-address">
            <span>로그인한 Google 이메일</span>
            <strong>{subscription.email}</strong>
          </div>
          {error && (
            <p className="email-subscription-error" role="alert">
              {error}
            </p>
          )}
          <button
            type="button"
            className="email-subscription-primary"
            data-autofocus
            disabled={pending !== null}
            onClick={() => void choose('subscribe')}
          >
            {pending === 'subscribe' ? '구독 중…' : '이메일 알림 구독하기'}
          </button>
          <button
            type="button"
            className="email-subscription-secondary"
            disabled={pending !== null}
            onClick={() => void choose('dismiss')}
          >
            {pending === 'dismiss' ? '저장 중…' : '지금은 괜찮아요'}
          </button>
        </>
      )}
    </PortfolioDialog>
  );
}
