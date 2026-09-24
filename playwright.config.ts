import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 2 : 0,
  use: {
    trace: 'on-first-retry',
    baseURL: 'http://localhost:3000',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: [
    {
      command: 'pnpm --filter @cybersixseven/student-web dev',
      url: 'http://localhost:3000',
      reuseExistingServer: !process.env.CI,
      env: {
        NEXT_PUBLIC_API_BASE_URL: 'http://localhost:8080',
        NEXT_PUBLIC_PRODUCT_ENABLED: 'true',
      },
    },
    {
      command: 'pnpm --filter @cybersixseven/admin-web dev',
      url: 'http://localhost:3001',
      reuseExistingServer: !process.env.CI,
      env: {
        NEXT_PUBLIC_API_BASE_URL: 'http://localhost:8080',
        NEXT_PUBLIC_PRODUCT_ENABLED: 'true',
      },
    },
  ],
});
