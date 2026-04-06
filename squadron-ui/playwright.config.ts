import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright E2E test configuration for Squadron UI.
 *
 * Tests assume the full stack is running via testldap-build-and-start.sh
 * with the UI available at http://localhost:4200.
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 1,
  workers: 1,
  reporter: [['html', { open: 'never' }], ['list']],
  timeout: 30_000,
  expect: {
    timeout: 10_000,
  },
  use: {
    baseURL: 'http://localhost:4200',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    // Setup: authenticate once and save storage state
    {
      name: 'setup',
      testMatch: /global-setup\.ts/,
    },
    // All tests that require authentication (exclude auth.spec.ts)
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        storageState: './e2e/.auth/user.json',
      },
      dependencies: ['setup'],
      testIgnore: /auth\.spec\.ts/,
    },
    // Tests that must NOT be authenticated (login page tests)
    {
      name: 'unauthenticated',
      use: {
        ...devices['Desktop Chrome'],
        storageState: { cookies: [], origins: [] },
      },
      testMatch: /auth\.spec\.ts/,
    },
  ],
});
