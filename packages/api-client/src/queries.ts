import { mutationOptions, queryOptions } from '@tanstack/react-query';
import {
  createStaffQuestion,
  createSubmission,
  deleteStaffQuestion,
  fetchAdminDevices,
  fetchAdminSubmissions,
  fetchLeaderboard,
  fetchQuestions,
  fetchStaffQuestions,
  fetchSubmission,
  resendDeviceCommand,
  updateStaffQuestion,
} from './client';
import type { CreateSubmissionRequest, UpsertQuestion } from './types';

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

export const adminKeys = {
  questions: ['admin', 'questions'] as const,
  submissions: (query: string) => ['admin', 'submissions', query] as const,
  devices: (query: string) => ['admin', 'devices', query] as const,
  leaderboard: (page: number) => ['admin', 'leaderboard', page] as const,
};

export function staffQuestionsQueryOptions() {
  return queryOptions({
    queryKey: adminKeys.questions,
    queryFn: fetchStaffQuestions,
  });
}

export function createQuestionMutationOptions() {
  return mutationOptions({
    mutationKey: ['admin', 'questions', 'create'] as const,
    mutationFn: (body: UpsertQuestion) => createStaffQuestion(body),
  });
}

export function updateQuestionMutationOptions() {
  return mutationOptions({
    mutationKey: ['admin', 'questions', 'update'] as const,
    mutationFn: (input: { id: string; body: UpsertQuestion }) =>
      updateStaffQuestion(input.id, input.body),
  });
}

export function deleteQuestionMutationOptions() {
  return mutationOptions({
    mutationKey: ['admin', 'questions', 'delete'] as const,
    mutationFn: (id: string) => deleteStaffQuestion(id),
  });
}

export function adminSubmissionsQueryOptions(params: {
  page: number;
  sort: string;
  studentId?: string;
}) {
  const query = `${params.page}:${params.sort}:${params.studentId ?? ''}`;
  return queryOptions({
    queryKey: adminKeys.submissions(query),
    queryFn: () =>
      fetchAdminSubmissions({
        page: params.page,
        size: 20,
        sort: params.sort,
        studentId: params.studentId,
      }),
  });
}

export function adminDevicesQueryOptions(params: { page: number; sort: string }) {
  return queryOptions({
    queryKey: adminKeys.devices(`${params.page}:${params.sort}`),
    queryFn: () => fetchAdminDevices({ page: params.page, size: 20, sort: params.sort }),
  });
}

export function leaderboardQueryOptions(page: number) {
  return queryOptions({
    queryKey: adminKeys.leaderboard(page),
    queryFn: () => fetchLeaderboard({ page, size: 20 }),
  });
}

export function resendCommandMutationOptions() {
  return mutationOptions({
    mutationKey: ['admin', 'devices', 'resend'] as const,
    mutationFn: (deviceId: string) => resendDeviceCommand(deviceId),
  });
}
