import { configureApiClient } from '@cybersixseven/api-client';
import { cleanup, render, waitFor } from '@testing-library/react';
import { createElement, useEffect } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider, useAuth } from './AuthProvider';
import { getAccessToken, resetAuthClientForTests } from './session';

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status });
}

const user = {
  id: '11111111-1111-1111-1111-111111111111',
  email: 'pat@example.test',
  nickname: 'Pat',
  role: 'STUDENT' as const,
};

function Probe({ onReady }: { onReady: (ready: boolean) => void }) {
  const { ready } = useAuth();
  useEffect(() => {
    onReady(ready);
  }, [onReady, ready]);
  return null;
}

beforeEach(() => {
  resetAuthClientForTests();
  configureApiClient({ baseUrl: 'http://localhost:8080' });
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  resetAuthClientForTests();
  window.history.replaceState({}, '', '/');
});

describe('AuthProvider', () => {
  it('skips bootstrap refresh on the OAuth callback path', async () => {
    window.history.pushState({}, '', '/auth/callback?code=one-time');
    const fetchMock = vi.fn(async () => json({ code: 'UNAUTHORIZED' }, 401));
    vi.stubGlobal('fetch', fetchMock);

    let ready = false;
    render(
      createElement(AuthProvider, null, createElement(Probe, { onReady: (value) => (ready = value) })),
    );

    await waitFor(() => expect(ready).toBe(true));
    expect(fetchMock).not.toHaveBeenCalled();
    expect(getAccessToken()).toBeNull();
  });

  it('bootstraps refresh on ordinary routes', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo) => {
      if (String(input).endsWith('/api/csrf')) {
        return json({ token: 'csrf-1' });
      }
      return json({ accessToken: 'boot.jwt', expiresInSeconds: 600, user });
    });
    vi.stubGlobal('fetch', fetchMock);

    let ready = false;
    render(
      createElement(AuthProvider, null, createElement(Probe, { onReady: (value) => (ready = value) })),
    );

    await waitFor(() => expect(ready).toBe(true));
    expect(getAccessToken()).toBe('boot.jwt');
    expect(fetchMock.mock.calls.some((call) => String(call[0]).endsWith('/api/auth/refresh'))).toBe(
      true,
    );
  });
});
