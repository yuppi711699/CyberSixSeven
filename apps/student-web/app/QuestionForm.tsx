'use client';

import {
  ApiClientError,
  claimSubmission,
  questionsQueryOptions,
  submitAnswersMutationOptions,
  type CreateSubmissionResponse,
  type Question,
} from '@cybersixseven/api-client';
import { useAuth } from '@cybersixseven/auth-client';
import { Button, Card, Input } from '@cybersixseven/ui';
import { useMutation, useQuery } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
import { toSubmissionAnswers, validateAnswers } from './validation';
import { AccessoryStatusPanel } from './AccessoryStatusPanel';
import styles from './page.module.css';

export function QuestionForm() {
  const { user } = useAuth();
  const questionsQuery = useQuery(questionsQueryOptions());
  const submitMutation = useMutation(submitAnswersMutationOptions());

  const [values, setValues] = useState<Record<string, string>>({});
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [result, setResult] = useState<CreateSubmissionResponse | null>(null);
  const [submissionSecret, setSubmissionSecret] = useState<string | null>(null);

  if (questionsQuery.isLoading) {
    return <p className={styles.status}>Loading questions…</p>;
  }

  if (questionsQuery.isError) {
    const message =
      questionsQuery.error instanceof ApiClientError
        ? questionsQuery.error.message
        : 'Could not load questions from the API.';
    return (
      <p className={styles.error} role="alert">
        {message}
      </p>
    );
  }

  const questions = questionsQuery.data ?? [];

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const ids = questions.map((question) => question.id);
    const errors = validateAnswers(ids, values);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }

    submitMutation.mutate(
      { answers: toSubmissionAnswers(ids, values) },
      {
        onSuccess: (response) => {
          setResult(response);
          setSubmissionSecret(response.submissionSecret);
          if (user && response.submissionSecret) {
            void claimSubmission(response.id, response.submissionSecret)
              .then(() => setSubmissionSecret(null))
              .catch(() => {
                /* keep capability for unclaimed polling */
              });
          }
        },
      },
    );
  }

  return (
    <form className={styles.form} onSubmit={onSubmit} noValidate>
      {questions.map((question, index) => (
        <QuestionField
          key={question.id}
          question={question}
          index={index}
          total={questions.length}
          value={values[question.id] ?? ''}
          error={fieldErrors[question.id]}
          disabled={submitMutation.isPending || result !== null}
          onChange={(next) => {
            setValues((current) => ({ ...current, [question.id]: next }));
            setFieldErrors((current) => {
              const rest = { ...current };
              delete rest[question.id];
              return rest;
            });
          }}
        />
      ))}

      {submitMutation.isError ? (
        <p className={styles.error} role="alert">
          {submitMutation.error instanceof ApiClientError
            ? submitMutation.error.message
            : 'Submission failed. The API may be unavailable.'}
        </p>
      ) : null}

      {result ? (
        <section className={styles.results} aria-labelledby="results-heading">
          <h2 id="results-heading" className={styles.prompt}>
            Results
          </h2>
          <p className={styles.status}>
            Score {result.score} / {result.maxScore}
          </p>
          <p className={styles.status} data-testid="device-delivery-note">
            Companion device signal queued. Results here are from the server response, not
            hardware acknowledgement.
          </p>
          <ul className={styles.options}>
            {result.answers.map((answer) => (
              <li key={answer.questionId} className={styles.option}>
                <span className={styles.eyebrow}>
                  {answer.correct ? 'Correct' : 'Incorrect'} · {answer.awardedPoints}/
                  {answer.maxPoints}
                </span>
                <p className={styles.resultPrompt}>{answer.prompt}</p>
                <p className={styles.status}>
                  Your answer: {answer.submittedAnswer}
                  {!answer.correct ? ` · Correct: ${answer.correctAnswer}` : null}
                </p>
              </li>
            ))}
          </ul>
          {/* Capability stays in memory for later same-session polling; never render/log it. */}
          {submissionSecret ? <span hidden data-testid="capability-held" /> : null}
          {result && (submissionSecret || user) ? (
            <AccessoryStatusPanel
              submissionId={result.id}
              secret={submissionSecret}
              canDownload={Boolean(user)}
            />
          ) : null}
        </section>
      ) : (
        <Button type="submit" disabled={submitMutation.isPending || questions.length === 0}>
          {submitMutation.isPending ? 'Submitting…' : 'Submit answers'}
        </Button>
      )}
    </form>
  );
}

function QuestionField({
  question,
  index,
  total,
  value,
  error,
  disabled,
  onChange,
}: {
  question: Question;
  index: number;
  total: number;
  value: string;
  error?: string;
  disabled: boolean;
  onChange: (value: string) => void;
}) {
  const inputId = `answer-${question.id}`;
  const errorId = `${inputId}-error`;

  return (
    <Card className={styles.card} aria-labelledby={`prompt-${question.id}`}>
      <p className={styles.eyebrow}>
        Question {index + 1} of {total}
      </p>
      <h2 id={`prompt-${question.id}`} className={styles.prompt}>
        {question.prompt}
      </h2>
      <label className={styles.label} htmlFor={inputId}>
        Your answer
      </label>
      <Input
        id={inputId}
        inputMode="numeric"
        pattern="-?[0-9]*"
        value={value}
        disabled={disabled}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId : undefined}
        onChange={(event) => onChange(event.target.value)}
      />
      {error ? (
        <p id={errorId} className={styles.fieldError} role="alert">
          {error}
        </p>
      ) : null}
    </Card>
  );
}
