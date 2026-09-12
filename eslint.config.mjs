// ESLint 10 reads flat config only. An .eslintrc.json is not loaded at all, so
// lint would pass while enforcing nothing. Every workspace runs `eslint .`
// against this one root config -- there is no per-package ESLint config.
import js from '@eslint/js';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  {
    ignores: [
      '**/node_modules/**',
      '**/.next/**',
      '**/.turbo/**',
      '**/next-env.d.ts',
      'services/**',
      'firmware/**',
      'documents/**',
    ],
  },
  js.configs.recommended,
  tseslint.configs.recommended,
  {
    files: ['**/*.{ts,tsx}'],
    rules: {
      '@typescript-eslint/consistent-type-imports': 'error',
    },
  },
);
