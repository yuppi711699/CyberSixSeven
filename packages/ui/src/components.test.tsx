import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { createElement, type ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { Card } from './Card';
import { Input } from './Input';
import { Modal } from './Modal';
import { Table, type TableColumn } from './Table';
import { ToastProvider, useToast } from './Toast';

afterEach(cleanup);

describe('Input', () => {
  it('stays disabled when told to', () => {
    render(createElement(Input, { 'aria-label': 'Score', disabled: true }));
    expect(screen.getByLabelText('Score').hasAttribute('disabled')).toBe(true);
  });
});

describe('Table', () => {
  it('calls the sort control and shows an empty state', () => {
    const onSort = vi.fn();
    const columns: TableColumn<{ id: string }>[] = [
      { key: 'id', header: 'Id', sortable: true, render: (row) => row.id },
    ];
    const { rerender } = render(
      createElement(Table<{ id: string }>, {
        columns,
        rows: [],
        rowKey: (row) => row.id,
        onSort,
        empty: 'No rows',
      }),
    );
    expect(screen.getByText('No rows')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Id' }));
    expect(onSort).toHaveBeenCalledWith('id');
    rerender(
      createElement(Table<{ id: string }>, {
        columns,
        rows: [{ id: 'a' }],
        rowKey: (row) => row.id,
        sortKey: 'id',
        sortDirection: 'desc',
        onSort,
      }),
    );
    expect(screen.getByRole('button', { name: 'Id ↓' })).toBeTruthy();
  });
});

describe('Modal', () => {
  it('closes from the dialog button', () => {
    const onClose = vi.fn();
    render(createElement(Modal, { open: true, title: 'Edit question', onClose }, 'Body'));
    expect(screen.getByRole('dialog', { name: 'Edit question' })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Close' }));
    expect(onClose).toHaveBeenCalledOnce();
  });
});

function ToastTrigger({ children }: { children?: ReactNode }) {
  const toast = useToast();
  return createElement(
    'button',
    { type: 'button', onClick: () => toast.show('command republished') },
    children ?? 'Notify',
  );
}

describe('Toast', () => {
  it('shows the message only after show is called', () => {
    render(createElement(ToastProvider, null, createElement(ToastTrigger)));
    expect(screen.queryByRole('status')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Notify' }));
    expect(screen.getByRole('status').textContent).toContain('command republished');
  });
});

describe('Card', () => {
  it('renders its children', () => {
    render(createElement(Card, null, 'Quest'));
    expect(screen.getByText('Quest')).toBeTruthy();
  });
});
