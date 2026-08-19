interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
  readonly SENTRY_DSN?: string;
  readonly SENTRY_ENVIRONMENT?: 'local' | 'staging' | 'production';
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
