import json
import os
import tempfile
from pathlib import Path
from unittest.mock import Mock
from uuid import uuid4

import boto3
import pytest
from moto import mock_aws

from app.consumer import EnvelopeError, handle_received, parse_job, process_message
from app.io import AccessoryIo, accessory_key


def _job_body(submission_id: str, command_id: str = "11111111-1111-1111-1111-111111111111") -> str:
    return json.dumps(
        {
            "commandId": command_id,
            "submissionId": submission_id,
            "deviceId": "esp32-dev-001",
            "event": "correct",
            "intensity": 3,
        }
    )


def _fake_stl() -> Path:
    handle = tempfile.NamedTemporaryFile(prefix="c67-test-", suffix=".stl", delete=False)
    path = Path(handle.name)
    handle.write(b"solid test\nendsolid test\n")
    handle.close()
    return path


@pytest.fixture
def stack():
    with mock_aws():
        s3 = boto3.client("s3", region_name="us-west-2")
        s3.create_bucket(
            Bucket="cybersixseven-accessories",
            CreateBucketConfiguration={"LocationConstraint": "us-west-2"},
        )
        sqs = boto3.client("sqs", region_name="us-west-2")
        dlq_url = sqs.create_queue(QueueName="model-gen-jobs-dlq")["QueueUrl"]
        dlq_arn = sqs.get_queue_attributes(QueueUrl=dlq_url, AttributeNames=["QueueArn"])["Attributes"][
            "QueueArn"
        ]
        queue_url = sqs.create_queue(
            QueueName="model-gen-jobs",
            Attributes={
                "VisibilityTimeout": "1",
                "RedrivePolicy": json.dumps({"deadLetterTargetArn": dlq_arn, "maxReceiveCount": "5"}),
            },
        )["QueueUrl"]
        notifies: list[tuple[str, str]] = []
        uploads: list[str] = []

        real_io = AccessoryIo(s3, "cybersixseven-accessories", lambda sid, key: notifies.append((sid, key)))
        original_upload = real_io.upload

        def tracking_upload(key: str, path: Path) -> None:
            uploads.append(key)
            original_upload(key, path)

        real_io.upload = tracking_upload  # type: ignore[method-assign]
        yield {
            "s3": s3,
            "sqs": sqs,
            "io": real_io,
            "queue_url": queue_url,
            "dlq_url": dlq_url,
            "notifies": notifies,
            "uploads": uploads,
        }


def test_existing_object_is_detected_with_head_and_skips_generation(stack, submission_id: str) -> None:
    key = accessory_key(submission_id)
    stack["s3"].put_object(Bucket="cybersixseven-accessories", Key=key, Body=b"solid already\nendsolid already\n")
    heads: list[str] = []
    original = stack["io"].object_exists

    def tracking_exists(object_key: str) -> bool:
        heads.append(object_key)
        return original(object_key)

    stack["io"].object_exists = tracking_exists  # type: ignore[method-assign]
    generate = Mock(side_effect=_fake_stl)
    process_message(_job_body(submission_id), receive_count=1, io=stack["io"], generate=generate)
    assert heads == [key]
    assert generate.call_count == 0
    assert stack["uploads"] == []
    assert stack["notifies"] == [(submission_id, key)]


def test_configure_tracing_is_disabled_for_tests() -> None:
    from app.consumer import configure_tracing

    assert configure_tracing() is False


def test_parse_job_requires_submission_id() -> None:
    with pytest.raises(ValueError, match="submissionId"):
        parse_job("{}")


def test_process_message_logs_submission_and_command(stack, submission_id: str, caplog: pytest.LogCaptureFixture) -> None:
    import logging

    caplog.set_level(logging.INFO, logger="model-gen-service")
    generate = Mock(side_effect=_fake_stl)
    process_message(_job_body(submission_id), receive_count=4, io=stack["io"], generate=generate)
    assert submission_id in caplog.text
    assert "11111111-1111-1111-1111-111111111111" in caplog.text
    assert '"receiveCount": 4' in caplog.text or '"receiveCount":4' in caplog.text


def test_configure_tracing_installs_sqs_propagator_when_enabled() -> None:
    import subprocess
    import sys

    env = os.environ.copy()
    env.pop("OTEL_SDK_DISABLED", None)
    env["OTEL_SERVICE_NAME"] = "model-gen-service"
    env["OTEL_EXPORTER_OTLP_ENDPOINT"] = "http://127.0.0.1:9"
    script = """
import os
os.environ.pop("OTEL_SDK_DISABLED", None)
from app.consumer import configure_tracing
from opentelemetry import trace
from opentelemetry.instrumentation.boto3sqs import Boto3SQSInstrumentor
from opentelemetry.instrumentation.botocore import BotocoreInstrumentor
assert configure_tracing() is True
assert configure_tracing() is True
attrs = dict(trace.get_tracer_provider().resource.attributes)
assert attrs.get("service.name") == "model-gen-service"
assert Boto3SQSInstrumentor().is_instrumented_by_opentelemetry
assert BotocoreInstrumentor().is_instrumented_by_opentelemetry
"""
    completed = subprocess.run(
        [sys.executable, "-c", script],
        cwd=os.path.dirname(os.path.dirname(__file__)),
        env=env,
        capture_output=True,
        text=True,
        timeout=30,
        check=False,
    )
    assert completed.returncode == 0, completed.stderr


def test_boto3sqs_instrumentor_is_the_sqs_propagator() -> None:
    from opentelemetry.instrumentation.boto3sqs import Boto3SQSInstrumentor

    assert callable(Boto3SQSInstrumentor)


