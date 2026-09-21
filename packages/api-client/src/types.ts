export interface Question {
  id: string;
  prompt: string;
  options: number[];
  displayOrder: number;
}

export interface AnswerSubmission {
  questionId: string;
  answer: number;
}

export interface CreateSubmissionRequest {
  answers: AnswerSubmission[];
}

export interface ScoredAnswer {
  questionId: string;
  prompt: string;
  options: number[];
  submittedAnswer: number;
  correctAnswer: number;
  correct: boolean;
  awardedPoints: number;
  maxPoints: number;
  displayOrder: number;
}

export interface CreateSubmissionResponse {
  id: string;
  submissionSecret: string;
  score: number;
  maxScore: number;
  answers: ScoredAnswer[];
}

export type AccessoryStatus = 'PENDING' | 'READY' | 'FAILED';

export interface SubmissionDetail {
  id: string;
  score: number;
  maxScore: number;
  answers: ScoredAnswer[];
  accessoryStatus: AccessoryStatus;
}

export interface ApiErrorBody {
  code?: string;
  message?: string;
}

export interface ClaimResponse {
  id: string;
  studentId: string;
}

export class ApiClientError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.name = 'ApiClientError';
    this.status = status;
    this.code = code;
  }
}
