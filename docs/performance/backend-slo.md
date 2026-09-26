# Backend SLOs

Measured on the local stack. Physical ACK is a separate device metric and is not part of either SLI.

| SLI | Definition | Target |
|---|---|---|
| Submission create | Wall time of `POST /api/submissions`, including auth and the Postgres commit | p95 < 300 ms |
| SNS to MQTT | `snsPublishedAt` message attribute (clock immediately before the confirmed SNS `Publish`) until `device-command-service` returns from the MQTT publish | p95 < 2 s |
| Physical ACK | MQTT publish in this process until the ESP32 status message | reported, not an SLO |

Instruments:

- `sli.submission` — handler timer on `POST /api/submissions`
- `sli.sns_to_mqtt` — timer in `device-command-service`
- `mqtt.ack.latency` — in-process publish-to-ACK timer. This is not an OpenTelemetry span on the ESP32.
- `outbox.lag.seconds` — age of the oldest unpublished `outbox_events` row

`SubmissionSloTests` asserts the submission p95 against a live Testcontainers Postgres. The SNS-to-MQTT timer is fed by the `snsPublishedAt` attribute (`DeviceCommandProcessorTests`).

## Alert

Page when `outbox.lag.seconds` stays above 30 for 5 minutes.

That means the publisher is paused, SNS is rejecting publishes, or rows are leased and not completing. Check `outbox.publish` `result=failure`, then the oldest `outbox_events.created_at` where `published_at` is null. Do not restart consumers first: a paused publisher is invisible to the queues.
