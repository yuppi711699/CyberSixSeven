let configuredBaseUrl: string | undefined;
let getAccessToken: (() => string | null | undefined) | undefined;
let onUnauthorized: (() => Promise<unknown> | unknown) | undefined;

export interface ClientOptions {
  baseUrl?: string;
  getAccessToken?: () => string | null | undefined;
  onUnauthorized?: () => Promise<unknown> | unknown;
}

export function configureApiClient(options: { baseUrl: string }): void {
  configureClient(options);
}

export function configureClient(options: ClientOptions): void {
  if (options.baseUrl) {
    configuredBaseUrl = options.baseUrl.replace(/\/$/, '');
  }
  if (options.getAccessToken) {
    getAccessToken = options.getAccessToken;
  }
  if (options.onUnauthorized) {
    onUnauthorized = options.onUnauthorized;
  }
}

export function getConfiguredAccessToken(): string | null {
  return getAccessToken?.() ?? null;
}

export async function notifyUnauthorized(): Promise<void> {
  await onUnauthorized?.();
}

export function resetApiClientForTests(): void {
  configuredBaseUrl = undefined;
  getAccessToken = undefined;
  onUnauthorized = undefined;
}

export function getApiBaseUrl(): string {
  if (configuredBaseUrl) {
    return configuredBaseUrl;
  }

  const fromEnv =
    typeof process !== 'undefined' ? process.env.NEXT_PUBLIC_API_BASE_URL : undefined;

  if (!fromEnv || fromEnv.trim().length === 0) {
    throw new ApiConfigError(
      'API base URL is not configured. Set NEXT_PUBLIC_API_BASE_URL or call configureApiClient.',
    );
  }

  return fromEnv.replace(/\/$/, '');
}

export class ApiConfigError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'ApiConfigError';
  }
}
