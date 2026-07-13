import { defineConfig } from '@playwright/test';

const isolatedBaseUrl = 'http://localhost:18088';

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  retries: 1,
  use: {
    baseURL: process.env['BASE_URL'] || isolatedBaseUrl,
    trace: 'on-first-retry',
  },
  webServer: process.env['SKIP_WEB_SERVER']
    ? undefined
    : {
        command:
          "sh -c 'APP_PORT=18088 POSTGRES_PASSWORD=e2e-local-only DEV_AUTH_ENABLED=true " +
          'docker compose -p engineering-evaluator-e2e up --build --wait && ' +
          "while true; do sleep 3600; done'",
        cwd: '..',
        url: isolatedBaseUrl,
        reuseExistingServer: false,
        timeout: 360_000,
      },
});
