import { afterEach, describe, expect, it, vi } from 'vitest';
import { normalizeApiError, normalizeNetworkError } from './errors';
import { ApiClientError } from './types';

afterEach(() => {
  vi.restoreAllMocks();
});

describe('normalizeApiError', () => {
  it('maps JSON error bodies to ApiClientError', async () => {
    const response = new Response(
      JSON.stringify({ code: 'INVALID_SUBMISSION', message: 'exactly 3 answers are required' }),
      { status: 400, headers: { 'Content-Type': 'application/json' } },
    );

    const error = await normalizeApiError(response);

    expect(error).toBeInstanceOf(ApiClientError);
    expect(error.status).toBe(400);
    expect(error.code).toBe('INVALID_SUBMISSION');
    expect(error.message).toBe('exactly 3 answers are required');
  });

  it('falls back when the body is not JSON', async () => {
    const response = new Response('nope', { status: 503 });

    const error = await normalizeApiError(response);

    expect(error.status).toBe(503);
    expect(error.code).toBe('HTTP_ERROR');
    expect(error.message).toContain('503');
  });
});

describe('normalizeNetworkError', () => {
  it('returns a bounded unavailable error for fetch failures', () => {
    const error = normalizeNetworkError(new TypeError('Failed to fetch'));

    expect(error.status).toBe(0);
    expect(error.code).toBe('NETWORK_ERROR');
    expect(error.message).toMatch(/unavailable/i);
  });

  it('passes through ApiClientError unchanged', () => {
    const original = new ApiClientError(400, 'INVALID_SUBMISSION', 'bad');
    expect(normalizeNetworkError(original)).toBe(original);
  });
});
