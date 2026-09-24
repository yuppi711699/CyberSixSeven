import json
from pathlib import Path

from types import SimpleNamespace

import boto3
import pytest
import urllib3
from moto import mock_aws

from app.io import (
    AccessoryIo,
    PlatformCallbackError,
    accessory_key,
    platform_notifier,
)


def test_accessory_key_is_deterministic_and_lowercase(submission_id: str) -> None:
    assert accessory_key(submission_id) == f"accessories/{submission_id}/reward.stl"
    assert accessory_key(submission_id.upper()) == f"accessories/{submission_id}/reward.stl"


def test_accessory_key_rejects_non_uuid() -> None:
    with pytest.raises(ValueError):
        accessory_key("not-a-uuid")
    with pytest.raises(ValueError):
        accessory_key("")


@pytest.fixture
def s3_io(submission_id: str):
    with mock_aws():
        s3 = boto3.client("s3", region_name="us-west-2")
        s3.create_bucket(Bucket="cybersixseven-accessories")
        calls: list[tuple[str, str]] = []

        def notify(sid: str, key: str) -> None:
            calls.append((sid, key))

        yield AccessoryIo(s3, "cybersixseven-accessories", notify), s3, calls


def test_object_exists_and_private_upload(s3_io, submission_id: str, tmp_path: Path) -> None:
    io, s3, _calls = s3_io
    key = accessory_key(submission_id)
    assert io.object_exists(key) is False
    stl = tmp_path / "reward.stl"
    stl.write_bytes(b"solid x\nendsolid x\n")
    io.upload(key, stl)
    assert io.object_exists(key) is True
    listed = s3.list_objects_v2(Bucket="cybersixseven-accessories").get("Contents", [])
    assert [item["Key"] for item in listed] == [key]
    acl = s3.get_object_acl(Bucket="cybersixseven-accessories", Key=key)
    grants = acl.get("Grants", [])
    for grant in grants:
        uri = grant.get("Grantee", {}).get("URI", "")
        assert "AllUsers" not in uri
        assert "AuthenticatedUsers" not in uri


def test_platform_notifier_sends_internal_key_and_canonical_body(monkeypatch, submission_id: str) -> None:
    captured: dict = {}

    def fake_request(self, method, url, body=None, headers=None, **kwargs):
        captured["method"] = method
        captured["url"] = url
        captured["body"] = body
        captured["headers"] = headers
        return SimpleNamespace(status=200, data=b'{"accessoryStatus":"READY"}')

    monkeypatch.setattr(urllib3.PoolManager, "request", fake_request)
    notify = platform_notifier("http://platform:8080", "secret-key", connect=1.0, read=2.0)
    key = accessory_key(submission_id)
    notify(submission_id, key)
    assert captured["method"] == "PATCH"
    assert captured["url"].endswith(f"/internal/submissions/{submission_id}/accessory")
    assert json.loads(captured["body"]) == {"accessoryKey": key}
    assert captured["headers"]["X-Internal-Api-Key"] == "secret-key"
    assert "Authorization" not in captured["headers"]


def test_platform_notifier_raises_on_http_error(monkeypatch, submission_id: str) -> None:
    def fake_request(self, method, url, body=None, headers=None, **kwargs):
        return SimpleNamespace(status=409, data=b"nope")

    monkeypatch.setattr(urllib3.PoolManager, "request", fake_request)
    notify = platform_notifier("http://platform:8080", "secret-key", connect=1.0, read=2.0)
    with pytest.raises(PlatformCallbackError) as exc:
        notify(submission_id, accessory_key(submission_id))
    assert exc.value.status == 409
