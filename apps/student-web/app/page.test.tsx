import { cleanup, render, screen } from '@testing-library/react';
import { createElement } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import HomePage from './page';

vi.mock('./QuestionForm', () => ({
  QuestionForm: () => createElement('div', { 'data-testid': 'question-form' }, 'form'),
}));

afterEach(cleanup);

describe('HomePage', () => {
  it('renders the CyberSixSeven brand and question form shell', () => {
    render(createElement(HomePage));

    expect(screen.getByRole('heading', { level: 1, name: 'CyberSixSeven' })).toBeTruthy();
    expect(screen.getByTestId('question-form')).toBeTruthy();
  });
});
