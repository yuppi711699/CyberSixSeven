'use client';

import type { ReactNode } from 'react';

export interface TableColumn<T> {
  key: string;
  header: string;
  sortable?: boolean;
  render: (row: T) => ReactNode;
}

export interface TableProps<T> {
  columns: TableColumn<T>[];
  rows: T[];
  rowKey: (row: T) => string;
  sortKey?: string;
  sortDirection?: 'asc' | 'desc';
  onSort?: (key: string) => void;
  empty?: string;
}

export function Table<T>({
  columns,
  rows,
  rowKey,
  sortKey,
  sortDirection = 'asc',
  onSort,
  empty = 'Nothing here yet.',
}: TableProps<T>) {
  return (
    <div style={{ overflowX: 'auto' }}>
      <table style={{ width: '100%', borderCollapse: 'collapse' }}>
        <thead>
          <tr>
            {columns.map((column) => (
              <th key={column.key} scope="col" style={{ textAlign: 'left', padding: '0.5rem' }}>
                {column.sortable && onSort ? (
                  <button type="button" onClick={() => onSort(column.key)}>
                    {column.header}
                    {sortKey === column.key ? (sortDirection === 'asc' ? ' ↑' : ' ↓') : ''}
                  </button>
                ) : (
                  column.header
                )}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 ? (
            <tr>
              <td colSpan={columns.length} style={{ padding: '0.75rem' }}>
                {empty}
              </td>
            </tr>
          ) : (
            rows.map((row) => (
              <tr key={rowKey(row)}>
                {columns.map((column) => (
                  <td key={column.key} style={{ padding: '0.5rem', verticalAlign: 'top' }}>
                    {column.render(row)}
                  </td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}
