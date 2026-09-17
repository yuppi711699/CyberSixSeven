import { afterEach, describe, expect, it, vi } from 'vitest';
import { createSubmission, fetchSubmission, SUBMISSION_SECRET_HEADER } from './client';
import { configureApiClient } from './config';

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('fetchSubmission', () => {
  it('sends the capability header and never a query-string secret or object key', async () => {
    configureApiClient({ baseUrl: 'http://localhost:8080' });
    const fetchMock = vi.fn(async (input: RequestInfo, init?: RequestInit) => {
      expect(String(input)).toBe(
        'http://localhost:8080/api/submissions/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
      );
      expect(String(input)).not.toMatch(/secret=/i);
      const headers = new Headers(init?.headers);
      expect(headers.get(SUBMISSION_SECRET_HEADER)).toBe('memory-only');
      return new Response(
        JSON.stringify({
          id: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
          score: 2,
          maxScore: 3,
          answers: [],
          accessoryStatus: 'PENDING',
        }),
        { status: 200 },
      );
    });
    vi.stubGlobal('fetch', fetchMock);

    const detail = await fetchSubmission(
      'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
      'memory-only',
    );

    expect(detail.accessoryStatus).toBe('PENDING');
    expect(JSON.stringify(detail)).not.toMatch(/reward\.stl|accessoryKey|accessoryUrl/i);
    expect(fetchMock).toHaveBeenCalledOnce();
  });
});

describe('createSubmission', () => {
  it('posts answers to /api/submissions', async () => {
    configureApiClient({ baseUrl: 'http://localhost:8080' });
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        return new Response(
          JSON.stringify({
            id: 'sub-1',
            submissionSecret: 'secret',
            score: 1,
            maxScore: 1,
            answers: [],
          }),
          { status: 201 },
        );
      }),
    );

    const response = await createSubmission({ answers: [] });
    expect(response.id).toBe('sub-1');
  });
});
