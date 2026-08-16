import type { ErrorEvent } from '@sentry/react';

const withoutQuery = (url: string) => url.split(/[?#]/, 1)[0];

export function sanitizeSentryEvent(event: ErrorEvent) {
  delete event.user;

  if (event.request) {
    delete event.request.cookies;
    delete event.request.data;
    delete event.request.headers;
    delete event.request.query_string;

    if (event.request.url) {
      event.request.url = withoutQuery(event.request.url);
    }
  }

  for (const breadcrumb of event.breadcrumbs ?? []) {
    if (typeof breadcrumb.data?.url === 'string') {
      breadcrumb.data.url = withoutQuery(breadcrumb.data.url);
    }
  }

  return event;
}
