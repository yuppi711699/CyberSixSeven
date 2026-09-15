import { describe, expect, it } from 'vitest';
import { toSubmissionAnswers, validateAnswers } from './validation';

describe('validateAnswers', () => {
  const ids = ['q1', 'q2'];

  it('requires every answer', () => {
    expect(validateAnswers(ids, { q1: '1', q2: '' })).toEqual({
      q2: 'Answer is required',
    });
  });

  it('rejects non-numeric input', () => {
    expect(validateAnswers(ids, { q1: '12', q2: 'abc' })).toEqual({
      q2: 'Enter a whole number',
    });
  });

  it('accepts whole numbers including negatives', () => {
    expect(validateAnswers(ids, { q1: '12', q2: '-3' })).toEqual({});
  });
});

describe('toSubmissionAnswers', () => {
  it('maps trimmed numeric strings', () => {
    expect(toSubmissionAnswers(['q1'], { q1: ' 42 ' })).toEqual([
      { questionId: 'q1', answer: 42 },
    ]);
  });
});
