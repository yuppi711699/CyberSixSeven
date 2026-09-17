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
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    queue_url = os.environ["MODEL_GEN_QUEUE_URL"]
    endpoint = os.environ.get("AWS_ENDPOINT_URL") or os.environ.get("AWS_ENDPOINT") or None
    region = os.environ.get("AWS_REGION", "us-east-1")
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
    log.info(
        "stage=%s submissionId=%s commandId=%s receiveCount=%s",
        stage,
        job.submission_id,
        job.command_id,
        receive_count,
    )


def main() -> None:
    poll_forever()


if __name__ == "__main__":
    main()
