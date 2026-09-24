import { ApiClientError } from '@cybersixseven/api-client';
import { AuthProvider, resetAuthClientForTests } from '@cybersixseven/auth-client';
import { ToastProvider } from '@cybersixseven/ui';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { createElement, type ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import DevicesPage from './page';

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
      createElement(ToastProvider, null, createElement(AuthProvider, null, createElement(DevicesPage))),
    ),
  );
}

afterEach(() => {
  cleanup();
  resetAuthClientForTests();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('DevicesPage', () => {
  it('shows the resend result only after the request resolves', async () => {
    let release: (value: Response) => void = () => undefined;
    const pending = new Promise<Response>((resolve) => {
      release = resolve;
    });
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
        if (url.includes('/api/admin/devices') && (!init || !init.method || init.method === 'GET')) {
          return new Response(
            JSON.stringify({
              content: [
                {
                  id: '22222222-2222-2222-2222-222222222222',
                  hardwareId: 'esp32-dev-001',
                  studentId: null,
                  active: true,
                  lastSeenAt: '2026-09-23T00:00:00Z',
                  createdAt: '2026-09-23T00:00:00Z',
                },
              ],
              page: { size: 20, number: 0, totalElements: 1, totalPages: 1 },
            }),
            { status: 200 },
          );
        }
        return pending;
      }),
    );

    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'Resend command' }));
    expect(screen.queryByRole('status')).toBeNull();
    expect(screen.getByRole('button', { name: 'Resending…' })).toBeTruthy();
    release(
      new Response(
        JSON.stringify({
          commandId: '33333333-3333-3333-3333-333333333333',
          accepted: true,
          message: 'command republished',
        }),
        { status: 200 },
      ),
    );
    expect(await screen.findByRole('status')).toHaveProperty(
      'textContent',
      expect.stringContaining('command republished'),
    );
  });

  it('shows a resend failure from the API', async () => {
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
          return new Response(
            JSON.stringify({
              content: [
                {
                  id: '22222222-2222-2222-2222-222222222222',
                  hardwareId: 'esp32-dev-001',
                  studentId: null,
                  active: true,
                  lastSeenAt: null,
                  createdAt: '2026-09-23T00:00:00Z',
                },
              ],
              page: { size: 20, number: 0, totalElements: 1, totalPages: 1 },
            }),
            { status: 200 },
          );
        }
        return new Response(JSON.stringify({ code: 'RESEND_TIMEOUT', message: 'command resend timed out' }), {
          status: 504,
        });
      }),
    );

    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'Resend command' }));
    expect(await screen.findByRole('alert')).toHaveProperty(
      'textContent',
      'command resend timed out',
    );
    expect(ApiClientError).toBeTruthy();
  });
});
