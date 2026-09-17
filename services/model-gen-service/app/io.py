"""S3 upload and internal platform callback. No scoring, no Postgres."""

from __future__ import annotations

import json
import os
from collections.abc import Callable
from pathlib import Path
from uuid import UUID

import boto3
import urllib3
from botocore.client import BaseClient
from botocore.config import Config
from botocore.exceptions import ClientError

BUCKET = "cybersixseven-accessories"
OBJECT_NAME = "reward.stl"
INTERNAL_KEY_HEADER = "X-Internal-Api-Key"


def accessory_key(submission_id: str) -> str:
    """Deterministic private key: accessories/{submissionId}/reward.stl."""
    try:
        normalized = str(UUID(str(submission_id)))
    except (ValueError, TypeError, AttributeError) as exc:
        raise ValueError("submissionId must be a UUID") from exc
    return f"accessories/{normalized}/{OBJECT_NAME}"


class PlatformCallbackError(RuntimeError):
    def __init__(self, status: int, body: bytes) -> None:
        super().__init__(f"platform accessory PATCH failed status={status}")
        self.status = status
        self.body = body


def aws_client(service: str, *, endpoint_url: str | None, region: str, connect: float, read: float) -> BaseClient:
    return boto3.client(
        service,
        endpoint_url=endpoint_url or None,
        region_name=region,
        config=Config(
            connect_timeout=connect,
            read_timeout=read,
            retries={"max_attempts": 3},
        ),
    )


def platform_notifier(
    base_url: str,
    api_key: str,
    *,
    connect: float,
    read: float,
) -> Callable[[str, str], None]:
    if not base_url or not api_key:
        raise ValueError("PLATFORM_API_BASE_URL and INTERNAL_API_KEY are required")
    http = urllib3.PoolManager(timeout=urllib3.Timeout(connect=connect, read=read))
    root = base_url.rstrip("/")

    def notify(submission_id: str, key: str) -> None:
        url = f"{root}/internal/submissions/{submission_id}/accessory"
        response = http.request(
            "PATCH",
            url,
            body=json.dumps({"accessoryKey": key}).encode("utf-8"),
            headers={
                "Content-Type": "application/json",
                "Accept": "application/json",
                INTERNAL_KEY_HEADER: api_key,
            },
        )
        if response.status >= 400:
            raise PlatformCallbackError(response.status, response.data or b"")

    return notify


class AccessoryIo:
    def __init__(
        self,
        s3: BaseClient,
        bucket: str,
        notify: Callable[[str, str], None],
    ) -> None:
        self._s3 = s3
        self._bucket = bucket
        self._notify = notify

    def object_exists(self, key: str) -> bool:
        try:
            self._s3.head_object(Bucket=self._bucket, Key=key)
            return True
        except ClientError as exc:
            code = str(exc.response.get("Error", {}).get("Code", ""))
            http_status = exc.response.get("ResponseMetadata", {}).get("HTTPStatusCode")
            if code in {"404", "NoSuchKey", "NotFound"} or http_status == 404:
                return False
            raise

    def upload(self, key: str, path: Path) -> None:
        with path.open("rb") as handle:
            self._s3.put_object(
                Bucket=self._bucket,
                Key=key,
                Body=handle,
                ContentType="model/stl",
            )

    def notify_platform(self, submission_id: str, key: str) -> None:
        self._notify(submission_id, key)


def io_from_env() -> AccessoryIo:
    endpoint = os.environ.get("AWS_ENDPOINT_URL") or os.environ.get("AWS_ENDPOINT") or None
    region = os.environ.get("AWS_REGION", "us-east-1")
    connect = float(os.environ.get("AWS_CONNECT_TIMEOUT_SECONDS", "2"))
    read = float(os.environ.get("AWS_READ_TIMEOUT_SECONDS", "30"))
    bucket = os.environ.get("ACCESSORY_BUCKET", BUCKET)
    s3 = aws_client("s3", endpoint_url=endpoint, region=region, connect=connect, read=read)
    notify = platform_notifier(
        os.environ.get("PLATFORM_API_BASE_URL", ""),
        os.environ.get("INTERNAL_API_KEY", ""),
        connect=float(os.environ.get("HTTP_CONNECT_TIMEOUT_SECONDS", "2")),
        read=float(os.environ.get("HTTP_READ_TIMEOUT_SECONDS", "10")),
    )
    return AccessoryIo(s3, bucket, notify)
