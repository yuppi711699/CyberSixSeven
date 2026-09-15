CREATE TABLE questions (
    id uuid PRIMARY KEY,
    prompt text NOT NULL,
    options jsonb NOT NULL,
    correct_answer integer NOT NULL,
    max_points integer NOT NULL CHECK (max_points > 0),
    display_order integer NOT NULL UNIQUE CHECK (display_order > 0),
    created_at timestamptz NOT NULL
);

CREATE TABLE submissions (
    id uuid PRIMARY KEY,
    student_id uuid,
    answers jsonb NOT NULL,
    score integer NOT NULL CHECK (score >= 0),
    max_score integer NOT NULL CHECK (max_score > 0),
    submission_secret_hash bytea NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT submissions_score_within_max CHECK (score <= max_score)
);

INSERT INTO questions (
    id,
    prompt,
    options,
    correct_answer,
    max_points,
    display_order,
    created_at
) VALUES
    (
        '00000000-0000-0000-0000-000000000001',
        'What is 7 + 5?',
        '[]'::jsonb,
        12,
        1,
        1,
        CURRENT_TIMESTAMP
    ),
    (
        '00000000-0000-0000-0000-000000000002',
        'What is 9 × 3?',
        '[]'::jsonb,
        27,
        1,
        2,
        CURRENT_TIMESTAMP
    ),
    (
        '00000000-0000-0000-0000-000000000003',
        'What is 20 - 8?',
        '[]'::jsonb,
        12,
        1,
        3,
        CURRENT_TIMESTAMP
    );
