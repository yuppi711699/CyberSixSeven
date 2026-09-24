'use client';

import { ApiClientError, leaderboardQueryOptions } from '@cybersixseven/api-client';
import { useAuth } from '@cybersixseven/auth-client';
import { Button, Table } from '@cybersixseven/ui';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { StaffFrame } from '../staff';
import styles from '../page.module.css';

export default function LeaderboardPage() {
  const { user, ready } = useAuth();
  const [page, setPage] = useState(0);
  const board = useQuery({
    ...leaderboardQueryOptions(page),
    enabled: ready && !!user && user.role !== 'STUDENT',
  });

  return (
    <StaffFrame title="Leaderboard">
      {board.isLoading ? <p className={styles.status}>Loading…</p> : null}
      {board.isError ? (
        <p className={styles.error} role="alert">
          {board.error instanceof ApiClientError ? board.error.message : 'Could not load the leaderboard.'}
        </p>
      ) : null}
      <Table
        columns={[
          { key: 'rank', header: 'Rank', render: (row) => row.rank },
          { key: 'nickname', header: 'Nickname', render: (row) => row.nickname },
          { key: 'userId', header: 'User', render: (row) => row.userId },
          { key: 'score', header: 'Points', render: (row) => row.score },
        ]}
        rows={board.data?.content ?? []}
        rowKey={(row) => row.userId}
        empty="No scores yet."
      />
      <Button type="button" variant="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>
        Previous
      </Button>
    </StaffFrame>
  );
}
