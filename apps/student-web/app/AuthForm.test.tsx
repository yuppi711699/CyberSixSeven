import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { createElement, type ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { resetAuthClientForTests } from '@cybersixseven/auth-client';
import { AuthForm } from './AuthForm';

vi.mock('next/link', () => ({
  default: ({ href, children }: { href: string; children: ReactNode }) =>
    createElement('a', { href }, children),
}));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: vi.fn() }),
}));

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  resetAuthClientForTests();
});

describe('AuthForm', () => {
  it('registers a student without sending a role', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/api/csrf')) {
        return new Response(JSON.stringify({ token: 'csrf-1' }), { status: 200 });
      }
      if (url.endsWith('/api/auth/register')) {
        expect(JSON.parse(String(init?.body))).toEqual({
          email: 'pat@example.test',
          password: 'password1',
          nickname: 'Pat',
        });
        return new Response(
          JSON.stringify({
            accessToken: 'a.b.c',
            expiresInSeconds: 600,
            user: {
              id: '11111111-1111-1111-1111-111111111111',
              email: 'pat@example.test',
              nickname: 'Pat',
              role: 'STUDENT',
            },
          }),
          { status: 201 },
        );
      }
      throw new Error(url);
    });
    vi.stubGlobal('fetch', fetchMock);

    render(createElement(AuthForm, { mode: 'student', homeHref: '/' }));
    fireEvent.change(screen.getByLabelText('Nickname'), { target: { value: 'Pat' } });
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'pat@example.test' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'password1' } });
    fireEvent.click(screen.getByRole('button', { name: 'Create account' }));

    await waitFor(() => {
      expect(
        fetchMock.mock.calls.some((call) => String(call[0]).endsWith('/api/auth/register')),
      ).toBe(true);
    });
  });
});
