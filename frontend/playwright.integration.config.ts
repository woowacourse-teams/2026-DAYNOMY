import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './integration',
  testMatch: '**/*.e2e.ts',
  workers: 1,
  timeout: 90_000,
  reporter: [['list'], ['json', { outputFile: 'integration-results/playwright.json' }]],
  use: {
    baseURL: 'http://127.0.0.1:4185',
    browserName: 'chromium',
    screenshot: 'only-on-failure',
    trace: 'off',
  },
  webServer: {
    command: 'npm run preview -- --host 127.0.0.1 --port 4185 --strictPort',
    url: 'http://127.0.0.1:4185',
    reuseExistingServer: false,
  },
});
