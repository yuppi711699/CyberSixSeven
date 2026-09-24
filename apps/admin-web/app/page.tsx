'use client';

import { isProductEnabled, useAuth } from '@cybersixseven/auth-client';
import { Button, Card } from '@cybersixseven/ui';
import Link from 'next/link';
import styles from './page.module.css';

export default function AdminHomePage() {
  const { user, ready, logout } = useAuth();

  return (
    <main className={styles.page}>
      <header>
        <h1 className={styles.title}>CyberSixSeven Admin</h1>
        <p className={styles.subtitle}>Teacher/admin portal</p>
      </header>
      {!ready ? (
        <p className={styles.status}>Loading…</p>
      ) : !user ? (
        <p className={styles.status}>
          <Link href="/login">Sign in</Link> with a teacher or admin account.
        </p>
      ) : user.role === 'STUDENT' ? (
        <section>
          <h2 className={styles.title}>Forbidden role</h2>
          <p className={styles.error} role="alert">
            Student accounts cannot use the admin app.
          </p>
          <Button type="button" variant="secondary" onClick={() => void logout()}>
            Log out
          </Button>
        </section>
      ) : !isProductEnabled() ? (
        <p className={styles.status} role="status">
          Admin product tools are unavailable until the v0.8 backend smoke gate.
        </p>
      ) : (
        <Card>
          <p className={styles.status}>Question bank, submissions, leaderboard, and devices.</p>
          <p className={styles.status}>
            <Link href="/questions">Questions</Link>
            {' · '}
            <Link href="/submissions">Submissions</Link>
            {' · '}
            <Link href="/leaderboard">Leaderboard</Link>
            {' · '}
            <Link href="/devices">Devices</Link>
          </p>
        </Card>
      )}
    </main>
  );
}
