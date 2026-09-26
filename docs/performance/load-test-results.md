# Load test results

`SubmissionSloTests` (20 sequential `POST /api/submissions` calls, one student, Testcontainers Postgres 17) is the submission SLI check. It fails the build when p95 is not under 300 ms.

The run that landed this milestone passed that assertion. Re-run:

```bash
./services/mvnw -f services/pom.xml -pl platform-api -Dtest=SubmissionSloTests test
```

SNS-to-MQTT p95 is the `sli.sns_to_mqtt` timer once Jaeger and both JVM processes are up with the javaagent. It is not a single parent span: the SQS consumer starts a new trace linked to the publisher. Physical ACK latency is `mqtt.ack.latency` and is outside the 2 s SLI.
