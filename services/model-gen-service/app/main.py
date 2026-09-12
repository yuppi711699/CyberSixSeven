"""FastAPI application for model-gen-service.

v0.1 scope is deliberately tiny: a liveness endpoint and nothing else. The
service's real job — generating ``.stl`` files — is exercised standalone by
``scripts/test_generate.py`` until the SQS consumer and S3 upload land in v0.4.

Run locally (port 8000 is this service's slot in the fixed port map):

    uvicorn app.main:app --reload --port 8000
"""

from fastapi import FastAPI

app = FastAPI(title="model-gen-service", version="0.1.0")


@app.get("/health")
def health() -> dict[str, str]:
    """Liveness probe. Returns ``{"status": "ok"}``."""
    return {"status": "ok"}
