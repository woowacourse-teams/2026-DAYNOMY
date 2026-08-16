interface ImportMetaEnv {
  readonly SENTRY_DSN?: string;
  readonly SENTRY_ENVIRONMENT?: 'local' | 'staging' | 'production';
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