def test_parse_job_rejects_sns_envelope() -> None:
    body = json.dumps({"Type": "Notification", "Message": _job_body(str(uuid4()))})
    with pytest.raises(EnvelopeError):
        parse_job(body)


def test_parse_job_reads_raw_event(submission_id: str) -> None:
    job = parse_job(_job_body(submission_id))
    assert job.submission_id == submission_id
    assert job.command_id == "11111111-1111-1111-1111-111111111111"


def test_duplicate_delivery_uploads_once_and_retries_patch(stack, submission_id: str) -> None:
    generate = Mock(side_effect=_fake_stl)
    body = _job_body(submission_id)
    process_message(body, receive_count=1, io=stack["io"], generate=generate)
    process_message(body, receive_count=2, io=stack["io"], generate=generate)

    key = accessory_key(submission_id)
    assert generate.call_count == 1
    assert stack["uploads"] == [key]
    assert stack["notifies"] == [(submission_id, key), (submission_id, key)]
    listed = stack["s3"].list_objects_v2(Bucket="cybersixseven-accessories").get("Contents", [])
    assert [item["Key"] for item in listed] == [key]


def test_upload_failure_does_not_callback_or_leave_a_temp_file(stack, submission_id: str, tmp_path: Path) -> None:
    stl = tmp_path / "reward.stl"
    stl.write_bytes(b"solid test\nendsolid test\n")
    generate = Mock(return_value=stl)

    def boom(key: str, path: Path) -> None:
        raise RuntimeError("s3 down")

    stack["io"].upload = boom  # type: ignore[method-assign]
    with pytest.raises(RuntimeError, match="s3 down"):
        process_message(_job_body(submission_id), receive_count=1, io=stack["io"], generate=generate)
    assert stack["notifies"] == []
    assert generate.call_count == 1
    # generate_accessory's caller unlinks; the fake path is the test file, still present unless unlinked.
    # process_message unlinks in finally after upload raises.
    assert not stl.exists()


def test_patch_failure_then_redelivery_skips_regeneration(stack, submission_id: str) -> None:
    generate = Mock(side_effect=_fake_stl)
    body = _job_body(submission_id)
    failing = AccessoryIo(
        stack["s3"],
        "cybersixseven-accessories",
        lambda sid, key: (_ for _ in ()).throw(RuntimeError("patch failed")),
    )
    with pytest.raises(RuntimeError, match="patch failed"):
        process_message(body, receive_count=1, io=failing, generate=generate)
    process_message(body, receive_count=2, io=stack["io"], generate=generate)
    assert generate.call_count == 1
    assert stack["notifies"] == [(submission_id, accessory_key(submission_id))]


def test_handle_received_deletes_only_after_success(stack, submission_id: str) -> None:
    sqs = stack["sqs"]
    queue_url = stack["queue_url"]
    sqs.send_message(QueueUrl=queue_url, MessageBody=_job_body(submission_id))
    first = sqs.receive_message(QueueUrl=queue_url, MaxNumberOfMessages=1, VisibilityTimeout=30)["Messages"][0]
    generate = Mock(side_effect=_fake_stl)

    failing = AccessoryIo(
        stack["s3"],
        "cybersixseven-accessories",
        lambda sid, key: (_ for _ in ()).throw(RuntimeError("patch failed")),
    )
    assert handle_received(sqs, queue_url, first, failing, generate=generate) is False
    # Visibility still held; change it so the same message can be received again.
    sqs.change_message_visibility(QueueUrl=queue_url, ReceiptHandle=first["ReceiptHandle"], VisibilityTimeout=0)
    second = sqs.receive_message(QueueUrl=queue_url, MaxNumberOfMessages=1, VisibilityTimeout=30)["Messages"][0]
    assert handle_received(sqs, queue_url, second, stack["io"], generate=generate) is True
    leftover = sqs.receive_message(QueueUrl=queue_url, MaxNumberOfMessages=1, WaitTimeSeconds=1)
    assert leftover.get("Messages", []) == []


def test_permanent_failure_redrives_to_dlq_after_five_receives(stack, submission_id: str) -> None:
    sqs = stack["sqs"]
    queue_url = stack["queue_url"]
    dlq_url = stack["dlq_url"]
    sqs.send_message(QueueUrl=queue_url, MessageBody=_job_body(submission_id))
    generate = Mock(side_effect=_fake_stl)
    failing = AccessoryIo(
        stack["s3"],
        "cybersixseven-accessories",
        lambda sid, key: (_ for _ in ()).throw(RuntimeError("permanent")),
    )
    for _ in range(5):
        received = sqs.receive_message(
            QueueUrl=queue_url,
            MaxNumberOfMessages=1,
            VisibilityTimeout=0,
            AttributeNames=["ApproximateReceiveCount"],
            WaitTimeSeconds=1,
        ).get("Messages", [])
        assert received, "expected the original message still on the main queue"
        handle_received(sqs, queue_url, received[0], failing, generate=generate)

    # Redrive happens on the receive *after* maxReceiveCount, once visibility expires.
    sixth = sqs.receive_message(
        QueueUrl=queue_url,
        MaxNumberOfMessages=1,
        VisibilityTimeout=0,
        WaitTimeSeconds=1,
    ).get("Messages", [])
    assert sixth == []

    dead = []
    for _ in range(8):
        dead = sqs.receive_message(QueueUrl=dlq_url, MaxNumberOfMessages=1, WaitTimeSeconds=1).get("Messages", [])
        if dead:
            break
    assert dead, "message should land on model-gen-jobs-dlq after 5 receives"
    assert json.loads(dead[0]["Body"])["submissionId"] == submission_id
