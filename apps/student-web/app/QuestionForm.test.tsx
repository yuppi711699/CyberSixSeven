import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { createElement, type ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { QuestionForm } from './QuestionForm';

const questions = [
  {
    id: '00000000-0000-0000-0000-000000000001',
    prompt: 'What is 7 + 5?',
    options: [],
    displayOrder: 1,
  },
  {
    id: '00000000-0000-0000-0000-000000000002',
    prompt: 'What is 9 × 3?',
    options: [],
    displayOrder: 2,
  },
  {
    id: '00000000-0000-0000-0000-000000000003',
    prompt: 'What is 20 - 8?',
    options: [],
    displayOrder: 3,
  },
];

function renderForm() {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return render(
    createElement(QueryClientProvider, { client }, createElement(QuestionForm) as ReactNode),
  );
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

beforeEach(() => {
  process.env.NEXT_PUBLIC_API_BASE_URL = 'http://localhost:8080';
});

describe('QuestionForm', () => {
  it('blocks submit when answers are missing', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo) => {
        const url = String(input);
        if (url.endsWith('/api/questions')) {
          return new Response(JSON.stringify(questions), { status: 200 });
        }
        throw new Error(`unexpected ${url}`);
      }),
    );

    renderForm();
    await screen.findByText('What is 7 + 5?');
    fireEvent.click(screen.getByRole('button', { name: 'Submit answers' }));
    expect(await screen.findAllByRole('alert')).toHaveLength(3);
  });

  it('disables the submit button while the mutation is pending', async () => {
    let resolveSubmit: ((value: Response) => void) | undefined;

    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo, init?: RequestInit) => {
        const url = String(input);
        if (url.endsWith('/api/questions')) {
          return new Response(JSON.stringify(questions), { status: 200 });
        }
        if (url.endsWith('/api/submissions') && init?.method === 'POST') {
          return new Promise<Response>((resolve) => {
            resolveSubmit = resolve;
          });
        }
        throw new Error(`unexpected ${url}`);
      }),
    );

    renderForm();
    await screen.findByText('What is 7 + 5?');

    const inputs = screen.getAllByLabelText('Your answer');
    fireEvent.change(inputs[0]!, { target: { value: '12' } });
    fireEvent.change(inputs[1]!, { target: { value: '27' } });
    fireEvent.change(inputs[2]!, { target: { value: '12' } });
    fireEvent.click(screen.getByRole('button', { name: 'Submit answers' }));

    await waitFor(() => {
      const button = screen.getByRole('button', { name: 'Submitting…' }) as HTMLButtonElement;
      expect(button.disabled).toBe(true);
    });

    resolveSubmit?.(
      new Response(
        JSON.stringify({
          id: 'sub-1',
          submissionSecret: 'secret',
          score: 3,
          maxScore: 3,
          answers: questions.map((question, index) => ({
            questionId: question.id,
            prompt: question.prompt,
            options: [],
            submittedAnswer: [12, 27, 12][index],
            correctAnswer: [12, 27, 12][index],
            correct: true,
            awardedPoints: 1,
            maxPoints: 1,
            displayOrder: question.displayOrder,
          })),
        }),
        { status: 201 },
      ),
    );

    expect(await screen.findByText('Score 3 / 3')).toBeTruthy();
  });

  it('shows a bounded API error when the backend is unavailable', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo, init?: RequestInit) => {
        const url = String(input);
        if (url.endsWith('/api/questions')) {
          return new Response(JSON.stringify(questions), { status: 200 });
        }
        if (url.endsWith('/api/submissions') && init?.method === 'POST') {
          throw new TypeError('Failed to fetch');
        }
        throw new Error(`unexpected ${url}`);
      }),
    );

    renderForm();
    await screen.findByText('What is 7 + 5?');
    const inputs = screen.getAllByLabelText('Your answer');
    fireEvent.change(inputs[0]!, { target: { value: '12' } });
    fireEvent.change(inputs[1]!, { target: { value: '27' } });
    fireEvent.change(inputs[2]!, { target: { value: '12' } });
    fireEvent.click(screen.getByRole('button', { name: 'Submit answers' }));

    const alert = await screen.findByRole('alert');
    expect(alert.textContent ?? '').toMatch(/unavailable/i);
  });

  it('renders server-scored results without client-side scoring', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo, init?: RequestInit) => {
        const url = String(input);
        if (url.endsWith('/api/questions')) {
          return new Response(JSON.stringify(questions), { status: 200 });
        }
        if (url.endsWith('/api/submissions') && init?.method === 'POST') {
          return new Response(
            JSON.stringify({
              id: 'sub-2',
              submissionSecret: 'memory-only',
              score: 2,
              maxScore: 3,
              answers: [
                {
                  questionId: questions[0]!.id,
                  prompt: questions[0]!.prompt,
                  options: [],
                  submittedAnswer: 12,
                  correctAnswer: 12,
                  correct: true,
                  awardedPoints: 1,
                  maxPoints: 1,
                  displayOrder: 1,
                },
                {
                  questionId: questions[1]!.id,
                  prompt: questions[1]!.prompt,
                  options: [],
                  submittedAnswer: 0,
                  correctAnswer: 27,
                  correct: false,
                  awardedPoints: 0,
                  maxPoints: 1,
                  displayOrder: 2,
                },
                {
                  questionId: questions[2]!.id,
                  prompt: questions[2]!.prompt,
                  options: [],
                  submittedAnswer: 12,
                  correctAnswer: 12,
                  correct: true,
                  awardedPoints: 1,
                  maxPoints: 1,
                  displayOrder: 3,
                },
              ],
            }),
            { status: 201 },
          );
        }
        throw new Error(`unexpected ${url}`);
      }),
    );

    renderForm();
    await screen.findByText('What is 7 + 5?');
    const inputs = screen.getAllByLabelText('Your answer');
    fireEvent.change(inputs[0]!, { target: { value: '12' } });
    fireEvent.change(inputs[1]!, { target: { value: '0' } });
    fireEvent.change(inputs[2]!, { target: { value: '12' } });
    fireEvent.click(screen.getByRole('button', { name: 'Submit answers' }));

    expect(await screen.findByText('Score 2 / 3')).toBeTruthy();
    expect(screen.getByText('Incorrect · 0/1')).toBeTruthy();
    expect(screen.getByTestId('capability-held')).toBeTruthy();
    expect(screen.queryByText('memory-only')).toBeNull();
  });
});
