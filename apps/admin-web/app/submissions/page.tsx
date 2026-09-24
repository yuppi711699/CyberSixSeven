'use client';

import { ApiClientError, adminSubmissionsQueryOptions } from '@cybersixseven/api-client';
import { useAuth } from '@cybersixseven/auth-client';
import { Button, Input, Table } from '@cybersixseven/ui';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { StaffFrame } from '../staff';
import styles from '../page.module.css';

export default function SubmissionsPage() {
  const { user, ready } = useAuth();
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState('createdAt,desc');
  const [studentId, setStudentId] = useState('');
  const submissions = useQuery({
    ...adminSubmissionsQueryOptions({
      page,
      sort,
      studentId: studentId.trim() || undefined,
    }),
    enabled: ready && !!user && user.role !== 'STUDENT',
  });
  const direction = sort.endsWith(',asc') ? 'asc' : 'desc';

  return (
    <StaffFrame title="Submissions">
      <label className={styles.label}>
        Student id
        <Input
          value={studentId}
          onChange={(event) => {
            setStudentId(event.target.value);
            setPage(0);
          }}
        />
      </label>
      {submissions.isLoading ? <p className={styles.status}>Loading…</p> : null}
      {submissions.isError ? (
        <p className={styles.error} role="alert">
          {submissions.error instanceof ApiClientError
            ? submissions.error.message
            : 'Could not load submissions.'}
        </p>
      ) : null}
      <Table
        columns={[
          { key: 'nickname', header: 'Nickname', render: (row) => row.nickname },
          { key: 'score', header: 'Score', sortable: true, render: (row) => `${row.score}/${row.maxScore}` },
          {
            key: 'createdAt',
            header: 'Created',
            sortable: true,
            render: (row) => row.createdAt,
          },
        ]}
        rows={submissions.data?.content ?? []}
        rowKey={(row) => row.id}
        sortKey={sort.startsWith('score') ? 'score' : 'createdAt'}
        sortDirection={direction}
        onSort={(key) => {
          setSort((current) => (current === `${key},desc` ? `${key},asc` : `${key},desc`));
          setPage(0);
        }}
        empty="No submissions."
      />
      <p className={styles.status}>
        <Button type="button" variant="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>
          Previous
        </Button>
        <Button
          type="button"
          variant="secondary"
          disabled={(submissions.data?.content.length ?? 0) < 20}
          onClick={() => setPage(page + 1)}
        >
          Next
        </Button>
      </p>
    </StaffFrame>
  );
}
