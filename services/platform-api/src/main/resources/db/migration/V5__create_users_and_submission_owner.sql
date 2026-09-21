CREATE TABLE users (
    id uuid PRIMARY KEY,
    email text NOT NULL,
    password_hash text,
    nickname text NOT NULL,
    role text NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT users_email_normalized CHECK (email = lower(btrim(email))),
    CONSTRAINT users_role_allowed CHECK (role IN ('STUDENT', 'TEACHER', 'ADMIN'))
);

CREATE UNIQUE INDEX users_email_uidx ON users (email);

ALTER TABLE submissions
    ADD CONSTRAINT submissions_student_id_fkey
    FOREIGN KEY (student_id) REFERENCES users (id);

CREATE INDEX submissions_id_student_id_idx ON submissions (id, student_id);
CREATE INDEX submissions_student_id_idx ON submissions (student_id);
