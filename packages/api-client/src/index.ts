export { configureApiClient, getApiBaseUrl, ApiConfigError } from './config';
export { createSubmission, fetchQuestions } from './client';
export { normalizeApiError, normalizeNetworkError } from './errors';
export { questionKeys, questionsQueryOptions, submitAnswersMutationOptions } from './queries';
export {
  ApiClientError,
  type AnswerSubmission,
  type ApiErrorBody,
  type CreateSubmissionRequest,
  type CreateSubmissionResponse,
  type Question,
  type ScoredAnswer,
} from './types';
