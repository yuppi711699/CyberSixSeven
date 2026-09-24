"""Shared AWS dummy credentials for moto 5."""

import os

import pytest

os.environ.setdefault("AWS_ACCESS_KEY_ID", "test")
os.environ.setdefault("AWS_SECRET_ACCESS_KEY", "test")
os.environ.setdefault("AWS_DEFAULT_REGION", "us-west-2")
os.environ.setdefault("AWS_REGION", "us-west-2")


@pytest.fixture
def submission_id() -> str:
    return "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
