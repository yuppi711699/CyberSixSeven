export type FieldErrors = Record<string, string>;

export function validateAnswers(
  questionIds: string[],
  values: Record<string, string>,
): FieldErrors {
  const errors: FieldErrors = {};

  for (const questionId of questionIds) {
    const raw = values[questionId];
    if (raw === undefined || raw.trim().length === 0) {
      errors[questionId] = 'Answer is required';
      continue;
    }

    if (!/^-?\d+$/.test(raw.trim())) {
      errors[questionId] = 'Enter a whole number';
    }
  }

  return errors;
}

export function toSubmissionAnswers(
  questionIds: string[],
  values: Record<string, string>,
): Array<{ questionId: string; answer: number }> {
  return questionIds.map((questionId) => ({
    questionId,
    answer: Number.parseInt(values[questionId]!.trim(), 10),
  }));
}
