import { QuestionForm } from './QuestionForm';
import styles from './page.module.css';

export default function HomePage() {
  return (
    <main className={styles.page}>
      <header>
        <h1 className={styles.title}>CyberSixSeven</h1>
        <p className={styles.subtitle}>Student web · answer the questions below</p>
      </header>
      <QuestionForm />
    </main>
  );
}
