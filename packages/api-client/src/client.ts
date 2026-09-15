import { getApiBaseUrl } from './config';
import { normalizeApiError, normalizeNetworkError } from './errors';
import type { CreateSubmissionRequest, CreateSubmissionResponse, Question } from './types';

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;

  try {
    response = await fetch(`${getApiBaseUrl()}${path}`, {
      ...init,
      headers: {
        Accept: 'application/json',
        ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
        ...init?.headers,
      },
    });
  } catch (error) {
    throw normalizeNetworkError(error);
  }

  if (!response.ok) {
    throw await normalizeApiError(response);
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
