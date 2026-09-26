import AxeBuilder from '@axe-core/playwright';
import { expect, type Page, test } from '@playwright/test';

const student = {
  id: '11111111-1111-1111-1111-111111111111',
  email: 'pat@example.test',
  nickname: 'Pat',
  role: 'STUDENT',
};

const teacher = {
  id: '22222222-2222-2222-2222-222222222222',
  email: 'teacher@example.test',
  nickname: 'Teacher',
  role: 'TEACHER',
};

async function stubSession(page: Page, user: typeof student) {
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
        user,
      }),
    });
  });
}

async function expectNoSeriousAxe(page: Page) {
  const results = await new AxeBuilder({ page }).analyze();
  const blocking = results.violations.filter(
    (violation) => violation.impact === 'critical' || violation.impact === 'serious',
  );
  expect(blocking, JSON.stringify(blocking, null, 2)).toEqual([]);
}

test.describe('axe', () => {
  test('student login', async ({ page }) => {
    await stubSession(page, student);
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();
    await expectNoSeriousAxe(page);
  });

  test('student questions, results, and accessory', async ({ page }) => {
    await stubSession(page, student);
    await page.route('**/api/questions', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            id: '00000000-0000-0000-0000-000000000001',
            prompt: 'What is 7 + 5?',
            options: [],
            displayOrder: 1,
          },
        ]),
      });
    });
    await page.route('**/api/submissions', async (route) => {
      if (route.request().method() !== 'POST') {
        await route.fallback();
        return;
      }
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify({
          id: '33333333-3333-3333-3333-333333333333',
          submissionSecret: 'capability-not-logged',
          score: 1,
          maxScore: 1,
          answers: [
            {
              questionId: '00000000-0000-0000-0000-000000000001',
              prompt: 'What is 7 + 5?',
              submittedAnswer: 12,
              correctAnswer: 12,
              awardedPoints: 1,
              maxPoints: 1,
              correct: true,
            },
          ],
        }),
      });
    });
    await page.route('**/api/submissions/33333333-3333-3333-3333-333333333333**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: '33333333-3333-3333-3333-333333333333',
          score: 1,
          maxScore: 1,
          answers: [],
          accessoryStatus: 'READY',
        }),
      });
    });

    await page.goto('/');
    await expect(page.getByRole('heading', { name: 'What is 7 + 5?' })).toBeVisible();
    await expectNoSeriousAxe(page);

    await page.getByLabel('Your answer').fill('12');
    await page.getByRole('button', { name: 'Submit answers' }).click();
    await expect(page.getByRole('heading', { name: 'Results' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Download accessory' })).toBeVisible();
    await expectNoSeriousAxe(page);

    const storage = await page.evaluate(() => JSON.stringify({ ...window.localStorage }));
    expect(storage).not.toContain('capability-not-logged');
    expect(storage).not.toContain('header.payload.sig');
  });

  test('admin login, question bank, and devices', async ({ page }) => {
    await stubSession(page, teacher);
    await page.route('**/api/admin/questions', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            id: '00000000-0000-0000-0000-000000000001',
            prompt: 'What is 7 + 5?',
            options: [],
            correctAnswer: 12,
            maxPoints: 1,
            displayOrder: 1,
          },
        ]),
      });
    });
    await page.route('**/api/admin/devices**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [
            {
              id: '44444444-4444-4444-4444-444444444444',
              hardwareId: 'esp32-dev-001',
              lastSeenAt: null,
            },
          ],
          totalElements: 1,
          totalPages: 1,
          number: 0,
          size: 20,
        }),
      });
    });

    await page.goto('http://localhost:3001/login');
    await expect(page.getByRole('heading', { name: 'Admin sign in' })).toBeVisible();
    await expectNoSeriousAxe(page);

    await page.goto('http://localhost:3001/questions');
    await expect(page.getByRole('heading', { name: 'Questions' })).toBeVisible();
    await expectNoSeriousAxe(page);

    await page.goto('http://localhost:3001/devices');
    await expect(page.getByRole('heading', { name: 'Devices' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Resend command' })).toBeVisible();
    await expectNoSeriousAxe(page);
  });
});
