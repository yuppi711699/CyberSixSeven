'use client';

import { ApiClientError, adminDevicesQueryOptions, resendCommandMutationOptions } from '@cybersixseven/api-client';
import { useAuth } from '@cybersixseven/auth-client';
import { Button, Table, useToast } from '@cybersixseven/ui';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { StaffFrame } from '../staff';
import styles from '../page.module.css';

export default function DevicesPage() {
  const toast = useToast();
  const { user, ready } = useAuth();
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState('createdAt,desc');
  const [pendingId, setPendingId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const devices = useQuery({
    ...adminDevicesQueryOptions({ page, sort }),
    enabled: ready && !!user && user.role !== 'STUDENT',
  });
  const resend = useMutation(resendCommandMutationOptions());

  return (
    <StaffFrame title="Devices">
      {devices.isLoading ? <p className={styles.status}>Loading…</p> : null}
      {devices.isError ? (
        <p className={styles.error} role="alert">
          {devices.error instanceof ApiClientError ? devices.error.message : 'Could not load devices.'}
        </p>
      ) : null}
      {error ? (
        <p className={styles.error} role="alert">
          {error}
        </p>
      ) : null}
      <Table
        columns={[
          { key: 'hardwareId', header: 'Hardware', sortable: true, render: (row) => row.hardwareId },
          {
            key: 'lastSeenAt',
            header: 'Last seen',
            sortable: true,
            render: (row) => row.lastSeenAt ?? 'never',
          },
          {
            key: 'resend',
            header: 'Resend',
            render: (row) => (
              <Button
                type="button"
                disabled={pendingId === row.id}
                onClick={() => {
                  setError(null);
                  setPendingId(row.id);
                  void resend
                    .mutateAsync(row.id)
                    .then((result) => {
                      toast.show(`${result.accepted ? 'Accepted' : 'Rejected'}: ${result.message}`);
                    })
                    .catch((caught: unknown) => {
                      const message =
                        caught instanceof ApiClientError ? caught.message : 'Resend failed.';
                      setError(message);
                      toast.show(message);
                    })
                    .finally(() => setPendingId(null));
                }}
              >
                {pendingId === row.id ? 'Resending…' : 'Resend command'}
              </Button>
            ),
          },
        ]}
        rows={devices.data?.content ?? []}
        rowKey={(row) => row.id}
        sortKey={sort.startsWith('lastSeenAt') ? 'lastSeenAt' : 'hardwareId'}
        sortDirection={sort.endsWith(',asc') ? 'asc' : 'desc'}
        onSort={(key) => setSort((current) => (current === `${key},desc` ? `${key},asc` : `${key},desc`))}
        empty="No devices."
      />
      <Button type="button" variant="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>
        Previous
      </Button>
    </StaffFrame>
  );
}
