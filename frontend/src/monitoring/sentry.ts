import * as Sentry from "@sentry/react";
import { sanitizeSentryEvent } from "./sanitizeSentryEvent.ts";

const supportedEnvironments = new Set(["staging", "production"]);

export function initSentry() {
  const dsn = import.meta.env.SENTRY_DSN;
  const environment = import.meta.env.SENTRY_ENVIRONMENT;

  if (
    !import.meta.env.PROD ||
    !dsn ||
    !environment ||
    !supportedEnvironments.has(environment)
  ) {
    return;
  }

  Sentry.init({
    dsn,
    environment,
    sendDefaultPii: false,
    beforeSend: sanitizeSentryEvent,
  });
}
