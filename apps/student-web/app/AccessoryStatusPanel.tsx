'use client';

import {
  ACCESSORY_POLL_INTERVAL_MS,
  ACCESSORY_POLL_TIMEOUT_MS,
  ApiClientError,
  downloadAccessory,
  submissionQueryOptions,
  type AccessoryStatus,
} from '@cybersixseven/api-client';
import { Button } from '@cybersixseven/ui';
import { useQuery } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import styles from './page.module.css';

export function AccessoryStatusPanel({
  submissionId,
  secret,
  canDownload = false,
  pollIntervalMs = ACCESSORY_POLL_INTERVAL_MS,
  pollTimeoutMs = ACCESSORY_POLL_TIMEOUT_MS,
}: {
  submissionId: string;
  secret?: string | null;
  canDownload?: boolean;
  pollIntervalMs?: number;
  pollTimeoutMs?: number;
}) {
  const [timedOut, setTimedOut] = useState(false);
  const [downloadError, setDownloadError] = useState<string | null>(null);
  const startedAtRef = useRef(Date.now());

  const query = useQuery({
    ...submissionQueryOptions(submissionId, {
      secret,
      enabled: Boolean(submissionId) && (Boolean(secret) || canDownload),
    }),
    refetchInterval: (current) => {
      const status = current.state.data?.accessoryStatus;
      if (status === 'READY' || status === 'FAILED' || timedOut) {
        return false;
      }
      return pollIntervalMs;
    },
  });

  const status: AccessoryStatus | undefined = query.data?.accessoryStatus;

  useEffect(() => {
    if (status === 'READY' || status === 'FAILED' || timedOut) {
      return;
    }
    const remaining = pollTimeoutMs - (Date.now() - startedAtRef.current);
    if (remaining <= 0) {
      setTimedOut(true);
      return;
    }
    const timer = window.setTimeout(() => setTimedOut(true), remaining);
    return () => window.clearTimeout(timer);
  }, [status, timedOut, pollTimeoutMs]);

  function retry() {
    startedAtRef.current = Date.now();
    setTimedOut(false);
    void query.refetch();
  }

  const body = JSON.stringify(query.data ?? {});
  if (/reward\.stl|accessoryKey|accessoryUrl|x-amz-|s3\.amazonaws/i.test(body)) {
    throw new Error('submission detail leaked an object location');
  }

  return (
    <section className={styles.accessory} aria-labelledby="accessory-heading">
      <h3 id="accessory-heading" className={styles.prompt}>
        Accessory
      </h3>
      <AccessoryBody
        submissionId={submissionId}
        status={status}
        timedOut={timedOut}
        loading={query.isLoading}
        error={query.error}
        canDownload={canDownload}
        downloadError={downloadError}
        onRetry={retry}
        onDownload={() => {
          setDownloadError(null);
          void downloadAccessory(submissionId).catch((caught) => {
            setDownloadError(
              caught instanceof ApiClientError ? caught.message : 'Download failed.',
            );
          });
        }}
      />
    </section>
  );
}

function AccessoryBody({
  submissionId,
  status,
  timedOut,
  loading,
  error,
  canDownload,
  downloadError,
  onRetry,
  onDownload,
}: {
  submissionId: string;
  status: AccessoryStatus | undefined;
  timedOut: boolean;
  loading: boolean;
  error: unknown;
  canDownload: boolean;
  downloadError: string | null;
  onRetry: () => void;
  onDownload: () => void;
}) {
  if (status === 'READY') {
    return (
      <>
        <p className={styles.status}>Your accessory is ready.</p>
        <AccessoryPreview />
        {canDownload ? (
          <Button type="button" onClick={onDownload}>
            Download accessory
          </Button>
        ) : (
          <>
            <Button type="button" disabled>
              Sign in to download
            </Button>
            <p className={styles.status}>Download is available after you sign in.</p>
          </>
        )}
        {downloadError ? (
          <p className={styles.error} role="alert">
            {downloadError}
          </p>
        ) : null}
        <span hidden data-testid="submission-id">
          {submissionId}
        </span>
      </>
    );
  }

  if (status === 'FAILED') {
    return (
      <p className={styles.error} role="alert">
        Accessory generation failed. You can retry in a moment.
      </p>
    );
  }

  if (timedOut) {
    return (
      <>
        <p className={styles.error} role="alert">
          Accessory generation is taking longer than expected.
        </p>
        <Button type="button" variant="secondary" onClick={onRetry}>
          Retry
        </Button>
      </>
    );
  }

  if (error) {
    const message =
      error instanceof ApiClientError ? error.message : 'Could not check accessory status.';
    return (
      <p className={styles.error} role="alert">
        {message}
      </p>
    );
  }

  if (loading || status === 'PENDING' || status === undefined) {
    return <p className={styles.status}>Generating your accessory…</p>;
  }

  return <p className={styles.status}>Generating your accessory…</p>;
}

function AccessoryPreview() {
  return (
    <svg
      className={styles.preview}
      viewBox="0 0 64 64"
      width="96"
      height="96"
      aria-hidden="true"
      data-testid="accessory-preview"
    >
      <ellipse cx="32" cy="50" rx="18" ry="6" fill="currentColor" opacity="0.35" />
      <polygon points="32,8 48,48 16,48" fill="currentColor" />
    </svg>
  );
}
