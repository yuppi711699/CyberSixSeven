import { expect, test } from '@playwright/test';

test.describe('admin role gate', () => {
  test('rejects a student session with an explicit forbidden-role screen', async ({ page }) => {
    await page.route('**/api/csrf', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ token: 'csrf-e2e' }),
      });
    });
    await page.route('**/api/auth/refresh', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        headers: { 'Set-Cookie': 'refresh_token=opaque; Path=/api/auth; HttpOnly; SameSite=Lax' },
        body: JSON.stringify({
          accessToken: 'header.payload.sig',
          expiresInSeconds: 600,
          user: {
            id: '11111111-1111-1111-1111-111111111111',
            email: 'pat@example.test',
            nickname: 'Pat',
            role: 'STUDENT',
          },
        }),
      });
    });

    await page.goto('http://localhost:3001/');
    await expect(page.getByRole('heading', { name: 'Forbidden role' })).toBeVisible();
    await expect(page.getByText('Student accounts cannot use the admin app')).toBeVisible();
  });

  test('admin login page offers email/password and google', async ({ page }) => {
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
    await expect(page.getByRole('heading', { name: 'Admin sign in' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Continue with Google' })).toBeVisible();
    expect(page.url()).not.toMatch(/eyJ|accessToken/);
  });
});
