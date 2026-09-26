import { expect, test } from '@playwright/test';

const live = process.env.E2E_LIVE === '1';

test.describe('local stack', () => {
  test.skip(!live, 'Set E2E_LIVE=1 with platform-api, both Next apps, and the local compose stack.');

  test('student registers, submits, and reaches accessory status', async ({ page }) => {
    const email = `e2e-${Date.now()}@example.test`;
    await page.goto('/login');
    await page.getByRole('link', { name: /create|register|sign up/i }).click();
    await page.getByLabel('Email').fill(email);
    await page.getByLabel('Password').fill('password1');
    await page.getByLabel(/nickname/i).fill('Pat');
    await page.getByRole('button', { name: /create|register/i }).click();
    await expect(page.getByRole('heading', { name: /what is/i }).first()).toBeVisible();
    const answers = page.getByLabel('Your answer');
    const count = await answers.count();
    for (let i = 0; i < count; i += 1) {
      await answers.nth(i).fill(i === 0 ? '12' : i === 1 ? '27' : '0');
    }
    await page.getByRole('button', { name: 'Submit answers' }).click();
    await expect(page.getByRole('heading', { name: 'Results' })).toBeVisible();
  });
});

test.describe('production auth preview', () => {
  test.skip(
    process.env.E2E_PRODUCTION !== '1',
    'Set E2E_PRODUCTION=1 with STUDENT_E2E_EMAIL/PASSWORD and STAFF_E2E_EMAIL/PASSWORD. Google hosted login stays manual.',
  );

  test('student origin login page is the auth-preview gate', async ({ page }) => {
    await page.goto('https://app.aiastrologyperdictions.com/login');
    await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();
  });
});
