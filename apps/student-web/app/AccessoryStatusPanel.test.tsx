import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { createElement, type ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AccessoryStatusPanel } from './AccessoryStatusPanel';

const detail = {
  id: 'sub-1',
  score: 3,
  maxScore: 3,
  answers: [],
};

function renderPanel(props?: { pollIntervalMs?: number; pollTimeoutMs?: number }) {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  return {
    client,
    ...render(
      createElement(
        QueryClientProvider,
        { client },
        createElement(AccessoryStatusPanel, {
          submissionId: 'sub-1',
          secret: 'memory-only',
          pollIntervalMs: 20,
          pollTimeoutMs: 5_000,
          ...props,
        }) as ReactNode,
      ),
    ),
  };
}

function assertNoLeak(container: HTMLElement) {
  const text = container.textContent ?? '';
  expect(text).not.toMatch(/reward\.stl|accessoryKey|accessoryUrl|x-amz-|s3\.amazonaws|memory-only/i);
  expect(window.localStorage.length).toBe(0);
  expect(window.sessionStorage.length).toBe(0);
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('AccessoryStatusPanel', () => {
  it('transitions from generating to ready without enabling download', async () => {
    let calls = 0;
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo) => {
        expect(String(input)).not.toMatch(/secret=/i);
        calls += 1;
        const status = calls === 1 ? 'PENDING' : 'READY';
        return new Response(JSON.stringify({ ...detail, accessoryStatus: status }), { status: 200 });
      }),
    );

    const { container } = renderPanel();
    expect(await screen.findByText('Generating your accessory…')).toBeTruthy();
    expect(await screen.findByText('Your accessory is ready.')).toBeTruthy();
    expect(screen.getByTestId('accessory-preview')).toBeTruthy();
    const download = screen.getByRole('button', { name: 'Sign in to download' }) as HTMLButtonElement;
    expect(download.disabled).toBe(true);
    expect(screen.queryByRole('link', { name: /download/i })).toBeNull();
    assertNoLeak(container);
  });

  it('stops polling on terminal failure', async () => {
    const fetchMock = vi.fn(async () => {
      return new Response(JSON.stringify({ ...detail, accessoryStatus: 'FAILED' }), { status: 200 });
    });
    vi.stubGlobal('fetch', fetchMock);

    renderPanel({ pollIntervalMs: 15 });
    expect(await screen.findByText(/Accessory generation failed/)).toBeTruthy();
    const afterReady = fetchMock.mock.calls.length;
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(fetchMock.mock.calls.length).toBe(afterReady);
  });

  it('stops after the bounded window and retries with a fresh window', async () => {
    const fetchMock = vi.fn(async () => {
      return new Response(JSON.stringify({ ...detail, accessoryStatus: 'PENDING' }), { status: 200 });
    });
    vi.stubGlobal('fetch', fetchMock);

    renderPanel({ pollIntervalMs: 20, pollTimeoutMs: 60 });
    expect(await screen.findByText(/taking longer than expected/i)).toBeTruthy();
    const beforeRetry = fetchMock.mock.calls.length;
    await new Promise((resolve) => setTimeout(resolve, 40));
    expect(fetchMock.mock.calls.length).toBe(beforeRetry);

    fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
    await waitFor(() => {
      expect(fetchMock.mock.calls.length).toBeGreaterThan(beforeRetry);
    });
  });

  it('cancels polling on unmount', async () => {
    const fetchMock = vi.fn(async () => {
      return new Response(JSON.stringify({ ...detail, accessoryStatus: 'PENDING' }), { status: 200 });
    });
    vi.stubGlobal('fetch', fetchMock);

    const { unmount } = renderPanel({ pollIntervalMs: 20, pollTimeoutMs: 5_000 });
    await screen.findByText('Generating your accessory…');
    await waitFor(() => expect(fetchMock.mock.calls.length).toBeGreaterThan(0));
    unmount();
    const afterUnmount = fetchMock.mock.calls.length;
    await new Promise((resolve) => setTimeout(resolve, 60));
    expect(fetchMock.mock.calls.length).toBe(afterUnmount);
  });
});
