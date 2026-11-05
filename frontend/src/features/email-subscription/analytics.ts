import { trackEvent } from '../../analytics';

type EmailSubscriptionProperties = {
  surface: 'email_subscription_prompt' | 'email_subscription_settings';
  action?: 'subscribe' | 'resubscribe' | 'unsubscribe' | 'dismiss';
  trigger?: 'button' | 'close';
  status?: 'success' | 'error';
  duration_ms?: number;
};

export function trackEmailSubscriptionEvent(
  name:
    | 'email_subscription_prompt_viewed'
    | 'email_subscription_submitted'
    | 'email_subscription_result',
  properties: EmailSubscriptionProperties,
) {
  try {
    trackEvent(name, properties);
  } catch {
    // Analytics must not interrupt subscription changes.
  }
}
