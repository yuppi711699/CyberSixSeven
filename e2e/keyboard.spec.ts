import { expect, test } from '@playwright/test';

test.describe('keyboard', () => {
  test('student sign-in is reachable from the keyboard', async ({ page }) => {
    await page.route('**/api/csrf', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ token: 'csrf-e2e' }),
      });
    });
    await page.route('**/api/auth/refresh', async (route) => {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ code: 'UNAUTHORIZED', message: 'refresh token required' }),
      });
    });

    await page.goto('/login');
    const nickname = page.getByLabel('Nickname');
    for (let i = 0; i < 8; i += 1) {
      if (await nickname.evaluate((node) => node === document.activeElement)) {
        break;
      }
      await page.keyboard.press('Tab');
    }
    await expect(nickname).toBeFocused();
    await page.keyboard.press('Tab');
    await expect(page.getByLabel('Email')).toBeFocused();
    await page.keyboard.press('Tab');
    await expect(page.getByLabel('Password')).toBeFocused();
    await page.keyboard.press('Tab');
    await expect(page.getByRole('button', { name: 'Create account' })).toBeFocused();
  });

  test('admin sign-in is reachable from the keyboard', async ({ page }) => {
    await page.route('**/api/csrf', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ token: 'csrf-e2e' }),
      });
    });
    await page.route('**/api/auth/refresh', async (route) => {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({ code: 'UNAUTHORIZED', message: 'refresh token required' }),
      });
    });

    await page.goto('http://localhost:3001/login');
    await page.keyboard.press('Tab');
    const email = page.getByLabel('Email');
    if (!(await email.evaluate((node) => node === document.activeElement))) {
      await page.keyboard.press('Tab');
    }
    await expect(email).toBeFocused();
    await page.keyboard.press('Tab');
    await expect(page.getByLabel('Password')).toBeFocused();
  });
});
