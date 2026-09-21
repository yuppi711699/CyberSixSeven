import { expect, test } from '@playwright/test';

test.describe('student auth chrome', () => {
  test('login page renders email/password and google, never a jwt in the url', async ({
    page,
  }) => {
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
    await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Continue with Google' })).toBeVisible();
    expect(page.url()).not.toMatch(/eyJ|accessToken/);
    const storage = await page.evaluate(() => ({
      local: { ...window.localStorage },
      session: { ...window.sessionStorage },
    }));
    expect(JSON.stringify(storage)).not.toMatch(/eyJ|accessToken|refresh_token/);
  });

  test('oauth callback exchanges once, strips the code, and never puts a jwt in the url', async ({
    page,
  }) => {
    let exchanges = 0;
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
    await page.route('**/api/auth/oauth/exchange', async (route) => {
      exchanges += 1;
      expect(route.request().headers()['x-xsrf-token']).toBe('csrf-e2e');
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
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
    await page.route('**/api/questions', async (route) => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    });

    await page.goto('/auth/callback?code=one-time');
    await expect(page).toHaveURL(/\/$/);
    expect(page.url()).not.toMatch(/code=|eyJ|accessToken/);
    expect(exchanges).toBe(1);
    await expect(page.getByText('Signed in as Pat')).toBeVisible();
  });

  test('logout posts csrf and returns to sign-in', async ({ page }) => {
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
    await page.route('**/api/auth/logout', async (route) => {
      expect(route.request().headers()['x-xsrf-token']).toBe('csrf-e2e');
      await route.fulfill({ status: 204 });
    });
    await page.route('**/api/questions', async (route) => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    });

    await page.goto('/');
    await expect(page.getByText('Signed in as Pat')).toBeVisible();
    await page.getByRole('button', { name: 'Log out' }).click();
    await expect(page.getByRole('link', { name: 'Sign in' })).toBeVisible();
    const storage = await page.evaluate(() => ({
      local: { ...window.localStorage },
      session: { ...window.sessionStorage },
    }));
    expect(JSON.stringify(storage)).not.toMatch(/eyJ|accessToken|refresh_token/);
  });
});
