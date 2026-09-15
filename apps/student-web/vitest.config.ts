import { defineConfig } from 'vitest/config';

export default defineConfig({
  oxc: {
    jsx: {
      runtime: 'automatic',
    },
  },
  test: {
    environment: 'jsdom',
    include: ['app/**/*.test.ts', 'app/**/*.test.tsx'],
    env: {
      NEXT_PUBLIC_API_BASE_URL: 'http://localhost:8080',
    },
  },
});
