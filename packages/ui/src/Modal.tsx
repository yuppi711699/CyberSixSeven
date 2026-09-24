'use client';

import type { ReactNode } from 'react';
import { Button } from './Button';

export interface ModalProps {
  open: boolean;
  title: string;
  onClose: () => void;
  children: ReactNode;
}

export function Modal({ open, title, onClose, children }: ModalProps) {
  if (!open) {
    return null;
  }
  return (
    <div
      role="presentation"
      onClick={onClose}
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(0, 0, 0, 0.55)',
        display: 'grid',
        placeItems: 'center',
        padding: '1rem',
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="c67-modal-title"
        onClick={(event) => event.stopPropagation()}
        style={{
          backgroundColor: 'var(--c67-surface, #121c2e)',
          color: 'var(--c67-fg, #e6edf6)',
          borderRadius: '0.75rem',
          padding: '1rem',
          minWidth: 'min(100%, 28rem)',
        }}
      >
        <h2 id="c67-modal-title" style={{ marginTop: 0 }}>
          {title}
        </h2>
        {children}
        <Button type="button" variant="secondary" onClick={onClose}>
          Close
        </Button>
      </div>
    </div>
  );
}
