import { ApiClientError, getApiBaseUrl, normalizeApiError, normalizeNetworkError } from '@cybersixseven/api-client';
import {
  CSRF_HEADER,
  REFRESH_FAMILY_REVOKED,
  REFRESH_LOCK_NAME,
  TerminalAuthError,
  type AuthResponse,
  type AuthUser,
} from './types';

export type { AuthUser, AuthResponse };

export type LockFn = <T>(name: string, callback: () => Promise<T>) => Promise<T>;

let accessToken: string | null = null;
let user: AuthUser | null = null;
let csrfToken: string | null = null;
let refreshInFlight: Promise<string | null> | null = null;
let lockFn: LockFn | null = defaultLock;
const listeners = new Set<() => void>();
const exchangeInFlight = new Map<string, Promise<AuthUser>>();

async function defaultLock<T>(name: string, callback: () => Promise<T>): Promise<T> {
  const locks = globalThis.navigator?.locks;
  if (locks?.request) {
    return await locks.request(name, () => callback());
  }
  return callback();
}

function notify(): void {
  for (const listener of listeners) {
    listener();
  }
}

function applySession(response: AuthResponse): void {
  accessToken = response.accessToken;
  user = response.user;
  notify();
}

export function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export function getAccessToken(): string | null {
  return accessToken;
}

export function getUser(): AuthUser | null {
  return user;
}

export function getCsrfToken(): string | null {
  return csrfToken;
}

export function clearSession(): void {
  accessToken = null;
  user = null;
  notify();
}

export function setLockFn(next: LockFn | null): void {
  lockFn = next;
}

export function resetAuthClientForTests(): void {
  accessToken = null;
  user = null;
  csrfToken = null;
  refreshInFlight = null;
  exchangeInFlight.clear();
  lockFn = defaultLock;
  listeners.clear();
}

export function googleAuthorizationUrl(): string {
  return `${getApiBaseUrl()}/oauth2/authorization/google`;
}

export function isProductEnabled(): boolean {
  return process.env.NEXT_PUBLIC_PRODUCT_ENABLED !== 'false';
}

async function parseAuthResponse(response: Response): Promise<AuthResponse> {
  if (!response.ok) {
    throw await normalizeApiError(response);
  }
  return (await response.json()) as AuthResponse;
}

export async function bootstrapCsrf(): Promise<string> {
  let response: Response;
  try {
    response = await fetch(`${getApiBaseUrl()}/api/csrf`, {
      credentials: 'include',
      headers: { Accept: 'application/json' },
    });
  } catch (error) {
    throw normalizeNetworkError(error);
  }
  if (!response.ok) {
    throw await normalizeApiError(response);
  }
  const body = (await response.json()) as { token?: string };
  if (!body.token) {
    throw new ApiClientError(response.status, 'CSRF_MISSING', 'csrf token missing');
  }
  csrfToken = body.token;
  return csrfToken;
}

async function authPost(path: string, body: unknown): Promise<Response> {
  if (!csrfToken) {
    await bootstrapCsrf();
  }
  try {
    return await fetch(`${getApiBaseUrl()}${path}`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        [CSRF_HEADER]: csrfToken ?? '',
      },
      body: JSON.stringify(body),
    });
  } catch (error) {
    throw normalizeNetworkError(error);
  }
}

export async function register(input: {
  email: string;
  password: string;
  nickname: string;
}): Promise<AuthUser> {
  const response = await authPost('/api/auth/register', input);
  applySession(await parseAuthResponse(response));
  return user as AuthUser;
}

export async function login(input: { email: string; password: string }): Promise<AuthUser> {
  const response = await authPost('/api/auth/login', input);
  applySession(await parseAuthResponse(response));
  return user as AuthUser;
}

export async function logout(): Promise<void> {
  const response = await authPost('/api/auth/logout', {});
  if (!response.ok && response.status !== 204) {
    throw await normalizeApiError(response);
  }
  clearSession();
}

async function refreshUnlocked(): Promise<string | null> {
  const response = await authPost('/api/auth/refresh', {});
  if (response.status === 401) {
    const error = await normalizeApiError(response);
    clearSession();
    if (error.code === REFRESH_FAMILY_REVOKED) {
      throw new TerminalAuthError(REFRESH_FAMILY_REVOKED, error.message);
    }
    return null;
  }
  applySession(await parseAuthResponse(response));
  return accessToken;
}

export async function refreshSession(): Promise<string | null> {
  if (refreshInFlight) {
    return refreshInFlight;
  }
  refreshInFlight = (async () => {
    const run = () => refreshUnlocked();
    try {
      if (lockFn) {
        return await lockFn(REFRESH_LOCK_NAME, run);
      }
      return await run();
    } finally {
      refreshInFlight = null;
    }
  })();
  return refreshInFlight;
}

export async function exchangeOauthCode(code: string): Promise<AuthUser> {
  if (!code) {
    throw new TerminalAuthError('OAUTH_EXCHANGE_INVALID', 'exchange code required');
  }
  const existing = exchangeInFlight.get(code);
  if (existing) {
    return existing;
  }
  const pending = (async () => {
    const response = await authPost('/api/auth/oauth/exchange', { code });
    applySession(await parseAuthResponse(response));
    return user as AuthUser;
  })();
  exchangeInFlight.set(code, pending);
  try {
    return await pending;
  } catch (error) {
    exchangeInFlight.delete(code);
    throw error;
  }
}

export function takeOauthCodeFromUrl(url: URL): string | null {
  const code = url.searchParams.get('code');
  if (!code) {
    return null;
  }
  url.searchParams.delete('code');
  return code;
}

export function assertNoTokenInStorage(): void {
  if (typeof window === 'undefined') {
    return;
  }
  const blob = `${window.localStorage.getItem('accessToken') ?? ''}${window.sessionStorage.getItem('accessToken') ?? ''}${window.location.href}`;
  if (/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/.test(blob)) {
    throw new Error('access token leaked into storage or URL');
  }
}
