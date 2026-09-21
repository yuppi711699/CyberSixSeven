import {
  getApiBaseUrl,
  getConfiguredAccessToken,
  notifyUnauthorized,
} from './config';
import { normalizeApiError, normalizeNetworkError } from './errors';
import type {
  ClaimResponse,
  CreateSubmissionRequest,
  CreateSubmissionResponse,
  Question,
  SubmissionDetail,
} from './types';

async function request<T>(
  path: string,
  init?: RequestInit,
  allowRetry = true,
): Promise<T> {
  const headers = new Headers(init?.headers);
  if (!headers.has('Accept')) {
    headers.set('Accept', 'application/json');
  }
  if (init?.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  const accessToken = getConfiguredAccessToken();
  if (accessToken && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${accessToken}`);
  }

  let response: Response;
  try {
    response = await fetch(`${getApiBaseUrl()}${path}`, {
      ...init,
      headers,
    });
  } catch (error) {
    throw normalizeNetworkError(error);
  }

  if (response.status === 401 && allowRetry && accessToken) {
    await notifyUnauthorized();
    return request<T>(path, init, false);
  }

  if (!response.ok) {
    throw await normalizeApiError(response);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

export function fetchQuestions(): Promise<Question[]> {
  return request<Question[]>('/api/questions');
}

export function createSubmission(
  body: CreateSubmissionRequest,
): Promise<CreateSubmissionResponse> {
  return request<CreateSubmissionResponse>('/api/submissions', {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export const SUBMISSION_SECRET_HEADER = 'X-Submission-Secret';

export function fetchSubmission(id: string, secret?: string | null): Promise<SubmissionDetail> {
  const headers: Record<string, string> = {};
  if (secret) {
    headers[SUBMISSION_SECRET_HEADER] = secret;
  }
  return request<SubmissionDetail>(`/api/submissions/${id}`, { headers });
}

export function claimSubmission(id: string, secret: string): Promise<ClaimResponse> {
  return request<ClaimResponse>(`/api/submissions/${id}/claim`, {
    method: 'POST',
    headers: {
      [SUBMISSION_SECRET_HEADER]: secret,
    },
    body: '{}',
  });
}

async function fetchAccessoryBlob(id: string, allowRetry: boolean): Promise<Blob> {
  const headers = new Headers();
  const accessToken = getConfiguredAccessToken();
  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`);
  }
  let response: Response;
  try {
    response = await fetch(`${getApiBaseUrl()}/api/submissions/${id}/accessory/download`, {
      headers,
      redirect: 'follow',
    });
  } catch (error) {
    throw normalizeNetworkError(error);
  }
  if (response.status === 401 && allowRetry && accessToken) {
    await notifyUnauthorized();
    return fetchAccessoryBlob(id, false);
  }
  if (!response.ok) {
    throw await normalizeApiError(response);
  }
  return response.blob();
}

export async function downloadAccessory(id: string): Promise<void> {
  const blob = await fetchAccessoryBlob(id, true);
  const objectUrl = URL.createObjectURL(blob);
  try {
    const anchor = document.createElement('a');
    anchor.href = objectUrl;
    anchor.download = 'reward.stl';
    document.body.append(anchor);
    anchor.click();
    anchor.remove();
  } finally {
    URL.revokeObjectURL(objectUrl);
  }
}
