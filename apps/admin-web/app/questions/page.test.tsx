import { AuthProvider, resetAuthClientForTests } from '@cybersixseven/auth-client';
import { ToastProvider } from '@cybersixseven/ui';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { createElement, type ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import QuestionsPage from './page';

vi.mock('next/link', () => ({
  default: ({ href, children }: { href: string; children: ReactNode }) =>
    createElement('a', { href }, children),
}));

const teacher = {
  accessToken: 'header.payload.sig',
  expiresInSeconds: 600,
  user: {
    id: '11111111-1111-1111-1111-111111111111',
    email: 'teacher@example.test',
    nickname: 'Teacher',
    role: 'TEACHER',
  },
};

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    createElement(
      QueryClientProvider,
      { client },
      createElement(ToastProvider, null, createElement(AuthProvider, null, createElement(QuestionsPage))),
    ),
  );
}

afterEach(() => {
  cleanup();
  resetAuthClientForTests();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('QuestionsPage', () => {
  it('shows a create error and does not claim the question was saved', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo, init?: RequestInit) => {
        const url = String(input);
        if (url.endsWith('/api/csrf')) {
          return new Response(JSON.stringify({ token: 'csrf' }), { status: 200 });
        }
        if (url.endsWith('/api/auth/refresh')) {
          return new Response(JSON.stringify(teacher), { status: 200 });
        }
        if (!init || !init.method || init.method === 'GET') {
          return new Response(JSON.stringify([]), { status: 200 });
        }
        return new Response(
          JSON.stringify({ code: 'INVALID_QUESTION', message: 'displayOrder is already used' }),
          { status: 409 },
        );
      }),
    );

    renderPage();
    fireEvent.change(await screen.findByLabelText('Prompt'), { target: { value: 'What is 1 + 1?' } });
    fireEvent.click(screen.getByRole('button', { name: 'Create question' }));
    expect(await screen.findByRole('alert')).toHaveProperty('textContent', 'displayOrder is already used');
    expect(screen.queryByRole('status')).toBeNull();
  });

  it('sort control is present on the question table', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo) => {
        const url = String(input);
        if (url.endsWith('/api/csrf')) {
          return new Response(JSON.stringify({ token: 'csrf' }), { status: 200 });
        }
        if (url.endsWith('/api/auth/refresh')) {
          return new Response(JSON.stringify(teacher), { status: 200 });
        }
        return new Response(
          JSON.stringify([
            {
              id: 'q1',
              prompt: 'What is 1 + 1?',
              options: [],
              correctAnswer: 2,
              maxPoints: 1,
              displayOrder: 4,
            },
          ]),
          { status: 200 },
        );
      }),
    );

    renderPage();
    expect(await screen.findByText('What is 1 + 1?')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Order ↑' })).toBeTruthy();
  });
});
