# Trace examples

Jaeger UI: `http://localhost:16686`. OTLP gRPC: `localhost:4317`. Image: `jaegertracing/all-in-one:1.76.0`.

Service names must be set or every span is `unknown_service`:

- `platform-api`
- `device-command-service`
- `reward-service`
- `model-gen-service`

## What a submission looks like

1. `platform-api` server span for `POST /api/submissions`.
2. After commit, the outbox publisher span for SNS `Publish`. The message carries one business attribute, `snsPublishedAt`. The javaagent adds trace context on top. Stay at or under 8 business attributes; AWS silently skips injection at 10.
3. `device-command-service` starts a **new** trace with a span link back to the publisher. That is the SQS receive-telemetry model. It is not one parent/child trace across the queue.
4. That consumer span calls `reward-service` `POST /reward/select` (HTTP, linked by the usual traceparent on the request) and then publishes MQTT.
5. `model-gen-service` is another new trace, linked from the same SNS message via `opentelemetry-instrumentation-boto3sqs`. Botocore alone does not do this.

Agent flags, in `infra/local/otel.env`:

- `otel.instrumentation.aws-sdk.experimental-use-propagator-for-messaging=true`
- `otel.instrumentation.messaging.experimental.receive-telemetry.enabled=true`
- `otel.instrumentation.logback-mdc.enabled=true`

## Logs

One submission's lines correlate on `trace_id` (agent MDC), `submissionId`, and `commandId`. Example shape from `OutboxPublisher` with structured logging on:

```json
{"service":{"name":"platform-api"},"message":"outbox published","commandId":"...","submissionId":"...","deviceId":"esp32-dev-001"}
```

## Device boundary

The ESP32 is not in the trace. Match `commandId` in the MQTT ack log, the DynamoDB item, and `mqtt.ack.latency`.
