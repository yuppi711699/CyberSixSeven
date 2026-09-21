export {
  configureApiClient,
  configureClient,
  getApiBaseUrl,
  resetApiClientForTests,
  ApiConfigError,
} from './config';
export {
  claimSubmission,
  createSubmission,
  downloadAccessory,
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
  type ClaimResponse,
  type CreateSubmissionRequest,
  type CreateSubmissionResponse,
  type Question,
  type ScoredAnswer,
  type SubmissionDetail,
} from './types';
