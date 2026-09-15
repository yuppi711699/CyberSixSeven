import { mutationOptions, queryOptions } from '@tanstack/react-query';
import { createSubmission, fetchQuestions } from './client';
import type { CreateSubmissionRequest } from './types';

export const questionKeys = {
  all: ['questions'] as const,
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
