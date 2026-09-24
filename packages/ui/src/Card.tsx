'use client';

import type { HTMLAttributes, ReactNode } from 'react';

export interface CardProps extends HTMLAttributes<HTMLElement> {
  children: ReactNode;
}

export function Card({ children, style, ...rest }: CardProps) {
  return (
    <section
      style={{
        backgroundColor: 'var(--c67-surface, #121c2e)',
        border: '1px solid var(--c67-border, #2a3b52)',
        borderRadius: '0.75rem',
        padding: '1rem',
        ...style,
      }}
      {...rest}
    >
      {children}
    </section>
  );
}
