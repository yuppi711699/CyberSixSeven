import { cleanup, render, screen } from '@testing-library/react';
import { AuthProvider } from '@cybersixseven/auth-client';
import { createElement, type ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import AdminHomePage from './page';

vi.mock('next/link', () => ({
  default: ({ href, children }: { href: string; children: ReactNode }) =>
    createElement('a', { href }, children),
}));

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('AdminHomePage', () => {
  it('asks staff to sign in', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo) => {
        if (String(input).endsWith('/api/csrf')) {
          return new Response(JSON.stringify({ token: 'csrf' }), { status: 200 });
        }
        return new Response(JSON.stringify({ code: 'UNAUTHORIZED' }), { status: 401 });
      }),
    );

    render(createElement(AuthProvider, null, createElement(AdminHomePage)));
    expect(screen.getByRole('heading', { level: 1, name: 'CyberSixSeven Admin' })).toBeTruthy();
    expect(await screen.findByRole('link', { name: 'Sign in' })).toBeTruthy();
  });

  it('rejects a student session with an explicit forbidden-role screen', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo) => {
        if (String(input).endsWith('/api/csrf')) {
          return new Response(JSON.stringify({ token: 'csrf' }), { status: 200 });
        }
        return new Response(
          JSON.stringify({
            accessToken: 'header.payload.sig',
            expiresInSeconds: 600,
            user: {
              id: '11111111-1111-1111-1111-111111111111',
              email: 'pat@example.test',
              nickname: 'Pat',
              role: 'STUDENT',
            },
          }),
          { status: 200 },
        );
      }),
    );

    render(createElement(AuthProvider, null, createElement(AdminHomePage)));
    expect(await screen.findByRole('heading', { name: 'Forbidden role' })).toBeTruthy();
    expect(screen.getByText('Student accounts cannot use the admin app.')).toBeTruthy();
  });
});
