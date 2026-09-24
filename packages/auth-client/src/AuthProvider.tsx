'use client';

import { configureClient } from '@cybersixseven/api-client';
import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import {
  clearSession,
  getAccessToken,
  getUser,
  login as loginRequest,
  logout as logoutRequest,
  refreshSession,
  register as registerRequest,
  subscribe,
  type AuthUser,
} from './session';

interface AuthContextValue {
  user: AuthUser | null;
  accessToken: string | null;
  ready: boolean;
  login: (input: { email: string; password: string }) => Promise<AuthUser>;
  register: (input: { email: string; password: string; nickname: string }) => Promise<AuthUser>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(getUser());
  const [accessToken, setAccessToken] = useState<string | null>(getAccessToken());
  const [ready, setReady] = useState(false);

  useEffect(() => {
    return subscribe(() => {
      setUser(getUser());
      setAccessToken(getAccessToken());
    });
  }, []);

  useEffect(() => {
    configureClient({
      getAccessToken,
      onUnauthorized: () => refreshSession(),
    });
    // Callback owns the one-time exchange. A parallel refresh 401 would clearSession()
    // after the code is consumed and force another Google round-trip.
    if (typeof window !== 'undefined' && window.location.pathname.endsWith('/auth/callback')) {
      setReady(true);
      return;
    }
    void refreshSession()
      .catch(() => {
        clearSession();
      })
      .finally(() => setReady(true));
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      accessToken,
      ready,
      login: loginRequest,
      register: registerRequest,
      logout: logoutRequest,
    }),
    [user, accessToken, ready],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return value;
}
