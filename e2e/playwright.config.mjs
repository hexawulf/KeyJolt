// Playwright config for the KeyJolt smoke suite.
//
//   npm run test:e2e        boot target/keyjolt-*.jar on 127.0.0.1:5095 (isolated temp
//                           and log dirs), run every test, stop it with SIGTERM
//   npm run test:e2e:url    run against an app that is already up (KEYJOLT_BASE_URL)
//   npm run test:e2e:live   read-only checks against https://keyjolt.dev (no key generation)
import { defineConfig, devices } from '@playwright/test';

const baseURL = process.env.KEYJOLT_BASE_URL || 'http://127.0.0.1:5095';

export default defineConfig({
  testDir: '.',
  testMatch: /.*\.spec\.mjs$/,
  outputDir: 'test-results',
  fullyParallel: false,
  workers: 1, // the app rate-limits per client; one worker keeps the budget predictable
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    acceptDownloads: true,
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
