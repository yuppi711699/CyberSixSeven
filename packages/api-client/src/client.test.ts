import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  claimSubmission,
  createSubmission,
  downloadAccessory,
  fetchSubmission,
  SUBMISSION_SECRET_HEADER,
} from './client';
import { configureClient, resetApiClientForTests } from './config';

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  resetApiClientForTests();
});

describe('fetchSubmission', () => {
  it('sends the capability header and never a query-string secret or object key', async () => {
    configureClient({ baseUrl: 'http://localhost:8080' });
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
    configureClient({ baseUrl: 'http://localhost:8080' });
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

describe('configureClient', () => {
  it('attaches the bearer token and retries once after onUnauthorized', async () => {
    const tokens = ['first', 'second'];
    let tokenIndex = 0;
    configureClient({
      baseUrl: 'http://localhost:8080',
      getAccessToken: () => tokens[tokenIndex] ?? null,
      onUnauthorized: () => {
        tokenIndex = 1;
      },
    });
    const fetchMock = vi.fn(async (_input: RequestInfo, init?: RequestInit) => {
      const header = new Headers(init?.headers).get('Authorization');
      if (header === 'Bearer first') {
        return new Response(JSON.stringify({ code: 'UNAUTHORIZED', message: 'expired' }), {
          status: 401,
        });
      }
      return new Response(
        JSON.stringify({ id: 'claimed', studentId: '11111111-1111-1111-1111-111111111111' }),
        { status: 200 },
      );
    });
    vi.stubGlobal('fetch', fetchMock);

    const claimed = await claimSubmission('sub-1', 'memory-only');
    expect(claimed.id).toBe('claimed');
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(new Headers(fetchMock.mock.calls[1]?.[1]?.headers).get('Authorization')).toBe(
      'Bearer second',
    );
  });

  it('polls owner detail without a capability header', async () => {
    configureClient({
      baseUrl: 'http://localhost:8080',
      getAccessToken: () => 'owner.jwt',
    });
    const fetchMock = vi.fn(async (_input: RequestInfo, init?: RequestInit) => {
      const headers = new Headers(init?.headers);
      expect(headers.get('Authorization')).toBe('Bearer owner.jwt');
      expect(headers.has(SUBMISSION_SECRET_HEADER)).toBe(false);
      return new Response(
        JSON.stringify({
          id: 'sub-1',
          score: 1,
          maxScore: 1,
          answers: [],
          accessoryStatus: 'READY',
        }),
        { status: 200 },
      );
    });
    vi.stubGlobal('fetch', fetchMock);
    await fetchSubmission('sub-1');
    expect(fetchMock).toHaveBeenCalledOnce();
  });
});

describe('downloadAccessory', () => {
  it('retries once after 401, never keeps the blob URL, and never stores the file path', async () => {
    const tokens = ['first', 'second'];
    let tokenIndex = 0;
    configureClient({
      baseUrl: 'http://localhost:8080',
      getAccessToken: () => tokens[tokenIndex] ?? null,
      onUnauthorized: () => {
        tokenIndex = 1;
      },
    });

    const createObjectURL = vi.fn(() => 'blob:http://localhost/tmp');
    const revokeObjectURL = vi.fn();
    const click = vi.fn();
    const remove = vi.fn();
    const append = vi.fn();
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      writable: true,
      value: createObjectURL,
    });
    Object.defineProperty(URL, 'revokeObjectURL', {
      configurable: true,
      writable: true,
      value: revokeObjectURL,
    });
    vi.stubGlobal('document', {
      createElement: () => ({ href: '', download: '', click, remove }),
      body: { append },
    });

    const fetchMock = vi.fn(async (_input: RequestInfo, init?: RequestInit) => {
      const header = new Headers(init?.headers).get('Authorization');
      if (header === 'Bearer first') {
        return new Response(JSON.stringify({ code: 'UNAUTHORIZED', message: 'expired' }), {
          status: 401,
        });
      }
      expect(String(_input)).toBe(
        'http://localhost:8080/api/submissions/sub-1/accessory/download',
      );
      return new Response(new Blob(['solid ascii']), { status: 200 });
    });
    vi.stubGlobal('fetch', fetchMock);

    await downloadAccessory('sub-1');
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(createObjectURL).toHaveBeenCalledOnce();
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:http://localhost/tmp');
    expect(click).toHaveBeenCalledOnce();
    expect(remove).toHaveBeenCalledOnce();
  });
});

