CREATE TABLE command_resend_audits (
    id uuid PRIMARY KEY,
    staff_user_id uuid NOT NULL REFERENCES users (id),
    device_id uuid NOT NULL REFERENCES devices (id),
    command_id uuid NOT NULL,
    outcome text NOT NULL,
    created_at timestamptz NOT NULL
);

CREATE INDEX command_resend_audits_device_created_idx
    ON command_resend_audits (device_id, created_at DESC);
