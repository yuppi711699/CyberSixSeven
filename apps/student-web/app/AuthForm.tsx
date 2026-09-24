'use client';

import { ApiClientError } from '@cybersixseven/api-client';
import { googleAuthorizationUrl, login, register } from '@cybersixseven/auth-client';
import { Button, Input } from '@cybersixseven/ui';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { type FormEvent, useState } from 'react';
import styles from './page.module.css';

export function AuthForm({
  mode,
  homeHref,
}: {
  mode: 'student' | 'admin';
  homeHref: string;
}) {
  const router = useRouter();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [nickname, setNickname] = useState('');
  const [registerMode, setRegisterMode] = useState(mode === 'student');
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setPending(true);
    try {
      if (registerMode) {
        await register({ email, password, nickname });
      } else {
        await login({ email, password });
      }
      router.replace(homeHref);
    } catch (caught) {
      setError(caught instanceof ApiClientError ? caught.message : 'Sign-in failed.');
    } finally {
      setPending(false);
    }
  }

  return (
    <form className={styles.form} onSubmit={(event) => void onSubmit(event)}>
      {registerMode ? (
        <label className={styles.label}>
          Nickname
          <Input
            value={nickname}
            onChange={(event) => setNickname(event.target.value)}
            required
          />
        </label>
      ) : null}
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
        {pending ? 'Working…' : registerMode ? 'Create account' : 'Sign in'}
      </Button>
      {mode === 'student' ? (
        <Button type="button" variant="secondary" onClick={() => setRegisterMode((current) => !current)}>
          {registerMode ? 'Already have an account? Sign in' : 'Need an account? Register'}
        </Button>
      ) : null}
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
        <Link href={homeHref}>Back</Link>
      </p>
    </form>
  );
}
