'use client';

import { AuthForm } from '../AuthForm';
import styles from '../page.module.css';

export default function LoginPage() {
  return (
    <main className={styles.page}>
      <header>
        <h1 className={styles.title}>Sign in</h1>
        <p className={styles.subtitle}>Student account · CyberSixSeven</p>
      </header>
      <AuthForm mode="student" homeHref="/" />
    </main>
  );
}
