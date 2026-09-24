export type UserRole = 'STUDENT' | 'TEACHER' | 'ADMIN';

export interface AuthUser {
  id: string;
  email: string;
  nickname: string;
  role: UserRole;
}

export interface AuthResponse {
  accessToken: string;
  expiresInSeconds: number;
  user: AuthUser;
}

export class TerminalAuthError extends Error {
  readonly code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = 'TerminalAuthError';
    this.code = code;
  }
}

export const REFRESH_FAMILY_REVOKED = 'REFRESH_FAMILY_REVOKED';
export const REFRESH_LOCK_NAME = 'c67-refresh';
export const CSRF_HEADER = 'X-XSRF-TOKEN';
