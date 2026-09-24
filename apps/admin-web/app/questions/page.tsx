'use client';

import {
  ApiClientError,
  createQuestionMutationOptions,
  deleteQuestionMutationOptions,
  staffQuestionsQueryOptions,
  updateQuestionMutationOptions,
  type StaffQuestion,
  type UpsertQuestion,
} from '@cybersixseven/api-client';
import { useAuth } from '@cybersixseven/auth-client';
import { Button, Input, Modal, Table, useToast } from '@cybersixseven/ui';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
import { StaffFrame } from '../staff';
import styles from '../page.module.css';

const emptyForm: UpsertQuestion = {
  prompt: '',
  options: [],
  correctAnswer: 0,
  maxPoints: 1,
  displayOrder: 1,
};

export default function QuestionsPage() {
  const toast = useToast();
  const { user, ready } = useAuth();
  const queryClient = useQueryClient();
  const questions = useQuery({
    ...staffQuestionsQueryOptions(),
    enabled: ready && !!user && user.role !== 'STUDENT',
  });
  const [form, setForm] = useState<UpsertQuestion>(emptyForm);
  const [editing, setEditing] = useState<StaffQuestion | null>(null);
  const [error, setError] = useState<string | null>(null);

  const createQuestion = useMutation(createQuestionMutationOptions());
  const updateQuestion = useMutation(updateQuestionMutationOptions());
  const deleteQuestion = useMutation(deleteQuestionMutationOptions());

  async function refresh() {
    await queryClient.invalidateQueries({ queryKey: staffQuestionsQueryOptions().queryKey });
  }

  async function onCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    try {
      await createQuestion.mutateAsync(form);
      setForm(emptyForm);
      await refresh();
      toast.show('Question saved');
    } catch (caught) {
      setError(caught instanceof ApiClientError ? caught.message : 'Could not save the question.');
    }
  }

  async function onUpdate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!editing) {
      return;
    }
    setError(null);
    try {
      await updateQuestion.mutateAsync({
        id: editing.id,
        body: {
          prompt: editing.prompt,
          options: editing.options,
          correctAnswer: editing.correctAnswer,
          maxPoints: editing.maxPoints,
          displayOrder: editing.displayOrder,
        },
      });
      setEditing(null);
      await refresh();
      toast.show('Question updated');
    } catch (caught) {
      setError(caught instanceof ApiClientError ? caught.message : 'Could not update the question.');
    }
  }

  return (
    <StaffFrame title="Questions">
      <form className={styles.form} onSubmit={(event) => void onCreate(event)}>
        <label className={styles.label}>
          Prompt
          <Input
            value={form.prompt}
            onChange={(event) => setForm({ ...form, prompt: event.target.value })}
            required
          />
        </label>
        <label className={styles.label}>
          Correct answer
          <Input
            inputMode="numeric"
            value={String(form.correctAnswer)}
            onChange={(event) =>
              setForm({ ...form, correctAnswer: Number(event.target.value) })
            }
            required
          />
        </label>
        <label className={styles.label}>
          Max points
          <Input
            inputMode="numeric"
            value={String(form.maxPoints)}
            onChange={(event) => setForm({ ...form, maxPoints: Number(event.target.value) })}
            required
          />
        </label>
        <label className={styles.label}>
          Display order
          <Input
            inputMode="numeric"
            value={String(form.displayOrder)}
            onChange={(event) =>
              setForm({ ...form, displayOrder: Number(event.target.value) })
            }
            required
          />
        </label>
        {error ? (
          <p className={styles.error} role="alert">
            {error}
          </p>
        ) : null}
        <Button type="submit" disabled={createQuestion.isPending}>
          {createQuestion.isPending ? 'Saving…' : 'Create question'}
        </Button>
      </form>
      {questions.isLoading ? <p className={styles.status}>Loading…</p> : null}
      {questions.isError ? (
        <p className={styles.error} role="alert">
          {questions.error instanceof ApiClientError
            ? questions.error.message
            : 'Could not load questions.'}
        </p>
      ) : null}
      <Table
        columns={[
          { key: 'prompt', header: 'Prompt', render: (row) => row.prompt },
          { key: 'correctAnswer', header: 'Answer', render: (row) => row.correctAnswer },
          { key: 'displayOrder', header: 'Order', sortable: true, render: (row) => row.displayOrder },
          {
            key: 'actions',
            header: 'Actions',
            render: (row) => (
              <>
                <Button type="button" variant="secondary" onClick={() => setEditing(row)}>
                  Edit
                </Button>
                <Button
                  type="button"
                  variant="secondary"
                  disabled={deleteQuestion.isPending}
                  onClick={() => {
                    void deleteQuestion.mutateAsync(row.id).then(refresh).then(
                      () => toast.show('Question deleted'),
                      (caught: unknown) =>
                        setError(
                          caught instanceof ApiClientError
                            ? caught.message
                            : 'Could not delete the question.',
                        ),
                    );
                  }}
                >
                  Delete
                </Button>
              </>
            ),
          },
        ]}
        rows={[...(questions.data ?? [])].sort((a, b) => a.displayOrder - b.displayOrder)}
        rowKey={(row) => row.id}
        sortKey="displayOrder"
        sortDirection="asc"
        onSort={() => undefined}
        empty="No questions."
      />
      <Modal open={editing !== null} title="Edit question" onClose={() => setEditing(null)}>
        {editing ? (
          <form className={styles.form} onSubmit={(event) => void onUpdate(event)}>
            <label className={styles.label}>
              Prompt
              <Input
                value={editing.prompt}
                onChange={(event) => setEditing({ ...editing, prompt: event.target.value })}
                required
              />
            </label>
            <Button type="submit" disabled={updateQuestion.isPending}>
              {updateQuestion.isPending ? 'Saving…' : 'Save changes'}
            </Button>
          </form>
        ) : null}
      </Modal>
    </StaffFrame>
  );
}
