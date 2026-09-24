'use client';

import type { InputHTMLAttributes } from 'react';

export type InputProps = InputHTMLAttributes<HTMLInputElement>;

export function Input({ style, ...rest }: InputProps) {
  return (
    <input
      style={{
        font: 'inherit',
        color: 'var(--c67-fg, #e6edf6)',
        backgroundColor: 'var(--c67-bg, #0b1220)',
        border: '1px solid var(--c67-border, #2a3b52)',
        borderRadius: '0.5rem',
        padding: '0.5rem 0.75rem',
        width: '100%',
        ...style,
      }}
      {...rest}
    />
  );
}
