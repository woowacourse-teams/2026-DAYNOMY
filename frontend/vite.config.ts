import { sentryVitePlugin } from '@sentry/vite-plugin';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

const sentryBuildConfigured = [
  process.env.SENTRY_AUTH_TOKEN,
  process.env.SENTRY_ORG,
  process.env.SENTRY_PROJECT,
  process.env.SENTRY_RELEASE,
].every(Boolean);

// https://vite.dev/config/
export default defineConfig({
  envPrefix: ['VITE_', 'SENTRY_DSN', 'SENTRY_ENVIRONMENT'],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    sourcemap: sentryBuildConfigured ? 'hidden' : false,
  },
  plugins: [
    react(),
    sentryBuildConfigured &&
      sentryVitePlugin({
        authToken: process.env.SENTRY_AUTH_TOKEN,
        org: process.env.SENTRY_ORG,
        project: process.env.SENTRY_PROJECT,
        release: { name: process.env.SENTRY_RELEASE },
        sourcemaps: { filesToDeleteAfterUpload: './dist/**/*.map' },
        telemetry: false,
      }),
  ],
});
