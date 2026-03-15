/**
 * Playwright configuration for E2E acceptance tests.
 *
 * PRD §13.1: E2E (Playwright): Golden path scenarios mapped to §18 acceptance criteria.
 * PRD §13.2 Gate 7: E2E smoke tests (< 5 min, PR subset only)
 *
 * Projects:
 *   - "chromium" — browser-based tests (golden-path.spec.ts) using mocked APIs
 *   - "api"      — API-level integration tests (full-week-lifecycle.spec.ts)
 *                   against a running backend (requires `./scripts/dev.sh --seed`)
 */
import { defineConfig, devices } from '@playwright/test';

const port = 3005;

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,
  reporter: process.env.CI ? 'github' : 'html',

  use: {
    baseURL: process.env.BASE_URL || `http://localhost:${String(port)}`,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },

  projects: [
    {
      name: 'chromium',
      testMatch: /golden-path\.spec\.ts$/,
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'api',
      testMatch: /full-week-lifecycle\.spec\.ts$/,
      use: {
        // No browser needed — these are pure API tests
        browserName: undefined as unknown as 'chromium',
      },
    },
  ],

  webServer: {
    command: `cd ../pa-host-stub && npm run dev -- --port ${String(port)} --strictPort`,
    port,
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
  },
});
