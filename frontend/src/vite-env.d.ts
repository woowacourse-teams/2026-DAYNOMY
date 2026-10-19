interface ImportMetaEnv {
  readonly VITE_POSTHOG_PROJECT_TOKEN?: string;
  readonly VITE_POSTHOG_HOST?: string;
  readonly VITE_API_BASE_URL?: string;
  readonly SENTRY_DSN?: string;
  readonly SENTRY_ENVIRONMENT?: 'local' | 'staging' | 'production';
  readonly GA_MEASUREMENT_ID?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
