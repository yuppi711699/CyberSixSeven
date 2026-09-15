let configuredBaseUrl: string | undefined;

export function configureApiClient(options: { baseUrl: string }): void {
  configuredBaseUrl = options.baseUrl.replace(/\/$/, '');
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
