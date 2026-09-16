CREATE TABLE robots (
    id uuid PRIMARY KEY,
    name text NOT NULL,
    model text NOT NULL,
    created_at timestamptz NOT NULL
);