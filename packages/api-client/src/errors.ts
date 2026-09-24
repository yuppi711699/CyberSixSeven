import { ApiClientError, type ApiErrorBody } from './types';

export async function normalizeApiError(response: Response): Promise<ApiClientError> {
  const fallback = `Request failed with status ${response.status}`;
  let body: ApiErrorBody | undefined;

  try {
    body = (await response.json()) as ApiErrorBody;
  } catch {
    return new ApiClientError(response.status, 'HTTP_ERROR', fallback);
  }

  const code = typeof body.code === 'string' && body.code.length > 0 ? body.code : 'HTTP_ERROR';
  const message =
    typeof body.message === 'string' && body.message.length > 0 ? body.message : fallback;

  return new ApiClientError(response.status, code, message);
}

export function normalizeNetworkError(error: unknown): ApiClientError {
  if (error instanceof ApiClientError) {
    return error;
  }

  return new ApiClientError(
    0,
    'NETWORK_ERROR',
    'The API is unavailable. Check that platform-api is running and try again.',
  );
}
