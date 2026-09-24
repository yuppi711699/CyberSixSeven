import { configureApiClient } from '@cybersixseven/api-client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  assertNoTokenInStorage,
  bootstrapCsrf,
  exchangeOauthCode,
  getAccessToken,
  googleAuthorizationUrl,
  isProductEnabled,
  login,
  logout,
  refreshSession,
  register,
  resetAuthClientForTests,
  setLockFn,
  takeOauthCodeFromUrl,
} from './session';
import { CSRF_HEADER, REFRESH_FAMILY_REVOKED, REFRESH_LOCK_NAME, TerminalAuthError } from './types';

const user = {
  id: '11111111-1111-1111-1111-111111111111',
  email: 'pat@example.test',
  nickname: 'Pat',
  role: 'STUDENT' as const,
};

function json(body: unknown, status = 200, headers?: HeadersInit) {
  return new Response(JSON.stringify(body), { status, headers });
}

beforeEach(() => {
  resetAuthClientForTests();
  configureApiClient({ baseUrl: 'http://localhost:8080' });
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  resetAuthClientForTests();
});

describe('auth session', () => {
  it('bootstraps CSRF from JSON and sends it on cookie mutations', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/api/csrf')) {
        return json({ token: 'csrf-1' });
      }
      expect(new Headers(init?.headers).get(CSRF_HEADER)).toBe('csrf-1');
      expect(init?.credentials).toBe('include');
      return json({ accessToken: 'a.b.c', expiresInSeconds: 600, user }, 200);
    });
    vi.stubGlobal('fetch', fetchMock);

    await bootstrapCsrf();
    await login({ email: 'pat@example.test', password: 'password1' });
    expect(getAccessToken()).toBe('a.b.c');
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
  });

  it('single-flights refresh so concurrent callers hit the API once', async () => {
    let csrf = 0;
    let refreshes = 0;
    let release: (() => void) | undefined;
    const gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo) => {
        const url = String(input);
        if (url.endsWith('/api/csrf')) {
          csrf += 1;
          return json({ token: 'csrf-1' });
        }
        if (url.endsWith('/api/auth/refresh')) {
          refreshes += 1;
          await gate;
          return json({ accessToken: `token-${refreshes}`, expiresInSeconds: 600, user });
        }
        throw new Error(url);
      }),
    );

    const first = refreshSession();
    const second = refreshSession();
    release?.();
    const [a, b] = await Promise.all([first, second]);
    expect(a).toBe(b);
    expect(refreshes).toBe(1);
    expect(csrf).toBe(1);
  });

  it('uses the injected lock around refresh', async () => {
    const names: string[] = [];
    setLockFn(async (name, callback) => {
      names.push(name);
      return callback();
    });
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo) => {
        if (String(input).endsWith('/api/csrf')) {
          return json({ token: 'csrf-1' });
        }
        return json({ accessToken: 'locked', expiresInSeconds: 600, user });
      }),
    );

    await refreshSession();
    expect(names).toEqual([REFRESH_LOCK_NAME]);
  });

  it('treats refresh-family reuse as terminal and clears memory', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo) => {
        if (String(input).endsWith('/api/csrf')) {
          return json({ token: 'csrf-1' });
        }
        return json({ code: REFRESH_FAMILY_REVOKED, message: 'revoked' }, 401);
      }),
    );

    await expect(refreshSession()).rejects.toBeInstanceOf(TerminalAuthError);
    expect(getAccessToken()).toBeNull();
  });

  it('exchanges an OAuth code once and strips it from the URL', async () => {
    let exchanges = 0;
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo) => {
        if (String(input).endsWith('/api/csrf')) {
          return json({ token: 'csrf-1' });
        }
        if (String(input).endsWith('/api/auth/oauth/exchange')) {
          exchanges += 1;
          return json({ accessToken: 'oauth.jwt', expiresInSeconds: 600, user });
        }
        throw new Error(String(input));
      }),
    );

    const url = new URL('http://localhost:3000/auth/callback?code=one-time');
    const code = takeOauthCodeFromUrl(url);
    expect(code).toBe('one-time');
    expect(url.search).toBe('');
    await exchangeOauthCode(code as string);
    await exchangeOauthCode(code as string);
    expect(exchanges).toBe(1);
    expect(url.toString()).not.toMatch(/eyJ|accessToken/);
  });

  it('registers without a role and logs out over CSRF', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/api/csrf')) {
        return json({ token: 'csrf-1' });
      }
      if (url.endsWith('/api/auth/register')) {
        expect(new Headers(init?.headers).get(CSRF_HEADER)).toBe('csrf-1');
        expect(JSON.parse(String(init?.body))).toEqual({
          email: 'pat@example.test',
          password: 'password1',
          nickname: 'Pat',
        });
        return json({ accessToken: 'reg.jwt', expiresInSeconds: 600, user }, 201);
      }
      if (url.endsWith('/api/auth/logout')) {
        expect(init?.method).toBe('POST');
        expect(new Headers(init?.headers).get(CSRF_HEADER)).toBe('csrf-1');
        return new Response(null, { status: 204 });
      }
      throw new Error(url);
    });
    vi.stubGlobal('fetch', fetchMock);

    await register({ email: 'pat@example.test', password: 'password1', nickname: 'Pat' });
    expect(getAccessToken()).toBe('reg.jwt');
    await logout();
    expect(getAccessToken()).toBeNull();
  });

  it('returns null on a generic refresh 401 without throwing terminal', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo) => {
        if (String(input).endsWith('/api/csrf')) {
          return json({ token: 'csrf-1' });
        }
        return json({ code: 'UNAUTHORIZED', message: 'refresh token required' }, 401);
      }),
    );

    await expect(refreshSession()).resolves.toBeNull();
    expect(getAccessToken()).toBeNull();
  });

  it('builds the google authorization URL from the API origin', () => {
    expect(googleAuthorizationUrl()).toBe('http://localhost:8080/oauth2/authorization/google');
  });

  it('gates product UI on NEXT_PUBLIC_PRODUCT_ENABLED and rejects stored JWTs', () => {
    const previous = process.env.NEXT_PUBLIC_PRODUCT_ENABLED;
    process.env.NEXT_PUBLIC_PRODUCT_ENABLED = 'false';
    expect(isProductEnabled()).toBe(false);
    process.env.NEXT_PUBLIC_PRODUCT_ENABLED = 'true';
    expect(isProductEnabled()).toBe(true);
    process.env.NEXT_PUBLIC_PRODUCT_ENABLED = previous;

    expect(() => assertNoTokenInStorage()).not.toThrow();
    window.sessionStorage.setItem('accessToken', 'eyJhbGciOiJIUzI1NiJ9.e30');
    expect(() => assertNoTokenInStorage()).toThrow(/leaked/i);
    window.sessionStorage.clear();
  });
});
