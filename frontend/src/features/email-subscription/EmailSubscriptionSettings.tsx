import { useEffect, useRef, useState } from 'react';
import {
  getEmailSubscription,
  subscribeToEmail,
  unsubscribeFromEmail,
  type EmailSubscriptionResponse,
} from './api';

export function EmailSubscriptionSettings({ disabled = false }: { disabled?: boolean }) {
  const [subscription, setSubscription] = useState<EmailSubscriptionResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [retry, setRetry] = useState(0);
  const controllerRef = useRef<AbortController | null>(null);
  const submitting = useRef(false);

  useEffect(() => {
    const controller = new AbortController();
    controllerRef.current = controller;
    setLoading(true);
    setError('');
    getEmailSubscription(controller.signal)
      .then((value) => {
        if (!controller.signal.aborted) setSubscription(value);
      })
      .catch(() => {
        if (!controller.signal.aborted)
          setError('구독 정보를 불러오지 못했어요. 다시 불러오기를 눌러 주세요.');
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [retry]);

  async function changeSubscription() {
    const controller = controllerRef.current;
    if (!subscription || disabled || submitting.current || !controller || controller.signal.aborted)
      return;
    const subscribed = subscription.status === 'SUBSCRIBED';
    submitting.current = true;
    setSaving(true);
    setError('');
    setNotice('');
    try {
      const updated = await (subscribed ? unsubscribeFromEmail : subscribeToEmail)(
        controller.signal,
      );
      if (controller.signal.aborted) return;
      setSubscription(updated);
      setNotice(
        updated.status === 'SUBSCRIBED' ? '이메일 알림을 구독했어요.' : '이메일 알림을 해지했어요.',
      );
    } catch {
      if (!controller.signal.aborted)
        setError('구독 설정을 저장하지 못했어요. 연결을 확인하고 다시 시도해 주세요.');
    } finally {
      submitting.current = false;
      if (!controller.signal.aborted) setSaving(false);
    }
  }

  const subscribed = subscription?.status === 'SUBSCRIBED';
  return (
    <section
      className="mypage-session mypage-email"
      aria-labelledby="mypage-email-title"
      aria-busy={loading || saving}
    >
      <h2 id="mypage-email-title">이메일 알림</h2>
      <div>
        {loading ? (
          <p role="status">구독 정보를 불러오고 있습니다.</p>
        ) : subscription ? (
          <>
            <div className="mypage-session-row">
              <div className="mypage-email-information">
                <h3>
                  {subscribed
                    ? '구독 중'
                    : subscription.status === 'UNSUBSCRIBED'
                      ? '구독 해지'
                      : '미구독'}
                </h3>
                <dl>
                  <dt>수신 이메일</dt>
                  <dd>{subscription.email}</dd>
                </dl>
              </div>
              <button
                type="button"
                className={subscribed ? '' : 'mypage-email-subscribe'}
                disabled={disabled || saving}
                onClick={() => void changeSubscription()}
              >
                {saving
                  ? '저장 중…'
                  : subscribed
                    ? '구독 해지'
                    : subscription.status === 'UNSUBSCRIBED'
                      ? '재구독'
                      : '구독하기'}
              </button>
            </div>
            <p className="mypage-note">
              내 자산과 보유 종목의 가격 변화, 새 관련 자료와 분석 업데이트 알림을 로그인한 Google
              이메일로 받아보세요.
            </p>
          </>
        ) : (
          <button type="button" disabled={disabled} onClick={() => setRetry((value) => value + 1)}>
            구독 정보 다시 불러오기
          </button>
        )}
        {error && <p role="alert">{error}</p>}
        {notice && <p role="status">{notice}</p>}
      </div>
    </section>
  );
}
