CREATE TABLE devices (
    id uuid PRIMARY KEY,
    hardware_id text NOT NULL UNIQUE,
    student_id uuid,
    active boolean NOT NULL DEFAULT true,
    last_seen_at timestamptz,
    created_at timestamptz NOT NULL
);

CREATE TABLE outbox_events (
    id uuid PRIMARY KEY,
    payload jsonb NOT NULL,
    created_at timestamptz NOT NULL,
    published_at timestamptz,
    lease_until timestamptz,
    lease_owner text
);

CREATE INDEX outbox_events_unpublished_created_at_idx
    ON outbox_events (created_at)
    WHERE published_at IS NULL;
