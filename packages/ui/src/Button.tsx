'use client';

import type { ButtonHTMLAttributes, CSSProperties } from 'react';

export type ButtonVariant = 'primary' | 'secondary';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
}

const base: CSSProperties = {
  font: 'inherit',
  fontWeight: 600,
  lineHeight: 1.2,
  padding: '0.625rem 1.125rem',
  borderRadius: '0.5rem',
  borderStyle: 'solid',
  borderWidth: '1px',
  cursor: 'pointer',
};

const variants: Record<ButtonVariant, CSSProperties> = {
  primary: {
    color: 'var(--c67-on-accent, #08111f)',
    backgroundColor: 'var(--c67-accent, #4de1c1)',
    borderColor: 'var(--c67-accent, #4de1c1)',
  },
  secondary: {
    color: 'var(--c67-fg, #e6edf6)',
    backgroundColor: 'transparent',
    borderColor: 'var(--c67-border, #2a3b52)',
  },
};

/**
 * The one shared component in v0.1. It exists mainly to prove that
 * `apps/student-web` really is linked to this workspace package and that Next
 * transpiles the raw TypeScript in `src/` (see `transpilePackages`).
 */
export function Button({ variant = 'primary', type = 'button', style, ...rest }: ButtonProps) {
  return <button type={type} style={{ ...base, ...variants[variant], ...style }} {...rest} />;
}
