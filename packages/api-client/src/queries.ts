import { mutationOptions, queryOptions } from '@tanstack/react-query';
import { createSubmission, fetchQuestions, fetchSubmission } from './client';
import type { CreateSubmissionRequest } from './types';

export const ACCESSORY_POLL_INTERVAL_MS = 2000;
export const ACCESSORY_POLL_TIMEOUT_MS = 60_000;

export const questionKeys = {
  all: ['questions'] as const,
};

export const submissionKeys = {
  detail: (id: string) => ['submissions', id] as const,
};

export function questionsQueryOptions() {
  return queryOptions({
    queryKey: questionKeys.all,
    queryFn: fetchQuestions,
  });
}

export function submitAnswersMutationOptions() {
  return mutationOptions({
    mutationKey: ['submissions', 'create'] as const,
    mutationFn: (request: CreateSubmissionRequest) => createSubmission(request),
  });
}

export function submissionQueryOptions(
  id: string,
  options?: { secret?: string | null; enabled?: boolean },
) {
  const secret = options?.secret ?? null;
  return queryOptions({
    queryKey: [...submissionKeys.detail(id), secret ? 'capability' : 'owner'] as const,
    queryFn: () => fetchSubmission(id, secret),
    enabled: options?.enabled ?? (Boolean(id) && Boolean(secret)),
  });
}
