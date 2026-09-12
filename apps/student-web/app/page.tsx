import { Button } from '@cybersixseven/ui';
import styles from './page.module.css';

export default function HomePage() {
  return (
    <main className={styles.page}>
      <header>
        <h1 className={styles.title}>CyberSixSeven</h1>
        <p className={styles.subtitle}>Student web &middot; v0.1 skeleton</p>
      </header>

      <article className={styles.card} aria-labelledby="question-prompt">
        <p className={styles.eyebrow}>Placeholder question &middot; 1 of 1</p>
        <h2 id="question-prompt" className={styles.prompt}>
          Which HTTP status code means Unauthorized?
        </h2>
        <ul className={styles.options}>
          <li className={styles.option}>401</li>
          <li className={styles.option}>403</li>
          <li className={styles.option}>404</li>
          <li className={styles.option}>500</li>
        </ul>
        <Button>Submit answer</Button>
      </article>
    </main>
  );
}
