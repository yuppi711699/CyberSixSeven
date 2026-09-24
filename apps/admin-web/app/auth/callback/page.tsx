'use client';

import { exchangeOauthCode, takeOauthCodeFromUrl } from '@cybersixseven/auth-client';
import { useRouter } from 'next/navigation';
import { useEffect, useRef, useState } from 'react';
import styles from '../../page.module.css';

export default function AdminAuthCallbackPage() {
  const router = useRouter();
  const ran = useRef(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (ran.current) {
      return;
    }
    ran.current = true;
    const url = new URL(window.location.href);
    const code = takeOauthCodeFromUrl(url);
    window.history.replaceState({}, '', `${url.pathname}${url.search}`);
    if (!code) {
      setError('Missing exchange code.');
      return;
    }
    void exchangeOauthCode(code)
      .then(() => router.replace('/'))
      .catch(() => setError('OAuth exchange failed. Try signing in again.'));
  }, [router]);

  return (
    <main className={styles.page}>
      <p className={error ? styles.error : styles.status} role={error ? 'alert' : 'status'}>
        {error ?? 'Finishing sign-in…'}
      </p>
    </main>
  );
}
