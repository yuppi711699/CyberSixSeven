'use client';

import { isProductEnabled, useAuth } from '@cybersixseven/auth-client';
import Link from 'next/link';
import { QuestionForm } from './QuestionForm';
import styles from './page.module.css';

export default function HomePage() {
  const { user, ready, logout } = useAuth();

  return (
    <main className={styles.page}>
      <header>
        <h1 className={styles.title}>CyberSixSeven</h1>
        <p className={styles.subtitle}>Student web · answer the questions below</p>
        {user ? (
          <p className={styles.status}>
            Signed in as {user.nickname}{' '}
            <button type="button" className={styles.linkButton} onClick={() => void logout()}>
              Log out
            </button>
          </p>
        ) : null}
      </header>
      {!ready ? (
        <p className={styles.status}>Loading…</p>
      ) : !isProductEnabled() ? (
        <p className={styles.status} role="status">
          Product routes are unavailable until the v0.8 backend smoke gate. Auth still works.
        </p>
      ) : !user ? (
        <p className={styles.status}>
          <Link href="/login">Sign in</Link> to start the questions.
        </p>
      ) : (
        <QuestionForm />
      )}
    </main>
  );
}
