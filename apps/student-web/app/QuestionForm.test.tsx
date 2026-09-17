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

function pendingDetail(id: string) {
  return {
    id,
    score: 3,
    maxScore: 3,
    answers: [],
    accessoryStatus: 'PENDING' as const,
  };
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status });
}

function scoredAnswers(submitted: number[]) {
  const correct = [12, 27, 12];
  return questions.map((question, index) => ({
    questionId: question.id,
    prompt: question.prompt,
    options: [],
    submittedAnswer: submitted[index],
    correctAnswer: correct[index],
    correct: submitted[index] === correct[index],
    awardedPoints: submitted[index] === correct[index] ? 1 : 0,
    maxPoints: 1,
    displayOrder: question.displayOrder,
  }));
}

function stubApi(handlers: {
  submit?: (init?: RequestInit) => Promise<Response> | Response;
}) {
  return vi.fn(async (input: RequestInfo, init?: RequestInit) => {
    const url = String(input);
    if (url.endsWith('/api/questions')) {
      return jsonResponse(questions);
    }
    if (url.endsWith('/api/submissions') && init?.method === 'POST') {
      if (!handlers.submit) {
        throw new Error(`unexpected POST ${url}`);
      }
      return handlers.submit(init);
    }
    if (url.includes('/api/submissions/')) {
      expect(url).not.toMatch(/secret=/i);
      const id = url.split('/').pop() ?? 'unknown';
      return jsonResponse(pendingDetail(id));
    }
    throw new Error(`unexpected ${url}`);
  });
}

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

async function fillAndSubmit(values: string[]) {
  await screen.findByText('What is 7 + 5?');
  const inputs = screen.getAllByLabelText('Your answer');
  values.forEach((value, index) => {
    fireEvent.change(inputs[index]!, { target: { value } });
  });
  fireEvent.click(screen.getByRole('button', { name: 'Submit answers' }));
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
    vi.stubGlobal('fetch', stubApi({}));

    renderForm();
    await screen.findByText('What is 7 + 5?');
    fireEvent.click(screen.getByRole('button', { name: 'Submit answers' }));
    expect(await screen.findAllByRole('alert')).toHaveLength(3);
  });

  it('disables the submit button while the mutation is pending', async () => {
    let resolveSubmit: ((value: Response) => void) | undefined;
    vi.stubGlobal(
      'fetch',
      stubApi({
        submit: () =>
          new Promise<Response>((resolve) => {
            resolveSubmit = resolve;
          }),
      }),
    );

    renderForm();
    await fillAndSubmit(['12', '27', '12']);

    await waitFor(() => {
      const button = screen.getByRole('button', { name: 'Submitting…' }) as HTMLButtonElement;
      expect(button.disabled).toBe(true);
    });

    resolveSubmit?.(
      jsonResponse(
        {
          id: 'sub-1',
          submissionSecret: 'secret',
          score: 3,
          maxScore: 3,
          answers: scoredAnswers([12, 27, 12]),
        },
        201,
      ),
    );

    expect(await screen.findByText('Score 3 / 3')).toBeTruthy();
    expect(await screen.findByText('Generating your accessory…')).toBeTruthy();
  });

  it('shows a bounded API error when the backend is unavailable', async () => {
    vi.stubGlobal(
      'fetch',
      stubApi({
        submit: () => {
          throw new TypeError('Failed to fetch');
        },
      }),
    );

    renderForm();
    await fillAndSubmit(['12', '27', '12']);

    const alert = await screen.findByRole('alert');
    expect(alert.textContent ?? '').toMatch(/unavailable/i);
  });

  it('renders server-scored results without client-side scoring', async () => {
    vi.stubGlobal(
      'fetch',
      stubApi({
        submit: () =>
          jsonResponse(
            {
              id: 'sub-2',
              submissionSecret: 'memory-only',
              score: 2,
              maxScore: 3,
              answers: scoredAnswers([12, 0, 12]),
            },
            201,
          ),
      }),
    );

    renderForm();
    await fillAndSubmit(['12', '0', '12']);

    expect(await screen.findByText('Score 2 / 3')).toBeTruthy();
    expect(screen.getByText('Incorrect · 0/1')).toBeTruthy();
    expect(screen.getByTestId('capability-held')).toBeTruthy();
    expect(screen.queryByText('memory-only')).toBeNull();
    expect(screen.getByTestId('device-delivery-note')).toBeTruthy();
    expect(screen.queryByText(/acknowledged/i)).toBeNull();
    expect(screen.queryByText(/delivered to device/i)).toBeNull();
    expect(await screen.findByText('Generating your accessory…')).toBeTruthy();
    expect(screen.queryByRole('link', { name: /download/i })).toBeNull();
    const fetchMock = vi.mocked(fetch);
    expect(fetchMock.mock.calls.some((call) => String(call[0]).includes('/device'))).toBe(false);
    expect(
      fetchMock.mock.calls.filter((call) => {
        const init = call[1];
        return String(call[0]).endsWith('/api/submissions') && init?.method === 'POST';
      }),
    ).toHaveLength(1);
  });

  it('renders results from the submission response without waiting for MQTT or ACK', async () => {
    const fetchMock = stubApi({
      submit: () =>
        jsonResponse(
          {
            id: 'sub-async',
            submissionSecret: 'memory-only',
            score: 3,
            maxScore: 3,
            answers: scoredAnswers([12, 27, 12]),
          },
          201,
        ),
    });
    vi.stubGlobal('fetch', fetchMock);

    renderForm();
    await fillAndSubmit(['12', '27', '12']);

    expect(await screen.findByText('Score 3 / 3')).toBeTruthy();
    expect(screen.getByTestId('device-delivery-note')).toBeTruthy();
    await waitFor(() => {
      expect(
        fetchMock.mock.calls.some(
          (call) =>
            String(call[0]).endsWith('/api/submissions') && call[1]?.method === 'POST',
        ),
      ).toBe(true);
    });
  });
});
