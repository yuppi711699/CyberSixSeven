import { cleanup, render, screen } from '@testing-library/react';
import { AuthProvider } from '@cybersixseven/auth-client';
import { createElement, type ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import HomePage from './page';

vi.mock('next/link', () => ({
  default: ({ href, children }: { href: string; children: ReactNode }) =>
    createElement('a', { href }, children),
}));

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('HomePage', () => {
  it('renders the CyberSixSeven brand and asks unauthenticated users to sign in', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo) => {
        const url = String(input);
        if (url.endsWith('/api/csrf')) {
          return new Response(JSON.stringify({ token: 'csrf' }), { status: 200 });
        }
        return new Response(JSON.stringify({ code: 'UNAUTHORIZED' }), { status: 401 });
      }),
    );

    render(createElement(AuthProvider, null, createElement(HomePage)));

    expect(screen.getByRole('heading', { level: 1, name: 'CyberSixSeven' })).toBeTruthy();
    expect(await screen.findByRole('link', { name: 'Sign in' })).toBeTruthy();
  });

  it('hides product forms when NEXT_PUBLIC_PRODUCT_ENABLED is false', async () => {
    const previous = process.env.NEXT_PUBLIC_PRODUCT_ENABLED;
    process.env.NEXT_PUBLIC_PRODUCT_ENABLED = 'false';
    try {
      vi.stubGlobal(
        'fetch',
        vi.fn(async (input: RequestInfo) => {
          if (String(input).endsWith('/api/csrf')) {
            return new Response(JSON.stringify({ token: 'csrf' }), { status: 200 });
          }
          return new Response(JSON.stringify({ code: 'UNAUTHORIZED' }), { status: 401 });
        }),
      );

      render(createElement(AuthProvider, null, createElement(HomePage)));
      expect(await screen.findByRole('status')).toBeTruthy();
      expect(screen.getByText(/unavailable until the v0.8 backend smoke gate/i)).toBeTruthy();
      expect(screen.queryByRole('link', { name: 'Sign in' })).toBeNull();
    } finally {
      process.env.NEXT_PUBLIC_PRODUCT_ENABLED = previous;
    }
  });
});
