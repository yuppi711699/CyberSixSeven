export { configureApiClient, getApiBaseUrl, ApiConfigError } from './config';
export {
  createSubmission,
  fetchQuestions,
  fetchSubmission,
  SUBMISSION_SECRET_HEADER,
} from './client';
export { normalizeApiError, normalizeNetworkError } from './errors';
export {
  ACCESSORY_POLL_INTERVAL_MS,
  ACCESSORY_POLL_TIMEOUT_MS,
  questionKeys,
  questionsQueryOptions,
  submitAnswersMutationOptions,
  submissionKeys,
  submissionQueryOptions,
} from './queries';
export {
  ApiClientError,
  type AccessoryStatus,
  type AnswerSubmission,
  type ApiErrorBody,
  type CreateSubmissionRequest,
  type CreateSubmissionResponse,
  type Question,
  type ScoredAnswer,
  type SubmissionDetail,
} from './types';
