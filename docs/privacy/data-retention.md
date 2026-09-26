# Data retention

What is stored, for how long, and what must never be logged.

| Data | Where | Retention |
|---|---|---|
| Email, Argon2 password hash, nickname, role | Postgres `users` | Until the account is deleted. Google-only accounts have a null password hash. |
| Scored answers, score, capability hash | Postgres `submissions.answers` (`jsonb`) and `submission_secret_hash` | Kept with the submission. The plaintext capability is returned once and is not stored. |
| Accessory object key | Postgres `submissions.accessory_key` | Key only. The STL is a private S3 object. |
| Outbox payload | Postgres `outbox_events` | Row remains after publish (`published_at` set). Payload is the device command, not the answer text. |
| Resend audit | Postgres `command_resend_audits` | Staff id, device id, command id, outcome, time. |
| Refresh tokens and OAuth exchange codes | Redis | Refresh TTL 14 days, rotated on use. Reuse revokes the family. Exchange codes live 60 seconds and are single-use. |
| Device heartbeats | Postgres `devices.last_seen_at` | Latest timestamp only. |
| Device event log | DynamoDB `device_events` | Command state machine keyed by `commandId`. |
| STL | S3 `cybersixseven-accessories` | Private. Browser download is a presigned GET of at most 5 minutes. |

Logs are ECS JSON. `trace_id` comes from the OpenTelemetry javaagent MDC. `submissionId`, `commandId`, and `deviceId` are added when the code has them. A structured-log customizer redacts passwords, tokens, `Authorization` values, API keys, capabilities, cookies, and JWTs. Do not log the capability plaintext, password, password hash, refresh cookie, or `X-Internal-Api-Key`.
