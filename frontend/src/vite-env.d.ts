interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
  readonly VITE_INVESTMENT_CALENDAR_MOCK_ENABLED?: string;
  readonly SENTRY_DSN?: string;
  readonly SENTRY_ENVIRONMENT?: 'local' | 'staging' | 'production';
  readonly GA_MEASUREMENT_ID?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
