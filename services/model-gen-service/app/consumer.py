"""SQS poller for model-gen-jobs. Own process — never a FastAPI lifespan hook."""

from __future__ import annotations

import json
import logging
import os
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path

from botocore.client import BaseClient

from app.geometry import generate_accessory
from app.io import AccessoryIo, accessory_key, aws_client, io_from_env

log = logging.getLogger("model-gen-service")

GenerateFn = Callable[..., Path]
_tracing_configured = False


def configure_tracing() -> bool:
    """Install SQS propagation. Returns False when the SDK is explicitly disabled (tests)."""
    global _tracing_configured
    if os.environ.get("OTEL_SDK_DISABLED", "").lower() == "true":
        return False
    if _tracing_configured:
        return True
    from opentelemetry import trace
    from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
    from opentelemetry.instrumentation.boto3sqs import Boto3SQSInstrumentor
    from opentelemetry.instrumentation.botocore import BotocoreInstrumentor
    from opentelemetry.sdk.resources import Resource
    from opentelemetry.sdk.trace import TracerProvider
    from opentelemetry.sdk.trace.export import BatchSpanProcessor

    service = os.environ.get("OTEL_SERVICE_NAME", "model-gen-service")
    endpoint = os.environ.get("OTEL_EXPORTER_OTLP_ENDPOINT", "http://127.0.0.1:4317")
    provider = TracerProvider(resource=Resource.create({"service.name": service}))
    provider.add_span_processor(BatchSpanProcessor(OTLPSpanExporter(endpoint=endpoint, insecure=True)))
    trace.set_tracer_provider(provider)
    # botocore does not propagate SQS. boto3sqs extracts context and starts a linked trace.
    BotocoreInstrumentor().instrument()
    Boto3SQSInstrumentor().instrument()
    _tracing_configured = True
    return True


@dataclass(frozen=True)
class Job:
    submission_id: str
    command_id: str | None


class EnvelopeError(ValueError):
    """Raised when the queue delivered an SNS notification wrapper."""


def parse_job(body: str) -> Job:
    data = json.loads(body)
    if isinstance(data, dict) and data.get("Type") == "Notification" and "Message" in data:
        raise EnvelopeError("SNS envelope received; RawMessageDelivery must be true")
    submission_id = data.get("submissionId")
    if not submission_id:
        raise ValueError("submissionId is required")
    command_id = data.get("commandId")
    return Job(str(submission_id), str(command_id) if command_id is not None else None)


def process_message(
    body: str,
    *,
    receive_count: int,
    io: AccessoryIo,
    generate: GenerateFn = generate_accessory,
) -> None:
    job = parse_job(body)
    key = accessory_key(job.submission_id)
    _log(job, receive_count, "head")
    if io.object_exists(key):
        _log(job, receive_count, "skip-upload")
    else:
        _log(job, receive_count, "generate")
        path = generate()
        try:
            _log(job, receive_count, "upload")
            io.upload(key, path)
        finally:
            path.unlink(missing_ok=True)
    _log(job, receive_count, "patch")
    io.notify_platform(job.submission_id, key)
    _log(job, receive_count, "done")


def handle_received(
    sqs: BaseClient,
    queue_url: str,
    message: dict,
    io: AccessoryIo,
    generate: GenerateFn = generate_accessory,
) -> bool:
    """Process one SQS message. Delete only after PATCH succeeds. Returns True if deleted."""
    receive_count = int(message.get("Attributes", {}).get("ApproximateReceiveCount", "1"))
    try:
        process_message(
            message["Body"],
            receive_count=receive_count,
            io=io,
            generate=generate,
        )
    except Exception:
        submission_id = _peek_submission_id(message.get("Body", ""))
        log.error(
            "stage=failed submissionId=%s receiveCount=%s",
            submission_id,
            receive_count,
            exc_info=True,
        )
        return False
    sqs.delete_message(QueueUrl=queue_url, ReceiptHandle=message["ReceiptHandle"])
    return True


def poll_forever(io: AccessoryIo | None = None) -> None:
    logging.basicConfig(level=logging.INFO, format="%(message)s")
    configure_tracing()
    queue_url = os.environ["MODEL_GEN_QUEUE_URL"]
    endpoint = os.environ.get("AWS_ENDPOINT_URL") or os.environ.get("AWS_ENDPOINT") or None
    region = os.environ.get("AWS_REGION", "us-west-2")
    visibility = int(os.environ.get("MODEL_GEN_VISIBILITY_TIMEOUT_SECONDS", "300"))
    accessory_io = io if io is not None else io_from_env()
    sqs = aws_client(
        "sqs",
        endpoint_url=endpoint,
        region=region,
        connect=float(os.environ.get("AWS_CONNECT_TIMEOUT_SECONDS", "2")),
        read=float(os.environ.get("AWS_READ_TIMEOUT_SECONDS", "30")),
    )
    log.info("polling queue=%s", queue_url)
    while True:
        response = sqs.receive_message(
            QueueUrl=queue_url,
            MaxNumberOfMessages=1,
            WaitTimeSeconds=20,
            VisibilityTimeout=visibility,
            AttributeNames=["ApproximateReceiveCount"],
        )
        for message in response.get("Messages", []):
            handle_received(sqs, queue_url, message, accessory_io)


def _peek_submission_id(body: str) -> str:
    try:
        return parse_job(body).submission_id
    except (EnvelopeError, ValueError, json.JSONDecodeError, TypeError):
        return "unknown"


def _log(job: Job, receive_count: int, stage: str) -> None:
    trace_id = None
    try:
        from opentelemetry import trace

        span = trace.get_current_span().get_span_context()
        if span.is_valid:
            trace_id = format(span.trace_id, "032x")
    except ImportError:
        trace_id = None
    log.info(
        json.dumps(
            {
                "service.name": "model-gen-service",
                "stage": stage,
                "submissionId": job.submission_id,
                "commandId": job.command_id,
                "receiveCount": receive_count,
                "trace_id": trace_id,
            }
        )
    )


def main() -> None:
    poll_forever()


if __name__ == "__main__":
    main()
