'use client';

import { createContext, type ReactNode, useCallback, useContext, useMemo, useState } from 'react';

interface ToastContextValue {
  show: (message: string) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [message, setMessage] = useState<string | null>(null);
  const show = useCallback((next: string) => setMessage(next), []);
  const value = useMemo(() => ({ show }), [show]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      {message ? (
        <div role="status" style={{ position: 'fixed', right: '1rem', bottom: '1rem' }}>
          {message}
          <button type="button" onClick={() => setMessage(null)} aria-label="Dismiss notice">
            Dismiss
          </button>
        </div>
      ) : null}
    </ToastContext.Provider>
  );
}

export function useToast(): ToastContextValue {
  const value = useContext(ToastContext);
  if (!value) {
    throw new Error('useToast must be used inside ToastProvider');
  }
  return value;
}
