import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { createElement } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { Button } from './Button';

afterEach(cleanup);

describe('Button', () => {
  it('renders children and forwards click behavior', () => {
    const onClick = vi.fn();

    render(createElement(Button, { onClick }, 'Submit answer'));
    fireEvent.click(screen.getByRole('button', { name: 'Submit answer' }));

    expect(onClick).toHaveBeenCalledOnce();
  });

  it('defaults to a non-submitting button', () => {
    render(createElement(Button, null, 'Continue'));

    expect(screen.getByRole('button', { name: 'Continue' }).getAttribute('type')).toBe('button');
  });
});
