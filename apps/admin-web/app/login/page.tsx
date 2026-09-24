'use client';

import { ApiClientError } from '@cybersixseven/api-client';
import { googleAuthorizationUrl, login } from '@cybersixseven/auth-client';
import { Button, Input } from '@cybersixseven/ui';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { type FormEvent, useState } from 'react';
import styles from '../page.module.css';

export default function AdminLoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPending(true);
    setError(null);
    try {
      await login({ email, password });
      router.replace('/');
    } catch (caught) {
      setError(caught instanceof ApiClientError ? caught.message : 'Sign-in failed.');
    } finally {
      setPending(false);
    }
  }

  return (
    <main className={styles.page}>
      <header>
        <h1 className={styles.title}>Admin sign in</h1>
        <p className={styles.subtitle}>Teacher and admin accounts only</p>
      </header>
      <form className={styles.form} onSubmit={(event) => void onSubmit(event)}>
        <label className={styles.label}>
          Email
          <Input
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            required
          />
        </label>
        <label className={styles.label}>
          Password
          <Input
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            minLength={8}
            required
          />
        </label>
        {error ? (
          <p className={styles.error} role="alert">
            {error}
          </p>
        ) : null}
        <Button type="submit" disabled={pending}>
          {pending ? 'Working…' : 'Sign in'}
        </Button>
        <Button
          type="button"
          variant="secondary"
          onClick={() => {
            window.location.href = googleAuthorizationUrl();
          }}
        >
          Continue with Google
        </Button>
        <p className={styles.status}>
          <Link href="/">Back</Link>
        </p>
      </form>
    </main>
  );
}
